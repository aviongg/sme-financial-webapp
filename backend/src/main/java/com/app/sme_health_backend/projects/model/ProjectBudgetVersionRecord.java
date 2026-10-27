package com.app.sme_health_backend.projects.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ProjectBudgetVersionRecord(
        UUID id,
        UUID businessId,
        UUID projectId,
        int revision,
        ProjectBudgetStatus status,
        String baseCurrency,
        BigDecimal plannedRevenue,
        BigDecimal plannedCost,
        BigDecimal plannedMargin,
        BigDecimal plannedMarginPct,
        String marginUnavailableReason,
        String calculationVersion,
        String sourceEstimateRef,
        long version,
        UUID createdBy,
        UUID updatedBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        UUID approvedBy,
        OffsetDateTime approvedAt,
        UUID supersededByBudgetId,
        OffsetDateTime supersededAt,
        List<LineRecord> lines
) {
    public record LineRecord(
            UUID id,
            UUID businessId,
            UUID projectId,
            UUID budgetVersionId,
            int lineNumber,
            ProjectCostCategory category,
            String description,
            BigDecimal quantity,
            String unit,
            BigDecimal unitCost,
            BigDecimal plannedLineCost
    ) { }
}
