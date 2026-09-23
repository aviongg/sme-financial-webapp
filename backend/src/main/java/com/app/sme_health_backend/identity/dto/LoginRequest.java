package com.app.sme_health_backend.identity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = false)
public record LoginRequest(
        @NotBlank(message = "Email is required")
        String email,

        @NotBlank(message = "Password is required")
        String password
) {
}
