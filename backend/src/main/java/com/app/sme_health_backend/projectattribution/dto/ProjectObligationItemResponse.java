package com.app.sme_health_backend.projectattribution.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProjectObligationItemResponse(
        UUID linkId,
        UUID obligationId,
        String direction,
        String counterpartyName,
        String referenceNumber,
        LocalDate issueDate,
        LocalDate dueDate,
        BigDecimal originalAmount,
        BigDecimal allocatedAmount,
        BigDecimal paidOrCollectedAmount,
        BigDecimal outstandingAmount,
        String currency,
        String obligationStatus,
        String linkStatus,
        List<ObligationSettlementEvidenceItem> paymentHistory,
        boolean hasAttributedPayment,
        BigDecimal attributedCashAmount,
        String notes
) {}
