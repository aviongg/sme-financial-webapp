package com.app.sme_health_backend.cashflow;

import com.app.sme_health_backend.cashflow.calculator.TrendProjectionCalculator;
import com.app.sme_health_backend.cashflow.calculator.TrendProjectionCalculator.TrendProjectionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrendProjectionCalculatorTests {

    private TrendProjectionCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new TrendProjectionCalculator();
    }

    @Test
    void shouldReturnNullWhenHistoryHasFewerThanThreeMonths() {
        assertNull(calculator.calculate(null));
        assertNull(calculator.calculate(Collections.emptyList()));
        assertNull(calculator.calculate(List.of(new BigDecimal("100000"))));
        assertNull(calculator.calculate(List.of(new BigDecimal("100000"), new BigDecimal("200000"))));
    }

    @Test
    void shouldCalculateThreeMonthsPositiveSlopeCorrectly() {
        // 100k, 200k, 300k -> projection = 400k, direction = upward, confidence = reasonable
        List<BigDecimal> data = List.of(
                new BigDecimal("100000"),
                new BigDecimal("200000"),
                new BigDecimal("300000")
        );

        TrendProjectionResult result = calculator.calculate(data);

        assertNotNull(result);
        assertEquals(0, result.projectedNetCashFlow().compareTo(new BigDecimal("400000.00")));
        assertEquals("upward", result.trendDirection());
        assertEquals("reasonable", result.confidence());
        assertEquals(0, result.rSquared().compareTo(new BigDecimal("1.0000")));
    }

    @Test
    void shouldCalculateThreeMonthsNegativeSlopeCorrectly() {
        // 300k, 200k, 100k -> projection = 0k, direction = downward, confidence = reasonable
        List<BigDecimal> data = List.of(
                new BigDecimal("300000"),
                new BigDecimal("200000"),
                new BigDecimal("100000")
        );

        TrendProjectionResult result = calculator.calculate(data);

        assertNotNull(result);
        assertEquals(0, result.projectedNetCashFlow().compareTo(new BigDecimal("0.00")));
        assertEquals("downward", result.trendDirection());
        assertEquals("reasonable", result.confidence());
        assertEquals(0, result.rSquared().compareTo(new BigDecimal("1.0000")));
    }

    @Test
    void shouldHandleConstantValuesAsFlatTrendWithReasonableConfidence() {
        // 150k, 150k, 150k -> projection = 150k, direction = flat, confidence = reasonable
        List<BigDecimal> data = List.of(
                new BigDecimal("150000"),
                new BigDecimal("150000"),
                new BigDecimal("150000")
        );

        TrendProjectionResult result = calculator.calculate(data);

        assertNotNull(result);
        assertEquals(0, result.projectedNetCashFlow().compareTo(new BigDecimal("150000.00")));
        assertEquals("flat", result.trendDirection());
        assertEquals("reasonable", result.confidence());
    }

    @Test
    void shouldHandleAllZeroValues() {
        // 0, 0, 0 -> projection = 0, direction = flat, confidence = reasonable
        List<BigDecimal> data = List.of(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        TrendProjectionResult result = calculator.calculate(data);

        assertNotNull(result);
        assertEquals(0, result.projectedNetCashFlow().compareTo(new BigDecimal("0.00")));
        assertEquals("flat", result.trendDirection());
        assertEquals("reasonable", result.confidence());
    }

    @Test
    void shouldIdentifyLowConfidenceForVolatileData() {
        // Volatile data with very low R^2 (< 0.30): e.g. 100k, 300k, 110k
        List<BigDecimal> data = List.of(
                new BigDecimal("100000"),
                new BigDecimal("300000"),
                new BigDecimal("110000")
        );

        TrendProjectionResult result = calculator.calculate(data);

        assertNotNull(result);
        assertTrue(result.rSquared().compareTo(new BigDecimal("0.30")) < 0);
        assertEquals("low", result.confidence());
    }

    @Test
    void shouldIdentifyModerateConfidence() {
        // Data with moderate R^2 (0.30 <= R^2 < 0.60): 100k, 250k, 200k gives R^2 ≈ 0.43
        List<BigDecimal> data = List.of(
                new BigDecimal("100000"),
                new BigDecimal("250000"),
                new BigDecimal("200000")
        );

        TrendProjectionResult result = calculator.calculate(data);

        assertNotNull(result);
        assertTrue(result.rSquared().compareTo(new BigDecimal("0.30")) >= 0);
        assertTrue(result.rSquared().compareTo(new BigDecimal("0.60")) < 0);
        assertEquals("moderate", result.confidence());
    }

    @Test
    void shouldCapHistoryWindowAtLatestSixMonths() {
        // 8 points: the first 2 should be discarded; latest 6: 100k, 120k, 140k, 160k, 180k, 200k
        // For latest 6 with slope = 20k, x = 0..5, projection at x = 6 is 200k + 20k = 220k
        List<BigDecimal> data = new ArrayList<>();
        data.add(new BigDecimal("999999")); // should be ignored
        data.add(new BigDecimal("888888")); // should be ignored
        data.add(new BigDecimal("100000"));
        data.add(new BigDecimal("120000"));
        data.add(new BigDecimal("140000"));
        data.add(new BigDecimal("160000"));
        data.add(new BigDecimal("180000"));
        data.add(new BigDecimal("200000"));

        TrendProjectionResult result = calculator.calculate(data);

        assertNotNull(result);
        assertEquals(0, result.projectedNetCashFlow().compareTo(new BigDecimal("220000.00")));
        assertEquals("upward", result.trendDirection());
        assertEquals("reasonable", result.confidence());
    }

    @Test
    void shouldEvaluateProjectionAtNextMonthIndexNotLastHistoricalIndex() {
        // For x = 0 (100k), x = 1 (200k), x = 2 (300k):
        // At x = 2 (N-1), value is 300k.
        // At x = 3 (N), value is 400k.
        List<BigDecimal> data = List.of(
                new BigDecimal("100000"),
                new BigDecimal("200000"),
                new BigDecimal("300000")
        );

        TrendProjectionResult result = calculator.calculate(data);

        assertNotNull(result);
        assertNotEquals(0, result.projectedNetCashFlow().compareTo(new BigDecimal("300000.00")));
        assertEquals(0, result.projectedNetCashFlow().compareTo(new BigDecimal("400000.00")));
    }

    @Test
    void shouldClampRSquaredToBetweenZeroAndOne() {
        List<BigDecimal> data = List.of(
                new BigDecimal("100000"),
                new BigDecimal("200000"),
                new BigDecimal("300000")
        );

        TrendProjectionResult result = calculator.calculate(data);

        assertNotNull(result);
        assertTrue(result.rSquared().compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(result.rSquared().compareTo(BigDecimal.ONE) <= 0);
    }

    @Test
    void shouldProduceRSquaredEqualsOneWhenSeriesHasZeroTotalVariance() {
        // When all y values are identical, SS_tot == 0 -> R^2 = 1.00
        List<BigDecimal> data = List.of(
                new BigDecimal("75000"),
                new BigDecimal("75000"),
                new BigDecimal("75000"),
                new BigDecimal("75000")
        );

        TrendProjectionResult result = calculator.calculate(data);

        assertNotNull(result);
        assertEquals(0, result.rSquared().compareTo(new BigDecimal("1.0000")));
        assertEquals("reasonable", result.confidence());
    }
}
