package com.app.sme_health_backend.reconciliation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReconciliationExcludeRequest(
        @NotBlank(message = "reason is required")
        @Size(max = 255, message = "reason cannot exceed 255 characters")
        String reason
) {}
