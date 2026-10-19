package com.app.sme_health_backend.accounting.draft;

import java.util.UUID;

/**
 * Request payload for updating a persistent record draft.
 */
public record DraftUpdateRequest(
        Long version,
        UUID accountId,
        String eventType,
        String effectiveDate,
        String amountInput,
        String description,
        UUID counterpartyId
) {
    public DraftUpdateRequest(
            Long version,
            UUID accountId,
            String eventType,
            String effectiveDate,
            String amountInput,
            String description
    ) {
        this(version, accountId, eventType, effectiveDate, amountInput, description, null);
    }
}
