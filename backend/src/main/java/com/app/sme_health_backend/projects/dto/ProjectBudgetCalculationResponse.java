package com.app.sme_health_backend.projects.dto;

import com.app.sme_health_backend.projects.model.ProjectCostCategory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Deterministic Phase A budget calculation result.
 * All values are explicitly PLANNED; no actual revenue, actual cost, or ledger posting is represented.
 */
public record ProjectBudgetCalculationResponse(
        UUID businessId,
        String baseCurrency,
        String currencyBasis,
        boolean planningOnly,
        String plannedRevenue,
        String plannedCost,
        String plannedMargin,
        String plannedMarginPct,
        UnavailableReasons unavailableReasons,
        List<Line> lines,
        Map<ProjectCostCategory, String> categorySubtotals,
        Calculation calculation,
        RawTotals rawTotals
) {
    public record Line(
            int lineNumber,
            ProjectCostCategory category,
            String description,
            String quantity,
            String unit,
            String unitCost,
            String plannedLineCost,
            BigDecimal rawQuantity,
            BigDecimal rawUnitCost,
            BigDecimal rawPlannedLineCost
    ) { }

    public record UnavailableReasons(String plannedMarginPct) { }

    public record Calculation(
            String version,
            int divisionScale,
            String divisionRounding,
            String accountingRoundingPolicy
    ) { }

    public record RawTotals(
            BigDecimal plannedRevenue,
            BigDecimal plannedCost,
            BigDecimal plannedMargin,
            BigDecimal plannedMarginPct
    ) { }
}
