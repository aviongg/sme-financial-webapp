package com.app.sme_health_backend.commission.facade;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Summary of commission costs attributable to a specific salesperson over a period.
 */
public record SalespersonCostSummary(
        UUID businessId,
        UUID salespersonId,
        String salespersonName,
        LocalDate fromDate,
        LocalDate toDate,
        BigDecimal totalEarnedAmount,
        BigDecimal totalApprovedAmount,
        BigDecimal totalSettledAmount,
        BigDecimal netPaidAmount,
        BigDecimal unappliedAdvanceBalance,
        String currency
) {
}
