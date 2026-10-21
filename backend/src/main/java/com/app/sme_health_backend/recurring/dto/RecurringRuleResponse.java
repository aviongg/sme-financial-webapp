package com.app.sme_health_backend.recurring.dto;

import java.time.Instant;
import java.util.UUID;

public record RecurringRuleResponse(
        UUID id,
        String name,
        String description,
        String eventType,
        UUID accountId,
        String accountName,
        String accountType,
        UUID counterpartyId,
        String counterpartyName,
        String amount,
        String currency,
        String frequency,
        String startDate,
        String endDate,
        String nextDueDate,
        String status,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {}
