package com.app.sme_health_backend.estimates.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import static com.app.sme_health_backend.estimates.dto.EstimateLineRequest.CostGroup;
import static com.app.sme_health_backend.estimates.dto.PricingPreviewRequest.PricingMode;

public record PricingPreviewResponse(UUID businessId, String currency, String currencyBasis, boolean planningOnly,
        List<Line> lines, Map<CostGroup, String> groupSubtotals, String totalPlannedCost, String saleableQuantity,
        PricingMode pricingMode, String pricingValue, String plannedBatchPrice, String plannedContribution,
        String plannedMargin, String plannedMarkup, String plannedPerUnitPrice,
        UnavailableReasons unavailableReasons, Calculation calculation) {
    public record Line(int lineNumber, CostGroup costGroup, String description, String quantity, String unit,
                       String unitCost, String plannedCost) { }
    public record UnavailableReasons(String plannedMargin, String plannedMarkup, String plannedPerUnitPrice) { }
    public record Calculation(String version, int divisionScale, String divisionRounding, String accountingRoundingPolicy) { }
}
