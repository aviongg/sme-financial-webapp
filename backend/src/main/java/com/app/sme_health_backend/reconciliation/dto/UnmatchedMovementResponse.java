package com.app.sme_health_backend.reconciliation.dto;

import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.accounting.model.TransactionEventType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UnmatchedMovementResponse(
        UUID accountMovementId,
        UUID transactionId,
        TransactionEventType eventType,
        MovementDirection movementDirection,
        BigDecimal amount,
        String currency,
        LocalDate effectiveDate,
        String description,
        String counterpartyName
) {}
