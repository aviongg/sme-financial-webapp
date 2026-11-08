package com.app.sme_health_backend.commission.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SalespersonCreateRequest(
    @NotBlank(message = "Display name is required")
    @Size(max = 255, message = "Display name must not exceed 255 characters")
    String displayName,

    @NotBlank(message = "Email is required")
    @Email(message = "Valid email is required")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    String email,

    @Size(max = 50, message = "Phone must not exceed 50 characters")
    String phone,

    UUID userId,

    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    String notes
) {}
