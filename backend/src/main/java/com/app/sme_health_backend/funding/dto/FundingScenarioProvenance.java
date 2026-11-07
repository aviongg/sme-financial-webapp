package com.app.sme_health_backend.funding.dto;

public record FundingScenarioProvenance(
        String openingLiquiditySource,
        String planningMode,
        String calculatedAt,
        String calculationEngine
) {}
