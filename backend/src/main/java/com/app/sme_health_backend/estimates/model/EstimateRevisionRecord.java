package com.app.sme_health_backend.estimates.model;

import com.app.sme_health_backend.estimates.dto.PricingPreviewRequest.PricingMode;
import com.app.sme_health_backend.estimates.service.EstimateCostCalculator.RawLine;
import com.app.sme_health_backend.estimates.service.PricingCalculator;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** A stored f06-preview-v1 revision: canonical inputs, exact authoritative outputs and approval history. */
public record EstimateRevisionRecord(UUID id, UUID businessId, UUID estimateId, int revision, EstimateRevisionStatus status,
                                     PricingMode pricingMode, BigDecimal pricingValue, BigDecimal saleableQuantity,
                                     BigDecimal totalPlannedCost, BigDecimal plannedBatchPrice, BigDecimal plannedContribution,
                                     BigDecimal plannedMargin, BigDecimal plannedMarkup, BigDecimal plannedPerUnitPrice,
                                     String calculationVersion, long version, UUID createdByUserId, UUID updatedByUserId,
                                     OffsetDateTime createdAt, OffsetDateTime updatedAt, UUID approvedByUserId,
                                     OffsetDateTime approvedAt, UUID supersededByVersionId, OffsetDateTime supersededAt,
                                     List<RawLine> lines) {
    public EstimateRevisionRecord withLines(List<RawLine> attached) {
        return new EstimateRevisionRecord(id, businessId, estimateId, revision, status, pricingMode, pricingValue, saleableQuantity,
                totalPlannedCost, plannedBatchPrice, plannedContribution, plannedMargin, plannedMarkup, plannedPerUnitPrice,
                calculationVersion, version, createdByUserId, updatedByUserId, createdAt, updatedAt, approvedByUserId, approvedAt,
                supersededByVersionId, supersededAt, List.copyOf(attached));
    }

    /** Equality is numeric (compareTo): PostgreSQL pads stored values to the column scale without changing them. */
    public boolean matches(PricingCalculator.Result result) {
        return pricingMode == result.pricingMode() && same(pricingValue, result.pricingValue())
                && same(saleableQuantity, result.saleableQuantity()) && same(totalPlannedCost, result.costs().total())
                && same(plannedBatchPrice, result.price()) && same(plannedContribution, result.contribution())
                && same(plannedMargin, result.margin()) && same(plannedMarkup, result.markup())
                && same(plannedPerUnitPrice, result.perUnit());
    }
    private static boolean same(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }
}
