package com.app.sme_health_backend.security;

import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.audit.model.AuditOutcome;
import com.app.sme_health_backend.audit.service.SecurityAuditService;
import com.app.sme_health_backend.crypto.SensitiveDataCipher;
import com.app.sme_health_backend.identity.controller.AuthController;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.mfa.entity.UserMfa;
import com.app.sme_health_backend.mfa.entity.UserMfaRecoveryCode;
import com.app.sme_health_backend.mfa.repository.UserMfaRecoveryCodeRepository;
import com.app.sme_health_backend.mfa.repository.UserMfaRepository;
import com.app.sme_health_backend.mfa.service.MfaAlreadyEnabledException;
import com.app.sme_health_backend.mfa.service.MfaService;
import com.app.sme_health_backend.mfa.service.TotpEngine;
import com.app.sme_health_backend.security.service.AppUserDetails;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MfaEnrollmentProtectionTest {
    private final UUID userId = UUID.randomUUID();
    private UserMfaRepository factors;
    private UserMfaRecoveryCodeRepository recovery;
    private AppUserRepository users;
    private TotpEngine totp;
    private SensitiveDataCipher cipher;
    private PasswordEncoder passwords;
    private SecurityAuditService audit;
    private MfaService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        factors = mock(UserMfaRepository.class);
        recovery = mock(UserMfaRecoveryCodeRepository.class);
        users = mock(AppUserRepository.class);
        totp = mock(TotpEngine.class);
        cipher = mock(SensitiveDataCipher.class);
        passwords = mock(PasswordEncoder.class);
        audit = mock(SecurityAuditService.class);
        service = new MfaService(factors, recovery, users, totp, cipher, passwords, null, audit);
        user = new AppUser();
        user.setId(userId);
        user.setEmail("enrollment@example.test");
        user.setPasswordHash("fixture-password-hash");
        user.setAuthVersion(4L);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(users.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
    }

    @AfterEach
    void cleanUp() { SecurityContextHolder.clearContext(); }

    @Test
    void enabledEnrollmentEndpointReturnsConflictPreservesFactorAndRecoveryCodeRemainsUsable() throws Exception {
        UserMfa enabled = new UserMfa(userId, "encrypted-original-factor", "ENABLED");
        OffsetDateTime original = OffsetDateTime.now().minusDays(1);
        enabled.setCreatedAt(original);
        enabled.setEnabledAt(original);
        enabled.setVerifiedAt(original);
        enabled.setLastUsedTimeStep(1234L);
        when(factors.findByUserIdForUpdate(userId)).thenReturn(Optional.of(enabled));
        AppUserDetails details = new AppUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AuthController(null, null, service, users, null))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(post("/api/auth/mfa/enroll/initiate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("mfa_already_enabled"));
        assertEquals("encrypted-original-factor", enabled.getTotpSecret());
        assertEquals("ENABLED", enabled.getStatus());
        assertEquals(original, enabled.getCreatedAt());
        assertEquals(original, enabled.getEnabledAt());
        assertEquals(original, enabled.getVerifiedAt());
        assertEquals(1234L, enabled.getLastUsedTimeStep());
        assertEquals(4L, user.getAuthVersion());
        verify(factors, never()).save(any());
        verify(users, never()).save(any());
        verifyNoInteractions(totp, cipher, recovery);
        verify(audit).recordSecurityEvent(eq(AuditEventType.MFA_ENROLLMENT_REJECTED), eq(userId),
                isNull(), isNull(), eq("MFA"), eq(userId.toString()), eq(AuditOutcome.FAILURE),
                any(), eq(Map.of("reason", "mfa_already_enabled")));

        UserMfaRecoveryCode existing = new UserMfaRecoveryCode(userId, "existing-recovery-hash");
        existing.setId(UUID.randomUUID());
        when(recovery.findByUserIdAndUsedAtIsNull(userId)).thenReturn(List.of(existing));
        when(passwords.matches("OLDRECOVERY", "existing-recovery-hash")).thenReturn(true);
        when(recovery.claimCode(eq(existing.getId()), any())).thenReturn(1);
        assertTrue(service.verifyRecoveryCode(userId, "OLDRECOVERY"));
        verify(recovery, never()).deleteByUserId(any());
    }

    @Test
    void serviceRejectsEnabledFactorBeforeSecretGeneration() {
        when(factors.findByUserIdForUpdate(userId))
                .thenReturn(Optional.of(new UserMfa(userId, "ciphertext", "ENABLED")));
        assertThrows(MfaAlreadyEnabledException.class, () -> service.initiateEnrollment(userId, user.getEmail()));
        verifyNoInteractions(cipher, totp, recovery);
    }

    @Test
    void restartingPendingSetupRenewsDeadlineAndReplacesOnlyPendingSecret() {
        UserMfa pending = new UserMfa(userId, "old-encrypted-pending", "PENDING");
        pending.setCreatedAt(OffsetDateTime.now().minusMinutes(16));
        when(factors.findByUserIdForUpdate(userId)).thenReturn(Optional.of(pending));
        when(totp.generateSecret()).thenReturn("new-pending-secret");
        when(cipher.encrypt("new-pending-secret", MfaService.getAad(userId))).thenReturn("new-encrypted-pending");
        OffsetDateTime before = OffsetDateTime.now();
        service.initiateEnrollment(userId, user.getEmail());
        assertEquals("new-encrypted-pending", pending.getTotpSecret());
        assertEquals("PENDING", pending.getStatus());
        assertFalse(pending.getCreatedAt().isBefore(before));
        assertNull(pending.getEnabledAt());
        assertNull(pending.getVerifiedAt());
        assertNull(pending.getLastUsedTimeStep());
        verifyNoInteractions(recovery);
    }

    @Test
    void firstPlatformAdminEnrollmentCreatesPendingAndConfirmationEnablesIt() {
        user.setPlatformRole("PLATFORM_ADMIN");
        when(totp.generateSecret()).thenReturn("first-secret");
        when(cipher.encrypt("first-secret", MfaService.getAad(userId))).thenReturn("first-ciphertext");
        when(factors.save(any())).thenAnswer(invocation -> {
            UserMfa saved = invocation.getArgument(0);
            when(factors.findByUserIdForUpdate(userId)).thenReturn(Optional.of(saved));
            return saved;
        });
        service.initiateEnrollment(userId, user.getEmail());
        UserMfa pending = factors.findByUserIdForUpdate(userId).orElseThrow();
        assertEquals("PENDING", pending.getStatus());
        when(passwords.matches("current-password", user.getPasswordHash())).thenReturn(true);
        when(cipher.decrypt("first-ciphertext", MfaService.getAad(userId))).thenReturn("first-secret");
        when(totp.validateCode(eq("first-secret"), eq("123456"), anyLong())).thenReturn(OptionalLong.of(42L));
        when(passwords.encode(anyString())).thenReturn("hashed-recovery-fixture");
        assertEquals(10, service.confirmEnrollment(userId, "current-password", "123456").size());
        assertEquals("ENABLED", pending.getStatus());
        assertNotNull(pending.getEnabledAt());
        assertNotNull(pending.getVerifiedAt());
        assertEquals(42L, pending.getLastUsedTimeStep());
        verify(recovery, times(10)).save(any());
    }
}
