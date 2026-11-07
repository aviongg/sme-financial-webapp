package com.app.sme_health_backend.funding.dto;

import java.math.BigDecimal;

public record OpeningLiquidityRequest(
        OpeningLiquiditySource source,
        BigDecimal manualAmount,
        String description
) {}
