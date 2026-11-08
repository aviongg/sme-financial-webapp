package com.app.sme_health_backend.fx.dto;

import com.app.sme_health_backend.fx.model.FxDirection;
import com.app.sme_health_backend.fx.model.RateSourceType;

import java.math.BigDecimal;

public record FxPreviewResponse(
        String baseCurrency,
        String quoteCurrency,
        BigDecimal rate,
        BigDecimal reciprocalRate,
        String rateConvention,
        RateSourceType sourceType,
        FxDirection direction,
        BigDecimal principalAmount,
        String principalCurrency,
        String settlementCurrency,
        BigDecimal grossSettlementAmount,
        BigDecimal totalFeeAmount,
        BigDecimal fixedFeeAmount,
        BigDecimal percentageFeeAmount,
        String feeCurrency,
        BigDecimal netSettlementAmount,
        BigDecimal totalSettlementDebited,
        BigDecimal effectiveRate,
        RateScenarioVariance scenarioVariance,
        FxPreviewProvenance provenance
) {}

