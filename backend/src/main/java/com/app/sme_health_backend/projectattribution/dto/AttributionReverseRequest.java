package com.app.sme_health_backend.projectattribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AttributionReverseRequest(
        @NotBlank(message = "Reversal reason is required")
        @Size(max = 255, message = "Reversal reason must not exceed 255 characters")
        String reversalReason
) {
}
