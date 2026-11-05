package com.app.sme_health_backend.estimates.dto;

import com.app.sme_health_backend.estimates.model.EstimateRevisionStatus;
import com.app.sme_health_backend.estimates.model.EstimateStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EstimateResponse(UUID id, UUID businessId, String code, String name, String description, EstimateStatus status,
                               String baseCurrency, boolean planningOnly, long version, UUID createdByUserId,
                               UUID updatedByUserId, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                               UUID archivedByUserId, OffsetDateTime archivedAt, int revisionCount,
                               RevisionSummary approvedRevision, RevisionSummary draftRevision, int conversionCount,
                               boolean hasApprovedHistory) {
    public record RevisionSummary(UUID id, int revision, EstimateRevisionStatus status, long version,
                                  String totalPlannedCost, String plannedBatchPrice) { }
}
