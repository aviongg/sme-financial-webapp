package com.app.sme_health_backend.financialquery.dto;

import java.math.BigDecimal;

public record FxSettlementImpactResult(
        String reportingCurrency,
        BigDecimal netRealizedGainLoss,
        int totalForeignObligationsCount,
        int openForeignObligationsCount,
        int currencyExposuresCount,
        String summary
) {}

