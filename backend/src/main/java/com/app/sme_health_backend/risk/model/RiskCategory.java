package com.app.sme_health_backend.risk.model;

public enum RiskCategory {
    LIQUIDITY_FORECAST("Projected Cash Deficit", "R01"),
    UPCOMING_PAYMENTS("Upcoming Payment Pressure", "R02"),
    RECEIVABLES_COLLECTION("Overdue Receivables", "R03"),
    COUNTERPARTY_CONCENTRATION("Customer Concentration", "R04"),
    RECURRING_OPERATIONS("Recurring Expense Pressure", "R05"),
    CASH_POSITION("Negative Actual Cash Position", "R06"),
    DATA_INTEGRITY("Financial Data Completeness", "R07"),
    BANK_RECONCILIATION("Bank Reconciliation Coverage", "R08");

    private final String displayName;
    private final String codePrefix;

    RiskCategory(String displayName, String codePrefix) {
        this.displayName = displayName;
        this.codePrefix = codePrefix;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCodePrefix() {
        return codePrefix;
    }
}
