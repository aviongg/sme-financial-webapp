package com.app.sme_health_backend.scoring.dto;

import com.app.sme_health_backend.scoring.entity.ScoreResult;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class ScoreResultResponse {

    private UUID id;

    @JsonProperty("userId")
    @JsonAlias("user_id")
    private UUID userId;

    private String month;

    @JsonProperty("compositeScore")
    @JsonAlias("composite_score")
    private BigDecimal compositeScore;

    private String band;

    @JsonProperty("componentScores")
    @JsonAlias("component_scores")
    private ComponentScoresDto componentScores;

    @JsonProperty("weakestComponent")
    @JsonAlias("weakest_component")
    private String weakestComponent;

    @JsonProperty("dataCompleteness")
    @JsonAlias("data_completeness")
    private BigDecimal dataCompleteness;

    @JsonProperty("computedAt")
    @JsonAlias("computed_at")
    private LocalDateTime computedAt;

    public static ScoreResultResponse fromEntity(ScoreResult entity) {
        if (entity == null) {
            return null;
        }

        ScoreResultResponse response = new ScoreResultResponse();
        response.id = entity.getId();
        response.userId = entity.getUserId();
        response.month = entity.getMonth();
        response.compositeScore = entity.getCompositeScore();
        response.band = entity.getBand();
        response.componentScores = entity.getComponentScores();
        response.weakestComponent = entity.getWeakestComponent();
        response.dataCompleteness = entity.getDataCompleteness();
        response.computedAt = entity.getComputedAt();

        return response;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public BigDecimal getCompositeScore() {
        return compositeScore;
    }

    public void setCompositeScore(BigDecimal compositeScore) {
        this.compositeScore = compositeScore;
    }

    public String getBand() {
        return band;
    }

    public void setBand(String band) {
        this.band = band;
    }

    public ComponentScoresDto getComponentScores() {
        return componentScores;
    }

    public void setComponentScores(ComponentScoresDto componentScores) {
        this.componentScores = componentScores;
    }

    public String getWeakestComponent() {
        return weakestComponent;
    }

    public void setWeakestComponent(String weakestComponent) {
        this.weakestComponent = weakestComponent;
    }

    public BigDecimal getDataCompleteness() {
        return dataCompleteness;
    }

    public void setDataCompleteness(BigDecimal dataCompleteness) {
        this.dataCompleteness = dataCompleteness;
    }

    public LocalDateTime getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(LocalDateTime computedAt) {
        this.computedAt = computedAt;
    }
}
