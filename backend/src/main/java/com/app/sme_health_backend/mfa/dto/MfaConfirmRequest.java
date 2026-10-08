package com.app.sme_health_backend.mfa.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaConfirmRequest(
        @NotBlank(message = "Verification code is required")
        String code,

        @NotBlank(message = "Current password is required")
        String password
) {
}
