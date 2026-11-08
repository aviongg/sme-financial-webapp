package com.app.sme_health_backend.commission.facade;

import com.app.sme_health_backend.commission.model.CommissionEarningStatus;
import com.app.sme_health_backend.commission.model.CommissionSourceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable read-only commission cost evidence record for consumption by Developer B systems.
 */
public record CommissionCostEvidence(
        UUID earningId,
        UUID businessId,
        UUID salespersonId,
        String salespersonName,
        UUID counterpartyId,
        String counterpartyName,
        CommissionSourceType sourceType,
        String sourceId,
        String sourceFingerprint,
        BigDecimal saleAmount,
        BigDecimal commissionRateSnapshot,
        BigDecimal earnedAmount,
        BigDecimal approvedAmount,
        CommissionEarningStatus status,
        BigDecimal settledAmount,
        BigDecimal netPaidAmount,
        String currency,
        Instant recognizedAt,
        Instant approvedAt,
        String reversalState, // "NONE", "PARTIALLY_REVERSED", "FULLY_REVERSED"
        String recognitionBasis // "COLLECTION_BASED" or "SALES_BASED"
) {
}
