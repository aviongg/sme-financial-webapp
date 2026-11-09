package com.app.sme_health_backend.projectattribution.dto;

import com.app.sme_health_backend.projectattribution.model.AttributionSourceType;
import com.app.sme_health_backend.projectattribution.model.ProjectOcrMatchMethod;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OcrEvidenceLinkRequest(
        @NotNull(message = "documentId is required")
        UUID documentId,
        UUID attributionId,
        UUID financialSourceId,
        AttributionSourceType financialSourceType,
        ProjectOcrMatchMethod matchMethod,
        String notes,
        String idempotencyKey
) {}
