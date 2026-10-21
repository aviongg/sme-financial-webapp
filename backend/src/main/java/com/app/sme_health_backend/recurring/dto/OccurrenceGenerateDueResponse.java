package com.app.sme_health_backend.recurring.dto;

public record OccurrenceGenerateDueResponse(
        int generatedCount,
        int processedRulesCount,
        String message
) {}
