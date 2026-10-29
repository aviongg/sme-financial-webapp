package com.app.sme_health_backend.forecast.dto;

import java.time.LocalDate;
import java.util.List;

public record DailyForecastRow(
        LocalDate date,
        int dayIndex,
        String projectedOpeningCash,
        String expectedReceivableCollections,
        String expectedRecurringReceipts,
        String totalExpectedInflows,
        String expectedPayableSettlements,
        String expectedRecurringExpenses,
        String totalExpectedOutflows,
        String netProjectedMovement,
        String projectedClosingCash,
        boolean isDeficit,
        String deficitAmount,
        List<ForecastItemDetail> contributingItems
) {}

