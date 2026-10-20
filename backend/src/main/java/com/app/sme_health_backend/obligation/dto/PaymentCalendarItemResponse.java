package com.app.sme_health_backend.obligation.dto;

import java.time.LocalDate;
import java.util.UUID;

public record PaymentCalendarItemResponse(
        UUID obligationId,
        String direction,
        UUID counterpartyId,
        String counterpartyName,
        String referenceNumber,
        String sourceType,
        String itemType,
        LocalDate date,
        String amount,
        String settledAmount,
        String outstandingAmount,
        boolean isOverdue,
        String notes
) {
}
