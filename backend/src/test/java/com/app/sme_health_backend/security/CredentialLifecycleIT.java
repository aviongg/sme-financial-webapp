package com.app.sme_health_backend.security;

import com.app.sme_health_backend.identity.credential.service.PasswordResetService;
import com.app.sme_health_backend.identity.credential.dto.PasswordResetConfirmRequest;
import com.app.sme_health_backend.identity.credential.dto.PasswordResetRequest;
import com.app.sme_health_backend.identity.credential.entity.PasswordResetToken;
import com.app.sme_health_backend.identity.credential.notification.InMemoryPasswordResetNotifier;
import com.app.sme_health_backend.identity.credential.repository.PasswordResetTokenRepository;
import com.app.sme_health_backend.identity.credential.dto.ChangePasswordRequest;
import com.app.sme_health_backend.identity.dto.LoginRequest;
import com.app.sme_health_backend.identity.dto.RegisterRequest;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.model.AccountStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.service.AuthenticationService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class CredentialLifecycleIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PasswordResetService resetService;

    @Autowired
    private PasswordResetTokenRepository resetTokenRepository;

    @Autowired
    private InMemoryPasswordResetNotifier resetNotifier;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String obtainCsrfToken(MockHttpSession session) throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn();
        String json = csrfResult.getResponse().getContentAsString();
        return objectMapper.readTree(json).get("token").asText();
    }

    @Test
    @DisplayName("must_change_password blocks tenant features and forces password change before full login")
    void testMustChangePasswordBlocksFeatures() throws Exception {
        String email = "must-change-" + UUID.randomUUID() + "@example.com";
        String initialPassword = "initial-password-123";

        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(initialPassword));
        user.setFullName("Forced Password User");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setMustChangePassword(true);
        user.setAuthVersion(0L);
        userRepository.saveAndFlush(user);

        // Login -> returns PASSWORD_CHANGE_REQUIRED
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, initialPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authStage").value("PASSWORD_CHANGE_REQUIRED"))
                .andReturn();

        jakarta.servlet.http.Cookie sessionCookie = loginResult.getResponse().getCookie("FINSIGHT_SESSION");
        assertNotNull(sessionCookie);

        // Attempting to access Feature 1-13 (/api/businesses) is blocked with 403
        mockMvc.perform(get("/api/businesses")
                        .cookie(sessionCookie))
                .andExpect(status().isForbidden());

        // Perform change-password
        String newPassword = "new-strong-password-456";
        mockMvc.perform(post("/api/auth/change-password")
                        .cookie(sessionCookie)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequest(initialPassword, newPassword))))
                .andExpect(status().isOk());

        // User must_change_password should now be false, auth_version incremented
        AppUser updated = userRepository.findByEmail(email).orElseThrow();
        assertFalse(updated.isMustChangePassword());
        assertEquals(1L, updated.getAuthVersion());

        // Old session should be invalidated; logging in with new password grants full authentication
        mockMvc.perform(post("/api/auth/login")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, newPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authStage").value("FULLY_AUTHENTICATED"));
    }

    @Test
    @DisplayName("Password change rejects invalid current password and rejects password reuse")
    void testPasswordChangeRejections() throws Exception {
        String email = "pw-reject-" + UUID.randomUUID() + "@example.com";
        String currentPassword = "current-password-123";

        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(currentPassword));
        user.setFullName("Password Reject User");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setMustChangePassword(false);
        user.setAuthVersion(0L);
        userRepository.saveAndFlush(user);

        // Login
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, currentPassword))))
                .andExpect(status().isOk())
                .andReturn();

        jakarta.servlet.http.Cookie sessionCookie = loginResult.getResponse().getCookie("FINSIGHT_SESSION");
        assertNotNull(sessionCookie);

        // Wrong current password
        mockMvc.perform(post("/api/auth/change-password")
                        .cookie(sessionCookie)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequest("wrong-password-999", "new-valid-pass-123"))))
                .andExpect(status().isUnauthorized());

        // Same password reuse
        mockMvc.perform(post("/api/auth/change-password")
                        .cookie(sessionCookie)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequest(currentPassword, currentPassword))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Password reset: anti-enumeration response, raw token never in DB, one-time consumption, auth_version increment")
    void testPasswordResetLifecycle() throws Exception {
        String email = "reset-test-" + UUID.randomUUID() + "@example.com";
        String oldPassword = "old-password-123";

        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(oldPassword));
        user.setFullName("Reset Test User");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setMustChangePassword(false);
        user.setAuthVersion(0L);
        userRepository.saveAndFlush(user);

        // 1. Anti-enumeration: unknown email returns same 200 response
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequest("unknown-email@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        // 2. Anti-enumeration: valid email returns same 200 response
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequest(email))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        String rawToken = resetNotifier.getLastDeliveredRawToken(email);
        assertNotNull(rawToken, "Reset raw token must be captured by notifier");

        // 3. Raw token is NEVER stored in database
        List<PasswordResetToken> tokens = resetTokenRepository.findAll();
        for (PasswordResetToken t : tokens) {
            assertNotEquals(rawToken, t.getTokenHash(), "Raw token must never be persisted");
            assertEquals(64, t.getTokenHash().length(), "Token in DB must be 64-char hex SHA-256 hash");
        }

        // 4. Confirm password reset
        String resetPassword = "new-reset-password-123";
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetConfirmRequest(rawToken, resetPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        // User auth_version incremented
        AppUser reloaded = userRepository.findByEmail(email).orElseThrow();
        assertEquals(1L, reloaded.getAuthVersion());

        // 5. One-time consumption: using the same token again fails
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetConfirmRequest(rawToken, "another-password-123"))))
                .andExpect(status().isBadRequest());

        // 6. Login with new password works
        MockHttpSession loginSession = new MockHttpSession();

        mockMvc.perform(post("/api/auth/login")
                        .session(loginSession)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, resetPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authStage").value("FULLY_AUTHENTICATED"));
    }

    @Test
    @DisplayName("Concurrent password reset confirmation: exactly one succeeds and second is rejected")
    void testConcurrentPasswordResetConfirmation() throws Exception {
        String email = "reset-concur-" + UUID.randomUUID() + "@example.com";
        String oldPassword = "old-password-123";

        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(oldPassword));
        user.setFullName("Concurrent Reset User");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setMustChangePassword(false);
        user.setAuthVersion(0L);
        userRepository.saveAndFlush(user);

        // Request reset
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequest(email))))
                .andExpect(status().isOk());

        String rawToken = resetNotifier.getLastDeliveredRawToken(email);
        assertNotNull(rawToken);

        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final String newPassword = "new-password-" + i + "-!xyz";
            executor.submit(() -> {
                try {
                    latch.await();
                    MvcResult result = mockMvc.perform(post("/api/auth/password-reset/confirm")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(new PasswordResetConfirmRequest(rawToken, newPassword))))
                            .andReturn();
                    if (result.getResponse().getStatus() == 200) {
                        successCount.incrementAndGet();
                    } else if (result.getResponse().getStatus() == 400) {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Exactly one thread succeeds in claiming the token
        assertEquals(1, successCount.get(), "Exactly one concurrent confirmation must succeed");
        assertEquals(threads - 1, failureCount.get(), "Remaining concurrent confirmations must be rejected with 400");
    }

    @Test
    @DisplayName("Session-bound mutations require CSRF token whereas reset request is narrow exception")
    void testSessionBoundMutationsRequireCsrf() throws Exception {
        String email = "csrf-audit-" + UUID.randomUUID() + "@example.com";
        String password = "audit-password-123";

        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFullName("CSRF Audit User");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setMustChangePassword(false);
        user.setAuthVersion(0L);
        userRepository.saveAndFlush(user);

        // Login to acquire session cookie
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();

        jakarta.servlet.http.Cookie sessionCookie = loginResult.getResponse().getCookie("FINSIGHT_SESSION");
        assertNotNull(sessionCookie);

        // 1. POST /api/auth/change-password without CSRF -> 403 Forbidden
        mockMvc.perform(post("/api/auth/change-password")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequest(password, "new-pass-123!456"))))
                .andExpect(status().isForbidden());

        // 2. POST /api/auth/mfa/enroll/initiate without CSRF -> 403 Forbidden
        mockMvc.perform(post("/api/auth/mfa/enroll/initiate")
                        .cookie(sessionCookie))
                .andExpect(status().isForbidden());

        // 3. POST /api/auth/mfa/disable without CSRF -> 403 Forbidden
        mockMvc.perform(post("/api/auth/mfa/disable")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + password + "\",\"verificationCode\":\"123456\"}"))
                .andExpect(status().isForbidden());

        // 4. Narrow unauthenticated exception: POST /api/auth/password-reset/request works WITHOUT CSRF
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequest(email))))
                .andExpect(status().isOk());
    }
}
