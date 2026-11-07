package com.app.sme_health_backend.funding.dto;

import java.time.LocalDate;
import java.util.List;

public record DailyFundingPosition(
        LocalDate date,
        String openingPosition,
        String plannedInflows,
        String plannedOutflows,
        String netPlannedMovement,
        String closingPosition,
        String cumulativeProjectedPosition,
        String deficit,
        boolean isShortfall,
        List<PlannedCashEventDetail> eventsOnDay
) {}
