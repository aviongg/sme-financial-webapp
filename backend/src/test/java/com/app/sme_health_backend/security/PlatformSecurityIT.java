package com.app.sme_health_backend.security;

import com.app.sme_health_backend.identity.dto.LoginRequest;
import com.app.sme_health_backend.identity.dto.RegisterRequest;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.entity.Business;
import com.app.sme_health_backend.identity.entity.BusinessMembership;
import com.app.sme_health_backend.identity.model.AccountStatus;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.model.MembershipStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.repository.BusinessMembershipRepository;
import com.app.sme_health_backend.identity.repository.BusinessRepository;
import com.app.sme_health_backend.mfa.repository.UserMfaRecoveryCodeRepository;
import com.app.sme_health_backend.mfa.repository.UserMfaRepository;
import com.app.sme_health_backend.mfa.service.MfaService;
import com.app.sme_health_backend.mfa.service.TotpEngine;
import com.app.sme_health_backend.platform.cli.PlatformAdminOperatorService;
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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class PlatformSecurityIT {

    private static final String APP_USER = "finsight_app";
    private static final String APP_PASSWORD = "FinSight_App_Runtime_2026_!*7vQ";

    private static final String MIGRATOR_USER = "finsight_migrator";
    private static final String MIGRATOR_PASSWORD = "FinSight_Migrator_Ddl_2026_!$4mP";

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/sme_health";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private BusinessMembershipRepository membershipRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformAdminOperatorService operatorService;

    @Autowired
    private UserMfaRepository userMfaRepository;

    @Autowired
    private UserMfaRecoveryCodeRepository recoveryCodeRepository;

    @Autowired
    private MfaService mfaService;

    @Autowired
    private TotpEngine totpEngine;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Connection createConnection(String user, String password) throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);
        props.setProperty("sslmode", "prefer");
        return DriverManager.getConnection(JDBC_URL, props);
    }

    private String obtainCsrfToken(MockHttpSession session) throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn();
        String json = csrfResult.getResponse().getContentAsString();
        return objectMapper.readTree(json).get("token").asText();
    }

    @Test
    @DisplayName("finsight_app raw SQL platform-role escalation is rejected by DB trigger trg_protect_platform_role")
    void testFinsightAppCannotEscalatePlatformRole() throws Exception {
        String email = "sql-escalate-" + UUID.randomUUID() + "@example.com";
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("password-123"));
        user.setFullName("Escalate Target");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setPlatformRole(null);
        user.setAuthVersion(0L);
        AppUser savedUser = userRepository.saveAndFlush(user);

        // Attempt NULL -> PLATFORM_ADMIN as finsight_app
        try (Connection appConn = createConnection(APP_USER, APP_PASSWORD)) {
            try (PreparedStatement ps = appConn.prepareStatement(
                    "UPDATE app_users SET platform_role = 'PLATFORM_ADMIN' WHERE id = ?")) {
                ps.setObject(1, savedUser.getId());
                SQLException ex = assertThrows(SQLException.class, ps::executeUpdate,
                        "finsight_app must not be able to elevate platform_role");
                assertTrue(ex.getSQLState().equals("55000") || ex.getMessage().contains("trigger"),
                        "Expected trigger error 55000: " + ex.getMessage());
            }
        }
    }

    @Test
    @DisplayName("Operator CLI promotion works and enables operator role changes")
    void testOperatorCliPromotion() throws Exception {
        String email = "op-promote-" + UUID.randomUUID() + "@example.com";
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("password-123"));
        user.setFullName("Operator Promo User");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setPlatformRole(null);
        user.setAuthVersion(0L);
        userRepository.saveAndFlush(user);

        try (Connection opConn = createConnection(MIGRATOR_USER, MIGRATOR_PASSWORD)) {
            operatorService.promotePlatformAdmin(opConn, email);
        }

        AppUser promoted = userRepository.findByEmail(email).orElseThrow();
        assertEquals("PLATFORM_ADMIN", promoted.getPlatformRole());
        assertTrue(promoted.isMustChangePassword());
        assertEquals(1L, promoted.getAuthVersion());

        // Now test revocation
        try (Connection opConn = createConnection(MIGRATOR_USER, MIGRATOR_PASSWORD)) {
            operatorService.revokePlatformAdmin(opConn, email);
        }

        AppUser revoked = userRepository.findByEmail(email).orElseThrow();
        assertNull(revoked.getPlatformRole());
        assertEquals(2L, revoked.getAuthVersion());
    }

    @Test
    @DisplayName("Tenant OWNER cannot access /api/platform/** (403 Forbidden)")
    void testTenantOwnerCannotAccessPlatformApi() throws Exception {
        String email = "tenant-owner-" + UUID.randomUUID() + "@example.com";
        String password = "owner-password-123";

        AppUser owner = new AppUser();
        owner.setEmail(email);
        owner.setPasswordHash(passwordEncoder.encode(password));
        owner.setFullName("Tenant Owner");
        owner.setAccountStatus(AccountStatus.ACTIVE);
        owner.setMustChangePassword(false);
        owner.setPlatformRole(null);
        owner.setAuthVersion(0L);
        AppUser savedOwner = userRepository.saveAndFlush(owner);

        Business biz = new Business();
        biz.setId(UUID.randomUUID());
        biz.setStatus("ACTIVE");
        Business savedBiz = businessRepository.saveAndFlush(biz);

        BusinessMembership membership = new BusinessMembership(
                savedOwner.getId(),
                savedBiz.getId(),
                MembershipRole.OWNER,
                MembershipStatus.ACTIVE
        );
        membershipRepository.saveAndFlush(membership);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authStage").value("FULLY_AUTHENTICATED"))
                .andReturn();

        jakarta.servlet.http.Cookie sessionCookie = loginResult.getResponse().getCookie("FINSIGHT_SESSION");
        assertNotNull(sessionCookie);

        // Tenant owner accessing platform API is rejected with 403
        mockMvc.perform(get("/api/platform/audit-events").cookie(sessionCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PLATFORM_ADMIN without membership cannot access tenant financial data")
    void testPlatformAdminCannotAccessTenantFinancialData() throws Exception {
        String adminEmail = "platform-pure-admin-" + UUID.randomUUID() + "@example.com";
        String password = "admin-password-123";

        AppUser admin = new AppUser();
        admin.setEmail(adminEmail);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setFullName("Pure Platform Admin");
        admin.setAccountStatus(AccountStatus.ACTIVE);
        admin.setMustChangePassword(false);
        admin.setPlatformRole("PLATFORM_ADMIN");
        admin.setAuthVersion(0L);
        userRepository.saveAndFlush(admin);

        // Platform admin without business membership logs in
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(adminEmail, password))))
                .andExpect(status().isOk())
                .andReturn();

        jakarta.servlet.http.Cookie sessionCookie = loginResult.getResponse().getCookie("FINSIGHT_SESSION");
        assertNotNull(sessionCookie);

        // Platform admin cannot access tenant endpoints
        mockMvc.perform(get("/api/businesses/active").cookie(sessionCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Operator MFA reset deletes MFA, recovery codes, sets must_change_password, increments auth_version, and revokes sessions")
    void testOperatorMfaResetLifecycle() throws Exception {
        String email = "op-reset-mfa-" + UUID.randomUUID() + "@example.com";
        String password = "admin-password-123";

        AppUser admin = new AppUser();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setFullName("MFA Reset Admin");
        admin.setAccountStatus(AccountStatus.ACTIVE);
        admin.setMustChangePassword(false);
        admin.setPlatformRole("PLATFORM_ADMIN");
        admin.setAuthVersion(0L);
        AppUser savedAdmin = userRepository.saveAndFlush(admin);

        // Enroll MFA
        String secret = mfaService.initiateEnrollment(savedAdmin.getId()).secret();
        String code = totpEngine.generateCode(secret);
        mfaService.confirmEnrollment(savedAdmin.getId(), password, code);

        assertTrue(userMfaRepository.findByUserId(savedAdmin.getId()).isPresent());
        assertEquals(10, recoveryCodeRepository.findByUserId(savedAdmin.getId()).size());

        // Perform login: step 1 returns MFA_CHALLENGE_REQUIRED
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authStage").value("MFA_CHALLENGE_REQUIRED"))
                .andReturn();

        jakarta.servlet.http.Cookie preAuthCookie = loginResult.getResponse().getCookie("FINSIGHT_SESSION");
        assertNotNull(preAuthCookie);

        // Step 2: Challenge with TOTP code
        long timestep = System.currentTimeMillis() / 1000L / 30L;
        String challengeCode = totpEngine.generateCode(secret, timestep + 1);
        MvcResult challengeResult = mockMvc.perform(post("/api/auth/mfa/challenge")
                        .cookie(preAuthCookie)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + challengeCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authStage").value("FULLY_AUTHENTICATED"))
                .andReturn();

        jakarta.servlet.http.Cookie fullSessionCookie = challengeResult.getResponse().getCookie("FINSIGHT_SESSION");
        if (fullSessionCookie == null) {
            fullSessionCookie = preAuthCookie;
        }

        // Session can access platform API
        mockMvc.perform(get("/api/platform/audit-events").cookie(fullSessionCookie))
                .andExpect(status().isOk());

        // Operator resets MFA
        try (Connection opConn = createConnection(MIGRATOR_USER, MIGRATOR_PASSWORD)) {
            operatorService.resetPlatformAdminMfa(opConn, email);
        }

        // Verify user_mfa and recovery codes are deleted
        assertTrue(userMfaRepository.findByUserId(savedAdmin.getId()).isEmpty());
        assertTrue(recoveryCodeRepository.findByUserId(savedAdmin.getId()).isEmpty());

        // Verify user state: must_change_password=true, auth_version incremented
        AppUser reloaded = userRepository.findByEmail(email).orElseThrow();
        assertTrue(reloaded.isMustChangePassword());
        assertEquals(1L, reloaded.getAuthVersion());

        // Verify active session was invalidated (AccountStatusValidationFilter returns 401)
        mockMvc.perform(get("/api/platform/audit-events").cookie(fullSessionCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Revoking PLATFORM_ADMIN increments auth_version and terminates active platform sessions")
    void testPlatformAdminRevocationTerminatesActivePlatformSessions() throws Exception {
        String email = "revoke-session-" + UUID.randomUUID() + "@example.com";
        String password = "admin-password-123";

        AppUser admin = new AppUser();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setFullName("Session Revoke Target");
        admin.setAccountStatus(AccountStatus.ACTIVE);
        admin.setMustChangePassword(false);
        admin.setPlatformRole("PLATFORM_ADMIN");
        admin.setAuthVersion(0L);
        AppUser savedAdmin = userRepository.saveAndFlush(admin);

        // Platform Admin has mandatory MFA: enroll MFA
        String secret = mfaService.initiateEnrollment(savedAdmin.getId()).secret();
        String code = totpEngine.generateCode(secret);
        mfaService.confirmEnrollment(savedAdmin.getId(), password, code);

        // Step 1: Login
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authStage").value("MFA_CHALLENGE_REQUIRED"))
                .andReturn();

        jakarta.servlet.http.Cookie preAuthCookie = loginResult.getResponse().getCookie("FINSIGHT_SESSION");
        assertNotNull(preAuthCookie);

        // Step 2: Challenge
        long timestep = System.currentTimeMillis() / 1000L / 30L;
        String challengeCode = totpEngine.generateCode(secret, timestep + 1);
        MvcResult challengeResult = mockMvc.perform(post("/api/auth/mfa/challenge")
                        .cookie(preAuthCookie)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + challengeCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authStage").value("FULLY_AUTHENTICATED"))
                .andReturn();

        jakarta.servlet.http.Cookie sessionCookie = challengeResult.getResponse().getCookie("FINSIGHT_SESSION");
        if (sessionCookie == null) {
            sessionCookie = preAuthCookie;
        }

        // Access platform API succeeds
        mockMvc.perform(get("/api/platform/audit-events").cookie(sessionCookie))
                .andExpect(status().isOk());

        // Operator revokes PLATFORM_ADMIN
        try (Connection opConn = createConnection(MIGRATOR_USER, MIGRATOR_PASSWORD)) {
            operatorService.revokePlatformAdmin(opConn, email);
        }

        AppUser revoked = userRepository.findByEmail(email).orElseThrow();
        assertNull(revoked.getPlatformRole());
        assertEquals(1L, revoked.getAuthVersion());

        // Prior session cannot continue accessing /api/platform/** (session invalidated -> 401)
        mockMvc.perform(get("/api/platform/audit-events").cookie(sessionCookie))
                .andExpect(status().isUnauthorized());
    }
}
