package com.app.sme_health_backend.financialquery.dto;

public record CurrencyCashSummary(
        String currency,
        int currencyDecimals,
        String openingBalance,
        String netMovements,
        String currentBalance,
        int accountCount
) {}
