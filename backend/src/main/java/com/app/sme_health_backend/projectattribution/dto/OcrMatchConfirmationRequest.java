package com.app.sme_health_backend.projectattribution.dto;

import java.util.UUID;

public record OcrMatchConfirmationRequest(
        UUID attributionId,
        UUID financialSourceId,
        String notes
) {}
