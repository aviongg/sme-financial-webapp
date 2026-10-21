package com.app.sme_health_backend.recurring.dto;

public record OccurrencePreviewItem(
        String scheduledDate,
        String amount,
        String currency,
        String eventType
) {}
