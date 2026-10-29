package com.app.sme_health_backend.forecast.dto;

import java.time.LocalDate;
import java.util.List;

public record DailyCashForecastResponse(
        String contractVersion,
        String currency,
        LocalDate asOfDate,
        int horizonDays,
        LocalDate horizonEndDate,
        ForecastSummary summary,
        ForecastCompleteness completeness,
        List<DailyForecastRow> dailyRows,
        String disclaimer
) {}

