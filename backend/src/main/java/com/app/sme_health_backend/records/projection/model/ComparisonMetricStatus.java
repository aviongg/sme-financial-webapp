package com.app.sme_health_backend.records.projection.model;

/**
 * Status of an individual financial metric comparison between MonthlyRecord and the ledger projection.
 */
public enum ComparisonMetricStatus {
    MATCH,
    DIFFERENCE,
    NOT_SUPPORTED_IN_LEDGER,
    NOT_PRESENT_IN_RECORD
}
