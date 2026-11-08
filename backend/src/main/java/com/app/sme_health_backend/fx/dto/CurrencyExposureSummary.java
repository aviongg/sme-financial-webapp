package com.app.sme_health_backend.fx.dto;

import java.math.BigDecimal;

public record CurrencyExposureSummary(
        String currency,
        String displayName,
        String symbol,
        int minorUnits,
        BigDecimal outstandingReceivables,
        BigDecimal outstandingPayables,
        BigDecimal netExposure,
        BigDecimal settledReceivablesVolume,
        BigDecimal settledPayablesVolume,
        int totalObligationsCount,
        int totalSettlementsCount
) {
}

