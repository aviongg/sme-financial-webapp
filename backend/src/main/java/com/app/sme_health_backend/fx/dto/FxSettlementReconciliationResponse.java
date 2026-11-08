package com.app.sme_health_backend.fx.dto;

import com.app.sme_health_backend.fx.model.FxSettlementStatus;
import com.app.sme_health_backend.fx.model.RealizedFxImpact;
import com.app.sme_health_backend.fx.model.RecognitionEvidenceStatus;
import com.app.sme_health_backend.fx.model.SettlementDirection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FxSettlementReconciliationResponse(
        UUID settlementId,
        UUID businessId,
        UUID obligationId,
        UUID financialTransactionId,
        UUID allocationId,
        SettlementDirection settlementDirection,
        LocalDate settlementDate,
        String obligationCurrency,
        BigDecimal obligationAmount,
        BigDecimal remainingObligationAmount,
        UUID accountId,
        String accountCurrency,
        BigDecimal settlementRate,
        String rateConvention,
        BigDecimal convertedPrincipal,
        BigDecimal feeAmount,
        String feeCurrency,
        String feeTreatment,
        BigDecimal totalAccountMovement,
        BigDecimal effectiveRate,
        FxSettlementStatus settlementStatus,
        UUID reversalTransactionId,
        String reversalReason,
        RecognitionEvidenceStatus recognitionEvidenceStatus,
        String missingEvidenceReason,
        BigDecimal recognitionRate,
        BigDecimal originalBookValue,
        BigDecimal allocatedBookValue,
        BigDecimal remainingBookValue,
        BigDecimal realizedFxDifference,
        RealizedFxImpact realizedFxImpact,
        BigDecimal netEconomicVariance,
        boolean cashReconciliationVerified,
        String explanation
) {
}

