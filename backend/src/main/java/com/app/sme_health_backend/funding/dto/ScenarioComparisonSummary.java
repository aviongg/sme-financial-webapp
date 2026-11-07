package com.app.sme_health_backend.funding.dto;

import java.util.List;

public record ScenarioComparisonSummary(
        String baselineScenarioName,
        String alternativeScenarioName,
        String peakFundingDifference,
        String closingLiquidityDifference,
        String totalInflowsDifference,
        String totalOutflowsDifference,
        Integer firstDeficitDateChangeDays,
        Integer recoveryDateChangeDays,
        List<DailyScenarioDifference> dailyDifferences
) {}
