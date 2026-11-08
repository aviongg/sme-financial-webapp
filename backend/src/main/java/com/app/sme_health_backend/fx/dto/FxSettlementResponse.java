package com.app.sme_health_backend.fx.dto;

import com.app.sme_health_backend.fx.entity.FxObligationSettlement;
import com.app.sme_health_backend.fx.model.FxSettlementStatus;
import com.app.sme_health_backend.fx.model.RealizedFxImpact;
import com.app.sme_health_backend.fx.model.RecognitionEvidenceStatus;
import com.app.sme_health_backend.fx.model.SettlementDirection;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FxSettlementResponse(
        UUID id,
        UUID businessId,
        UUID obligationId,
        UUID financialTransactionId,
        UUID allocationId,
        SettlementDirection settlementDirection,
        String obligationCurrency,
        BigDecimal obligationAmount,
        UUID accountId,
        String accountCurrency,
        BigDecimal convertedPrincipal,
        BigDecimal feeAmount,
        String feeCurrency,
        BigDecimal totalAccountMovement,
        BigDecimal exchangeRate,
        String rateConvention,
        String baseCurrency,
        String quoteCurrency,
        String rateSource,
        Instant rateTimestamp,
        LocalDate settlementDate,
        BigDecimal effectiveRate,
        FxSettlementStatus status,
        UUID reversalTransactionId,
        String reversalReason,
        Instant reversedAt,
        String notes,
        String idempotencyKey,
        BigDecimal allocatedBookValue,
        BigDecimal realizedFxDifference,
        RealizedFxImpact realizedFxImpact,
        RecognitionEvidenceStatus recognitionEvidenceStatus,
        String missingEvidenceReason,
        UUID createdByUserId,
        Instant createdAt
) {
    public static FxSettlementResponse fromEntity(FxObligationSettlement entity) {
        return new FxSettlementResponse(
                entity.getId(),
                entity.getBusinessId(),
                entity.getObligationId(),
                entity.getFinancialTransactionId(),
                entity.getAllocationId(),
                entity.getSettlementDirection(),
                entity.getObligationCurrency(),
                entity.getObligationAmount(),
                entity.getAccountId(),
                entity.getAccountCurrency(),
                entity.getConvertedPrincipal(),
                entity.getFeeAmount(),
                entity.getFeeCurrency(),
                entity.getTotalAccountMovement(),
                entity.getExchangeRate(),
                entity.getRateConvention(),
                entity.getBaseCurrency(),
                entity.getQuoteCurrency(),
                entity.getRateSource(),
                entity.getRateTimestamp(),
                entity.getSettlementDate(),
                entity.getEffectiveRate(),
                entity.getStatus(),
                entity.getReversalTransactionId(),
                entity.getReversalReason(),
                entity.getReversedAt(),
                entity.getNotes(),
                entity.getIdempotencyKey(),
                entity.getAllocatedBookValue(),
                entity.getRealizedFxDifference(),
                entity.getRealizedFxImpact(),
                entity.getRecognitionEvidenceStatus(),
                entity.getMissingEvidenceReason(),
                entity.getCreatedByUserId(),
                entity.getCreatedAt()
        );
    }
}
