package com.app.sme_health_backend.security;

import com.app.sme_health_backend.identity.dto.RegisterRequest;
import com.app.sme_health_backend.identity.service.AuthenticationService;
import com.app.sme_health_backend.security.filter.SessionMaxLifetimeFilter;
import com.app.sme_health_backend.security.test.WithMockAppUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc // Runs WITH full security filter chain
class SecurityFilterChainTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationService authenticationService;

    @Test
    @DisplayName("Protected endpoint without authentication returns 401 Unauthorized")
    void testProtectedEndpointReturns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/dashboard/00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockAppUser
    @DisplayName("Protected endpoint proceeds when authenticated")
    void testProtectedEndpointProceedsWhenAuthenticated() throws Exception {
        // Authenticated user can proceed past security to the controller layer (returns 404 rather than 401)
        mockMvc.perform(get("/api/dashboard/00000000-0000-0000-0000-000000000001")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("testuser@example.com")))
                .andExpect(status().isNotFound()); // 404 proves security layer allowed the request through
    }

    @Test
    @DisplayName("State-changing request without CSRF token returns 403 Forbidden")
    void testStateChangingRequestWithoutCsrfReturns403() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"valid-password-123\",\"fullName\":\"Test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("State-changing request with valid CSRF token succeeds")
    void testStateChangingRequestWithValidCsrfSucceeds() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"valid-password-123\",\"fullName\":\"Test\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Allowed CORS origin is granted CORS headers with credentials")
    void testAllowedCorsOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/csrf")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("Disallowed CORS origin is not granted Access-Control-Allow-Origin header")
    void testDisallowedCorsOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/csrf")
                        .header("Origin", "http://malicious-attacker.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SessionMaxLifetimeFilter invalidates session when elapsed time exceeds 8 hours")
    void testSessionMaxLifetimeExpired() throws Exception {
        SessionMaxLifetimeFilter filter = new SessionMaxLifetimeFilter();
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/dashboard/00000000-0000-0000-0000-000000000001");
        org.springframework.mock.web.MockHttpServletResponse response =
                new org.springframework.mock.web.MockHttpServletResponse();
        MockHttpSession session = new MockHttpSession();

        // Set auth time to 8 hours and 1 minute ago
        long oldAuthTime = System.currentTimeMillis() - Duration.ofHours(8).plusMinutes(1).toMillis();
        session.setAttribute(SessionMaxLifetimeFilter.SESSION_AUTH_TIME_ATTR, oldAuthTime);
        request.setSession(session);

        filter.doFilter(request, response, new org.springframework.mock.web.MockFilterChain());

        org.junit.jupiter.api.Assertions.assertEquals(401, response.getStatus());
        assertTrue(session.isInvalid(), "Expired session must be invalidated");
    }

    @Test
    @DisplayName("Document file endpoint without authentication returns 401 Unauthorized")
    void testDocumentFileEndpointWithoutAuthReturns401() throws Exception {
        mockMvc.perform(get("/api/documents/00000000-0000-0000-0000-000000000001/file"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Document file endpoint with invalid internal service key returns 401 Unauthorized")
    void testDocumentFileEndpointWithInvalidInternalServiceKeyReturns401() throws Exception {
        mockMvc.perform(get("/api/documents/00000000-0000-0000-0000-000000000001/file")
                        .header("X-Internal-Service-Key", "wrong-secret-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Internal service key cannot access non-document endpoints")
    void testInternalServiceKeyCannotAccessOtherEndpoints() throws Exception {
        mockMvc.perform(get("/api/dashboard/00000000-0000-0000-0000-000000000001")
                        .header("X-Internal-Service-Key", "internal_ocr_dev_secret_2026"))
                .andExpect(status().isUnauthorized());
    }

    private void assertTrue(boolean condition, String message) {
        org.junit.jupiter.api.Assertions.assertTrue(condition, message);
    }
}
