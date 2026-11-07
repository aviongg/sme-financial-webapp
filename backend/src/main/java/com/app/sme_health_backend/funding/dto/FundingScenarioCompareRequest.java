package com.app.sme_health_backend.funding.dto;

public record FundingScenarioCompareRequest(
        String currency,
        String asOfDate,
        Integer horizonDays,
        OpeningLiquidityRequest openingLiquidity,
        ScenarioDefinition baselineScenario,
        ScenarioDefinition alternativeScenario
) {
    public FundingScenarioCompareRequest {
        if (currency == null || currency.isBlank()) {
            currency = "PKR";
        }
        if (horizonDays == null) {
            horizonDays = 30;
        }
    }
}
