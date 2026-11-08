package com.app.sme_health_backend.commission.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SalespersonSettlementSummaryResponse(
    UUID salespersonId,
    String salespersonName,
    BigDecimal totalEarnedAmount,
    BigDecimal totalApprovedAmount,
    BigDecimal totalAdvancesPaidAmount,
    BigDecimal totalAdvancesAppliedAmount,
    BigDecimal unappliedAdvanceBalance,
    BigDecimal totalCashPaidAmount,
    BigDecimal outstandingCommissionPayable,
    List<CommissionEarningResponse> earnings,
    List<CommissionAdvanceResponse> advances,
    List<CommissionPayoutResponse> payouts,
    String currency
) {}
