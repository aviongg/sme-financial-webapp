package com.app.sme_health_backend.score;

import com.app.sme_health_backend.score.dto.ScoreResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ScoreResultContractTests {

    @Test
    void keepsUnavailableComponentsAndMakesAnImmutableDefensiveCopy() {
        Map<String, BigDecimal> components = components();
        components.put("repayment", null);
        ScoreResult score = score(components, "Stable", "cashflow", "0.80");
        components.put("cashflow", BigDecimal.ZERO);

        assertDoesNotThrow(score::validate);
        assertEquals(new BigDecimal("60"), score.getComponentScores().get("cashflow"));
        assertTrue(score.getComponentScores().containsKey("repayment"));
        assertNull(score.getComponentScores().get("repayment"));
        assertThrows(UnsupportedOperationException.class,
                () -> score.getComponentScores().put("trend", BigDecimal.ONE));
    }

    @Test
    void validatesDomainsWithoutRecomputingBandOrWeakestComponent() {
        ScoreResult score = score(components(), "At Risk", "compliance", "0.00");
        assertDoesNotThrow(score::validate);
        assertEquals("At Risk", score.getBand());
        assertEquals("compliance", score.getWeakestComponent());
    }

    @Test
    void rejectsAliasedMissingAndAdditionalComponentFields() {
        Map<String, BigDecimal> components = components();
        components.put("cash_flow", components.remove("cashflow"));
        assertThrows(IllegalArgumentException.class, () -> score(components, "Stable", "trend", "1").validate());
        components.remove("cash_flow");
        assertThrows(IllegalArgumentException.class, () -> score(components, "Stable", "trend", "1").validate());
        components.put("cashflow", BigDecimal.TEN);
        components.put("extra", BigDecimal.TEN);
        assertThrows(IllegalArgumentException.class, () -> score(components, "Stable", "trend", "1").validate());
    }

    @ParameterizedTest
    @ValueSource(strings = {"80", "100", "1.01", "-0.01"})
    void rejectsPercentAndOutOfRangeCompleteness(String completeness) {
        assertThrows(IllegalArgumentException.class,
                () -> score(components(), "Stable", "trend", completeness).validate());
    }

    @ParameterizedTest
    @ValueSource(strings = {"stable", "Good", "strong", "Needs attention", "at_risk"})
    void rejectsNoncanonicalBands(String band) {
        assertThrows(IllegalArgumentException.class,
                () -> score(components(), band, "trend", "1").validate());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-9", "2026-00", "2026-13", "2026-09-01", " 2026-09", "+2026-09"})
    void rejectsMalformedMonths(String month) {
        assertThrows(IllegalArgumentException.class, () -> ScoreResult.validateMonth(month));
    }

    @Test
    void rejectsMissingRequiredFieldsAndOutOfRangeScores() {
        assertThrows(IllegalArgumentException.class, () -> score(null, "Stable", "trend", "1").validate());
        assertThrows(IllegalArgumentException.class, () -> score(components(), null, "trend", "1").validate());
        assertThrows(IllegalArgumentException.class, () -> score(components(), "Stable", "cash_flow", "1").validate());
        Map<String, BigDecimal> components = components();
        components.put("trend", new BigDecimal("100.01"));
        assertThrows(IllegalArgumentException.class, () -> score(components, "Stable", "trend", "1").validate());
        ScoreResult incomplete = new ScoreResult(null, null, null, null, null, null, null, null);
        assertThrows(IllegalArgumentException.class, incomplete::validate);
    }

    private static Map<String, BigDecimal> components() {
        Map<String, BigDecimal> components = new LinkedHashMap<>();
        ScoreResult.COMPONENT_KEYS.forEach(key -> components.put(key, new BigDecimal("60")));
        return components;
    }

    private static ScoreResult score(Map<String, BigDecimal> components, String band,
                                     String weakest, String completeness) {
        return new ScoreResult(UUID.randomUUID(), "2026-09", new BigDecimal("60"), band,
                components, weakest, new BigDecimal(completeness), LocalDateTime.of(2026, 9, 19, 12, 0));
    }
}
