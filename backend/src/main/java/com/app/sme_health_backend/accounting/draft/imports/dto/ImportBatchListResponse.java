package com.app.sme_health_backend.accounting.draft.imports.dto;

import java.util.List;

/**
 * Paginated response for listing import batches.
 */
public record ImportBatchListResponse(
        List<ImportBatchSummaryItem> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
