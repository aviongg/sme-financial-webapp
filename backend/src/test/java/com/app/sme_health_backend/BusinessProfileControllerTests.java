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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    void shouldChangeLanguagePreference() throws Exception {
        UUID id = UUID.randomUUID();
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(id);
        profile.setLanguagePreference("ur");
        when(businessProfileService.updateLanguagePreference(id, "ur")).thenReturn(profile);
        mockMvc.perform(patch("/api/profile/{userId}/language", id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"languagePreference\":\"ur\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.languagePreference").value("ur"));
    }

    @Test
    void shouldUpdateWhatsAppPreference() throws Exception {
        UUID id = UUID.randomUUID();
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(id);
        profile.setWhatsappNumber("+923001234567");
        profile.setWhatsappOptIn(true);
        when(businessProfileService.updateWhatsAppPreference(id, "+923001234567", true)).thenReturn(profile);

        mockMvc.perform(patch("/api/profile/{userId}/whatsapp", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"whatsappNumber\":\"+923001234567\",\"optIn\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsappNumber").value("+923001234567"))
                .andExpect(jsonPath("$.whatsappOptIn").value(true));
    }

    @Test
    void shouldRejectUnsupportedOrMissingLanguage() throws Exception {
        for (String body : new String[]{"{}", "{\"languagePreference\":\"fr\"}", "{\"languagePreference\":\"\"}"}) {
            mockMvc.perform(patch("/api/profile/{userId}/language", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void shouldRejectDirectProfileCreationPostNotSupported() throws Exception {
        UUID userId = UUID.randomUUID();
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
                .andExpect(status().isNotFound());
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
}