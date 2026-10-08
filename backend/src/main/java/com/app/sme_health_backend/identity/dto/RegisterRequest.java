package com.app.sme_health_backend.identity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Size(max = 320, message = "Email must not exceed 320 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 12, max = 128, message = "Password must be between 12 and 128 characters")
        String password,

        @NotBlank(message = "Full name is required")
        @Size(max = 150, message = "Full name must not exceed 150 characters")
        String fullName
) {
}
