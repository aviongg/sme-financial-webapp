package com.app.sme_health_backend.accounting.draft.imports.dto;

import java.util.List;
import java.util.UUID;

/**
 * Request payload for saving a batch chunk of reviewed spreadsheet rows.
 */
public record ImportDraftsRequest(
        UUID batchId,
        String sourceLabel,
        List<ImportDraftRowItem> rows
) {
}
