package com.app.sme_health_backend.reconciliation.dto;

import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.accounting.model.TransactionEventType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ReconciliationMatchCandidateResponse(
        UUID accountMovementId,
        UUID transactionId,
        TransactionEventType eventType,
        MovementDirection movementDirection,
        BigDecimal amount,
        String currency,
        LocalDate effectiveDate,
        String description,
        String reference,
        String counterpartyName,
        int confidenceScore,
        List<String> matchReasons
) {}
