package com.app.sme_health_backend.accounting.draft;

import java.time.Instant;
import java.util.UUID;

/**
 * Summary representation of a persistent record draft for list queries.
 */
public record DraftSummaryItem(
        UUID id,
        UUID accountId,
        String eventType,
        String effectiveDate,
        String amountInput,
        String description,
        String status,
        boolean readyToRecord,
        Long version,
        UUID recordedTransactionId,
        UUID createdById,
        UUID updatedById,
        Instant createdAt,
        Instant updatedAt
) {
}
