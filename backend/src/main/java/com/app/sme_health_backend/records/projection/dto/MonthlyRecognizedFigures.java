package com.app.sme_health_backend.records.projection.dto;

import com.app.sme_health_backend.financialquery.model.CapabilityStatus;
import com.app.sme_health_backend.financialquery.model.DataCompleteness;

/**
 * Recognized income and expense metrics distinguishing verified cash sales, approved
 * invoice-backed earned revenue, and provisional unprovenanced obligation candidates.
 *
 * Excludes COGS, Gross Profit, Operating Profit, and Net Profit (strictly UNAVAILABLE
 * pending Developer B's F08 Inventory Valuation module; zero substitution is strictly rejected).
 */
public record MonthlyRecognizedFigures(
        String currency,

        // 1. Revenue Recognition
        String verifiedDirectSaleRevenue,
        String invoiceIssuedEarnedRevenue,
        String provisionalReceivableCandidates,
        CapabilityStatus authoritativeAccrualRevenueStatus,
        DataCompleteness accrualRevenueCompleteness,
        String authoritativeAccrualRevenue,
        String provisionalTotalRevenue,

        // 2. Operating Expense Recognition
        String verifiedCashOperatingExpenses,
        String billIncurredOperatingExpenses,
        String provisionalPayableCandidates,
        String authoritativeAccrualOperatingExpenses,
        String provisionalTotalOperatingExpenses,

        // 3. Profitability - Strictly UNAVAILABLE (No Zero Substitution)
        CapabilityStatus cogsStatus,
        String cogs,
        String cogsUnavailableReason,

        CapabilityStatus grossProfitStatus,
        String grossProfit,
        String grossProfitUnavailableReason,

        CapabilityStatus operatingProfitStatus,
        String operatingProfit,
        String operatingProfitUnavailableReason,

        CapabilityStatus netProfitStatus,
        String netProfit,
        String netProfitUnavailableReason,

        // 4. Clearly Qualified Diagnostic Operating Measure
        String revenueLessKnownOperatingExpensesBeforeCogs,
        String cashRevenueLessKnownOperatingExpenses,

        String notes
) {
    // Backwards-compatible accessor aliases
    public String cashBasisRevenue() {
        return verifiedDirectSaleRevenue;
    }

    public String cashBasisOperatingExpenses() {
        return verifiedCashOperatingExpenses;
    }

    public String accrualBasisRecognizedRevenue() {
        return authoritativeAccrualRevenue;
    }

    public String accrualBasisOperatingExpenses() {
        return authoritativeAccrualOperatingExpenses;
    }
}
