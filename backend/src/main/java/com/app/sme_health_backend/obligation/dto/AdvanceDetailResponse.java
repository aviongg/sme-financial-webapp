package com.app.sme_health_backend.obligation.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AdvanceDetailResponse(
        UUID id,
        UUID businessId,
        UUID counterpartyId,
        String counterpartyName,
        String counterpartyType,
        String direction,
        UUID financialTransactionId,
        String originalAmount,
        String appliedAmount,
        String unappliedAmount,
        String currency,
        LocalDate effectiveDate,
        String status,
        String referenceNumber,
        String description,
        String cancelledAt,
        String cancellationReason,
        UUID reversalTransactionId,
        String createdAt,
        List<AdvanceApplicationItemResponse> applications
) {
}
