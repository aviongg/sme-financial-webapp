package com.app.sme_health_backend.financialquery.dto;

import java.time.LocalDate;
import java.util.UUID;

public record OverdueReceivableItem(
        UUID obligationId,
        String referenceNumber,
        UUID counterpartyId,
        String counterpartyName,
        LocalDate issueDate,
        LocalDate dueDate,
        long overdueDays,
        String bucketCode,
        String originalAmount,
        String settledAmount,
        String outstandingAmount
) {}
