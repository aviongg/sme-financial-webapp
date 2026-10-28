package com.app.sme_health_backend.records.projection.dto;

import com.app.sme_health_backend.records.projection.model.ComparisonMetricStatus;
import com.app.sme_health_backend.records.projection.model.RecognitionBasis;

/**
 * Detailed comparison of a single financial metric between MonthlyRecord and the ledger projection.
 */
public record MonthlyMetricComparison(
        String metricKey,
        String metricLabel,
        String monthlyRecordValue,
        String ledgerValue,
        String difference,
        ComparisonMetricStatus status,
        RecognitionBasis recognitionBasis,
        String explanation
) {}
