package com.app.sme_health_backend.risk.dto;

import com.app.sme_health_backend.risk.model.RiskCategory;
import com.app.sme_health_backend.risk.model.RiskCompleteness;
import com.app.sme_health_backend.risk.model.RiskSeverity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FinancialRiskInsight(
        String id,
        String riskCode,
        RiskCategory category,
        RiskSeverity severity,
        String title,
        String summary,
        String whyItMatters,
        String currency,
        BigDecimal affectedAmount,
        BigDecimal affectedPercentage,
        String timeframe,
        LocalDate startDate,
        LocalDate endDate,
        List<RiskEvidenceReference> evidence,
        RiskActionRecommendation recommendation,
        RiskCompleteness completeness
) {}
