package com.app.sme_health_backend.recommendation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "recommendations")
public class Recommendation {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "month", nullable = false, length = 7)
    private String month;

    @Column(
            name = "recommendation_text",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String text;

    @Column(name = "category", nullable = false, length = 40)
    private String category;

    @Column(name = "priority", nullable = false, length = 10)
    private String priority;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "source_version", length = 64)
    private String sourceVersion;

    @Column(name = "language", length = 5)
    private String language;

    @Column(name = "source_computed_at")
    private LocalDateTime sourceComputedAt;

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getSourceVersion() {
        return sourceVersion;
    }

    public void setSourceVersion(String sourceVersion) {
        this.sourceVersion = sourceVersion;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public LocalDateTime getSourceComputedAt() {
        return sourceComputedAt;
    }

    public void setSourceComputedAt(LocalDateTime sourceComputedAt) {
        this.sourceComputedAt = sourceComputedAt;
    }
}
