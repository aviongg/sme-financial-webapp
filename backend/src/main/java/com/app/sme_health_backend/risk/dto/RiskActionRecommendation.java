package com.app.sme_health_backend.risk.dto;

import com.app.sme_health_backend.risk.model.Urgency;

public record RiskActionRecommendation(
        String actionCode,
        String title,
        String description,
        Urgency urgency,
        String targetRoute
) {}
