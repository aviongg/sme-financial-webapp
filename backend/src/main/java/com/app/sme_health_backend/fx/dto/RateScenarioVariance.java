package com.app.sme_health_backend.fx.dto;

import java.math.BigDecimal;

public record RateScenarioVariance(
        BigDecimal comparisonRate,
        BigDecimal comparisonGrossAmount,
        BigDecimal comparisonSettlementAmount,
        BigDecimal varianceAmount,
        BigDecimal variancePercentage,
        String impact // FAVORABLE, UNFAVORABLE, NEUTRAL
) {}

