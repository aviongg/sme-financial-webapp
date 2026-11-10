package com.app.sme_health_backend.bridge.dto;

import java.util.List;

public record LedgerCompletenessReport(
        boolean hasDirectSaleReceipts,
        boolean hasReceivableCollections,
        boolean hasOperatingDisbursements,
        boolean hasPayableSettlements,
        boolean hasOpenReceivables,
        boolean hasOpenPayables,
        boolean hasManualCogsProvided,
        boolean hasManualTaxProvided,
        boolean hasManualFinancingProvided,
        String readinessStatus,
        List<String> dataGaps,
        List<String> warnings
) {
}

