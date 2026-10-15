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
        String observationAt
) {}
