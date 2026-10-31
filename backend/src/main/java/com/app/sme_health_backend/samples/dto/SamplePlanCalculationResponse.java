package com.app.sme_health_backend.samples.dto;

import com.app.sme_health_backend.projects.model.ProjectCostCategory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Deterministic Phase A plan calculation result.
 * All values are explicitly PLANNED; no actual revenue, actual cost, or ledger posting is represented.
 */
public record SamplePlanCalculationResponse(
        UUID businessId,
        String baseCurrency,
        String currencyBasis,
        boolean planningOnly,
        String plannedRecovery,
        String plannedCost,
        String plannedContribution,
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

    public record UnavailableReasons(String plannedContribution) { }

    public record Calculation(
            String version,
            String accountingRoundingPolicy
    ) { }

    public record RawTotals(
            BigDecimal plannedRecovery,
            BigDecimal plannedCost,
            BigDecimal plannedContribution
    ) { }
}
