package com.app.sme_health_backend.funding.dto;

import java.time.LocalDate;

public record PlannedCashEventDetail(
        String eventId,
        LocalDate date,
        String direction,
        String amount,
        String description,
        String category
) {}
