package com.app.sme_health_backend.projectattribution.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CommercialDocumentLinkResponse(
        UUID id,
        UUID businessId,
        UUID projectId,
        UUID documentId,
        String documentKind,
        String documentNumber,
        String counterpartyName,
        LocalDate documentDate,
        LocalDate dueDate,
        BigDecimal documentTotal,
        BigDecimal allocatedAmount,
        String currency,
        String linkStatus,
        String accountingStatus,
        boolean isMatchedToFinancialObligation,
        UUID matchedObligationId,
        String notes,
        Instant createdAt
) {}
