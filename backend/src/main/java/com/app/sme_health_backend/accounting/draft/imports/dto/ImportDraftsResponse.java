package com.app.sme_health_backend.accounting.draft.imports.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response payload for saving a batch chunk of reviewed spreadsheet rows.
 */
public record ImportDraftsResponse(
        UUID batchId,
        String sourceLabel,
        int savedCount,
        int replayedCount,
        int totalRowsInBatch,
        List<ImportDraftRowResult> rows
) {
}
