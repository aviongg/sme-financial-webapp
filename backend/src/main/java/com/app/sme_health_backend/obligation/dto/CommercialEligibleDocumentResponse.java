package com.app.sme_health_backend.obligation.dto;

import com.app.sme_health_backend.commercial.model.CommercialDocumentKind;
import com.app.sme_health_backend.commercial.model.CommercialDocumentStatus;
import com.app.sme_health_backend.obligation.ObligationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CommercialEligibleDocumentResponse(
        UUID documentId,
        String documentNumber,
        CommercialDocumentKind kind,
        CommercialDocumentStatus status,
        String counterpartyName,
        LocalDate documentDate,
        LocalDate dueDate,
        BigDecimal total,
        String currency,
        boolean isRecognized,
        UUID obligationId,
        String obligationNumber,
        BigDecimal originalAmount,
        BigDecimal outstandingAmount,
        BigDecimal allocatedAmount,
        ObligationStatus obligationStatus,
        Instant recognitionTimestamp,
        String notes
) {
}

