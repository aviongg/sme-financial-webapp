package com.app.sme_health_backend.funding.dto;

import java.time.LocalDate;
import java.util.List;

public record FundingScenarioCompareResponse(
        String currency,
        LocalDate asOfDate,
        LocalDate horizonStart,
        LocalDate horizonEnd,
        int horizonDays,
        OpeningLiquiditySummary openingLiquidity,
        ScenarioResult baseline,
        ScenarioResult alternative,
        ScenarioComparisonSummary comparison,
        FundingScenarioProvenance provenance,
        List<String> warnings,
        List<String> limitations
) {}
