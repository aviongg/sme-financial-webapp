package com.app.sme_health_backend.profile.dto;

import jakarta.validation.constraints.NotNull;

public record WhatsAppPreferenceRequest(
        String whatsappNumber,

        @NotNull(message = "Opt-in status is required")
        Boolean optIn
) {}
