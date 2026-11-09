package com.app.sme_health_backend.projectattribution.dto;

import com.app.sme_health_backend.projectattribution.model.AttributionCostCategory;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProjectCostingSummaryResponse(
        UUID projectId,
        String projectCode,
        String projectName,
        String projectStatus,
        String baseCurrency,
        Integer approvedBudgetRevision,
        boolean hasApprovedBudget,
        BigDecimal plannedRevenue,
        BigDecimal plannedCost,
        BigDecimal plannedMargin,
        BigDecimal plannedMarginPct,
        BigDecimal attributedCashInflows,
        BigDecimal attributedCashOutflows,
        BigDecimal cashHeadroom,
        BigDecimal cashHeadroomPct,
        List<CategoryCostSummary> categories,
        BigDecimal recognizedCommissionCost,
        BigDecimal nonCashFxImpact,
        BigDecimal unattributedEligibleAmount,
        String profitabilityStatus,
        String unavailabilityReason,
        BigDecimal actualGrossProfit,
        BigDecimal actualGrossMarginPct,
        String coverageNotes
) {
    public record CategoryCostSummary(
            AttributionCostCategory category,
            BigDecimal plannedCost,
            BigDecimal actualCashOutflow,
            BigDecimal cashHeadroom,
            BigDecimal recognizedCost,
            BigDecimal nonCashEvidence
    ) {
    }
}
