package com.app.sme_health_backend.forecast.dto;

import java.math.BigDecimal;

public record ForecastScenarioRequest(
        String currency,
        Integer horizonDays,
        String asOfDate,
        Integer collectionDelayDays,
        BigDecimal collectionPercentage,
        Integer paymentDelayDays,
        Boolean includeOverdueReceivables,
        Integer overdueSpreadDays
) {
    public ForecastScenarioRequest {
        if (currency == null || currency.isBlank()) currency = "PKR";
        if (horizonDays == null) horizonDays = 30;
        if (collectionDelayDays == null) collectionDelayDays = 0;
        if (collectionPercentage == null) collectionPercentage = new BigDecimal("100.00");
        if (paymentDelayDays == null) paymentDelayDays = 0;
        if (includeOverdueReceivables == null) includeOverdueReceivables = Boolean.FALSE;
        if (overdueSpreadDays == null) overdueSpreadDays = 1;
    }
}

