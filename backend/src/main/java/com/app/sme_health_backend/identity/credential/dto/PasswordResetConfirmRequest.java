package com.app.sme_health_backend.identity.credential.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetConfirmRequest(
        @NotBlank(message = "Reset token is required")
        String token,

        @NotBlank(message = "New password is required")
        String newPassword
) {
}
