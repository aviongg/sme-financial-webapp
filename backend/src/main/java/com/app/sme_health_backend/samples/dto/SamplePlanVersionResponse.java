package com.app.sme_health_backend.samples.dto;

import com.app.sme_health_backend.samples.model.SamplePlanStatus;
import com.app.sme_health_backend.samples.model.RecoveryIntent;
import com.app.sme_health_backend.samples.model.SamplePlanVersionRecord;
import com.app.sme_health_backend.projects.model.ProjectCostCategory;
import com.app.sme_health_backend.samples.service.SampleDecimals;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SamplePlanVersionResponse(
        UUID id,
        UUID businessId,
        UUID activityId,
        int revision,
        SamplePlanStatus status,
        String baseCurrency,
        RecoveryIntent recoveryIntent,
        boolean planningOnly,
        String financialBasis,
        String plannedRecovery,
        String plannedCost,
        String plannedContribution,
        UnavailableReasons unavailableReasons,
        Map<ProjectCostCategory, String> categorySubtotals,
        List<Line> lines,
        String calculationVersion,
        long version,
        UUID createdBy,
        UUID updatedBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        UUID approvedBy,
        OffsetDateTime approvedAt,
        UUID supersededByPlanId,
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

    public record UnavailableReasons(String plannedContribution) { }

    public static SamplePlanVersionResponse fromRecord(SamplePlanVersionRecord record) {
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
                    SampleDecimals.decimal(line.quantity()),
                    line.unit(),
                    SampleDecimals.amount(line.unitCost()),
                    SampleDecimals.amount(line.plannedLineCost())
            );
        }).toList();

        Map<ProjectCostCategory, String> categorySubtotals = new EnumMap<>(ProjectCostCategory.class);
        for (ProjectCostCategory category : ProjectCostCategory.values()) {
            categorySubtotals.put(category, SampleDecimals.amount(rawSubtotals.get(category)));
        }

        return new SamplePlanVersionResponse(
                record.id(),
                record.businessId(),
                record.activityId(),
                record.revision(),
                record.status(),
                record.baseCurrency(),
                record.recoveryIntent(),
                true,
                "PLANNED_ONLY",
                SampleDecimals.amount(record.plannedRecovery()),
                SampleDecimals.amount(record.plannedCost()),
                SampleDecimals.amount(record.plannedContribution()),
                new UnavailableReasons(record.contributionUnavailableReason()),
                Collections.unmodifiableMap(categorySubtotals),
                mappedLines,
                record.calculationVersion(),
                record.version(),
                record.createdBy(),
                record.updatedBy(),
                record.createdAt(),
                record.updatedAt(),
                record.approvedBy(),
                record.approvedAt(),
                record.supersededByPlanId(),
                record.supersededAt()
        );
    }
}
