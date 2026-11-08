package com.app.sme_health_backend.fx.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record FxSettlementPreviewResponse(
        UUID obligationId,
        String obligationDirection,
        String obligationCurrency,
        BigDecimal totalObligationAmount,
        BigDecimal currentPaidObligationAmount,
        BigDecimal outstandingObligationAmount,
        BigDecimal settleAmountInObligationCurrency,
        BigDecimal remainingObligationAmountAfterSettlement,
        UUID accountId,
        String accountName,
        String accountCurrency,
        BigDecimal exchangeRate,
        String rateConvention,
        BigDecimal convertedPrincipalInAccountCurrency,
        BigDecimal fixedFeeAmount,
        BigDecimal percentageFeeAmount,
        BigDecimal totalFeeAmount,
        String feeCurrency,
        BigDecimal totalAccountMovement,
        String accountMovementType,
        BigDecimal effectiveRate
) {}

