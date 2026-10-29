package com.app.sme_health_backend.risk.dto;

import com.app.sme_health_backend.risk.model.RiskSeverity;

import java.time.LocalDate;

public record RiskSummary(
        int criticalCount,
        int highCount,
        int mediumCount,
        int lowCount,
        int notAssessedCount,
        int totalAssessedCategories,
        int totalCategories,
        LocalDate asOfDate,
        int horizonDays,
        String currency,
        RiskSeverity highestSeverity,
        boolean hasDataCompletenessIssues
) {}
