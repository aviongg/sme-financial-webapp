package com.app.sme_health_backend.mfa.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaRecoveryRequest(
        @NotBlank(message = "Recovery code is required")
        String recoveryCode
) {
}
