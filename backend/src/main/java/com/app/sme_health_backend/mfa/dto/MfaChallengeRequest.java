package com.app.sme_health_backend.mfa.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaChallengeRequest(
        @NotBlank(message = "MFA code is required")
        String code
) {
}
