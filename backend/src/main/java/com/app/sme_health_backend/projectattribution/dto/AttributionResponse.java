package com.app.sme_health_backend.projectattribution.dto;

import com.app.sme_health_backend.projectattribution.entity.ProjectFinancialAttribution;
import com.app.sme_health_backend.projectattribution.model.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AttributionResponse(
        UUID id,
        UUID businessId,
        UUID projectId,
        AttributionSourceType sourceType,
        UUID sourceId,
        AttributionDirection sourceDirection,
        String sourceCurrency,
        BigDecimal sourceEligibleAmount,
        BigDecimal attributedAmount,
        String attributedCurrency,
        AttributionCostCategory costCategory,
        EconomicClassification economicClassification,
        LocalDate sourceEventDate,
        LocalDate attributionDate,
        String notes,
        AttributionStatus status,
        String reversalReason,
        Instant reversedAt,
        UUID reversedBy,
        String idempotencyKey,
        String sourceProvenance,
        BigDecimal exchangeRate,
        String rateSource,
        String rateConvention,
        LocalDate rateDate,
        boolean rateVerified,
        BigDecimal baseAmount,
        UUID createdByUserId,
        Instant createdAt
) {
    public static AttributionResponse fromEntity(ProjectFinancialAttribution e) {
        return new AttributionResponse(
                e.getId(),
                e.getBusinessId(),
                e.getProjectId(),
                e.getSourceType(),
                e.getSourceId(),
                e.getSourceDirection(),
                e.getSourceCurrency(),
                e.getSourceEligibleAmount(),
                e.getAttributedAmount(),
                e.getAttributedCurrency(),
                e.getCostCategory(),
                e.getEconomicClassification(),
                e.getSourceEventDate(),
                e.getAttributionDate(),
                e.getNotes(),
                e.getStatus(),
                e.getReversalReason(),
                e.getReversedAt(),
                e.getReversedBy(),
                e.getIdempotencyKey(),
                e.getSourceProvenance(),
                e.getExchangeRate(),
                e.getRateSource(),
                e.getRateConvention(),
                e.getRateDate(),
                e.isRateVerified(),
                e.getBaseAmount(),
                e.getCreatedByUserId(),
                e.getCreatedAt()
        );
    }
}
