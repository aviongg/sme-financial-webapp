package com.app.sme_health_backend.intelligence;

/** Reporting categories assignable to authoritative facts (V48 financial_classifications). */
public enum FinancialCategory {
    PRODUCT_SALES(true), SERVICE_REVENUE(true), OTHER_INCOME(true),
    DIRECT_MATERIALS(false), DIRECT_LABOUR(false), RENT(false), SALARIES_WAGES(false), UTILITIES(false),
    MARKETING(false), TRANSPORT_LOGISTICS(false), REPAIRS_MAINTENANCE(false), PROFESSIONAL_FEES(false),
    TAXES_LICENSES(false), FINANCE_CHARGES(false), SUPPLIES(false), OTHER_EXPENSE(false);

    private final boolean revenue;

    FinancialCategory(boolean revenue) {
        this.revenue = revenue;
    }

    public boolean revenue() {
        return revenue;
    }
}
