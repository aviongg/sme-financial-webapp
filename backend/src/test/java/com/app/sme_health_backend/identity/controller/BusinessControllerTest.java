package com.app.sme_health_backend.identity.controller;

import com.app.sme_health_backend.identity.dto.BusinessResponse;
import com.app.sme_health_backend.identity.dto.CreateBusinessRequest;
import com.app.sme_health_backend.identity.dto.SelectActiveBusinessRequest;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.model.MembershipStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.service.ActiveBusinessContext;
import com.app.sme_health_backend.identity.service.BusinessService;
import com.app.sme_health_backend.security.test.WithMockAppUser;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BusinessController.class)
@Import(GlobalExceptionHandler.class)
class BusinessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BusinessService businessService;

    @MockitoBean
    private ActiveBusinessContext activeBusinessContext;

    @MockitoBean
    private AppUserRepository userRepository;

    private static final String TEST_USER_ID = "00000000-0000-0000-0000-000000000001";

    @Test
    @WithMockAppUser(id = TEST_USER_ID)
    @DisplayName("POST /api/businesses creates business and sets active business in session post-commit")
    void shouldCreateBusinessAndSetActiveInSession() throws Exception {
        UUID newBusinessId = UUID.randomUUID();
        UUID userId = UUID.fromString(TEST_USER_ID);

        BusinessResponse response = new BusinessResponse(
                newBusinessId,
                "retail",
                "en",
                MembershipRole.OWNER,
                MembershipStatus.ACTIVE,
                true
        );

        when(businessService.createBusiness(any(CreateBusinessRequest.class), eq(userId)))
                .thenReturn(response);

        String payload = """
                {
                    "businessType": "retail",
                    "languagePreference": "en",
                    "whatsappOptIn": false
                }
                """;

        mockMvc.perform(post("/api/businesses")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.businessId").value(newBusinessId.toString()))
                .andExpect(jsonPath("$.businessType").value("retail"))
                .andExpect(jsonPath("$.languagePreference").value("en"))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.whatsappNumber").doesNotExist());

        // Verifies active business is set in session after service returns
        verify(activeBusinessContext).setActiveBusinessId(any(HttpServletRequest.class), eq(newBusinessId));
    }

    @Test
    @WithMockAppUser(id = TEST_USER_ID)
    @DisplayName("POST /api/businesses rejects forged client-supplied userId or role")
    void shouldIgnoreForgedClientSuppliedUserId() throws Exception {
        UUID newBusinessId = UUID.randomUUID();
        UUID authenticatedUserId = UUID.fromString(TEST_USER_ID);

        BusinessResponse response = new BusinessResponse(
                newBusinessId,
                "services",
                "ur",
                MembershipRole.OWNER,
                MembershipStatus.ACTIVE,
                true
        );

        when(businessService.createBusiness(any(CreateBusinessRequest.class), eq(authenticatedUserId)))
                .thenReturn(response);

        // Client attempts to pass arbitrary userId and role
        String forgedPayload = """
                {
                    "userId": "99999999-9999-9999-9999-999999999999",
                    "role": "VIEWER",
                    "businessType": "services",
                    "languagePreference": "ur",
                    "whatsappOptIn": false
                }
                """;

        mockMvc.perform(post("/api/businesses")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(forgedPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.businessId").value(newBusinessId.toString()));

        // Service must be invoked with authenticated user's ID, NEVER the forged userId
        ArgumentCaptor<UUID> userCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(businessService).createBusiness(any(CreateBusinessRequest.class), userCaptor.capture());
        assertThat(userCaptor.getValue()).isEqualTo(authenticatedUserId);
    }

    @Test
    @WithMockAppUser(id = TEST_USER_ID)
    @DisplayName("POST /api/businesses rejects invalid business type with 400 Bad Request")
    void shouldRejectInvalidBusinessType() throws Exception {
        String invalidPayload = """
                {
                    "businessType": "crypto_trading",
                    "languagePreference": "en",
                    "whatsappOptIn": false
                }
                """;

        mockMvc.perform(post("/api/businesses")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.businessType").exists());
    }

    @Test
    @WithMockAppUser(id = TEST_USER_ID)
    @DisplayName("GET /api/businesses returns caller's businesses with active flag")
    void shouldListCallerBusinesses() throws Exception {
        UUID userId = UUID.fromString(TEST_USER_ID);
        UUID b1 = UUID.randomUUID();
        UUID b2 = UUID.randomUUID();

        when(activeBusinessContext.getSessionActiveBusinessId(any(HttpServletRequest.class))).thenReturn(b1);

        List<BusinessResponse> list = List.of(
                new BusinessResponse(b1, "retail", "en", MembershipRole.OWNER, MembershipStatus.ACTIVE, true),
                new BusinessResponse(b2, "trade", "ur", MembershipRole.ACCOUNTANT, MembershipStatus.ACTIVE, false)
        );

        when(businessService.listUserBusinesses(userId, b1)).thenReturn(list);

        mockMvc.perform(get("/api/businesses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].businessId").value(b1.toString()))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[0].whatsappNumber").doesNotExist())
                .andExpect(jsonPath("$[1].businessId").value(b2.toString()))
                .andExpect(jsonPath("$[1].active").value(false))
                .andExpect(jsonPath("$[1].role").value("ACCOUNTANT"));
    }

    @Test
    @WithMockAppUser(id = TEST_USER_ID)
    @DisplayName("POST /api/businesses/active selects active business and updates session")
    void shouldSelectActiveBusiness() throws Exception {
        UUID userId = UUID.fromString(TEST_USER_ID);
        UUID targetBusinessId = UUID.randomUUID();

        BusinessResponse response = new BusinessResponse(
                targetBusinessId,
                "manufacturing",
                "en",
                MembershipRole.MANAGER,
                MembershipStatus.ACTIVE,
                true
        );

        when(businessService.validateAndGetBusinessForActivation(userId, targetBusinessId))
                .thenReturn(response);

        String payload = """
                {
                    "businessId": "%s"
                }
                """.formatted(targetBusinessId);

        mockMvc.perform(post("/api/businesses/active")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(targetBusinessId.toString()))
                .andExpect(jsonPath("$.role").value("MANAGER"))
                .andExpect(jsonPath("$.active").value(true));

        verify(activeBusinessContext).setActiveBusinessId(any(HttpServletRequest.class), eq(targetBusinessId));
    }

    @Test
    @WithMockAppUser(id = TEST_USER_ID)
    @DisplayName("POST /api/businesses/active rejects unauthorized or non-member business selection")
    void shouldRejectUnauthorizedBusinessSelection() throws Exception {
        UUID userId = UUID.fromString(TEST_USER_ID);
        UUID targetBusinessId = UUID.randomUUID();

        when(businessService.validateAndGetBusinessForActivation(userId, targetBusinessId))
                .thenThrow(new AccessDeniedException("User is not an active member of this business"));

        String payload = """
                {
                    "businessId": "%s"
                }
                """.formatted(targetBusinessId);

        mockMvc.perform(post("/api/businesses/active")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockAppUser(id = TEST_USER_ID)
    @DisplayName("GET /api/businesses/active returns active business when set")
    void shouldGetActiveBusinessWhenSet() throws Exception {
        UUID userId = UUID.fromString(TEST_USER_ID);
        UUID activeId = UUID.randomUUID();

        when(activeBusinessContext.getSessionActiveBusinessId(any(HttpServletRequest.class))).thenReturn(activeId);

        BusinessResponse response = new BusinessResponse(
                activeId,
                "retail",
                "en",
                MembershipRole.OWNER,
                MembershipStatus.ACTIVE,
                true
        );
        when(businessService.getActiveBusiness(userId, activeId)).thenReturn(response);

        mockMvc.perform(get("/api/businesses/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(activeId.toString()))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @WithMockAppUser(id = TEST_USER_ID)
    @DisplayName("GET /api/businesses/active returns 404 when no business is active in session")
    void shouldReturn404WhenNoActiveBusiness() throws Exception {
        when(activeBusinessContext.getSessionActiveBusinessId(any(HttpServletRequest.class))).thenReturn(null);

        mockMvc.perform(get("/api/businesses/active"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
