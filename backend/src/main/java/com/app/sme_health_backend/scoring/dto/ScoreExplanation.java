package com.app.sme_health_backend.scoring.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Persisted evidence at calculation time. Weights are fractions; absent scores remain null. */
public record ScoreExplanation(
        int historyMonthsAvailable,
        String previousMonth,
        BigDecimal previousScore,
        BigDecimal overallDelta,
        Map<String, Component> components,
        List<Change> majorChanges
) {
    public record Component(String status, BigDecimal score, BigDecimal baseWeight,
                            BigDecimal effectiveWeight, int historyMonthsUsed, String evidenceType,
                            String basis, List<Driver> drivers, BigDecimal previousScore, BigDecimal delta) { }
    /** Values are plain decimal/boolean/categorical strings; keys and units are localized by the client. */
    public record Driver(String key, String value, String unit) { }
    public record Change(String component, BigDecimal previousScore, BigDecimal currentScore, BigDecimal delta) { }
}
