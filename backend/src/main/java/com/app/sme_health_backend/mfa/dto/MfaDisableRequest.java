package com.app.sme_health_backend.mfa.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaDisableRequest(
        @NotBlank(message = "Password is required")
        String password,

        @NotBlank(message = "Verification code is required")
        String verificationCode
) {
}
