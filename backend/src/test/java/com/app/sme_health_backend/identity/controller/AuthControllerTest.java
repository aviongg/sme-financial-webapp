package com.app.sme_health_backend.identity.controller;

import com.app.sme_health_backend.identity.dto.LoginRequest;
import com.app.sme_health_backend.identity.dto.RegisterRequest;
import com.app.sme_health_backend.identity.dto.UserResponse;
import com.app.sme_health_backend.identity.model.AccountStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.service.AuthenticationService;
import com.app.sme_health_backend.identity.credential.service.PasswordResetService;
import com.app.sme_health_backend.mfa.service.MfaService;
import com.app.sme_health_backend.security.test.WithMockAppUser;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import com.app.sme_health_backend.security.ratelimit.IdentityRateLimitingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationService authenticationService;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private MfaService mfaService;

    @MockitoBean
    private AppUserRepository userRepository;

    @MockitoBean
    private IdentityRateLimitingService rateLimitingService;

    @Test
    @DisplayName("GET /api/auth/csrf returns CSRF token with header and parameter names")
    void testGetCsrfToken() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").isNotEmpty())
                .andExpect(jsonPath("$.parameterName").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/auth/register succeeds with valid payload and returns 201 Created")
    void testRegisterSuccess() throws Exception {
        UUID id = UUID.randomUUID();
        UserResponse response = new UserResponse(
                id,
                "newuser@example.com",
                "New User",
                AccountStatus.ACTIVE,
                false,
                OffsetDateTime.now()
        );
        when(authenticationService.register(any(RegisterRequest.class))).thenReturn(response);

        String payload = """
                {
                    "email": "newuser@example.com",
                    "password": "valid-secure-password-123",
                    "fullName": "New User"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.email").value("newuser@example.com"))
                .andExpect(jsonPath("$.fullName").value("New User"));
    }

    @Test
    @DisplayName("POST /api/auth/register prevents privilege escalation: unknown privileged fields like role or is_platform_admin are ignored")
    void testRegisterRejectsPrivilegedFields() throws Exception {
        UUID id = UUID.randomUUID();
        UserResponse response = new UserResponse(
                id,
                "hacker@example.com",
                "Malicious User",
                AccountStatus.ACTIVE,
                false,
                OffsetDateTime.now()
        );
        when(authenticationService.register(any(RegisterRequest.class))).thenReturn(response);

        String payload = """
                {
                    "email": "hacker@example.com",
                    "password": "valid-secure-password-123",
                    "fullName": "Malicious User",
                    "is_platform_admin": true,
                    "role": "OWNER"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("hacker@example.com"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /api/auth/login succeeds and returns 200 OK with UserResponse")
    void testLoginSuccess() throws Exception {
        UUID id = UUID.randomUUID();
        UserResponse response = new UserResponse(
                id,
                "user@example.com",
                "Test User",
                AccountStatus.ACTIVE,
                false,
                OffsetDateTime.now()
        );
        when(authenticationService.login(any(LoginRequest.class), any(), any())).thenReturn(response);

        String payload = """
                {
                    "email": "user@example.com",
                    "password": "valid-password-123"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    @Test
    @DisplayName("POST /api/auth/logout succeeds and returns 204 No Content")
    void testLogoutSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockAppUser
    @DisplayName("GET /api/auth/me returns 200 OK when authenticated")
    void testGetCurrentUserAuthenticated() throws Exception {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UserResponse response = new UserResponse(
                id,
                "testuser@example.com",
                "Test User",
                AccountStatus.ACTIVE,
                false,
                OffsetDateTime.now()
        );
        when(authenticationService.getCurrentUser()).thenReturn(response);

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.email").value("testuser@example.com"));
    }

    @Test
    @DisplayName("GET /api/auth/me returns 403 Forbidden or 401 when unauthenticated")
    void testGetCurrentUserUnauthenticated() throws Exception {
        when(authenticationService.getCurrentUser()).thenThrow(new AccessDeniedException("User is not authenticated"));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isForbidden());
    }
}
