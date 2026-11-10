package com.app.sme_health_backend.obligation.dto;

import com.app.sme_health_backend.obligation.ObligationSourceType;
import com.app.sme_health_backend.obligation.ObligationStatus;
import com.app.sme_health_backend.obligation.aging.ObligationDirection;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CommercialRecognitionResponse(
        UUID obligationId,
        UUID businessId,
        ObligationDirection direction,
        ObligationSourceType sourceType,
        String sourceId,
        String documentNumber,
        UUID counterpartyId,
        String counterpartyName,
        String currency,
        BigDecimal originalAmount,
        BigDecimal outstandingAmount,
        BigDecimal allocatedAmount,
        ObligationStatus status,
        LocalDate issueDate,
        LocalDate dueDate,
        Instant recognitionTimestamp,
        String recognitionProvenance,
        String notes,
        ObligationDetailResponse obligationDetail
) {
}

