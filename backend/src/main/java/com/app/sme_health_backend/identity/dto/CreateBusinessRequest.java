package com.app.sme_health_backend.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateBusinessRequest(
        @NotBlank(message = "Business type is required")
        @Size(max = 20, message = "Business type must not exceed 20 characters")
        @Pattern(
                regexp = "trade|manufacturing|services|retail",
                message = "Business type must be one of: trade, manufacturing, services, retail"
        )
        String businessType,

        @Size(max = 2, message = "Language preference must not exceed 2 characters")
        @Pattern(
                regexp = "en|ur",
                message = "Language preference must be either en or ur"
        )
        String languagePreference,

        @Size(max = 20, message = "WhatsApp number must not exceed 20 characters")
        @Pattern(
                regexp = "^(?:\\+[0-9]{6,19}|[0-9]{7,20})$",
                message = "WhatsApp number must contain 7 to 20 digits, with an optional + prefix"
        )
        String whatsappNumber,

        boolean whatsappOptIn,

        @Pattern(
                regexp = "immediate|2weeks|1month_plus|irregular",
                message = "paymentBehavior must be one of: immediate, 2weeks, 1month_plus, irregular"
        )
        String paymentBehavior,

        Boolean ntnRegistered,

        Boolean businessRegistered
) {
    public CreateBusinessRequest {
        if (languagePreference == null || languagePreference.isBlank()) {
            languagePreference = "en";
        }
    }
}
