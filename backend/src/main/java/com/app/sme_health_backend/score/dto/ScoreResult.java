package com.app.sme_health_backend.score.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shared read-only contract consumed by downstream score features.
 */
public class ScoreResult {

    public static final List<String> COMPONENT_KEYS = List.of(
            "cashflow", "profitability", "repayment", "trend", "compliance"
    );
    private static final Set<String> BANDS = Set.of("Strong", "Stable", "Needs Attention", "At Risk");
    private static final BigDecimal MAX_SCORE = new BigDecimal("100");

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
        // Map.copyOf rejects null values, which mean unavailable scores in the shared contract.
        this.componentScores = componentScores == null ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(componentScores));
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

    /** Checks the persisted scoring contract without recalculating any of its values. */
    public void validate() {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        validateMonth(month);
        requireRange(compositeScore, MAX_SCORE, "Composite score");
        if (band == null || !BANDS.contains(band)) {
            throw new IllegalArgumentException("Band must be Strong, Stable, Needs Attention, or At Risk");
        }
        if (componentScores == null || !componentScores.keySet().equals(Set.copyOf(COMPONENT_KEYS))) {
            throw new IllegalArgumentException("Component scores must contain exactly the five canonical component keys");
        }
        for (String key : COMPONENT_KEYS) {
            if (componentScores.get(key) != null) {
                requireRange(componentScores.get(key), MAX_SCORE, "Component score: " + key);
            }
        }
        if (weakestComponent == null || !COMPONENT_KEYS.contains(weakestComponent)) {
            throw new IllegalArgumentException("Weakest component must be a canonical component key");
        }
        requireRange(dataCompleteness, BigDecimal.ONE, "Data completeness");
        if (computedAt == null) {
            throw new IllegalArgumentException("Score computation time is required");
        }
    }

    public static YearMonth validateMonth(String month) {
        if (month == null || !month.matches("[0-9]{4}-(0[1-9]|1[0-2])")) {
            throw new IllegalArgumentException("Month must be in YYYY-MM format");
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Month must be in YYYY-MM format", exception);
        }
    }

    private static void requireRange(BigDecimal value, BigDecimal maximum, String field) {
        if (value == null || value.signum() < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(field + " must be between 0 and " + maximum.toPlainString());
        }
    }
}
