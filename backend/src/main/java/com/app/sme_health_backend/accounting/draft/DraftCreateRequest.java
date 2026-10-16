package com.app.sme_health_backend.accounting.draft;

import java.util.UUID;

/**
 * Request payload for creating a persistent record draft.
 */
public record DraftCreateRequest(
        UUID accountId,
        String eventType,
        String effectiveDate,
        String amountInput,
        String description
) {
}
