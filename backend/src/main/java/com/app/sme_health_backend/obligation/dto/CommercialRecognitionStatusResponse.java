package com.app.sme_health_backend.obligation.dto;

import com.app.sme_health_backend.commercial.model.CommercialDocumentKind;
import com.app.sme_health_backend.obligation.ObligationSourceType;
import com.app.sme_health_backend.obligation.ObligationStatus;
import com.app.sme_health_backend.obligation.aging.ObligationDirection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CommercialRecognitionStatusResponse(
        UUID documentId,
        CommercialDocumentKind documentKind,
        String documentNumber,
        String commercialStatus,
        boolean isRecognized,
        UUID obligationId,
        ObligationDirection direction,
        ObligationSourceType sourceType,
        BigDecimal originalAmount,
        BigDecimal outstandingAmount,
        BigDecimal allocatedAmount,
        String currency,
        ObligationStatus obligationStatus,
        Instant recognitionTimestamp,
        List<ObligationAllocationResponse> allocations,
        String notes
) {
}

