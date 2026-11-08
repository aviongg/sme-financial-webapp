package com.app.sme_health_backend.fx.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record FxSettlementPreviewRequest(
        UUID obligationId,
        UUID accountId,
        BigDecimal amountInObligationCurrency,
        BigDecimal exchangeRate,
        BigDecimal fixedFee,
        BigDecimal percentageFeePercent,
        String feeCurrency
) {
    public FxSettlementPreviewRequest {
        if (fixedFee == null) {
            fixedFee = BigDecimal.ZERO;
        }
        if (percentageFeePercent == null) {
            percentageFeePercent = BigDecimal.ZERO;
        }
    }
}

