package com.app.sme_health_backend.samples.dto;

import com.app.sme_health_backend.samples.model.SampleKind;
import com.app.sme_health_backend.samples.model.RecoveryIntent;
import java.util.UUID;
import com.app.sme_health_backend.samples.model.SampleRecord;
import com.app.sme_health_backend.samples.model.SampleStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record SampleResponse(
        UUID id,
        UUID businessId,
        String code,
        String name,
        SampleKind kind,
        String description,
        RecoveryIntent recoveryIntent,
        UUID projectId,
        SampleStatus status,
        LocalDate startDate,
        LocalDate targetEndDate,
        String baseCurrency,
        String currencyBasis,
        boolean planningOnly,
        String financialBasis,
        long version,
        UUID createdBy,
        UUID updatedBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        SamplePlanVersionResponse activeApprovedPlan,
        SamplePlanVersionResponse latestDraftPlan,
        int revisionCount,
        boolean canDeleteDraftActivity
) {
    public static SampleResponse fromRecord(
            SampleRecord activity,
            SamplePlanVersionResponse activeApprovedPlan,
            SamplePlanVersionResponse latestDraftPlan,
            int revisionCount,
            boolean hasApprovedOrSupersededHistory
    ) {
        return new SampleResponse(
                activity.id(),
                activity.businessId(),
                activity.code(),
                activity.name(),
                activity.kind(),
                activity.description(),
                activity.recoveryIntent(),
                activity.projectId(),
                activity.status(),
                activity.startDate(),
                activity.targetEndDate(),
                activity.baseCurrency(),
                "APPLICATION_FIXED_PKR",
                true,
                "PLANNED_ONLY",
                activity.version(),
                activity.createdBy(),
                activity.updatedBy(),
                activity.createdAt(),
                activity.updatedAt(),
                activeApprovedPlan,
                latestDraftPlan,
                revisionCount,
                activity.status().canDeleteActivity(hasApprovedOrSupersededHistory)
        );
    }
}
