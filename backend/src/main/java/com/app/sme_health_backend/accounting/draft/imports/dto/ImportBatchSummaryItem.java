package com.app.sme_health_backend.accounting.draft.imports.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Summary item for listing import batches.
 */
public record ImportBatchSummaryItem(
        UUID batchId,
        String sourceLabel,
        int totalRows,
        Instant createdAt,
        Instant updatedAt
) {
}
