package com.app.sme_health_backend.forecast.dto;

import java.time.LocalDate;

public record DailyScenarioComparisonRow(
        LocalDate date,
        int dayIndex,
        String baselineClosingCash,
        String scenarioClosingCash,
        String variance,
        String baselineInflow,
        String scenarioInflow,
        String baselineOutflow,
        String scenarioOutflow
) {}

