package com.app.sme_health_backend.records.projection.dto;

import java.util.UUID;

/**
 * Account-level monthly cash and movement breakdown.
 */
public record AccountMonthlyCashPosition(
        UUID accountId,
        String accountName,
        String accountType,
        String currency,
        int currencyDecimals,
        String openingBalanceAsOfStartOfMonth,
        String startingBalanceAdjustmentDuringMonth,
        String totalInflows,
        String totalOutflows,
        String netMovementDuringMonth,
        String closingBalanceAsOfEndOfMonth,
        String status,
        String institutionName,
        String maskedIdentifier
) {}
