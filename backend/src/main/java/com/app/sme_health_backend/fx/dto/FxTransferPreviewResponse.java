package com.app.sme_health_backend.fx.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record FxTransferPreviewResponse(
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
        BigDecimal totalSourceDebit,
        BigDecimal effectiveRate,
        String explanation
) {}
