package com.app.sme_health_backend.projects.dto;

import com.app.sme_health_backend.projects.model.ProjectBudgetStatus;
import com.app.sme_health_backend.projects.model.ProjectBudgetVersionRecord;
import com.app.sme_health_backend.projects.model.ProjectCostCategory;
import com.app.sme_health_backend.projects.service.ProjectDecimals;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProjectBudgetVersionResponse(
        UUID id,
        UUID businessId,
        UUID projectId,
        int revision,
        ProjectBudgetStatus status,
        String baseCurrency,
        boolean planningOnly,
        String plannedRevenue,
        String plannedCost,
        String plannedMargin,
        String plannedMarginPct,
        UnavailableReasons unavailableReasons,
        Map<ProjectCostCategory, String> categorySubtotals,
        List<Line> lines,
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
        OffsetDateTime supersededAt
) {
    public record Line(
            UUID id,
            int lineNumber,
            ProjectCostCategory category,
            String description,
            String quantity,
            String unit,
            String unitCost,
            String plannedLineCost
    ) { }

    public record UnavailableReasons(String plannedMarginPct) { }

    public static ProjectBudgetVersionResponse fromRecord(ProjectBudgetVersionRecord record) {
        if (record == null) {
            return null;
        }
        Map<ProjectCostCategory, BigDecimal> rawSubtotals = new EnumMap<>(ProjectCostCategory.class);
        for (ProjectCostCategory category : ProjectCostCategory.values()) {
            rawSubtotals.put(category, BigDecimal.ZERO);
        }
        List<Line> mappedLines = record.lines().stream().map(line -> {
            rawSubtotals.merge(line.category(), line.plannedLineCost(), BigDecimal::add);
            return new Line(
                    line.id(),
                    line.lineNumber(),
                    line.category(),
                    line.description(),
                    ProjectDecimals.decimal(line.quantity()),
                    line.unit(),
                    ProjectDecimals.amount(line.unitCost()),
                    ProjectDecimals.amount(line.plannedLineCost())
            );
        }).toList();

        Map<ProjectCostCategory, String> categorySubtotals = new EnumMap<>(ProjectCostCategory.class);
        for (ProjectCostCategory category : ProjectCostCategory.values()) {
            categorySubtotals.put(category, ProjectDecimals.amount(rawSubtotals.get(category)));
        }

        return new ProjectBudgetVersionResponse(
                record.id(),
                record.businessId(),
                record.projectId(),
                record.revision(),
                record.status(),
                record.baseCurrency(),
                true,
                ProjectDecimals.amount(record.plannedRevenue()),
                ProjectDecimals.amount(record.plannedCost()),
                ProjectDecimals.amount(record.plannedMargin()),
                ProjectDecimals.decimal(record.plannedMarginPct()),
                new UnavailableReasons(record.marginUnavailableReason()),
                Collections.unmodifiableMap(categorySubtotals),
                mappedLines,
                record.calculationVersion(),
                record.sourceEstimateRef(),
                record.version(),
                record.createdBy(),
                record.updatedBy(),
                record.createdAt(),
                record.updatedAt(),
                record.approvedBy(),
                record.approvedAt(),
                record.supersededByBudgetId(),
                record.supersededAt()
        );
    }
}
