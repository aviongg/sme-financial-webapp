package com.app.sme_health_backend.records.projection.dto;

/**
 * Granular breakdown of business cash inflows, outflows, and transfer neutrality.
 * Fully accounts for all posted movements including unclassified transactions.
 */
public record MonthlyOperatingCashFlow(
        String currency,
        String grossDirectSaleReceipts,
        String reversedDirectSaleReceipts,
        String netDirectSaleReceipts,
        String grossReceivableCollections,
        String reversedReceivableCollections,
        String netReceivableCollections,
        String unclassifiedCashInflows,
        String totalCashInflow,
        String grossOperatingExpensePayments,
        String reversedOperatingExpensePayments,
        String netOperatingExpensePayments,
        String grossPayableSettlements,
        String reversedPayableSettlements,
        String netPayableSettlements,
        String unclassifiedCashOutflows,
        String totalCashOutflow,
        String netOperatingCashFlow,
        String totalInternalTransfersIn,
        String totalInternalTransfersOut,
        String netInternalTransfersAtBusinessLevel,
        String totalNetCashMovement
) {}
