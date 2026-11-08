package com.app.sme_health_backend.commission.dto;

import com.app.sme_health_backend.commission.model.SalespersonStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Performance breakdown for an individual salesperson.
 */
public record SalespersonPerformanceResponse(
        UUID salespersonId,
        String displayName,
        String email,
        SalespersonStatus status,
        long activeCustomerCount,
        BigDecimal attributedCollectionAmount,
        BigDecimal earnedAmount,
        BigDecimal approvedAmount,
        BigDecimal unapprovedAmount,
        BigDecimal advancesPaid,
        BigDecimal advancesApplied,
        BigDecimal unappliedAdvanceBalance,
        BigDecimal directPayoutAmount,
        BigDecimal payoutReversals,
        BigDecimal netPayoutAmount,
        BigDecimal totalSettledAmount,
        BigDecimal outstandingPayable,
        String currency
) {
}
