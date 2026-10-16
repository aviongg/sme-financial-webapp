package com.app.sme_health_backend.accounting.dto;

import java.time.LocalDate;
import java.util.UUID;

public record TransactionSummaryResponse(
        String currency,
        LocalDate startDate,
        LocalDate endDate,
        UUID accountId,
        String eventType,
        long transactionCount,
        String moneyReceived,
        String moneyPaid,
        String netCashMovement,
        String transferIn,
        String transferOut,
        String netAccountMovement,
        String observationAt
) {
    public TransactionSummaryResponse(
            String currency,
            LocalDate startDate,
            LocalDate endDate,
            UUID accountId,
            String eventType,
            long transactionCount,
            String moneyReceived,
            String moneyPaid,
            String netCashMovement,
            String observationAt
    ) {
        this(
                currency, startDate, endDate, accountId, eventType,
                transactionCount, moneyReceived, moneyPaid, netCashMovement,
                "0.00", "0.00", netCashMovement, observationAt
        );
    }
}
