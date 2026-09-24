package com.app.sme_health_backend.profile.controller;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.profile.dto.BusinessProfileResponse;
import com.app.sme_health_backend.profile.dto.LanguagePreferenceRequest;
import com.app.sme_health_backend.profile.dto.WhatsAppPreferenceRequest;
import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.service.BusinessProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class BusinessProfileController {

    private final BusinessProfileService businessProfileService;
    private final BusinessAuthorizationService authService;

    public BusinessProfileController(
            BusinessProfileService businessProfileService,
            BusinessAuthorizationService authService
    ) {
        this.businessProfileService = businessProfileService;
        this.authService = authService;
    }

    @PatchMapping("/language")
    public BusinessProfileResponse updateLanguagePreference(
            @Valid @RequestBody LanguagePreferenceRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.BUSINESS_SETTINGS_MANAGE);
        return BusinessProfileResponse.fromEntity(
                businessProfileService.updateLanguagePreference(context.businessId(), requestDto.languagePreference()));
    }

    @PatchMapping("/whatsapp")
    public BusinessProfileResponse updateWhatsAppPreference(
            @Valid @RequestBody WhatsAppPreferenceRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.WHATSAPP_CONFIG_MANAGE);
        return BusinessProfileResponse.fromEntity(
                businessProfileService.updateWhatsAppPreference(
                        context.businessId(),
                        requestDto.whatsappNumber(),
                        requestDto.optIn()
                )
        );
    }

    @GetMapping
    public ResponseEntity<BusinessProfileResponse> getProfile(HttpServletRequest request) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        BusinessProfile profile = businessProfileService.getProfile(context.businessId());
        return ResponseEntity.ok(
                BusinessProfileResponse.fromEntity(profile)
        );
    }
}