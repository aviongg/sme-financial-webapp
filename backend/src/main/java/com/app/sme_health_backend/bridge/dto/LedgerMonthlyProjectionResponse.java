package com.app.sme_health_backend.bridge.dto;

import com.app.sme_health_backend.records.dto.MonthlyRecordResponse;

import java.math.BigDecimal;
import java.util.UUID;

public record LedgerMonthlyProjectionResponse(
        UUID businessId,
        String yearMonth,
        BigDecimal ledgerCashInflow,
        BigDecimal ledgerCashOutflow,
        BigDecimal ledgerNetCashFlow,
        BigDecimal ledgerOperatingRevenue,
        BigDecimal ledgerOperatingExpenses,
        BigDecimal asOfReceivablesOutstanding,
        BigDecimal asOfPayablesOutstanding,
        BigDecimal currentCashBalanceEom,
        boolean hasExistingMonthlyRecord,
        MonthlyRecordResponse existingMonthlyRecord,
        LedgerCompletenessReport completeness,
        String calculationVersion,
        String notes
) {
}

