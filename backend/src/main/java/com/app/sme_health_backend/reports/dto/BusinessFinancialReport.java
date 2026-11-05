package com.app.sme_health_backend.reports.dto;

import java.util.List;

/**
 * Canonical, stateless F12B Phase A dataset. Every amount is an exact decimal string (never a JSON float) and
 * {@code null} always means "not recorded" — it is never converted to zero. The JSON endpoint and the CSV export
 * are both rendered from this single object so they cannot disagree.
 *
 * <p>No tenant identifier, user identifier, record identifier or audit data is part of this contract.
 */
public record BusinessFinancialReport(
        String schemaVersion,
        BusinessSummary business,
        Period period,
        String currency,
        String currencyBasis,
        String language,
        String generatedAt,
        List<MonthlyRow> monthlyRows,
        PeriodFlows periodFlows,
        LatestBalances latestBalances,
        LatestHealth latestHealth,
        Completeness completeness,
        List<String> limitations
) {
    public static final String SCHEMA_VERSION = "business-financial-summary-v1";
    public static final String CURRENCY = "PKR";
    public static final String CURRENCY_BASIS = "APPLICATION_FIXED_PKR";

    public record BusinessSummary(String name, String businessType) { }

    public record Period(String fromMonth, String toMonth, int monthCount) { }

    /** One persisted monthly snapshot. Flow fields are for that month; balance fields are end-of-month snapshots. */
    public record MonthlyRow(
            String month,
            String revenue,
            String cogs,
            String operatingExpenses,
            String interestExpense,
            String cashInflow,
            String cashOutflow,
            String netCashMovement,
            String cashBalanceEom,
            String receivablesOutstanding,
            String payablesOutstanding,
            String inventoryValue,
            String loanOutstanding,
            String financingType
    ) { }

    /**
     * Sum of one flow over the recorded months of the period. {@code total} is {@code null} when no month recorded
     * the value. {@code status} is COMPLETE (every included month recorded it), PARTIAL or NOT_RECORDED.
     */
    public record Flow(String total, int recordedMonths, int includedMonths, String status) { }

    public record PeriodFlows(
            String basis,
            int monthsIncluded,
            Flow revenue,
            Flow cogs,
            Flow operatingExpenses,
            Flow interestExpense,
            Flow cashInflow,
            Flow cashOutflow,
            Flow netCashMovement
    ) { }

    /** Point-in-time balances of the latest recorded month in the period. Never summed, never back-filled. */
    public record LatestBalances(
            String basis,
            String asOfMonth,
            String cashBalanceEom,
            String receivablesOutstanding,
            String payablesOutstanding,
            String inventoryValue,
            String loanOutstanding
    ) { }

    /** The persisted score of the latest month in the period. Never averaged or recalculated. */
    public record LatestHealth(
            String basis,
            String month,
            String compositeScore,
            String band,
            List<ComponentScore> components,
            String weakestComponent,
            String dataCompleteness,
            String methodologyVersion,
            String computedAt
    ) { }

    public record ComponentScore(String key, String score) { }

    public record FieldCoverage(String field, int recordedMonths, int includedMonths) { }

    public record Completeness(
            int requestedMonths,
            int recordedMonths,
            List<String> missingMonths,
            boolean complete,
            List<FieldCoverage> optionalFields
    ) { }
}
