package com.app.sme_health_backend.projectattribution.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProjectCommitmentsSummaryResponse(
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
        String profitabilityStatus,
        String unavailabilityReason,
        String evidenceCompletenessNotes
) {
    public ProjectCommitmentsSummaryResponse(
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
            int linkedObligationsCount,
            int linkedCommercialDocumentsCount,
            int unmatchedCommercialDocumentsCount,
            String profitabilityStatus,
            String unavailabilityReason,
            String evidenceCompletenessNotes
    ) {
        this(
                projectId, projectCode, projectName, baseCurrency, plannedCost, plannedRevenue,
                attributedCashOutflows, attributedCashInflows, cashHeadroom,
                outstandingSupplierPayables, outstandingCustomerReceivables,
                unpostedCommercialBills, unpostedCommercialInvoices,
                recognizedCommissionCost, nonCashFxImpact, totalOutstandingCommitments,
                outstandingSupplierPayables, unpostedCommercialBills, totalOutstandingCommitments,
                linkedObligationsCount, linkedCommercialDocumentsCount, unmatchedCommercialDocumentsCount,
                profitabilityStatus, unavailabilityReason, evidenceCompletenessNotes
        );
    }
}
