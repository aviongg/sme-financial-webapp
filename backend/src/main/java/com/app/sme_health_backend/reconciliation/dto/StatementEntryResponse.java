package com.app.sme_health_backend.reconciliation.dto;

import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.reconciliation.model.StatementEntryStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StatementEntryResponse(
        UUID id,
        UUID importId,
        UUID accountId,
        int sourceRowNumber,
        LocalDate postedDate,
        LocalDate valueDate,
        MovementDirection direction,
        BigDecimal amount,
        String currency,
        String description,
        String bankReference,
        String externalTransactionId,
        BigDecimal runningBalance,
        StatementEntryStatus status,
        String exclusionReason,
        UUID linkedDraftId,
        boolean duplicateWarning,
        Instant createdAt
) {}
