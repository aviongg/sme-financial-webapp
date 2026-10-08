package com.app.sme_health_backend.security;

import com.app.sme_health_backend.identity.dto.RegisterRequest;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.model.AccountStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.service.AuthenticationService;
import com.app.sme_health_backend.mfa.repository.UserMfaRepository;
import com.app.sme_health_backend.mfa.service.MfaService;
import com.app.sme_health_backend.mfa.service.TotpEngine;
import com.app.sme_health_backend.security.service.SessionRevocationService;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthSecurityClosureIT extends DisposablePostgres {
    private static final String PASSWORD = "closure-fixture-password-123";
    @Autowired MockMvc http;
    @Autowired AuthenticationService auth;
    @Autowired AppUserRepository users;
    @Autowired UserMfaRepository factors;
    @Autowired MfaService mfa;
    @Autowired TotpEngine totp;
    @Autowired SessionRevocationService revocation;
    @Autowired org.springframework.session.FindByIndexNameSessionRepository sessions;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    private record Account(AppUser user, String secret, List<String> recovery) {}

    private Account account() {
        String email = "closure-" + UUID.randomUUID() + "@example.test";
        auth.register(new RegisterRequest(email, PASSWORD, "Closure Test"));
        AppUser user = users.findByEmail(email).orElseThrow();
        String secret = mfa.initiateEnrollment(user.getId(), email).secret();
        List<String> recovery = mfa.confirmEnrollment(user.getId(), totp.generateCode(secret), PASSWORD, null);
        return new Account(users.findById(user.getId()).orElseThrow(), secret, recovery);
    }

    private Cookie login(Account account) throws Exception {
        return http.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", account.user.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authStage").value("MFA_CHALLENGE_REQUIRED"))
                .andReturn().getResponse().getCookie("FINSIGHT_SESSION");
    }

    @Test
    void jdbcIndexIncludesPendingSessionsAndRevocationDeletesThem() throws Exception {
        Account account = account();
        Cookie pending = login(account);
        assertNotNull(pending);
        assertFalse(sessions.findByPrincipalName(account.user.getEmail()).isEmpty());
        revocation.revokeAllSessions(account.user.getEmail(), account.user.getId(), "CLOSURE_TEST");
        assertTrue(sessions.findByPrincipalName(account.user.getEmail()).isEmpty());
        http.perform(post("/api/auth/mfa/recovery").cookie(pending).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("recoveryCode", account.recovery.getFirst()))))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"version", "passwordRequired", "disabled", "factorRemoved", "expired"})
    void stalePendingSessionCannotPromoteEvenWithValidRecoveryCode(String change) throws Exception {
        Account account = account();
        Cookie pending = login(account);
        AppUser current = users.findById(account.user.getId()).orElseThrow();
        switch (change) {
            case "version" -> current.setAuthVersion(current.getAuthVersion() + 1);
            case "passwordRequired" -> current.setMustChangePassword(true);
            case "disabled" -> current.setAccountStatus(AccountStatus.DISABLED);
            case "factorRemoved" -> { var factor = factors.findByUserId(current.getId()).orElseThrow(); factor.setStatus("PENDING"); factors.saveAndFlush(factor); }
            case "expired" -> sessions.findByPrincipalName(current.getEmail()).values().forEach(value -> {
                var s = (org.springframework.session.Session) value;
                s.setAttribute("FINSIGHT_PRE_AUTH_EXPIRES_AT", System.currentTimeMillis() - 1);
                sessions.save(s);
            });
        }
        users.saveAndFlush(current);
        http.perform(post("/api/auth/mfa/recovery").cookie(pending).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("recoveryCode", account.recovery.getFirst()))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("pre_auth_invalid"));
        assertTrue(sessions.findByPrincipalName(current.getEmail()).isEmpty());
    }

    @Test
    void normalTotpChallengeRotatesSessionAndEstablishesFullAuthentication() throws Exception {
        Account account = account();
        Cookie pending = login(account);
        String code = totp.generateCode(account.secret, Instant.now().getEpochSecond() / 30 + 1);
        Cookie full = http.perform(post("/api/auth/mfa/challenge").cookie(pending).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("code", code))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authStage").value("FULLY_AUTHENTICATED"))
                .andReturn().getResponse().getCookie("FINSIGHT_SESSION");
        assertNotNull(full);
        assertNotEquals(pending.getValue(), full.getValue());
        http.perform(get("/api/auth/me").cookie(full)).andExpect(status().isOk());
    }

    @Test
    void enabledFactorCiphertextMetadataAndRecoverySurviveConflict() throws Exception {
        Account account = account();
        Cookie pending = login(account);
        Cookie full = http.perform(post("/api/auth/mfa/recovery").cookie(pending).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("recoveryCode", account.recovery.getFirst()))))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("FINSIGHT_SESSION");
        Map<String, Object> before = jdbc.queryForMap("SELECT * FROM user_mfa WHERE user_id=?", account.user.getId());
        long version = users.findById(account.user.getId()).orElseThrow().getAuthVersion();
        http.perform(post("/api/auth/mfa/enroll/initiate").cookie(full).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("mfa_already_enabled"));
        assertEquals(before, jdbc.queryForMap("SELECT * FROM user_mfa WHERE user_id=?", account.user.getId()));
        assertEquals(version, users.findById(account.user.getId()).orElseThrow().getAuthVersion());
        assertTrue(mfa.verifyRecoveryCode(account.user.getId(), account.recovery.get(1)));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM security_audit_events WHERE actor_user_id=? AND event_type='MFA_ENROLLMENT_REJECTED'", Integer.class, account.user.getId()));
    }

    @Test
    void pendingRestartPersistsFreshConfirmationDeadline() {
        auth.register(new RegisterRequest("restart-" + UUID.randomUUID() + "@example.test", PASSWORD, "Restart"));
        AppUser user = users.findAll().stream().filter(u -> u.getFullName().equals("Restart")).findFirst().orElseThrow();
        mfa.initiateEnrollment(user.getId(), user.getEmail());
        var pending = factors.findByUserId(user.getId()).orElseThrow();
        pending.setCreatedAt(OffsetDateTime.now().minusMinutes(20));
        factors.saveAndFlush(pending);
        var restarted = mfa.initiateEnrollment(user.getId(), user.getEmail());
        assertTrue(factors.findByUserId(user.getId()).orElseThrow().getCreatedAt().isAfter(OffsetDateTime.now().minusMinutes(1)));
        assertEquals(10, mfa.confirmEnrollment(user.getId(), totp.generateCode(restarted.secret()), PASSWORD, null).size());
    }
}
