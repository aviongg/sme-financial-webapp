package com.app.sme_health_backend.recurring.dto;

import java.util.UUID;

public record RecurringRuleUpdateRequest(
        String name,
        String description,
        String eventType,
        UUID accountId,
        UUID counterpartyId,
        String amount,
        String frequency,
        String endDate,
        Long version
) {}
