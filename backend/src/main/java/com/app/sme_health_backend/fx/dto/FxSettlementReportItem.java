package com.app.sme_health_backend.fx.dto;

import com.app.sme_health_backend.fx.model.FxSettlementStatus;
import com.app.sme_health_backend.fx.model.RealizedFxImpact;
import com.app.sme_health_backend.fx.model.RecognitionEvidenceStatus;
import com.app.sme_health_backend.fx.model.RecognitionProvenance;
import com.app.sme_health_backend.fx.model.SettlementDirection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FxSettlementReportItem(
        UUID settlementId,
        LocalDate settlementDate,
        SettlementDirection direction,
        UUID counterpartyId,
        String counterpartyName,
        UUID obligationId,
        String referenceNumber,
        String obligationCurrency,
        BigDecimal obligationAmount,
        UUID accountId,
        String accountCurrency,
        BigDecimal convertedPrincipal,
        BigDecimal feeAmount,
        String feeCurrency,
        String feeTreatment,
        BigDecimal totalAccountMovement,
        BigDecimal exchangeRate,
        String rateSource,
        BigDecimal recognitionRate,
        BigDecimal allocatedBookValue,
        BigDecimal realizedFxDifference,
        RealizedFxImpact realizedFxImpact,
        RecognitionEvidenceStatus recognitionEvidenceStatus,
        RecognitionProvenance recognitionProvenance,
        BigDecimal netEconomicVariance,
        boolean cashReconciliationVerified,
        FxSettlementStatus status,
        UUID reversalTransactionId,
        String reversalReason
) {
}

