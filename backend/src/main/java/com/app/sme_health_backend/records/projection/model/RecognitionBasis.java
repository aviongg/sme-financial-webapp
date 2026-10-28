package com.app.sme_health_backend.records.projection.model;

/**
 * Underlying recognition policy or data origin governing a financial metric.
 */
public enum RecognitionBasis {
    CASH_FLOW,
    ACCRUAL_ACCOUNTING,
    POINT_IN_TIME_BALANCE,
    SUPPLEMENTAL_DECLARATION,
    UNINTEGRATED_MODULE
}
