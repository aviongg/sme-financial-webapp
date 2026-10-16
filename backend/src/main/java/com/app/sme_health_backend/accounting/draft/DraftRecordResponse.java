package com.app.sme_health_backend.accounting.draft;

import com.app.sme_health_backend.accounting.dto.TransactionDetailResponse;

/**
 * Response payload returned upon successfully recording a draft into a financial transaction.
 */
public record DraftRecordResponse(
        DraftDetailResponse draft,
        TransactionDetailResponse transaction
) {
}
