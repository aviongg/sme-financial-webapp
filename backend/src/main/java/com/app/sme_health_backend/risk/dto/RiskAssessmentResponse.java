package com.app.sme_health_backend.risk.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RiskAssessmentResponse(
        String version,
        UUID businessId,
        LocalDate asOfDate,
        int horizonDays,
        String currency,
        RiskSummary summary,
        List<RiskCategoryAssessment> categories,
        List<FinancialRiskInsight> insights,
        Instant evaluatedAt
) {}
