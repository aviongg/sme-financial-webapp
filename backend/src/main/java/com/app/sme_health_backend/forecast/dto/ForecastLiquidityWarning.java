package com.app.sme_health_backend.forecast.dto;

import java.time.LocalDate;

public record ForecastLiquidityWarning(
        String code,
        String severity,
        String title,
        String description,
        LocalDate affectedDate,
        String amount
) {}

