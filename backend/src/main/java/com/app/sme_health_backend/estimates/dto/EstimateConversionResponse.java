package com.app.sme_health_backend.estimates.dto;

import com.app.sme_health_backend.estimates.model.EstimateConversionRecord;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EstimateConversionResponse(UUID id, UUID estimateId, UUID estimateVersionId, UUID projectId,
                                         UUID projectBudgetVersionId, String conversionContractVersion,
                                         String sourceCalculationVersion, String targetCalculationVersion,
                                         UUID convertedByUserId, OffsetDateTime convertedAt, boolean planningOnly,
                                         String targetPlanningStatus) {
    public static EstimateConversionResponse from(EstimateConversionRecord record) {
        if (record == null) return null;
        return new EstimateConversionResponse(record.id(), record.estimateId(), record.estimateVersionId(), record.projectId(),
                record.projectBudgetVersionId(), record.conversionContractVersion(), record.sourceCalculationVersion(),
                record.targetCalculationVersion(), record.convertedByUserId(), record.convertedAt(), true, "PLANNED_ONLY");
    }
}
