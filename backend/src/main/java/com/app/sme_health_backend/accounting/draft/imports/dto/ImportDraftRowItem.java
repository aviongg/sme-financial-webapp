package com.app.sme_health_backend.accounting.draft.imports.dto;

import java.util.UUID;

/**
 * Representation of an individual spreadsheet row to be saved as a draft.
 */
public record ImportDraftRowItem(
        String sourceRowId,
        int sourceRowNumber,
        UUID accountId,
        String eventType,
        String effectiveDate,
        String amountInput,
        String description,
        String sourceReference
) {
}
