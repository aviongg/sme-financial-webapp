package com.app.sme_health_backend.fx.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FxAnalyticsOverviewResponse(
        UUID businessId,
        String reportingCurrency,
        List<CurrencyExposureSummary> currencyExposures,
        FxPeriodSummaryResponse performanceSummary,
        List<FxPeriodTrendSummary> periodicTrends,
        int totalForeignObligationsCount,
        int openForeignObligationsCount,
        Instant asOfTimestamp
) {
}

