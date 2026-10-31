package com.app.sme_health_backend.samples.model;

import java.math.BigDecimal;
import com.app.sme_health_backend.projects.model.ProjectCostCategory;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SamplePlanVersionRecord(
        UUID id,
        UUID businessId,
        UUID activityId,
        int revision,
        SamplePlanStatus status,
        String baseCurrency,
        RecoveryIntent recoveryIntent,
        BigDecimal plannedRecovery,
        BigDecimal plannedCost,
        BigDecimal plannedContribution,
        String contributionUnavailableReason,
        String calculationVersion,
        long version,
        UUID createdBy,
        UUID updatedBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        UUID approvedBy,
        OffsetDateTime approvedAt,
        UUID supersededByPlanId,
        OffsetDateTime supersededAt,
        List<LineRecord> lines
) {
    public record LineRecord(
            UUID id,
            UUID businessId,
            UUID activityId,
            UUID planVersionId,
            int lineNumber,
            ProjectCostCategory category,
            String description,
            BigDecimal quantity,
            String unit,
            BigDecimal unitCost,
            BigDecimal plannedLineCost
    ) { }
}
