package com.app.sme_health_backend.recommendation.dto;

import com.app.sme_health_backend.recommendation.entity.RecommendationStatus;
import jakarta.validation.constraints.NotNull;

public record RecommendationStatusRequest(@NotNull RecommendationStatus status) { }
