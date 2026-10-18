package com.app.sme_health_backend.obligation.aging;

/**
 * Standard aging buckets for overdue analysis relative to an as-of date.
 */
public enum AgingBucket {
    UNKNOWN_DUE_DATE,
    NOT_YET_DUE,
    DUE_TODAY,
    OVERDUE_1_30,
    OVERDUE_31_60,
    OVERDUE_61_90,
    OVERDUE_91_PLUS;

    /**
     * Checks if this bucket represents an overdue state (strictly past due date).
     *
     * @return true for OVERDUE_1_30, OVERDUE_31_60, OVERDUE_61_90, and OVERDUE_91_PLUS
     */
    public boolean isOverdue() {
        return this == OVERDUE_1_30 || this == OVERDUE_31_60 || this == OVERDUE_61_90 || this == OVERDUE_91_PLUS;
    }
}
