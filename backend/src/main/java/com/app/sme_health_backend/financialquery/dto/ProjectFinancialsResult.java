package com.app.sme_health_backend.financialquery.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProjectFinancialsResult(
        UUID projectId,
        String projectCode,
        String projectName,
        String baseCurrency,
        BigDecimal plannedCost,
        BigDecimal plannedRevenue,
        BigDecimal attributedCashOutflows,
        BigDecimal attributedCashInflows,
        BigDecimal cashHeadroom,
        BigDecimal outstandingSupplierPayables,
        BigDecimal outstandingCustomerReceivables,
        BigDecimal unpostedCommercialBills,
        BigDecimal unpostedCommercialInvoices,
        BigDecimal recognizedCommissionCost,
        BigDecimal nonCashFxImpact,
        BigDecimal totalOutstandingCommitments,
        BigDecimal authoritativeFinancialOutstanding,
        BigDecimal commercialUnpostedExposure,
        BigDecimal verifiedCombinedExposure,
        int linkedObligationsCount,
        int linkedCommercialDocumentsCount,
        int unmatchedCommercialDocumentsCount,
        String profitabilityStatus
) {}

