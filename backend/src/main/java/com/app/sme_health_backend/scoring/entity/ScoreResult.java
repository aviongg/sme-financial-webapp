package com.app.sme_health_backend.scoring.entity;

import com.app.sme_health_backend.scoring.dto.ComponentScoresDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "score_results")
public class ScoreResult {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "month", nullable = false, length = 7)
    private String month;

    @Column(name = "composite_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal compositeScore;

    @Column(name = "band", nullable = false, length = 20)
    private String band;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "component_scores", nullable = false, columnDefinition = "jsonb")
    private ComponentScoresDto componentScores;

    @Column(name = "weakest_component", nullable = false, length = 20)
    private String weakestComponent;

    @Column(name = "data_completeness", nullable = false, precision = 3, scale = 2)
    private BigDecimal dataCompleteness;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;

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
