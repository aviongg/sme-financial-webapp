package com.app.sme_health_backend.commission.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Authoritative summary of commission financial measures.
 * Explicitly separates cumulative-to-date stocks from period flows.
 */
public record CommissionAnalyticsOverviewResponse(
        // --- Cumulative-to-date Measures ---
        BigDecimal totalEarned,
        BigDecimal totalApproved,
        BigDecimal totalUnapproved,
        BigDecimal totalAdvancesPaid,
        BigDecimal totalAdvancesApplied,
        BigDecimal totalAdvancesUnapplied,
        BigDecimal totalGrossPayouts,
        BigDecimal totalReversals,
        BigDecimal totalNetPayouts,
        BigDecimal totalSettled,
        BigDecimal totalOutstandingPayable,
        BigDecimal totalNetCashOutflow,

        // --- Period Flow Measures (null if no period specified) ---
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        BigDecimal periodEarned,
        BigDecimal periodApproved,
        BigDecimal periodAdvancesPaid,
        BigDecimal periodAdvancesApplied,
        BigDecimal periodGrossPayouts,
        BigDecimal periodReversals,
        BigDecimal periodNetPayouts,
        BigDecimal periodSettled,
        BigDecimal periodNetCashOutflow,

        // --- Metadata ---
        String currency,
        long totalSalespersonsCount,
        long activeSalespersonsCount
) {
}
