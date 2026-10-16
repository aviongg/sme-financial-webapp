package com.app.sme_health_backend.accounting.draft;

import java.util.List;

/**
 * Paginated list response of record drafts.
 */
public record DraftListResponse(
        List<DraftSummaryItem> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
