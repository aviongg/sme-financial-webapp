package com.app.sme_health_backend.forecast.dto;

public record ForecastScenarioResponse(
        String contractVersion,
        DailyCashForecastResponse baseline,
        DailyCashForecastResponse scenario,
        ForecastScenarioComparison comparison
) {}

