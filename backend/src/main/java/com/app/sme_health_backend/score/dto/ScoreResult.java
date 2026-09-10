package com.app.sme_health_backend.score.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Shared read-only contract consumed by downstream score features.
 */
public class ScoreResult {

    private final UUID userId;
    private final String month;
    private final BigDecimal compositeScore;
    private final String band;
    private final Map<String, BigDecimal> componentScores;
    private final String weakestComponent;
    private final BigDecimal dataCompleteness;
    private final LocalDateTime computedAt;

    public ScoreResult(
            UUID userId,
            String month,
            BigDecimal compositeScore,
            String band,
            Map<String, BigDecimal> componentScores,
            String weakestComponent,
            BigDecimal dataCompleteness,
            LocalDateTime computedAt
    ) {
        this.userId = userId;
        this.month = month;
        this.compositeScore = compositeScore;
        this.band = band;
        this.componentScores = componentScores;
        this.weakestComponent = weakestComponent;
        this.dataCompleteness = dataCompleteness;
        this.computedAt = computedAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getMonth() {
        return month;
    }

    public BigDecimal getCompositeScore() {
        return compositeScore;
    }

    public String getBand() {
        return band;
    }

    public Map<String, BigDecimal> getComponentScores() {
        return componentScores;
    }

    public String getWeakestComponent() {
        return weakestComponent;
    }

    public BigDecimal getDataCompleteness() {
        return dataCompleteness;
    }

    public LocalDateTime getComputedAt() {
        return computedAt;
    }
}
