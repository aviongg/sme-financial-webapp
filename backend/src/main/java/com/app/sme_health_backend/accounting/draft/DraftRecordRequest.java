package com.app.sme_health_backend.accounting.draft;

import java.util.UUID;

/**
 * Request payload for recording a persistent record draft as an authoritative transaction.
 */
public record DraftRecordRequest(
        Long version,
        String confirmedAmount,
        UUID accountId,
        String eventType,
        String effectiveDate,
        String description,
        UUID counterpartyId
) {
    public DraftRecordRequest(
            Long version,
            String confirmedAmount,
            UUID accountId,
            String eventType,
            String effectiveDate,
            String description
    ) {
        this(version, confirmedAmount, accountId, eventType, effectiveDate, description, null);
    }
}
