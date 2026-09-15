package com.app.sme_health_backend.recommendation.dto;

import com.app.sme_health_backend.recommendation.entity.Recommendation;

import java.time.LocalDateTime;
import java.util.UUID;

public class RecommendationResponse {

    private UUID id;
    private UUID userId;
    private String month;
    private String text;
    private String category;
    private String priority;
    private LocalDateTime createdAt;

    public static RecommendationResponse fromEntity(
            Recommendation recommendation
    ) {
        RecommendationResponse response = new RecommendationResponse();

        response.id = recommendation.getId();
        response.userId = recommendation.getUserId();
        response.month = recommendation.getMonth();
        response.text = recommendation.getText();
        response.category = recommendation.getCategory();
        response.priority = recommendation.getPriority();
        response.createdAt = recommendation.getCreatedAt();

        return response;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
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
}
