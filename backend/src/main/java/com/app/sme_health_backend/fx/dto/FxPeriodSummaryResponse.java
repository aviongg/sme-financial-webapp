package com.app.sme_health_backend.fx.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FxPeriodSummaryResponse(
        UUID businessId,
        String reportingCurrency,
        int totalSettlementsCount,
        int activeSettlementsCount,
        int reversedSettlementsCount,
        int unrecognizedSettlementsCount,
        BigDecimal totalConvertedPrincipal,
        BigDecimal totalRealizedGain,
        BigDecimal totalRealizedLoss,
        BigDecimal netRealizedFx,
        BigDecimal totalBankFees,
        BigDecimal netCashImpact,
        Instant asOfTimestamp
) {
}

