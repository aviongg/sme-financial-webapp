package com.app.sme_health_backend.insight.dto;

import com.app.sme_health_backend.insight.entity.Insight;

import java.time.LocalDateTime;
import java.util.UUID;

public class InsightResponse {

    private UUID id;
    private UUID userId;
    private String month;
    private String text;
    private String category;
    private String priority;
    private LocalDateTime createdAt;

    public static InsightResponse fromEntity(Insight insight) {
        InsightResponse response = new InsightResponse();

        response.id = insight.getId();
        response.userId = insight.getUserId();
        response.month = insight.getMonth();
        response.text = insight.getText();
        response.category = insight.getCategory();
        response.priority = insight.getPriority();
        response.createdAt = insight.getCreatedAt();

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
