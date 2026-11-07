package com.app.sme_health_backend.funding;

import com.app.sme_health_backend.funding.dto.*;
import com.app.sme_health_backend.funding.exception.FundingScenarioValidationException;
import com.app.sme_health_backend.funding.service.FundingScenarioCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FundingScenarioCalculatorTest {

    private FundingScenarioCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new FundingScenarioCalculator();
    }

    @Test
    @DisplayName("Section 6 Required Numerical Fixture: 0 opening, Day 1 -60k, Day 5 +20k, Day 10 +50k -> Peak 60k, Recovery Day 10")
    void testSection6RequiredNumericalFixture() {
        LocalDate asOfDate = LocalDate.parse("2026-11-01"); // Day 0
        LocalDate horizonEnd = asOfDate.plusDays(15);

        List<PlannedCashEventInput> events = List.of(
                new PlannedCashEventInput("EVT-1", "2026-11-02", CashFlowDirection.OUTFLOW, new BigDecimal("60000.00"), "Materials payment", "MATERIALS"),
                new PlannedCashEventInput("EVT-2", "2026-11-06", CashFlowDirection.INFLOW, new BigDecimal("20000.00"), "Customer receipt 1", "SALES"),
                new PlannedCashEventInput("EVT-3", "2026-11-11", CashFlowDirection.INFLOW, new BigDecimal("50000.00"), "Customer receipt 2", "SALES")
        );

        ScenarioResult result = calculator.calculateScenario(
                "Baseline Plan",
                BigDecimal.ZERO,
                asOfDate,
                horizonEnd,
                events
        );

        assertNotNull(result);
        assertEquals("Baseline Plan", result.scenarioName());
        assertEquals("70000.00", result.totalPlannedInflows());
        assertEquals("60000.00", result.totalPlannedOutflows());
        assertEquals("10000.00", result.netPlannedMovement());
        assertEquals("10000.00", result.closingProjectedLiquidity());

        // Exact Peak Funding Requirement
        assertEquals("60000.00", result.peakFundingRequirement());
        assertEquals(LocalDate.parse("2026-11-02"), result.peakDeficitDate()); // Day 1
        assertEquals(LocalDate.parse("2026-11-02"), result.firstDeficitDate()); // Day 1

        // Exact Recovery Date (Day 10)
        assertEquals(LocalDate.parse("2026-11-11"), result.recoveryDate()); // Day 10
        assertTrue(result.hasDeficit());
        assertTrue(result.isRecovered());
        assertEquals(9, result.deficitDayCount()); // Day 1 through Day 9 (9 days)

        // Verify Daily Timeline Cumulative Positions
        DailyFundingPosition day1 = findDay(result.timeline(), LocalDate.parse("2026-11-02"));
        assertEquals("-60000.00", day1.closingPosition());
        assertEquals("-60000.00", day1.cumulativeProjectedPosition());
        assertEquals("60000.00", day1.deficit());
        assertTrue(day1.isShortfall());

        DailyFundingPosition day5 = findDay(result.timeline(), LocalDate.parse("2026-11-06"));
        assertEquals("-40000.00", day5.closingPosition());
        assertEquals("-40000.00", day5.cumulativeProjectedPosition());
        assertEquals("40000.00", day5.deficit());
        assertTrue(day5.isShortfall());

        DailyFundingPosition day10 = findDay(result.timeline(), LocalDate.parse("2026-11-11"));
        assertEquals("10000.00", day10.closingPosition());
        assertEquals("10000.00", day10.cumulativeProjectedPosition());
        assertEquals("0.00", day10.deficit());
        assertFalse(day10.isShortfall());
    }

    @Test
    @DisplayName("Alternative Scenario Comparison: Earlier receipt timing reduces peak funding requirement")
    void testAlternativeScenarioComparisonWithDifferentReceiptTiming() {
        LocalDate asOfDate = LocalDate.parse("2026-11-01");
        LocalDate horizonEnd = asOfDate.plusDays(15);

        // Baseline: Day 1 -60k, Day 5 +20k, Day 10 +50k
        List<PlannedCashEventInput> baselineEvents = List.of(
                new PlannedCashEventInput("BASE-1", "2026-11-02", CashFlowDirection.OUTFLOW, new BigDecimal("60000.00"), "Materials", "MATERIALS"),
                new PlannedCashEventInput("BASE-2", "2026-11-06", CashFlowDirection.INFLOW, new BigDecimal("20000.00"), "Customer 1", "SALES"),
                new PlannedCashEventInput("BASE-3", "2026-11-11", CashFlowDirection.INFLOW, new BigDecimal("50000.00"), "Customer 2", "SALES")
        );

        // Alternative: Customer pays 40k on Day 1 and 30k on Day 5
        List<PlannedCashEventInput> altEvents = List.of(
                new PlannedCashEventInput("ALT-1", "2026-11-02", CashFlowDirection.OUTFLOW, new BigDecimal("60000.00"), "Materials", "MATERIALS"),
                new PlannedCashEventInput("ALT-2", "2026-11-02", CashFlowDirection.INFLOW, new BigDecimal("40000.00"), "Early Customer Advance", "SALES"),
                new PlannedCashEventInput("ALT-3", "2026-11-06", CashFlowDirection.INFLOW, new BigDecimal("30000.00"), "Customer Milestone 2", "SALES")
        );

        ScenarioResult baseline = calculator.calculateScenario("Baseline", BigDecimal.ZERO, asOfDate, horizonEnd, baselineEvents);
        ScenarioResult alternative = calculator.calculateScenario("Alternative", BigDecimal.ZERO, asOfDate, horizonEnd, altEvents);

        assertEquals("60000.00", baseline.peakFundingRequirement());
        assertEquals("20000.00", alternative.peakFundingRequirement()); // Net on Day 1 is -20k
        assertEquals(LocalDate.parse("2026-11-06"), alternative.recoveryDate()); // Day 5 (-20k + 30k = +10k)

        ScenarioComparisonSummary comp = calculator.compareScenarios(baseline, alternative);
        assertNotNull(comp);
        assertEquals("Baseline", comp.baselineScenarioName());
        assertEquals("Alternative", comp.alternativeScenarioName());

        // Peak funding difference: 20k - 60k = -40,000.00 (improved by 40k)
        assertEquals("-40000.00", comp.peakFundingDifference());
        // Closing liquidity is identical (+10,000.00 in both)
        assertEquals("0.00", comp.closingLiquidityDifference());

        // Recovery date changed by -5 days (Day 5 instead of Day 10)
        assertEquals(-5, comp.recoveryDateChangeDays());
    }

    @Test
    @DisplayName("Never-recovering scenario: Outflows exceed inflows and closing remains negative")
    void testNeverRecoveringScenario() {
        LocalDate asOfDate = LocalDate.parse("2026-11-01");
        LocalDate horizonEnd = asOfDate.plusDays(10);

        List<PlannedCashEventInput> events = List.of(
                new PlannedCashEventInput("EVT-1", "2026-11-02", CashFlowDirection.OUTFLOW, new BigDecimal("100000.00"), "Capex", "CAPEX"),
                new PlannedCashEventInput("EVT-2", "2026-11-05", CashFlowDirection.INFLOW, new BigDecimal("30000.00"), "Revenue", "SALES")
        );

        ScenarioResult result = calculator.calculateScenario("Capex Deficit", BigDecimal.ZERO, asOfDate, horizonEnd, events);

        assertEquals("100000.00", result.peakFundingRequirement());
        assertEquals("-70000.00", result.closingProjectedLiquidity());
        assertEquals(LocalDate.parse("2026-11-02"), result.firstDeficitDate());
        assertNull(result.recoveryDate()); // Does NOT recover within horizon
        assertTrue(result.hasDeficit());
        assertFalse(result.isRecovered());
    }

    @Test
    @DisplayName("No-deficit scenario: Sufficient opening liquidity prevents any shortfall")
    void testNoDeficitScenario() {
        LocalDate asOfDate = LocalDate.parse("2026-11-01");
        LocalDate horizonEnd = asOfDate.plusDays(10);

        List<PlannedCashEventInput> events = List.of(
                new PlannedCashEventInput("EVT-1", "2026-11-02", CashFlowDirection.OUTFLOW, new BigDecimal("25000.00"), "Rent", "RENT"),
                new PlannedCashEventInput("EVT-2", "2026-11-08", CashFlowDirection.INFLOW, new BigDecimal("50000.00"), "Sales", "SALES")
        );

        ScenarioResult result = calculator.calculateScenario(
                "Healthy Cash",
                new BigDecimal("100000.00"),
                asOfDate,
                horizonEnd,
                events
        );

        assertEquals("0.00", result.peakFundingRequirement());
        assertNull(result.firstDeficitDate());
        assertNull(result.peakDeficitDate());
        assertNull(result.recoveryDate());
        assertFalse(result.hasDeficit());
        assertFalse(result.isRecovered());
        assertEquals(0, result.deficitDayCount());
        assertEquals("125000.00", result.closingProjectedLiquidity());
    }

    @Test
    @DisplayName("Same-day events: Multiple inflows and outflows on the same date are netted deterministically")
    void testSameDayEventsNettingAndOrdering() {
        LocalDate asOfDate = LocalDate.parse("2026-11-01");
        LocalDate horizonEnd = asOfDate.plusDays(5);

        List<PlannedCashEventInput> events = List.of(
                new PlannedCashEventInput("OUT-2", "2026-11-02", CashFlowDirection.OUTFLOW, new BigDecimal("30000.00"), "Outflow B", "OPS"),
                new PlannedCashEventInput("IN-1", "2026-11-02", CashFlowDirection.INFLOW, new BigDecimal("50000.00"), "Inflow A", "SALES"),
                new PlannedCashEventInput("OUT-1", "2026-11-02", CashFlowDirection.OUTFLOW, new BigDecimal("10000.00"), "Outflow A", "OPS"),
                new PlannedCashEventInput("IN-2", "2026-11-02", CashFlowDirection.INFLOW, new BigDecimal("15000.00"), "Inflow B", "SALES")
        );

        ScenarioResult result = calculator.calculateScenario("Same Day", BigDecimal.ZERO, asOfDate, horizonEnd, events);

        DailyFundingPosition day1 = findDay(result.timeline(), LocalDate.parse("2026-11-02"));
        // Total Inflows: 50k + 15k = 65k
        // Total Outflows: 30k + 10k = 40k
        // Net: +25k
        assertEquals("65000.00", day1.plannedInflows());
        assertEquals("40000.00", day1.plannedOutflows());
        assertEquals("25000.00", day1.netPlannedMovement());
        assertEquals("25000.00", day1.closingPosition());

        // Verify deterministic event ordering: INFLOW first (IN-1, IN-2), then OUTFLOW (OUT-1, OUT-2)
        assertEquals(4, day1.eventsOnDay().size());
        assertEquals("IN-1", day1.eventsOnDay().get(0).eventId());
        assertEquals("IN-2", day1.eventsOnDay().get(1).eventId());
        assertEquals("OUT-1", day1.eventsOnDay().get(2).eventId());
        assertEquals("OUT-2", day1.eventsOnDay().get(3).eventId());
    }

    @Test
    @DisplayName("Validation: Rejects duplicate event identifiers")
    void testRejectsDuplicateEventIds() {
        LocalDate asOfDate = LocalDate.parse("2026-11-01");
        List<PlannedCashEventInput> events = List.of(
                new PlannedCashEventInput("DUPE-ID", "2026-11-02", CashFlowDirection.INFLOW, new BigDecimal("1000.00"), "First", "OPS"),
                new PlannedCashEventInput("DUPE-ID", "2026-11-03", CashFlowDirection.INFLOW, new BigDecimal("2000.00"), "Second", "OPS")
        );

        FundingScenarioValidationException ex = assertThrows(FundingScenarioValidationException.class, () ->
                calculator.calculateScenario("Dupe Test", BigDecimal.ZERO, asOfDate, asOfDate.plusDays(7), events));
        assertTrue(ex.getMessage().contains("Duplicate eventId"));
    }

    @Test
    @DisplayName("Validation: Rejects event dates outside evaluation horizon")
    void testRejectsEventDateOutsideHorizon() {
        LocalDate asOfDate = LocalDate.parse("2026-11-01");
        LocalDate horizonEnd = asOfDate.plusDays(10);

        List<PlannedCashEventInput> events = List.of(
                new PlannedCashEventInput("OUT-OF-BOUNDS", "2026-11-15", CashFlowDirection.INFLOW, new BigDecimal("1000.00"), "Late", "OPS")
        );

        FundingScenarioValidationException ex = assertThrows(FundingScenarioValidationException.class, () ->
                calculator.calculateScenario("Bounds Test", BigDecimal.ZERO, asOfDate, horizonEnd, events));
        assertTrue(ex.getMessage().contains("outside evaluation horizon"));
    }

    @Test
    @DisplayName("Validation: Rejects invalid or excessive event amounts and scale")
    void testRejectsInvalidAmounts() {
        LocalDate asOfDate = LocalDate.parse("2026-11-01");

        // Non-positive amount
        List<PlannedCashEventInput> zeroAmountEvents = List.of(
                new PlannedCashEventInput("ZERO-AMT", "2026-11-02", CashFlowDirection.INFLOW, BigDecimal.ZERO, "Zero", "OPS")
        );
        assertThrows(FundingScenarioValidationException.class, () ->
                calculator.calculateScenario("Zero Amt", BigDecimal.ZERO, asOfDate, asOfDate.plusDays(7), zeroAmountEvents));

        // Excess decimal places (> 2)
        List<PlannedCashEventInput> excessDecimals = List.of(
                new PlannedCashEventInput("EXCESS", "2026-11-02", CashFlowDirection.INFLOW, new BigDecimal("10.125"), "Fraction", "OPS")
        );
        assertThrows(FundingScenarioValidationException.class, () ->
                calculator.calculateScenario("Excess Decimals", BigDecimal.ZERO, asOfDate, asOfDate.plusDays(7), excessDecimals));
    }

    @Test
    @DisplayName("Validation: Rejects scenario with more than 100 events")
    void testRejectsExcessiveEventCount() {
        LocalDate asOfDate = LocalDate.parse("2026-11-01");
        List<PlannedCashEventInput> events = new ArrayList<>();
        for (int i = 1; i <= 101; i++) {
            events.add(new PlannedCashEventInput("EVT-" + i, "2026-11-02", CashFlowDirection.INFLOW, new BigDecimal("10.00"), "Item " + i, "OPS"));
        }

        FundingScenarioValidationException ex = assertThrows(FundingScenarioValidationException.class, () ->
                calculator.calculateScenario("Overflow Test", BigDecimal.ZERO, asOfDate, asOfDate.plusDays(7), events));
        assertTrue(ex.getMessage().contains("cannot contain more than 100 events"));
    }

    private static DailyFundingPosition findDay(List<DailyFundingPosition> timeline, LocalDate date) {
        return timeline.stream()
                .filter(p -> p.date().equals(date))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Date " + date + " not found in timeline"));
    }
}
