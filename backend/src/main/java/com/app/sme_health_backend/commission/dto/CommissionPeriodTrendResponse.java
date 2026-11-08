package com.app.sme_health_backend.commission.dto;

import java.math.BigDecimal;

/**
 * Period trend metrics (e.g. monthly grouping).
 */
public record CommissionPeriodTrendResponse(
        String periodLabel, // e.g. "2026-10" or "Oct 2026"
        BigDecimal earnedAmount,
        BigDecimal approvedAmount,
        BigDecimal advancesPaid,
        BigDecimal advancesApplied,
        BigDecimal netPayoutAmount,
        BigDecimal netCashOutflow,
        String currency
) {
}
