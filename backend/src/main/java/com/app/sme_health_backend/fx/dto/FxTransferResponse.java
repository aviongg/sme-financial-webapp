package com.app.sme_health_backend.fx.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FxTransferResponse(
        UUID id,
        UUID businessId,
        UUID sourceAccountId,
        String sourceAccountName,
        String sourceCurrency,
        UUID destinationAccountId,
        String destinationAccountName,
        String destinationCurrency,
        BigDecimal sourceAmount,
        BigDecimal exchangeRate,
        BigDecimal convertedDestinationPrincipal,
        BigDecimal feeAmount,
        String feeCurrency,
        String feePlacement,
        BigDecimal netDestinationAmount,
        BigDecimal totalSourceAmount,
        UUID financialTransactionId,
        UUID sourceMovementId,
        UUID destinationMovementId,
        LocalDate transferDate,
        String status,
        UUID reversalTransactionId,
        String reversalReason,
        Instant reversedAt,
        String notes,
        UUID createdByUserId,
        Instant createdAt
) {}
