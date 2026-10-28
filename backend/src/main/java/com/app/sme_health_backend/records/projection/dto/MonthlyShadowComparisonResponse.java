package com.app.sme_health_backend.records.projection.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Shadow comparison identifying differences between historical MonthlyRecord values and ledger-derived figures.
 */
public record MonthlyShadowComparisonResponse(
        String contractVersion,
        UUID businessId,
        String month,
        LocalDate evaluatedAsOfDate,
        boolean hasMonthlyRecord,
        UUID monthlyRecordId,
        List<MonthlyMetricComparison> comparisons,
        int matchingMetricCount,
        int differingMetricCount,
        int unsupportedMetricCount,
        String summary,
        List<String> recognitionBasisNotes
) {}
