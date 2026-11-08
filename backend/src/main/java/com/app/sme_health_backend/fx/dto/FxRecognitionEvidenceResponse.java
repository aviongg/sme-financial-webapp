package com.app.sme_health_backend.fx.dto;

import com.app.sme_health_backend.fx.entity.FxObligationRecognitionEvidence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FxRecognitionEvidenceResponse(
        UUID id,
        UUID businessId,
        UUID obligationId,
        String nativeCurrency,
        BigDecimal nativeAmount,
        String functionalCurrency,
        BigDecimal recognitionRate,
        String rateConvention,
        BigDecimal bookValue,
        LocalDate recognitionDate,
        String recognitionSource,
        String evidenceProvenance,
        String notes,
        UUID createdByUserId,
        Instant createdAt
) {
    public static FxRecognitionEvidenceResponse fromEntity(FxObligationRecognitionEvidence entity) {
        return new FxRecognitionEvidenceResponse(
                entity.getId(),
                entity.getBusinessId(),
                entity.getObligationId(),
                entity.getNativeCurrency(),
                entity.getNativeAmount(),
                entity.getFunctionalCurrency(),
                entity.getRecognitionRate(),
                entity.getRateConvention(),
                entity.getBookValue(),
                entity.getRecognitionDate(),
                entity.getRecognitionSource(),
                entity.getEvidenceProvenance(),
                entity.getNotes(),
                entity.getCreatedByUserId(),
                entity.getCreatedAt()
        );
    }
}

