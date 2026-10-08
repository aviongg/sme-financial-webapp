package com.app.sme_health_backend.identity.credential.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetRequest(
        @NotBlank(message = "Email is required")
        String email
) {
}
