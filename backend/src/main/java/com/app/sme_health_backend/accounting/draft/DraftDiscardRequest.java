package com.app.sme_health_backend.accounting.draft;

/**
 * Request payload for discarding a persistent record draft.
 */
public record DraftDiscardRequest(
        Long version,
        String reason
) {
}
