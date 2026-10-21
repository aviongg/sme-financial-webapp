package com.app.sme_health_backend.recurring.dto;

import java.time.Instant;
import java.util.UUID;

public record RecurringOccurrenceResponse(
        UUID id,
        UUID recurringRuleId,
        String ruleName,
        Long ruleVersion,
        String scheduledDate,
        String amount,
        String currency,
        String eventType,
        UUID accountId,
        String accountName,
        UUID counterpartyId,
        String counterpartyName,
        String description,
        String status,
        UUID draftId,
        UUID postedTransactionId,
        Instant generatedAt,
        Instant postedAt,
        Instant skippedAt,
        String skippedReason,
        Instant reversedAt
) {}
