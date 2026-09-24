package com.app.sme_health_backend;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.profile.controller.BusinessProfileController;
import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.service.BusinessProfileService;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BusinessProfileController.class)
@Import(GlobalExceptionHandler.class)
class BusinessProfileControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BusinessProfileService businessProfileService;

    @MockitoBean
    private BusinessAuthorizationService authService;

    private final UUID userId = UUID.randomUUID();
    private final UUID businessId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), any(BusinessPermission.class))).thenReturn(context);
    }

    @Test
    void shouldChangeLanguagePreference() throws Exception {
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(businessId);
        profile.setLanguagePreference("ur");
        when(businessProfileService.updateLanguagePreference(businessId, "ur")).thenReturn(profile);

        mockMvc.perform(patch("/api/profile/language")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"languagePreference\":\"ur\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.languagePreference").value("ur"));
    }

    @Test
    void shouldUpdateWhatsAppPreference() throws Exception {
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(businessId);
        profile.setWhatsappNumber("+923001234567");
        profile.setWhatsappOptIn(true);
        when(businessProfileService.updateWhatsAppPreference(businessId, "+923001234567", true)).thenReturn(profile);

        mockMvc.perform(patch("/api/profile/whatsapp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"whatsappNumber\":\"+923001234567\",\"optIn\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsappNumber").value("+923001234567"))
                .andExpect(jsonPath("$.whatsappOptIn").value(true));
    }

    @Test
    void shouldRejectUnsupportedOrMissingLanguage() throws Exception {
        for (String body : new String[]{"{}", "{\"languagePreference\":\"fr\"}", "{\"languagePreference\":\"\"}"}) {
            mockMvc.perform(patch("/api/profile/language")
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void shouldGetProfile() throws Exception {
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(businessId);
        profile.setBusinessType("retail");
        profile.setLanguagePreference("en");

        when(businessProfileService.getProfile(businessId)).thenReturn(profile);

        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(businessId.toString()))
                .andExpect(jsonPath("$.businessType").value("retail"))
                .andExpect(jsonPath("$.languagePreference").value("en"));
    }

    @Test
    void shouldReturnNotFoundWhenProfileDoesNotExist() throws Exception {
        when(businessProfileService.getProfile(businessId))
                .thenThrow(new ResourceNotFoundException("Business profile not found for this user"));

        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void shouldRejectLegacyGetProfileWithUserIdUrl() throws Exception {
        mockMvc.perform(get("/api/profile/{userId}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectLegacyPatchLanguageWithUserIdUrl() throws Exception {
        mockMvc.perform(patch("/api/profile/{userId}/language", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"languagePreference\":\"ur\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectLegacyPatchWhatsappWithUserIdUrl() throws Exception {
        mockMvc.perform(patch("/api/profile/{userId}/whatsapp", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"whatsappNumber\":\"+923001234567\",\"optIn\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectDirectProfileCreationPostNotSupported() throws Exception {
        String request = """
                {
                  "businessType": "retail",
                  "languagePreference": "en",
                  "whatsappOptIn": false
                }
                """;

        mockMvc.perform(post("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isMethodNotAllowed());
    }
}