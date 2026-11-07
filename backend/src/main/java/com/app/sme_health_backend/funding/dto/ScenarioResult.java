package com.app.sme_health_backend.funding.dto;

import java.time.LocalDate;
import java.util.List;

public record ScenarioResult(
        String scenarioName,
        String totalPlannedInflows,
        String totalPlannedOutflows,
        String netPlannedMovement,
        String closingProjectedLiquidity,
        String peakFundingRequirement,
        LocalDate peakDeficitDate,
        LocalDate firstDeficitDate,
        LocalDate recoveryDate,
        int deficitDayCount,
        boolean hasDeficit,
        boolean isRecovered,
        List<DailyFundingPosition> timeline
) {}
