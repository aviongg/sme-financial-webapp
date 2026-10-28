package com.app.sme_health_backend.estimates.dto;

import com.app.sme_health_backend.estimates.model.EstimateRevisionStatus;
import com.app.sme_health_backend.estimates.service.ProjectConversionValidator.Issue;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A persisted revision. {@code result} is the stored f06-preview-v1 output, serialized exactly as the stateless preview.
 * {@code conversionReadiness} reports F05 exact-fit; a non-convertible revision is still a valid estimate.
 */
public record EstimateRevisionResponse(UUID id, UUID estimateId, UUID businessId, int revision, EstimateRevisionStatus status,
                                       boolean planningOnly, long version, UUID createdByUserId, UUID updatedByUserId,
                                       OffsetDateTime createdAt, OffsetDateTime updatedAt, UUID approvedByUserId,
                                       OffsetDateTime approvedAt, UUID supersededByVersionId, OffsetDateTime supersededAt,
                                       PricingPreviewResponse result, EstimateConversionResponse conversion,
                                       ConversionReadiness conversionReadiness) {
    public record ConversionReadiness(boolean convertible, String code, List<Issue> issues) { }
}
