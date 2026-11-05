package com.app.sme_health_backend.inventory;

import com.app.sme_health_backend.inventory.service.InventoryValidationException;
import com.app.sme_health_backend.inventory.service.WacPolicy;
import com.app.sme_health_backend.inventory.service.WeightedAverageCostCalculator;
import com.app.sme_health_backend.inventory.service.WeightedAverageCostCalculator.Result;
import com.app.sme_health_backend.inventory.service.WeightedAverageCostCalculator.Step;
import com.app.sme_health_backend.inventory.service.WeightedAverageCostCalculator.StepType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Pure F08 Phase A weighted-average-cost rules under the draft policy f08-wac-draft-1. */
class WeightedAverageCostCalculatorTests {
    private final WeightedAverageCostCalculator calculator = new WeightedAverageCostCalculator();

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static Step receipt(String quantity, String value) {
        return new Step(StepType.RECEIPT, d(quantity), d(value));
    }

    private static Step issue(String quantity) {
        return new Step(StepType.ISSUE, d(quantity), null);
    }

    private static void assertExact(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, d(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    private static String errorCode(Runnable action) {
        InventoryValidationException ex = assertThrows(InventoryValidationException.class, action::run);
        Map.Entry<String, String> entry = ex.errors().entrySet().iterator().next();
        return entry.getKey() + "=" + entry.getValue();
    }

    @Test
    void draftPolicyIsStampedAndNotApproved() {
        WacPolicy policy = calculator.policy();
        assertEquals("f08-wac-draft-1", policy.version());
        assertEquals(4, policy.valueScale());
        assertEquals(RoundingMode.HALF_EVEN, policy.valueRounding());
        assertEquals(6, policy.averageDisplayScale());
        assertFalse(policy.approved(), "Scale-4 HALF_EVEN is a draft proposal, not frozen accounting policy");
    }

    @Test
    void receiptAddsQuantityAndValueExactlyAndDerivesAverage() {
        Result result = calculator.calculate(d("10"), d("1000"), List.of(receipt("10", "2000")));
        assertExact("20", result.closing().quantity());
        assertExact("3000", result.closing().value());
        assertExact("150", result.closing().averageUnitCost());
        assertExact("100", result.opening().averageUnitCost());
    }

    @Test
    void partialIssueIsValuedAtCurrentAverage() {
        Result result = calculator.calculate(d("10"), d("1000"), List.of(receipt("10", "2000"), issue("4")));
        var issued = result.steps().get(1);
        assertExact("600", issued.issueValue());
        assertFalse(issued.fullDepletion());
        assertExact("16", result.closing().quantity());
        assertExact("2400", result.closing().value());
        assertExact("150", result.closing().averageUnitCost());
    }

    @Test
    void fullDepletionTakesAllRemainingValueAndClearsResidual() {
        Result result = calculator.calculate(d("3"), d("10.0000"), List.of(issue("1"), issue("2")));
        var last = result.steps().get(1);
        assertTrue(last.fullDepletion());
        assertExact("6.6667", last.issueValue());
        assertEquals(0, result.closing().quantity().signum());
        assertEquals(0, result.closing().value().signum());
        assertNull(result.closing().averageUnitCost(), "Zero-stock average is null, not zero");
    }

    @Test
    void fractionalFixtureRoundsHalfEvenAndConservesValueExactly() {
        Result result = calculator.calculate(d("0"), d("0"),
                List.of(receipt("3", "10.0000"), issue("1"), issue("1"), issue("1")));
        assertExact("3.3333", result.steps().get(1).issueValue());
        assertExact("6.6667", result.steps().get(1).after().value());
        // 6.6667 * 1 / 2 = 3.33335 is a tie; HALF_EVEN rounds to the even neighbour 3.3334.
        assertExact("3.3334", result.steps().get(2).issueValue());
        assertExact("3.3333", result.steps().get(2).after().value());
        assertExact("3.3333", result.steps().get(3).issueValue());
        assertTrue(result.steps().get(3).fullDepletion());
        assertEquals(0, result.closing().value().signum());
        assertExact("10.0000", result.totalIssuedValue());
        assertExact("10.0000", result.totalReceivedValue());
    }

    @Test
    void overIssueIsRejectedAsInsufficientStockAndStockNeverGoesNegative() {
        assertEquals("steps[1].quantity=insufficient_stock",
                errorCode(() -> calculator.calculate(d("5"), d("50"), List.of(issue("2"), issue("3.000001")))));
        assertEquals("steps[0].quantity=insufficient_stock",
                errorCode(() -> calculator.calculate(d("0"), d("0"), List.of(issue("1")))));
    }

    @Test
    void zeroStockAverageIsNullForEmptyOpeningAndAfterDepletion() {
        Result empty = calculator.calculate(d("0"), d("0"), List.of());
        assertNull(empty.opening().averageUnitCost());
        assertNull(empty.closing().averageUnitCost());
        assertTrue(empty.steps().isEmpty());
    }

    @Test
    void zeroValueReceiptIsAcceptedAndLowersTheAverage() {
        Result result = calculator.calculate(d("10"), d("1000"), List.of(receipt("10", "0"), issue("5")));
        assertExact("20", result.steps().get(0).after().quantity());
        assertExact("1000", result.steps().get(0).after().value());
        assertExact("50", result.steps().get(0).after().averageUnitCost());
        assertExact("250", result.steps().get(1).issueValue());
        // Zero-quantity stock cannot carry value; a zero-value stock with quantity is allowed.
        Result zeroCost = calculator.calculate(d("0"), d("0"), List.of(receipt("4", "0"), issue("1")));
        assertExact("0", zeroCost.steps().get(1).issueValue());
        assertExact("0", zeroCost.closing().averageUnitCost());
    }

    @Test
    void invalidPrecisionIsRejectedNotRounded() {
        assertEquals("openingQuantity=decimal_invalid",
                errorCode(() -> calculator.calculate(d("1.0000001"), d("1"), List.of())));
        assertEquals("openingValue=decimal_invalid",
                errorCode(() -> calculator.calculate(d("1"), d("1.00001"), List.of())));
        assertEquals("steps[0].value=decimal_invalid",
                errorCode(() -> calculator.calculate(d("0"), d("0"), List.of(receipt("1", "0.12345")))));
        assertEquals("steps[0].quantity=decimal_invalid",
                errorCode(() -> calculator.calculate(d("0"), d("0"), List.of(receipt("0.0000001", "1")))));
        // Trailing zeros beyond the scale are still exactly representable.
        assertExact("1", calculator.calculate(d("1.00000000"), d("1.000000"), List.of()).closing().value());
    }

    @Test
    void invalidQuantitiesValuesAndStepsAreRejected() {
        assertEquals("steps[0].quantity=quantity_invalid",
                errorCode(() -> calculator.calculate(d("0"), d("0"), List.of(receipt("0", "1")))));
        assertEquals("steps[0].quantity=quantity_invalid",
                errorCode(() -> calculator.calculate(d("5"), d("5"), List.of(issue("0")))));
        assertEquals("steps[0].quantity=quantity_invalid",
                errorCode(() -> calculator.calculate(d("5"), d("5"), List.of(issue("-1")))));
        assertEquals("openingQuantity=quantity_invalid",
                errorCode(() -> calculator.calculate(d("-1"), d("0"), List.of())));
        assertEquals("steps[0].value=value_invalid",
                errorCode(() -> calculator.calculate(d("0"), d("0"), List.of(receipt("1", "-1")))));
        assertEquals("openingValue=value_without_quantity",
                errorCode(() -> calculator.calculate(d("0"), d("5"), List.of())));
        assertEquals("steps[0].value=required",
                errorCode(() -> calculator.calculate(d("0"), d("0"), List.of(new Step(StepType.RECEIPT, d("1"), null)))));
        assertEquals("steps[0].value=value_not_allowed",
                errorCode(() -> calculator.calculate(d("5"), d("5"), List.of(new Step(StepType.ISSUE, d("1"), d("1"))))));
        List<Step> tooMany = new ArrayList<>();
        for (int i = 0; i <= WeightedAverageCostCalculator.MAX_STEPS; i++) tooMany.add(receipt("1", "1"));
        assertEquals("steps=step_count_invalid", errorCode(() -> calculator.calculate(d("0"), d("0"), tooMany)));
    }

    @Test
    void calculationIsDeterministicAndRepeatable() {
        List<Step> steps = List.of(receipt("7.5", "123.4567"), issue("2.25"), receipt("1.000001", "0.0003"), issue("3"));
        Result first = calculator.calculate(d("1.5"), d("9.9999"), steps);
        for (int run = 0; run < 25; run++) {
            assertEquals(first, new WeightedAverageCostCalculator().calculate(d("1.5"), d("9.9999"), steps));
        }
    }

    @Test
    void valueIsConservedForRandomisedSequences() {
        Random random = new Random(20261031L);
        for (int sequence = 0; sequence < 300; sequence++) {
            BigDecimal quantity = BigDecimal.ZERO;
            List<Step> steps = new ArrayList<>();
            for (int index = 0; index < 40; index++) {
                if (quantity.signum() > 0 && random.nextBoolean()) {
                    BigDecimal q = random.nextInt(5) == 0
                            ? quantity
                            : quantity.multiply(BigDecimal.valueOf(random.nextInt(99) + 1)).movePointLeft(2)
                                    .setScale(6, RoundingMode.DOWN);
                    if (q.signum() == 0) continue;
                    steps.add(new Step(StepType.ISSUE, q, null));
                    quantity = quantity.subtract(q);
                } else {
                    BigDecimal q = BigDecimal.valueOf(random.nextInt(1_000_000) + 1, random.nextInt(4));
                    BigDecimal c = BigDecimal.valueOf(random.nextInt(10_000_000), random.nextInt(5));
                    steps.add(new Step(StepType.RECEIPT, q, c));
                    quantity = quantity.add(q);
                }
            }
            Result result = calculator.calculate(BigDecimal.ZERO, BigDecimal.ZERO, steps);
            assertEquals(0, result.totalReceivedValue().subtract(result.totalIssuedValue()).compareTo(result.closing().value()),
                    "total input value - total issued value = ending value");
            for (var step : result.steps()) {
                assertTrue(step.after().quantity().signum() >= 0 && step.after().value().signum() >= 0);
                if (step.after().quantity().signum() == 0) {
                    assertEquals(0, step.after().value().signum(), "Zero stock never carries a residual value");
                    assertNull(step.after().averageUnitCost());
                }
                if (step.issueValue() != null) assertTrue(step.issueValue().scale() <= 4);
            }
        }
    }

    @Test
    void roundingParametersAreConfigurableForTesting() {
        var halfUp = new WeightedAverageCostCalculator(new WacPolicy("test-half-up", 4, RoundingMode.HALF_UP, 6, false));
        // 0.0001 * 1 / 2 = 0.00005: HALF_EVEN keeps the even digit 0, HALF_UP rounds away from zero.
        List<Step> tie = List.of(receipt("2", "0.0001"), issue("1"));
        assertExact("0.0001", halfUp.calculate(d("0"), d("0"), tie).steps().get(1).issueValue(), "HALF_UP");
        assertExact("0", calculator.calculate(d("0"), d("0"), tie).steps().get(1).issueValue(), "HALF_EVEN");
        assertThrows(IllegalArgumentException.class, () -> new WacPolicy("bad", 4, RoundingMode.UNNECESSARY, 6, false));
    }

    private static void assertExact(String expected, BigDecimal actual, String message) {
        assertEquals(0, d(expected).compareTo(actual), message);
    }
}
