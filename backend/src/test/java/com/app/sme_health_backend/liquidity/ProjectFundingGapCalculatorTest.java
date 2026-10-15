package com.app.sme_health_backend.liquidity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectFundingGapCalculatorTest {

    private final ProjectFundingGapCalculator calculator = new ProjectFundingGapCalculator();

    @Nested
    @DisplayName("Reference Fixture & Core Semantics")
    class ReferenceFixtureTests {

        @Test
        @DisplayName("Reference fixture from task specification calculates expected metrics and continuous daily curve")
        void referenceFixtureMatchesSpecification() {
            LocalDate start = LocalDate.of(2026, 11, 1);
            LocalDate end = LocalDate.of(2026, 11, 5);
            BigDecimal opening = new BigDecimal("30000.00");

            List<PlannedProjectCashEvent> events = List.of(
                    new PlannedProjectCashEvent("E1", LocalDate.of(2026, 11, 1), ProjectCashFlowDirection.OUTFLOW, new BigDecimal("80000.00")),
                    new PlannedProjectCashEvent("E2", LocalDate.of(2026, 11, 2), ProjectCashFlowDirection.INFLOW, new BigDecimal("20000.00")),
                    new PlannedProjectCashEvent("E3", LocalDate.of(2026, 11, 3), ProjectCashFlowDirection.OUTFLOW, new BigDecimal("20000.00")),
                    new PlannedProjectCashEvent("E4", LocalDate.of(2026, 11, 4), ProjectCashFlowDirection.INFLOW, new BigDecimal("70000.00"))
                    // 2026-11-05: no events
            );

            ProjectFundingPlanInput input = new ProjectFundingPlanInput("PKR", opening, start, end, events);
            ProjectFundingGapResult result = calculator.calculate(input);

            // Invariants & Metrics
            assertThat(result.currency()).isEqualTo("PKR");
            assertThat(result.openingFunds()).isEqualByComparingTo("30000.00");
            assertThat(result.horizonStart()).isEqualTo(start);
            assertThat(result.horizonEnd()).isEqualTo(end);
            assertThat(result.totalInflow()).isEqualByComparingTo("90000.00");
            assertThat(result.totalOutflow()).isEqualByComparingTo("100000.00");
            assertThat(result.finalClosingPosition()).isEqualByComparingTo("20000.00");
            assertThat(result.additionalFundingRequired()).isEqualByComparingTo("50000.00");
            assertThat(result.firstShortfallDate()).isEqualTo(LocalDate.of(2026, 11, 1));
            assertThat(result.firstPeakShortfallDate()).isEqualTo(LocalDate.of(2026, 11, 1));
            assertThat(result.recoveryDate()).isEqualTo(LocalDate.of(2026, 11, 4));
            assertThat(result.shortfallDayCount()).isEqualTo(3);
            assertThat(result.hasShortfall()).isTrue();
            assertThat(result.isRecoveredWithinHorizon()).isTrue();

            // Daily curve continuous verification (5 calendar days)
            List<DailyProjectFundingPosition> curve = result.dailyCurve();
            assertThat(curve).hasSize(5);

            // 2026-11-01
            assertThat(curve.get(0).date()).isEqualTo(LocalDate.of(2026, 11, 1));
            assertThat(curve.get(0).plannedInflows()).isEqualByComparingTo("0.00");
            assertThat(curve.get(0).plannedOutflows()).isEqualByComparingTo("80000.00");
            assertThat(curve.get(0).netFlow()).isEqualByComparingTo("-80000.00");
            assertThat(curve.get(0).closingPosition()).isEqualByComparingTo("-50000.00");
            assertThat(curve.get(0).isShortfall()).isTrue();

            // 2026-11-02
            assertThat(curve.get(1).date()).isEqualTo(LocalDate.of(2026, 11, 2));
            assertThat(curve.get(1).plannedInflows()).isEqualByComparingTo("20000.00");
            assertThat(curve.get(1).plannedOutflows()).isEqualByComparingTo("0.00");
            assertThat(curve.get(1).netFlow()).isEqualByComparingTo("20000.00");
            assertThat(curve.get(1).closingPosition()).isEqualByComparingTo("-30000.00");
            assertThat(curve.get(1).isShortfall()).isTrue();

            // 2026-11-03
            assertThat(curve.get(2).date()).isEqualTo(LocalDate.of(2026, 11, 3));
            assertThat(curve.get(2).plannedInflows()).isEqualByComparingTo("0.00");
            assertThat(curve.get(2).plannedOutflows()).isEqualByComparingTo("20000.00");
            assertThat(curve.get(2).netFlow()).isEqualByComparingTo("-20000.00");
            assertThat(curve.get(2).closingPosition()).isEqualByComparingTo("-50000.00");
            assertThat(curve.get(2).isShortfall()).isTrue();

            // 2026-11-04
            assertThat(curve.get(3).date()).isEqualTo(LocalDate.of(2026, 11, 4));
            assertThat(curve.get(3).plannedInflows()).isEqualByComparingTo("70000.00");
            assertThat(curve.get(3).plannedOutflows()).isEqualByComparingTo("0.00");
            assertThat(curve.get(3).netFlow()).isEqualByComparingTo("70000.00");
            assertThat(curve.get(3).closingPosition()).isEqualByComparingTo("20000.00");
            assertThat(curve.get(3).isShortfall()).isFalse();

            // 2026-11-05
            assertThat(curve.get(4).date()).isEqualTo(LocalDate.of(2026, 11, 5));
            assertThat(curve.get(4).plannedInflows()).isEqualByComparingTo("0.00");
            assertThat(curve.get(4).plannedOutflows()).isEqualByComparingTo("0.00");
            assertThat(curve.get(4).netFlow()).isEqualByComparingTo("0.00");
            assertThat(curve.get(4).closingPosition()).isEqualByComparingTo("20000.00");
            assertThat(curve.get(4).isShortfall()).isFalse();
        }

        @Test
        @DisplayName("Static delegate calculateGap returns identical result")
        void staticDelegateMatches() {
            LocalDate start = LocalDate.of(2026, 11, 1);
            LocalDate end = LocalDate.of(2026, 11, 2);
            ProjectFundingPlanInput input = new ProjectFundingPlanInput(
                    "PKR",
                    new BigDecimal("1000.00"),
                    start,
                    end,
                    List.of(new PlannedProjectCashEvent("E1", start, ProjectCashFlowDirection.INFLOW, new BigDecimal("500.00")))
            );

            ProjectFundingGapResult instanceResult = calculator.calculate(input);
            ProjectFundingGapResult staticResult = ProjectFundingGapCalculator.calculateGap(input);

            assertThat(instanceResult).isEqualTo(staticResult);
        }
    }

    @Nested
    @DisplayName("Edge Cases & Deficit Scenarios")
    class EdgeCaseTests {

        @Test
        @DisplayName("No events and explicit zero opening funds produces zero funding gap and null milestone dates")
        void noEventsAndExplicitZeroOpeningFunds() {
            LocalDate start = LocalDate.of(2026, 12, 1);
            LocalDate end = LocalDate.of(2026, 12, 3);
            ProjectFundingPlanInput input = new ProjectFundingPlanInput(
                    "PKR",
                    BigDecimal.ZERO,
                    start,
                    end,
                    List.of()
            );

            ProjectFundingGapResult result = calculator.calculate(input);

            assertThat(result.additionalFundingRequired()).isEqualByComparingTo("0.00");
            assertThat(result.totalInflow()).isEqualByComparingTo("0.00");
            assertThat(result.totalOutflow()).isEqualByComparingTo("0.00");
            assertThat(result.finalClosingPosition()).isEqualByComparingTo("0.00");
            assertThat(result.firstShortfallDate()).isNull();
            assertThat(result.firstPeakShortfallDate()).isNull();
            assertThat(result.recoveryDate()).isNull();
            assertThat(result.shortfallDayCount()).isEqualTo(0);
            assertThat(result.hasShortfall()).isFalse();
            assertThat(result.isRecoveredWithinHorizon()).isFalse();
            assertThat(result.dailyCurve()).hasSize(3);
            for (DailyProjectFundingPosition pos : result.dailyCurve()) {
                assertThat(pos.closingPosition()).isEqualByComparingTo("0.00");
                assertThat(pos.isShortfall()).isFalse();
            }
        }

        @Test
        @DisplayName("Sufficient opening funds produces zero deficit without shortfall milestones")
        void sufficientOpeningFunds() {
            LocalDate start = LocalDate.of(2026, 11, 1);
            LocalDate end = LocalDate.of(2026, 11, 3);
            ProjectFundingPlanInput input = new ProjectFundingPlanInput(
                    "PKR",
                    new BigDecimal("100000.00"),
                    start,
                    end,
                    List.of(
                            new PlannedProjectCashEvent("E1", LocalDate.of(2026, 11, 1), ProjectCashFlowDirection.OUTFLOW, new BigDecimal("20000.00")),
                            new PlannedProjectCashEvent("E2", LocalDate.of(2026, 11, 2), ProjectCashFlowDirection.OUTFLOW, new BigDecimal("30000.00")),
                            new PlannedProjectCashEvent("E3", LocalDate.of(2026, 11, 3), ProjectCashFlowDirection.INFLOW, new BigDecimal("10000.00"))
                    )
            );

            ProjectFundingGapResult result = calculator.calculate(input);

            assertThat(result.additionalFundingRequired()).isEqualByComparingTo("0.00");
            assertThat(result.finalClosingPosition()).isEqualByComparingTo("60000.00");
            assertThat(result.firstShortfallDate()).isNull();
            assertThat(result.firstPeakShortfallDate()).isNull();
            assertThat(result.recoveryDate()).isNull();
            assertThat(result.shortfallDayCount()).isEqualTo(0);
            assertThat(result.hasShortfall()).isFalse();
        }

        @Test
        @DisplayName("Plan that never recovers within the horizon leaves recovery date null")
        void neverRecoveringPlanLeavesRecoveryNull() {
            LocalDate start = LocalDate.of(2026, 11, 1);
            LocalDate end = LocalDate.of(2026, 11, 4);
            ProjectFundingPlanInput input = new ProjectFundingPlanInput(
                    "PKR",
                    new BigDecimal("10000.00"),
                    start,
                    end,
                    List.of(
                            new PlannedProjectCashEvent("E1", LocalDate.of(2026, 11, 1), ProjectCashFlowDirection.OUTFLOW, new BigDecimal("30000.00")),
                            new PlannedProjectCashEvent("E2", LocalDate.of(2026, 11, 2), ProjectCashFlowDirection.OUTFLOW, new BigDecimal("10000.00")),
                            new PlannedProjectCashEvent("E3", LocalDate.of(2026, 11, 3), ProjectCashFlowDirection.INFLOW, new BigDecimal("5000.00"))
                    )
            );

            ProjectFundingGapResult result = calculator.calculate(input);

            assertThat(result.additionalFundingRequired()).isEqualByComparingTo("30000.00");
            assertThat(result.firstShortfallDate()).isEqualTo(LocalDate.of(2026, 11, 1));
            assertThat(result.firstPeakShortfallDate()).isEqualTo(LocalDate.of(2026, 11, 2));
            assertThat(result.recoveryDate()).isNull();
            assertThat(result.shortfallDayCount()).isEqualTo(4);
            assertThat(result.finalClosingPosition()).isEqualByComparingTo("-25000.00");
            assertThat(result.hasShortfall()).isTrue();
            assertThat(result.isRecoveredWithinHorizon()).isFalse();
        }

        @Test
        @DisplayName("Temporary recovery followed by another shortfall determines true final recovery date")
        void temporaryRecoveryFollowedByAnotherShortfall() {
            LocalDate start = LocalDate.of(2026, 11, 1);
            LocalDate end = LocalDate.of(2026, 11, 6);
            ProjectFundingPlanInput input = new ProjectFundingPlanInput(
                    "PKR",
                    new BigDecimal("10000.00"),
                    start,
                    end,
                    List.of(
                            // 11-01: -20000 -> pos -10000 (shortfall 1)
                            new PlannedProjectCashEvent("E1", LocalDate.of(2026, 11, 1), ProjectCashFlowDirection.OUTFLOW, new BigDecimal("20000.00")),
                            // 11-02: +15000 -> pos +5000 (temporary recovery, not lasting)
                            new PlannedProjectCashEvent("E2", LocalDate.of(2026, 11, 2), ProjectCashFlowDirection.INFLOW, new BigDecimal("15000.00")),
                            // 11-03: -25000 -> pos -20000 (shortfall 2, peak deficit)
                            new PlannedProjectCashEvent("E3", LocalDate.of(2026, 11, 3), ProjectCashFlowDirection.OUTFLOW, new BigDecimal("25000.00")),
                            // 11-04: +30000 -> pos +10000 (recovery from which positions stay non-negative)
                            new PlannedProjectCashEvent("E4", LocalDate.of(2026, 11, 4), ProjectCashFlowDirection.INFLOW, new BigDecimal("30000.00")),
                            // 11-05: +5000 -> pos +15000
                            new PlannedProjectCashEvent("E5", LocalDate.of(2026, 11, 5), ProjectCashFlowDirection.INFLOW, new BigDecimal("5000.00"))
                            // 11-06: no events -> pos +15000
                    )
            );

            ProjectFundingGapResult result = calculator.calculate(input);

            assertThat(result.additionalFundingRequired()).isEqualByComparingTo("20000.00");
            assertThat(result.firstShortfallDate()).isEqualTo(LocalDate.of(2026, 11, 1));
            assertThat(result.firstPeakShortfallDate()).isEqualTo(LocalDate.of(2026, 11, 3));
            assertThat(result.recoveryDate()).isEqualTo(LocalDate.of(2026, 11, 4));
            assertThat(result.shortfallDayCount()).isEqualTo(2);
            assertThat(result.finalClosingPosition()).isEqualByComparingTo("15000.00");
        }

        @Test
        @DisplayName("Recovery on the final day of the horizon correctly identifies the final date")
        void recoveryOnFinalDay() {
            LocalDate start = LocalDate.of(2026, 11, 1);
            LocalDate end = LocalDate.of(2026, 11, 3);
            ProjectFundingPlanInput input = new ProjectFundingPlanInput(
                    "PKR",
                    new BigDecimal("10000.00"),
                    start,
                    end,
                    List.of(
                            new PlannedProjectCashEvent("E1", LocalDate.of(2026, 11, 1), ProjectCashFlowDirection.OUTFLOW, new BigDecimal("20000.00")), // pos -10000
                            new PlannedProjectCashEvent("E2", LocalDate.of(2026, 11, 2), ProjectCashFlowDirection.OUTFLOW, new BigDecimal("5000.00")),  // pos -15000
                            new PlannedProjectCashEvent("E3", LocalDate.of(2026, 11, 3), ProjectCashFlowDirection.INFLOW, new BigDecimal("25000.00"))   // pos +10000
                    )
            );

            ProjectFundingGapResult result = calculator.calculate(input);

            assertThat(result.recoveryDate()).isEqualTo(LocalDate.of(2026, 11, 3));
            assertThat(result.shortfallDayCount()).isEqualTo(2);
            assertThat(result.firstShortfallDate()).isEqualTo(LocalDate.of(2026, 11, 1));
            assertThat(result.firstPeakShortfallDate()).isEqualTo(LocalDate.of(2026, 11, 2));
            assertThat(result.additionalFundingRequired()).isEqualByComparingTo("15000.00");
        }

        @Test
        @DisplayName("Same-day inflows and outflows are netted into single daily aggregate")
        void sameDayInflowsAndOutflowsNetted() {
            LocalDate date = LocalDate.of(2026, 11, 1);
            ProjectFundingPlanInput input = new ProjectFundingPlanInput(
                    "PKR",
                    new BigDecimal("1000.00"),
                    date,
                    date,
                    List.of(
                            new PlannedProjectCashEvent("E1", date, ProjectCashFlowDirection.INFLOW, new BigDecimal("5000.00")),
                            new PlannedProjectCashEvent("E2", date, ProjectCashFlowDirection.OUTFLOW, new BigDecimal("2000.00")),
                            new PlannedProjectCashEvent("E3", date, ProjectCashFlowDirection.OUTFLOW, new BigDecimal("1000.00")),
                            new PlannedProjectCashEvent("E4", date, ProjectCashFlowDirection.INFLOW, new BigDecimal("3000.00"))
                    )
            );

            ProjectFundingGapResult result = calculator.calculate(input);

            assertThat(result.totalInflow()).isEqualByComparingTo("8000.00");
            assertThat(result.totalOutflow()).isEqualByComparingTo("3000.00");
            assertThat(result.finalClosingPosition()).isEqualByComparingTo("6000.00");
            assertThat(result.additionalFundingRequired()).isEqualByComparingTo("0.00");
            assertThat(result.dailyCurve()).hasSize(1);
            DailyProjectFundingPosition day1 = result.dailyCurve().get(0);
            assertThat(day1.plannedInflows()).isEqualByComparingTo("8000.00");
            assertThat(day1.plannedOutflows()).isEqualByComparingTo("3000.00");
            assertThat(day1.netFlow()).isEqualByComparingTo("5000.00");
            assertThat(day1.closingPosition()).isEqualByComparingTo("6000.00");
        }

        @Test
        @DisplayName("Repeated peak deficits select earliest date reaching the minimum negative position")
        void repeatedPeakDeficitsSelectEarliestDate() {
            LocalDate start = LocalDate.of(2026, 11, 1);
            LocalDate end = LocalDate.of(2026, 11, 5);
            ProjectFundingPlanInput input = new ProjectFundingPlanInput(
                    "PKR",
                    BigDecimal.ZERO,
                    start,
                    end,
                    List.of(
                            new PlannedProjectCashEvent("E1", LocalDate.of(2026, 11, 1), ProjectCashFlowDirection.OUTFLOW, new BigDecimal("50000.00")), // pos -50000 (first peak)
                            new PlannedProjectCashEvent("E2", LocalDate.of(2026, 11, 2), ProjectCashFlowDirection.INFLOW, new BigDecimal("10000.00")),  // pos -40000
                            new PlannedProjectCashEvent("E3", LocalDate.of(2026, 11, 3), ProjectCashFlowDirection.OUTFLOW, new BigDecimal("10000.00")), // pos -50000 (repeated peak)
                            new PlannedProjectCashEvent("E4", LocalDate.of(2026, 11, 4), ProjectCashFlowDirection.INFLOW, new BigDecimal("20000.00")),  // pos -30000
                            new PlannedProjectCashEvent("E5", LocalDate.of(2026, 11, 5), ProjectCashFlowDirection.OUTFLOW, new BigDecimal("20000.00"))  // pos -50000 (repeated peak)
                    )
            );

            ProjectFundingGapResult result = calculator.calculate(input);

            assertThat(result.additionalFundingRequired()).isEqualByComparingTo("50000.00");
            assertThat(result.firstPeakShortfallDate()).isEqualTo(LocalDate.of(2026, 11, 1));
            assertThat(result.firstShortfallDate()).isEqualTo(LocalDate.of(2026, 11, 1));
            assertThat(result.shortfallDayCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("Shuffled input event ordering does not alter calculation results")
        void shuffledInputOrderInvariant() {
            LocalDate start = LocalDate.of(2026, 11, 1);
            LocalDate end = LocalDate.of(2026, 11, 5);
            BigDecimal opening = new BigDecimal("30000.00");

            List<PlannedProjectCashEvent> events = List.of(
                    new PlannedProjectCashEvent("E1", LocalDate.of(2026, 11, 1), ProjectCashFlowDirection.OUTFLOW, new BigDecimal("80000.00")),
                    new PlannedProjectCashEvent("E2", LocalDate.of(2026, 11, 2), ProjectCashFlowDirection.INFLOW, new BigDecimal("20000.00")),
                    new PlannedProjectCashEvent("E3", LocalDate.of(2026, 11, 3), ProjectCashFlowDirection.OUTFLOW, new BigDecimal("20000.00")),
                    new PlannedProjectCashEvent("E4", LocalDate.of(2026, 11, 4), ProjectCashFlowDirection.INFLOW, new BigDecimal("70000.00"))
            );

            ProjectFundingGapResult standardResult = calculator.calculate(
                    new ProjectFundingPlanInput("PKR", opening, start, end, events)
            );

            List<PlannedProjectCashEvent> reversed = new ArrayList<>(events);
            Collections.reverse(reversed);
            ProjectFundingGapResult reversedResult = calculator.calculate(
                    new ProjectFundingPlanInput("PKR", opening, start, end, reversed)
            );

            assertThat(reversedResult).isEqualTo(standardResult);

            List<PlannedProjectCashEvent> shuffled = new ArrayList<>(events);
            Collections.shuffle(shuffled);
            ProjectFundingGapResult shuffledResult = calculator.calculate(
                    new ProjectFundingPlanInput("PKR", opening, start, end, shuffled)
            );

            assertThat(shuffledResult).isEqualTo(standardResult);
        }

        @Test
        @DisplayName("Decimal accuracy handles arbitrarily large values without floating-point conversion")
        void decimalAccuracyAndLargeValues() {
            LocalDate start = LocalDate.of(2026, 11, 1);
            LocalDate end = LocalDate.of(2026, 11, 2);

            BigDecimal opening = new BigDecimal("123456789012.34");
            BigDecimal inflow = new BigDecimal("456789012345.67");
            BigDecimal outflow = new BigDecimal("999999999999.99");

            ProjectFundingPlanInput input = new ProjectFundingPlanInput(
                    "PKR",
                    opening,
                    start,
                    end,
                    List.of(
                            new PlannedProjectCashEvent("E1", start, ProjectCashFlowDirection.INFLOW, inflow),
                            new PlannedProjectCashEvent("E2", end, ProjectCashFlowDirection.OUTFLOW, outflow)
                    )
            );

            ProjectFundingGapResult result = calculator.calculate(input);

            // Day 1: 123456789012.34 + 456789012345.67 = 580245801358.01
            // Day 2: 580245801358.01 - 999999999999.99 = -419754198641.98
            assertThat(result.additionalFundingRequired()).isEqualByComparingTo("419754198641.98");
            assertThat(result.finalClosingPosition()).isEqualByComparingTo("-419754198641.98");
            assertThat(result.firstShortfallDate()).isEqualTo(end);
            assertThat(result.firstPeakShortfallDate()).isEqualTo(end);
        }

        @Test
        @DisplayName("Output daily curve and input event collection are strictly immutable")
        void immutableOutputs() {
            LocalDate start = LocalDate.of(2026, 11, 1);
            LocalDate end = LocalDate.of(2026, 11, 2);
            List<PlannedProjectCashEvent> mutableEvents = new ArrayList<>();
            mutableEvents.add(new PlannedProjectCashEvent("E1", start, ProjectCashFlowDirection.INFLOW, new BigDecimal("100.00")));

            ProjectFundingPlanInput input = new ProjectFundingPlanInput("PKR", BigDecimal.TEN, start, end, mutableEvents);
            ProjectFundingGapResult result = calculator.calculate(input);

            // Modifying original list does not alter input
            mutableEvents.add(new PlannedProjectCashEvent("E2", end, ProjectCashFlowDirection.INFLOW, new BigDecimal("200.00")));
            assertThat(input.events()).hasSize(1);

            // Modifying result daily curve throws UnsupportedOperationException
            assertThatThrownBy(() -> result.dailyCurve().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("Validation Rules & Rejection Modes")
    class ValidationTests {

        private final LocalDate start = LocalDate.of(2026, 11, 1);
        private final LocalDate end = LocalDate.of(2026, 11, 5);

        @Test
        @DisplayName("Null input throws ProjectFundingGapException")
        void nullInput() {
            assertThatThrownBy(() -> calculator.calculate(null))
                    .isInstanceOf(ProjectFundingGapException.class)
                    .hasMessageContaining("Plan input cannot be null");
        }

        @Test
        @DisplayName("Missing or unsupported currency is rejected")
        void currencyValidation() {
            assertThatThrownBy(() -> calculator.calculate(new ProjectFundingPlanInput(null, BigDecimal.ZERO, start, end, List.of())))
                    .isInstanceOf(ProjectFundingGapException.class)
                    .hasMessageContaining("Currency is required");

            assertThatThrownBy(() -> calculator.calculate(new ProjectFundingPlanInput("   ", BigDecimal.ZERO, start, end, List.of())))
                    .isInstanceOf(ProjectFundingGapException.class)
                    .hasMessageContaining("Currency is required");

            assertThatThrownBy(() -> calculator.calculate(new ProjectFundingPlanInput("USD", BigDecimal.ZERO, start, end, List.of())))
                    .isInstanceOf(ProjectFundingGapException.class)
                    .hasMessageContaining("Unsupported currency 'USD'");
        }

        @Test
        @DisplayName("Missing or inverted horizon is rejected")
        void horizonValidation() {
            assertThatThrownBy(() -> calculator.calculate(new ProjectFundingPlanInput("PKR", BigDecimal.ZERO, null, end, List.of())))
                    .isInstanceOf(ProjectFundingGapException.class)
                    .hasMessageContaining("Horizon start date is required");

            assertThatThrownBy(() -> calculator.calculate(new ProjectFundingPlanInput("PKR", BigDecimal.ZERO, start, null, List.of())))
                    .isInstanceOf(ProjectFundingGapException.class)
                    .hasMessageContaining("Horizon end date is required");

            assertThatThrownBy(() -> calculator.calculate(new ProjectFundingPlanInput("PKR", BigDecimal.ZERO, end, start, List.of())))
                    .isInstanceOf(ProjectFundingGapException.class)
                    .hasMessageContaining("Horizon start date")
                    .hasMessageContaining("cannot be after horizon end date");
        }

        @Test
        @DisplayName("Missing opening funds must fail validation (not assumed zero)")
        void missingOpeningFundsFailsValidation() {
            assertThatThrownBy(() -> calculator.calculate(new ProjectFundingPlanInput("PKR", null, start, end, List.of())))
                    .isInstanceOf(ProjectFundingGapException.class)
                    .hasMessageContaining("Opening funds are required");
        }

        @Test
        @DisplayName("Negative opening funds or opening funds with fractional cents are rejected")
        void invalidOpeningFunds() {
            assertThatThrownBy(() -> calculator.calculate(new ProjectFundingPlanInput("PKR", new BigDecimal("-0.01"), start, end, List.of())))
                    .isInstanceOf(ProjectFundingGapException.class)
                    .hasMessageContaining("Opening funds must be non-negative");

            assertThatThrownBy(() -> calculator.calculate(new ProjectFundingPlanInput("PKR", new BigDecimal("10.005"), start, end, List.of())))
                    .isInstanceOf(ProjectFundingGapException.class)
                    .hasMessageContaining("must be representable exactly at two decimal places without rounding");
        }

        @Test
        @DisplayName("Null events list is rejected")
        void nullEventsList() {
            assertThatThrownBy(() -> calculator.calculate(new ProjectFundingPlanInput("PKR", BigDecimal.ZERO, start, end, null)))
                    .isInstanceOf(ProjectFundingGapException.class)
                    .hasMessageContaining("Events list is required");
        }

        @Test
        @DisplayName("Null event item within list is rejected")
        void nullEventItem() {
            List<PlannedProjectCashEvent> events = Collections.singletonList(null);
            ProjectFundingPlanInput input = new ProjectFundingPlanInput("PKR", BigDecimal.ZERO, start, end, events);
            assertThatThrownBy(() -> calculator.calculate(input))
                    .isInstanceOf(ProjectFundingGapException.class)
                    .hasMessageContaining("Planned cash event cannot be null");
        }

        @Test
        @DisplayName("Event with missing or blank eventId is rejected")
        void blankEventId() {
            List<PlannedProjectCashEvent> events = List.of(
                    new PlannedProjectCashEvent("  ", start, ProjectCashFlowDirection.INFLOW, new BigDecimal("100.00"))
            );
            ProjectFundingPlanInput input = new ProjectFundingPlanInput("PKR", BigDecimal.ZERO, start, end, events);
            assertThatThrownBy(() -> calculator.calculate(input))
                    .isInstanceOf(ProjectFundingGapException.class)
                    .hasMessageContaining("Event ID is required and cannot be blank");
        }

        @Test
        @DisplayName("Duplicate event IDs are rejected")
        void duplicateEventIds() {
            List<PlannedProjectCashEvent> events = List.of(
                    new PlannedProjectCashEvent("EVT-1", start, ProjectCashFlowDirection.INFLOW, new BigDecimal("100.00")),
                    new PlannedProjectCashEvent("EVT-1", end, ProjectCashFlowDirection.OUTFLOW, new BigDecimal("50.00"))
            );
            ProjectFundingPlanInput input = new ProjectFundingPlanInput("PKR", BigDecimal.ZERO, start, end, events);
            assertThatThrownBy(() -> calculator.calculate(input))
                    .isInstanceOf(ProjectFundingGapException.class)
                    .hasMessageContaining("Duplicate event ID: 'EVT-1'");
        }

        @Test
        @DisplayName("Event date outside horizon is rejected")
        void eventDateOutsideHorizon() {
            List<PlannedProjectCashEvent> before = List.of(
                    new PlannedProjectCashEvent("E1", start.minusDays(1), ProjectCashFlowDirection.INFLOW, new BigDecimal("100.00"))
            );
            assertThatThrownBy(() -> calculator.calculate(new ProjectFundingPlanInput("PKR", BigDecimal.ZERO, start, end, before)))
                    .isInstanceOf(ProjectFundingGapException.class)
                    .hasMessageContaining("is outside horizon");

            List<PlannedProjectCashEvent> after = List.of(
                    new PlannedProjectCashEvent("E2", end.plusDays(1), ProjectCashFlowDirection.INFLOW, new BigDecimal("100.00"))
            );
            assertThatThrownBy(() -> calculator.calculate(new ProjectFundingPlanInput("PKR", BigDecimal.ZERO, start, end, after)))
                    .isInstanceOf(ProjectFundingGapException.class)
                    .hasMessageContaining("is outside horizon");
        }

        @Test
        @DisplayName("Event with missing direction is rejected")
        void missingEventDirection() {
            List<PlannedProjectCashEvent> events = List.of(
                    new PlannedProjectCashEvent("E1", start, null, new BigDecimal("100.00"))
            );
            ProjectFundingPlanInput input = new ProjectFundingPlanInput("PKR", BigDecimal.ZERO, start, end, events);
            assertThatThrownBy(() -> calculator.calculate(input))
                    .isInstanceOf(ProjectFundingGapException.class)
                    .hasMessageContaining("Event direction is required");
        }

        @Test
        @DisplayName("Event amount non-positive or requiring rounding is rejected")
        void invalidEventAmount() {
            // Null amount
            List<PlannedProjectCashEvent> nullAmt = List.of(
                    new PlannedProjectCashEvent("E1", start, ProjectCashFlowDirection.INFLOW, null)
            );
            assertThatThrownBy(() -> calculator.calculate(new ProjectFundingPlanInput("PKR", BigDecimal.ZERO, start, end, nullAmt)))
                    .isInstanceOf(ProjectFundingGapException.class)
                    .hasMessageContaining("Event amount is required");

            // Zero amount
            List<PlannedProjectCashEvent> zeroAmt = List.of(
                    new PlannedProjectCashEvent("E2", start, ProjectCashFlowDirection.INFLOW, BigDecimal.ZERO)
            );
            assertThatThrownBy(() -> calculator.calculate(new ProjectFundingPlanInput("PKR", BigDecimal.ZERO, start, end, zeroAmt)))
                    .isInstanceOf(ProjectFundingGapException.class)
                    .hasMessageContaining("must be strictly positive (> 0)");

            // Negative amount
            List<PlannedProjectCashEvent> negAmt = List.of(
                    new PlannedProjectCashEvent("E3", start, ProjectCashFlowDirection.INFLOW, new BigDecimal("-10.00"))
            );
            assertThatThrownBy(() -> calculator.calculate(new ProjectFundingPlanInput("PKR", BigDecimal.ZERO, start, end, negAmt)))
                    .isInstanceOf(ProjectFundingGapException.class)
                    .hasMessageContaining("must be strictly positive (> 0)");

            // Scale > 2 requiring rounding
            List<PlannedProjectCashEvent> unroundedAmt = List.of(
                    new PlannedProjectCashEvent("E4", start, ProjectCashFlowDirection.INFLOW, new BigDecimal("50.123"))
            );
            assertThatThrownBy(() -> calculator.calculate(new ProjectFundingPlanInput("PKR", BigDecimal.ZERO, start, end, unroundedAmt)))
                    .isInstanceOf(ProjectFundingGapException.class)
                    .hasMessageContaining("must be representable exactly at two decimal places without rounding");
        }
    }
}
