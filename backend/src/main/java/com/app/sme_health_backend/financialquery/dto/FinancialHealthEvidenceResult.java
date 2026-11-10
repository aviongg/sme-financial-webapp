package com.app.sme_health_backend.financialquery.dto;

import java.math.BigDecimal;
import java.util.List;

public record FinancialHealthEvidenceResult(
        String yearMonth,
        BigDecimal ledgerCashInflow,
        BigDecimal ledgerCashOutflow,
        BigDecimal ledgerNetCashFlow,
        BigDecimal ledgerOperatingRevenue,
        BigDecimal ledgerOperatingExpenses,
        BigDecimal ledgerCashBalanceEom,
        BigDecimal ledgerReceivablesOutstanding,
        BigDecimal ledgerPayablesOutstanding,
        String readinessStatus,
        boolean hasExistingRecord,
        List<String> warnings
) {}
