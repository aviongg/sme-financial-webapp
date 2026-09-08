package com.app.sme_health_backend;

import com.app.sme_health_backend.profile.controller.BusinessProfileController;
import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.service.BusinessProfileService;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BusinessProfileController.class)
@Import(GlobalExceptionHandler.class)
class BusinessProfileControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BusinessProfileService businessProfileService;

    @Test
    void shouldCreateBusinessProfile() throws Exception {
        UUID userId = UUID.randomUUID();

        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(userId);
        profile.setBusinessType("retail");
        profile.setLanguagePreference("en");
        profile.setWhatsappOptIn(false);
        profile.setCreatedAt(LocalDateTime.now());

        when(businessProfileService.createProfile(any()))
                .thenReturn(profile);

        String request = """
                {
                  "userId": "%s",
                  "businessType": "retail",
                  "languagePreference": "en",
                  "whatsappOptIn": false
                }
                """.formatted(userId);

        mockMvc.perform(post("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.businessType").value("retail"))
                .andExpect(jsonPath("$.languagePreference").value("en"))
                .andExpect(jsonPath("$.whatsappOptIn").value(false));
    }

    @Test
    void shouldRejectInvalidBusinessType() throws Exception {
        UUID userId = UUID.randomUUID();

        String request = """
                {
                  "userId": "%s",
                  "businessType": "invalid",
                  "languagePreference": "en",
                  "whatsappOptIn": false
                }
                """.formatted(userId);

        mockMvc.perform(post("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.businessType").exists());
    }

    @Test
    void shouldReturnConflictForDuplicateProfile() throws Exception {
        UUID userId = UUID.randomUUID();

        when(businessProfileService.createProfile(any()))
                .thenThrow(new DuplicateResourceException(
                        "Business profile already exists for this user"
                ));

        String request = """
                {
                  "userId": "%s",
                  "businessType": "retail",
                  "languagePreference": "en",
                  "whatsappOptIn": false
                }
                """.formatted(userId);

        mockMvc.perform(post("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void shouldReturnNotFoundWhenProfileDoesNotExist() throws Exception {
        UUID userId = UUID.randomUUID();

        when(businessProfileService.getProfile(userId))
                .thenThrow(new ResourceNotFoundException(
                        "Business profile not found for this user"
                ));

        mockMvc.perform(get("/api/profile/{userId}", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void shouldRejectWhatsappOptInWithoutNumber() throws Exception {
        UUID userId = UUID.randomUUID();

        when(businessProfileService.createProfile(any()))
                .thenThrow(new IllegalArgumentException(
                        "WhatsApp number is required when WhatsApp opt-in is enabled"
                ));

        String request = """
                {
                  "userId": "%s",
                  "businessType": "retail",
                  "languagePreference": "en",
                  "whatsappOptIn": true
                }
                """.formatted(userId);

        mockMvc.perform(post("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}