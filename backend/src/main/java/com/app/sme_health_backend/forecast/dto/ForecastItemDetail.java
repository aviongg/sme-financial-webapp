package com.app.sme_health_backend.forecast.dto;

import java.time.LocalDate;

public record ForecastItemDetail(
        String sourceType,
        String sourceId,
        String direction,
        String counterpartyName,
        String referenceNumber,
        String description,
        String amount,
        LocalDate originalDate,
        LocalDate forecastDate,
        boolean isScenarioShifted
) {}

