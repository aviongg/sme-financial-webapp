package com.app.sme_health_backend.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public class BusinessProfileRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotBlank(message = "Business type is required")
    @Pattern(
        regexp = "trade|manufacturing|services|retail",
        message = "Business type must be one of: trade, manufacturing, services, retail"
    )
    private String businessType;

    @Pattern(
        regexp = "en|ur",
        message = "Language preference must be either en or ur"
    )
    private String languagePreference = "en";

    @Pattern(
        regexp = "^\\+?[0-9]{7,20}$",
        message = "WhatsApp number must contain 7 to 20 digits and may start with +"
    )
    private String whatsappNumber;

    private boolean whatsappOptIn = false;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public String getLanguagePreference() {
        return languagePreference;
    }

    public void setLanguagePreference(String languagePreference) {
        this.languagePreference = languagePreference;
    }

    public String getWhatsappNumber() {
        return whatsappNumber;
    }

    public void setWhatsappNumber(String whatsappNumber) {
        this.whatsappNumber = whatsappNumber;
    }

    public boolean isWhatsappOptIn() {
        return whatsappOptIn;
    }

    public void setWhatsappOptIn(boolean whatsappOptIn) {
        this.whatsappOptIn = whatsappOptIn;
    }
}