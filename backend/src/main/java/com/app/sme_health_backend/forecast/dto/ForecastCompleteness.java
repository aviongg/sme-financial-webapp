package com.app.sme_health_backend.forecast.dto;

import com.app.sme_health_backend.financialquery.model.DataCompleteness;

import java.util.List;

public record ForecastCompleteness(
        DataCompleteness completeness,
        List<String> omittedCategories,
        int missingDueDatesCount,
        String missingDueDatesTotalExposure,
        boolean hasOverdueReceivables,
        boolean hasUnpostedDrafts,
        int activeRecurringRulesCount,
        List<ForecastLiquidityWarning> warnings
) {}

