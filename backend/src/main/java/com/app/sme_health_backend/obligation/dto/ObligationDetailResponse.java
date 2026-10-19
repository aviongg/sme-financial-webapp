package com.app.sme_health_backend.obligation.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ObligationDetailResponse(
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
        LocalDate voidDate,
        String voidReason,
        List<ObligationAllocationResponse> allocations,
        String createdAt
) {
}
