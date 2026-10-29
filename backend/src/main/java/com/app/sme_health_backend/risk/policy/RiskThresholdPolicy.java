package com.app.sme_health_backend.risk.policy;

import java.math.BigDecimal;

public final class RiskThresholdPolicy {

    private RiskThresholdPolicy() {}

    // R04 Customer Receivables Concentration Thresholds (%)
    public static final BigDecimal CUSTOMER_CONCENTRATION_CRITICAL_THRESHOLD = new BigDecimal("70.00");
    public static final BigDecimal CUSTOMER_CONCENTRATION_HIGH_THRESHOLD = new BigDecimal("50.00");
    public static final BigDecimal CUSTOMER_CONCENTRATION_MEDIUM_THRESHOLD = new BigDecimal("35.00");

    // R03 Overdue Receivables Ratio Thresholds
    public static final BigDecimal OVERDUE_RATIO_CRITICAL_THRESHOLD = new BigDecimal("0.50");
    public static final BigDecimal OVERDUE_RATIO_HIGH_THRESHOLD = new BigDecimal("0.25");

    // R05 Recurring Expense Pressure Ratio Threshold (Recurring Outflow / Available Cash)
    public static final BigDecimal RECURRING_CASH_RATIO_HIGH_THRESHOLD = new BigDecimal("1.00");
    public static final BigDecimal RECURRING_CASH_RATIO_MEDIUM_THRESHOLD = new BigDecimal("0.50");

    // R08 Bank Reconciliation Thresholds
    public static final int BANK_RECON_UNMATCHED_COUNT_HIGH_THRESHOLD = 5;
    public static final BigDecimal BANK_RECON_UNMATCHED_AMOUNT_HIGH_THRESHOLD = new BigDecimal("50000.00");

    // Default Evaluation Parameters
    public static final int DEFAULT_HORIZON_DAYS = 30;
    public static final int MIN_HORIZON_DAYS = 1;
    public static final int MAX_HORIZON_DAYS = 365;
    public static final String DEFAULT_CURRENCY = "PKR";
}
