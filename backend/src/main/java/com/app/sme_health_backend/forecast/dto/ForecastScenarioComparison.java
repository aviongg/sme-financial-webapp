package com.app.sme_health_backend.forecast.dto;

import java.util.List;
import java.util.Map;

public record ForecastScenarioComparison(
        String scenarioName,
        Map<String, Object> assumptions,
        ForecastSummary baselineSummary,
        ForecastSummary scenarioSummary,
        String endingCashVariance,
        String minimumCashVariance,
        String firstDeficitDateChange,
        List<DailyScenarioComparisonRow> dailyComparisons
) {}

