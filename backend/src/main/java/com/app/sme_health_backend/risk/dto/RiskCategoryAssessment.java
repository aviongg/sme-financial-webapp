package com.app.sme_health_backend.risk.dto;

import com.app.sme_health_backend.risk.model.RiskCategory;
import com.app.sme_health_backend.risk.model.RiskSeverity;

public record RiskCategoryAssessment(
        RiskCategory category,
        String categoryName,
        String status,
        int insightCount,
        RiskSeverity highestSeverity,
        String reason
) {}
