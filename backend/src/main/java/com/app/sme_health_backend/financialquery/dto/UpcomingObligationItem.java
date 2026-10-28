package com.app.sme_health_backend.financialquery.dto;

import java.time.LocalDate;
import java.util.UUID;

public record UpcomingObligationItem(
        UUID obligationId,
        String direction,
        UUID counterpartyId,
        String counterpartyName,
        String referenceNumber,
        String sourceType,
        String scheduleType, // "SCHEDULED_INSTALLMENT" or "OBLIGATION_DUE"
        LocalDate dueDate,
        String amount,
        String paidAmount,
        String outstandingAmount,
        boolean isOverdue,
        String notes
) {}
