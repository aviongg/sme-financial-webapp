package com.app.sme_health_backend.funding.dto;

import java.math.BigDecimal;

public record PlannedCashEventInput(
        String eventId,
        String date,
        CashFlowDirection direction,
        BigDecimal amount,
        String description,
        String category
) {}
