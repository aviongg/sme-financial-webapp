package com.app.sme_health_backend.projectattribution.dto;

import jakarta.validation.constraints.NotBlank;

public record OcrEvidenceInvalidateRequest(
        @NotBlank(message = "invalidation reason is required")
        String reason
) {}
