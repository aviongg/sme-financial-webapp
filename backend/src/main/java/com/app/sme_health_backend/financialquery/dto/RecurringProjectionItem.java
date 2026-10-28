package com.app.sme_health_backend.financialquery.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Expected recurring operation projection.
 * Crucial policy: Strictly marked as an estimate projection, NOT a legally recognized obligation.
 */
public record RecurringProjectionItem(
        UUID ruleId,
        String ruleName,
        String eventType,
        String direction,
        LocalDate expectedDate,
        String amount,
        String currency,
        String accountName,
        boolean isEstimate
) {}
