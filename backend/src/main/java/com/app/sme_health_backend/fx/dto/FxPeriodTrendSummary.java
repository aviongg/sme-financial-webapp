package com.app.sme_health_backend.fx.dto;

import java.math.BigDecimal;

public record FxPeriodTrendSummary(
        String period,
        int settlementCount,
        BigDecimal totalConvertedPrincipal,
        BigDecimal totalRealizedGain,
        BigDecimal totalRealizedLoss,
        BigDecimal netRealizedFx,
        BigDecimal totalBankFees,
        BigDecimal netCashImpact
) {
}

