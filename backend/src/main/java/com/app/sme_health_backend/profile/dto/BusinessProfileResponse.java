package com.app.sme_health_backend.profile.dto;

import com.app.sme_health_backend.profile.entity.BusinessProfile;

import java.time.LocalDateTime;
import java.util.UUID;

public class BusinessProfileResponse {

    private UUID userId;
    private String businessType;
    private String languagePreference;
    private String whatsappNumber;
    private boolean whatsappOptIn;
    private LocalDateTime createdAt;

    public static BusinessProfileResponse fromEntity(BusinessProfile profile) {
        BusinessProfileResponse response = new BusinessProfileResponse();

        response.userId = profile.getUserId();
        response.businessType = profile.getBusinessType();
        response.languagePreference = profile.getLanguagePreference();
        response.whatsappNumber = profile.getWhatsappNumber();
        response.whatsappOptIn = profile.isWhatsappOptIn();
        response.createdAt = profile.getCreatedAt();

        return response;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getBusinessType() {
        return businessType;
    }

    public String getLanguagePreference() {
        return languagePreference;
    }

    public String getWhatsappNumber() {
        return whatsappNumber;
    }

    public boolean isWhatsappOptIn() {
        return whatsappOptIn;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}