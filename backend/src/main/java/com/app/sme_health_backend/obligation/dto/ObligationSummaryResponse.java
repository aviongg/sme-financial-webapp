package com.app.sme_health_backend.obligation.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ObligationSummaryResponse(
        UUID id,
        UUID businessId,
        UUID counterpartyId,
        String counterpartyName,
        String counterpartyType,
        String direction,
        String sourceType,
        String sourceId,
        String referenceNumber,
        LocalDate issueDate,
        LocalDate dueDate,
        String originalAmount,
        String paidAmount,
        String outstandingAmount,
        String currency,
        String status,
        String settlementStatus,
        String agingBucket,
        Integer daysOverdue,
        String description,
        String createdAt
) {
}
