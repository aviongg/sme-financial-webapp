package com.app.sme_health_backend.accounting.dto;

import java.util.UUID;

public record TransactionPostRequest(
        UUID accountId,
        String eventType,
        String amount,
        String effectiveDate,
        String description,
        UUID counterpartyId
) {
    public TransactionPostRequest(
            UUID accountId,
            String eventType,
            String amount,
            String effectiveDate,
            String description
    ) {
        this(accountId, eventType, amount, effectiveDate, description, null);
    }
}
