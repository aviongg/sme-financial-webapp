package com.app.sme_health_backend.profile.controller;

import com.app.sme_health_backend.profile.dto.BusinessProfileRequest;
import com.app.sme_health_backend.profile.dto.BusinessProfileResponse;
import com.app.sme_health_backend.profile.dto.LanguagePreferenceRequest;
import com.app.sme_health_backend.profile.dto.WhatsAppPreferenceRequest;
import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.service.BusinessProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/profile")
public class BusinessProfileController {

    private final BusinessProfileService businessProfileService;

    public BusinessProfileController(BusinessProfileService businessProfileService) {
        this.businessProfileService = businessProfileService;
    }


    @PatchMapping("/{userId}/language")
    public BusinessProfileResponse updateLanguagePreference(
            @PathVariable UUID userId, @Valid @RequestBody LanguagePreferenceRequest request) {
        return BusinessProfileResponse.fromEntity(
                businessProfileService.updateLanguagePreference(userId, request.languagePreference()));
    }

    @PatchMapping("/{userId}/whatsapp")
    public BusinessProfileResponse updateWhatsAppPreference(
            @PathVariable UUID userId, @Valid @RequestBody WhatsAppPreferenceRequest request) {
        return BusinessProfileResponse.fromEntity(
                businessProfileService.updateWhatsAppPreference(
                        userId,
                        request.whatsappNumber(),
                        request.optIn()
                )
        );
    }

    @GetMapping("/{userId}")
    public ResponseEntity<BusinessProfileResponse> getProfile(
            @PathVariable UUID userId) {

        BusinessProfile profile = businessProfileService.getProfile(userId);

        return ResponseEntity.ok(
                BusinessProfileResponse.fromEntity(profile)
        );
    }
}