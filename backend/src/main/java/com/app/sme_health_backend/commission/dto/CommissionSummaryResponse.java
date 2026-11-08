package com.app.sme_health_backend.commission.dto;

import java.math.BigDecimal;

public record CommissionSummaryResponse(
    BigDecimal totalEarnedAmount,
    BigDecimal totalApprovedAmount,
    BigDecimal totalAdvancesPaidAmount,
    BigDecimal totalAdvancesAppliedAmount,
    BigDecimal totalCashPaidAmount,
    BigDecimal totalPendingAmount,
    BigDecimal totalPaidAmount,
    long earningsCount,
    long approvedCount,
    long settledCount,
    long advancesCount,
    String currency
) {}
