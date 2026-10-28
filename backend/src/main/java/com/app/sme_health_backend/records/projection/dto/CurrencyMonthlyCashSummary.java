package com.app.sme_health_backend.records.projection.dto;

/**
 * Currency-scoped monthly cash aggregation without unapproved FX blending.
 */
public record CurrencyMonthlyCashSummary(
        String currency,
        int decimals,
        String totalOpeningBalance,
        String totalStartingBalanceAdjustments,
        String totalInflows,
        String totalOutflows,
        String totalNetMovements,
        String totalClosingBalance,
        int accountCount
) {}
