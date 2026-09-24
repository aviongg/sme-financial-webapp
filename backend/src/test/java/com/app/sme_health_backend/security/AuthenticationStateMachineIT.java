package com.app.sme_health_backend.security;

import com.app.sme_health_backend.identity.dto.LoginRequest;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.model.AccountStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.mfa.dto.MfaChallengeRequest;
import com.app.sme_health_backend.mfa.service.MfaService;
import com.app.sme_health_backend.mfa.service.TotpEngine;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthenticationStateMachineIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MfaService mfaService;

    @Autowired
    private TotpEngine totpEngine;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Platform admin without MFA enters MFA_ENROLLMENT_REQUIRED stage and cannot access features")
    void testPlatformAdminRequiresMfaEnrollment() throws Exception {
        String email = "pa-no-mfa-" + UUID.randomUUID() + "@example.com";
        String password = "admin-password-123";

        AppUser admin = new AppUser();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setFullName("Platform Admin Candidate");
        admin.setAccountStatus(AccountStatus.ACTIVE);
        admin.setMustChangePassword(false);
        admin.setPlatformRole("PLATFORM_ADMIN");
        admin.setAuthVersion(0L);
        userRepository.saveAndFlush(admin);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authStage").value("MFA_ENROLLMENT_REQUIRED"))
                .andReturn();

        jakarta.servlet.http.Cookie sessionCookie = loginResult.getResponse().getCookie("FINSIGHT_SESSION");
        assertNotNull(sessionCookie);

        // Platform API and Tenant APIs are blocked
        mockMvc.perform(get("/api/platform/audit-events").cookie(sessionCookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/businesses").cookie(sessionCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Five failed challenge attempts invalidate pre-auth session")
    void testFiveFailedChallengesInvalidatePreAuthSession() throws Exception {
        String email = "mfa-fail-" + UUID.randomUUID() + "@example.com";
        String password = "mfa-user-password-123";

        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFullName("MFA Failure User");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setMustChangePassword(false);
        user.setAuthVersion(0L);
        AppUser savedUser = userRepository.saveAndFlush(user);

        // Enroll MFA
        String secret = mfaService.initiateEnrollment(savedUser.getId()).secret();
        String validTotp = totpEngine.generateCode(secret);
        mfaService.confirmEnrollment(savedUser.getId(), password, validTotp);

        // Login enters MFA_CHALLENGE_REQUIRED
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authStage").value("MFA_CHALLENGE_REQUIRED"))
                .andReturn();

        jakarta.servlet.http.Cookie sessionCookie = loginResult.getResponse().getCookie("FINSIGHT_SESSION");
        assertNotNull(sessionCookie);

        // Issue 4 invalid attempts
        for (int i = 1; i <= 4; i++) {
            mockMvc.perform(post("/api/auth/mfa/challenge")
                            .cookie(sessionCookie)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new MfaChallengeRequest("999999"))))
                    .andExpect(status().isUnauthorized());
        }

        // 5th failed attempt invalidates session
        mockMvc.perform(post("/api/auth/mfa/challenge")
                        .cookie(sessionCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaChallengeRequest("999999"))))
                .andExpect(status().isUnauthorized());

        // Subsequent call fails because session was invalidated
        mockMvc.perform(post("/api/auth/mfa/challenge")
                        .cookie(sessionCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaChallengeRequest("999999"))))
                .andExpect(status().isUnauthorized());
    }
}
