package com.app.sme_health_backend.accounting.draft.imports.dto;

import java.util.UUID;

/**
 * Result representation of an individual imported row mapped to its persistent draft.
 */
public record ImportDraftRowResult(
        String sourceRowId,
        int sourceRowNumber,
        String sourceReference,
        UUID draftId,
        String status, // "SAVED" or "REPLAYED"
        String draftStatus, // "DRAFT", "RECORDED", "DISCARDED"
        boolean readyToRecord,
        Long version,
        UUID recordedTransactionId
) {
}
