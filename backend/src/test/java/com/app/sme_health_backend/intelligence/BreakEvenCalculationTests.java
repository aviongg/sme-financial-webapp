package com.app.sme_health_backend.intelligence;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** Pure break-even-v1 arithmetic: no guessing, exact decimals, explicit unavailable states. */
class BreakEvenCalculationTests {
    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void availableResultUsesOneExactDivisionRoundedHalfUp() {
        BreakEvenService.Result r = BreakEvenService.calculate(d("1050.0000"), d("350.0000"), d("1000.0000"));
        assertEquals("AVAILABLE", r.status());
        assertEquals(0, d("700").compareTo(r.contribution()));
        assertEquals(0, d("66.67").compareTo(r.contributionMarginPct()));
        assertEquals(0, d("1500.00").compareTo(r.breakEvenRevenue()));
        assertEquals(0, d("-450.00").compareTo(r.marginOfSafety()));
    }

    @Test
    void repeatingRatioDoesNotDriftThroughIntermediateRounding() {
        // F * R / C = 100 * 3 / 1 -> exact 300.00 even though C/R = 0.333... is non-terminating.
        BreakEvenService.Result r = BreakEvenService.calculate(d("3"), d("2"), d("100"));
        assertEquals(0, d("300.00").compareTo(r.breakEvenRevenue()));
        BreakEvenService.Result q = BreakEvenService.calculate(d("7"), d("4"), d("10"));
        assertEquals(0, d("23.33").compareTo(q.breakEvenRevenue()), "70/3 = 23.333.. HALF_UP 23.33");
    }

    @Test
    void zeroAndNegativeContributionAreNotAchievable() {
        BreakEvenService.Result zero = BreakEvenService.calculate(d("500"), d("500"), d("100"));
        assertEquals("NOT_ACHIEVABLE", zero.status());
        assertEquals("ZERO_CONTRIBUTION", zero.reason());
        assertNull(zero.breakEvenRevenue());
        BreakEvenService.Result negative = BreakEvenService.calculate(d("500"), d("600"), d("100"));
        assertEquals("NEGATIVE_CONTRIBUTION", negative.reason());
        assertEquals(0, d("-20.00").compareTo(negative.contributionMarginPct()));
    }

    @Test
    void noRevenueIsUnavailableAndZeroFixedCostBreaksEvenAtZero() {
        assertEquals("NO_REVENUE", BreakEvenService.calculate(d("0"), d("0"), d("100")).reason());
        BreakEvenService.Result r = BreakEvenService.calculate(d("100"), d("40"), d("0"));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.breakEvenRevenue()));
        assertEquals(0, d("100.00").compareTo(r.marginOfSafetyPct()));
    }
}
