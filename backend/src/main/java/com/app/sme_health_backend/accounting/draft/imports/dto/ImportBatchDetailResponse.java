package com.app.sme_health_backend.accounting.draft.imports.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detailed representation of an import batch and all its linked draft rows.
 */
public record ImportBatchDetailResponse(
        UUID batchId,
        String sourceLabel,
        int totalRows,
        Instant createdAt,
        Instant updatedAt,
        List<ImportDraftRowResult> rows
) {
}
