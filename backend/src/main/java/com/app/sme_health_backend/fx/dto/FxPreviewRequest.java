package com.app.sme_health_backend.fx.dto;

import com.app.sme_health_backend.fx.model.FxDirection;
import com.app.sme_health_backend.fx.model.RateSourceType;

import java.math.BigDecimal;

public record FxPreviewRequest(
        String baseCurrency,
        String quoteCurrency,
        BigDecimal rate,
        FxDirection direction,
        BigDecimal principalAmount,
        String principalCurrency,
        BigDecimal fixedFee,
        String feeCurrency,
        BigDecimal percentageFeePercent,
        BigDecimal comparisonRate,
        RateSourceType sourceType
) {
    public FxPreviewRequest {
        if (principalCurrency == null || principalCurrency.isBlank()) {
            principalCurrency = baseCurrency;
        }
        if (sourceType == null) {
            sourceType = RateSourceType.MANUAL_USER_RATE;
        }
        if (fixedFee == null) {
            fixedFee = BigDecimal.ZERO;
        }
        if (percentageFeePercent == null) {
            percentageFeePercent = BigDecimal.ZERO;
        }
    }
}

