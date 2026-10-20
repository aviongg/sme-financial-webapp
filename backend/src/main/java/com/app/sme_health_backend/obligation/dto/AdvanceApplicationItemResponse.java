package com.app.sme_health_backend.obligation.dto;

import java.time.LocalDate;
import java.util.UUID;

public record AdvanceApplicationItemResponse(
        UUID allocationId,
        UUID obligationId,
        String obligationReferenceNumber,
        String amount,
        LocalDate effectiveDate,
        String status,
        String cancelledAt,
        String cancellationReason,
        String createdAt
) {
}
