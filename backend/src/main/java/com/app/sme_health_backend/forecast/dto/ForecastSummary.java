package com.app.sme_health_backend.forecast.dto;

import java.time.LocalDate;

public record ForecastSummary(
        String currency,
        LocalDate asOfDate,
        int horizonDays,
        LocalDate horizonEndDate,
        String startingCash,
        String endingProjectedCash,
        String minimumProjectedCash,
        LocalDate minimumCashDate,
        LocalDate firstDeficitDate,
        String maximumExpectedShortfall,
        String totalExpectedInflows,
        String totalExpectedOutflows,
        String netProjectedMovement,
        String overdueReceivablesTotal,
        String overduePayablesTotal,
        String unpostedDraftsInflow,
        String unpostedDraftsOutflow,
        long unpostedDraftsCount
) {}

