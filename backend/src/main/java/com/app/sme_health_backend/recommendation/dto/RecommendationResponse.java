package com.app.sme_health_backend.recommendation.dto;

import com.app.sme_health_backend.recommendation.entity.Recommendation;

import java.time.LocalDateTime;
import java.util.UUID;

public class RecommendationResponse {

    private UUID id;
    private UUID businessId;
    private String month;
    private String text;
    private String category;
    private String priority;
    private LocalDateTime createdAt;
    private String sourceVersion;
    private String language;
    private LocalDateTime sourceComputedAt;

    public static RecommendationResponse fromEntity(
            Recommendation recommendation
    ) {
        RecommendationResponse response = new RecommendationResponse();

        response.id = recommendation.getId();
        response.businessId = recommendation.getUserId();
        response.month = recommendation.getMonth();
        response.text = recommendation.getText();
        response.category = recommendation.getCategory();
        response.priority = recommendation.getPriority();
        response.createdAt = recommendation.getCreatedAt();
        response.sourceVersion = recommendation.getSourceVersion();
        response.language = recommendation.getLanguage();
        response.sourceComputedAt = recommendation.getSourceComputedAt();

        return response;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public String getMonth() {
        return month;
    }

    public String getText() {
        return text;
    }

    public String getCategory() {
        return category;
    }

    public String getPriority() {
        return priority;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getSourceVersion() {
        return sourceVersion;
    }

    public String getLanguage() {
        return language;
    }

    public LocalDateTime getSourceComputedAt() {
        return sourceComputedAt;
    }
}
