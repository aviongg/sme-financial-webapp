package com.app.sme_health_backend.accounting.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TransactionDetailResponse(
        UUID id,
        UUID businessId,
        UUID accountId,
        String accountName,
        String accountType,
        String eventType,
        String movementDirection,
        String amount,
        String currency,
        String baseAmountPkr,
        LocalDate effectiveDate,
        String description,
        String resultingBalance,
        List<MovementDetailResponse> movements,
        UUID createdByUserId,
        String createdAt,
        UUID sourceAccountId,
        String sourceAccountName,
        UUID destinationAccountId,
        String destinationAccountName
) {
    public TransactionDetailResponse(
            UUID id,
            UUID businessId,
            UUID accountId,
            String accountName,
            String accountType,
            String eventType,
            String movementDirection,
            String amount,
            String currency,
            String baseAmountPkr,
            LocalDate effectiveDate,
            String description,
            String resultingBalance,
            List<MovementDetailResponse> movements,
            UUID createdByUserId,
            String createdAt
    ) {
        this(
                id, businessId, accountId, accountName, accountType,
                eventType, movementDirection, amount, currency, baseAmountPkr,
                effectiveDate, description, resultingBalance, movements,
                createdByUserId, createdAt, null, null, null, null
        );
    }
}
