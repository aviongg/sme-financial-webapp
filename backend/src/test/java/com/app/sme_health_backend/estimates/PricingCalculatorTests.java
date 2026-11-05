package com.app.sme_health_backend.estimates;

import com.app.sme_health_backend.estimates.dto.*;
import com.app.sme_health_backend.estimates.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.util.*;
import static com.app.sme_health_backend.estimates.dto.EstimateLineRequest.CostGroup.*;
import static com.app.sme_health_backend.estimates.dto.PricingPreviewRequest.PricingMode.*;
import static org.junit.jupiter.api.Assertions.*;

class PricingCalculatorTests {
    private final PricingCalculator calculator = new PricingCalculator(new EstimateCostCalculator());
    private final UUID business = UUID.randomUUID();
    private EstimateLineRequest line(String quantity, String cost) {
        return new EstimateLineRequest(MATERIAL, "Material assumption", quantity, "item", cost);
    }
    private PricingPreviewResponse preview(PricingPreviewRequest.PricingMode mode, String value, String cost, String quantity) {
        return calculator.preview(business, new PricingPreviewRequest(List.of(line("1", cost)), quantity, mode, value));
    }
    @Test void marginAndMarkupAcceptanceExampleRemainDifferent() {
        var margin = preview(TARGET_MARGIN, "0.20", "800.00", "10");
        var markup = preview(TARGET_MARKUP, "0.20", "800.00", "10");
        assertEquals("1000.00", margin.plannedBatchPrice()); assertEquals("200.00", margin.plannedContribution());
        assertEquals("0.2", margin.plannedMargin()); assertEquals("0.25", margin.plannedMarkup());
        assertEquals("100.00", margin.plannedPerUnitPrice());
        assertEquals("960.00", markup.plannedBatchPrice()); assertEquals("0.2", markup.plannedMarkup());
        assertEquals("0.166666666666666666666667", markup.plannedMargin());
        assertEquals("96.00", markup.plannedPerUnitPrice());
    }
    @Test void zeroCostPreservesUnknownMarkup() {
        var priced = preview(PROPOSED_PRICE, "25", "0", null);
        assertEquals("25.00", priced.plannedContribution()); assertEquals("1", priced.plannedMargin());
        assertNull(priced.plannedMarkup()); assertEquals("ZERO_COST", priced.unavailableReasons().plannedMarkup());
        assertNull(priced.plannedPerUnitPrice()); assertEquals("SALEABLE_QUANTITY_UNKNOWN", priced.unavailableReasons().plannedPerUnitPrice());
        for (var mode : List.of(TARGET_MARGIN, TARGET_MARKUP)) {
            var zero = preview(mode, "0.20", "0", "0");
            assertEquals("0.00", zero.plannedBatchPrice()); assertNull(zero.plannedMargin()); assertNull(zero.plannedMarkup());
        }
    }
    @Test void zeroProposedPriceHasUnknownMarginAndNegativeContributionAndMarkup() {
        var result = preview(PROPOSED_PRICE, "0", "800", "0");
        assertEquals("-800.00", result.plannedContribution()); assertEquals("-1", result.plannedMarkup());
        assertNull(result.plannedMargin()); assertEquals("ZERO_PRICE", result.unavailableReasons().plannedMargin());
        assertNull(result.plannedPerUnitPrice()); assertEquals("ZERO_SALEABLE_QUANTITY", result.unavailableReasons().plannedPerUnitPrice());
    }
    @Test void belowCostPriceHasNegativeRatiosRatherThanClamping() {
        var result = preview(PROPOSED_PRICE, "400", "800", "1");
        assertEquals("-1", result.plannedMargin()); assertEquals("-0.5", result.plannedMarkup());
    }
    @Test void targetZeroMarginAndMarkupReturnExactCost() {
        for (var mode : List.of(TARGET_MARGIN, TARGET_MARKUP)) {
            var result = preview(mode, "0", "800.000000000001", "1");
            assertEquals("800.000000000001", result.plannedBatchPrice());
            assertEquals("0", result.plannedMargin()); assertEquals("0", result.plannedMarkup());
        }
    }
    @ParameterizedTest @ValueSource(strings = {"1", "1.000000000001", "1.2", "999999999999"})
    void targetMarginAtOrAboveOneIsRejected(String target) {
        assertEquals("margin_out_of_range", assertThrows(EstimateValidationException.class,
                () -> preview(TARGET_MARGIN, target, "800", "1")).errors().get("pricingValue"));
    }
    @Test void fractionalQuantityAndEveryCostGroupUseExactProductAndSum() {
        var input = List.of(line("1.25", "12.345"), new EstimateLineRequest(LABOUR, "Work", "2", "hours", "3.25"),
                new EstimateLineRequest(OVERHEAD, "Rent share", "1", "share", "2"),
                new EstimateLineRequest(FREIGHT, "Transport", "1", "trip", "4"),
                new EstimateLineRequest(OTHER, "Packaging", "0.5", "pack", "3"));
        var result = calculator.preview(business, new PricingPreviewRequest(input, "2.5", PROPOSED_PRICE, "30"));
        assertEquals("15.43125", result.lines().getFirst().plannedCost());
        assertEquals("15.43125", result.groupSubtotals().get(MATERIAL)); assertEquals("6.50", result.groupSubtotals().get(LABOUR));
        assertEquals("29.43125", result.totalPlannedCost()); assertEquals("0.56875", result.plannedContribution());
        assertEquals("12.00", result.plannedPerUnitPrice()); assertEquals(5, result.groupSubtotals().size());
    }
    @ParameterizedTest @ValueSource(strings = {"-1", "-0", "1e2", "+1", "NaN", "Infinity", "", " ", " 1", "1 ", "1,000", ".5", "1.", "١", "1000000000000", "0.1234567890123"})
    void malformedNegativeOrOutOfBoundDecimalsAreRejectedAcrossEveryInput(String invalid) {
        for (var input : List.of(new PricingPreviewRequest(List.of(line(invalid, "1")), "1", PROPOSED_PRICE, "1"),
                new PricingPreviewRequest(List.of(line("1", invalid)), "1", PROPOSED_PRICE, "1"),
                new PricingPreviewRequest(List.of(line("1", "1")), invalid, PROPOSED_PRICE, "1"),
                new PricingPreviewRequest(List.of(line("1", "1")), "1", PROPOSED_PRICE, invalid))) {
            assertThrows(EstimateValidationException.class, () -> calculator.preview(business, input));
        }
    }
    @Test void nullRequiredInputsAreRejectedWithoutInventingZero() {
        assertThrows(EstimateValidationException.class, () -> calculator.preview(business, null));
        var inputs = Arrays.asList(new PricingPreviewRequest(null, null, PROPOSED_PRICE, "1"),
                new PricingPreviewRequest(List.of(line("1", "1")), null, null, "1"),
                new PricingPreviewRequest(List.of(line("1", "1")), null, PROPOSED_PRICE, null),
                new PricingPreviewRequest(List.of(line(null, "1")), null, PROPOSED_PRICE, "1"),
                new PricingPreviewRequest(List.of(line("1", null)), null, PROPOSED_PRICE, "1"),
                new PricingPreviewRequest(Arrays.asList((EstimateLineRequest) null), null, PROPOSED_PRICE, "1"));
        inputs.forEach(input -> assertThrows(EstimateValidationException.class, () -> calculator.preview(business, input)));
    }
    @Test void lineAndTextResourceBoundsAreExplicit() {
        for (var lines : List.of(List.<EstimateLineRequest>of(), Collections.nCopies(101, line("1", "1"))))
            assertThrows(EstimateValidationException.class, () -> calculator.preview(business, new PricingPreviewRequest(lines, null, PROPOSED_PRICE, "1")));
        for (var line : List.of(new EstimateLineRequest(null, "x", "1", "item", "1"),
                new EstimateLineRequest(MATERIAL, " ", "1", "item", "1"),
                new EstimateLineRequest(MATERIAL, "x".repeat(201), "1", "item", "1"),
                new EstimateLineRequest(MATERIAL, "x", "1", "u".repeat(41), "1"),
                new EstimateLineRequest(MATERIAL, "x\n", "1", "item", "1")))
            assertThrows(EstimateValidationException.class, () -> calculator.preview(business, new PricingPreviewRequest(List.of(line), null, PROPOSED_PRICE, "1")));
    }
    @Test void smallestProductIsNotRoundedToMinorUnits() {
        var result = calculator.preview(business, new PricingPreviewRequest(List.of(line("0.000000000001", "0.000000000001")), null, TARGET_MARKUP, "0"));
        assertEquals("0.000000000000000000000001", result.totalPlannedCost());
        assertEquals(result.totalPlannedCost(), result.plannedBatchPrice());
    }
    @Test void repeatingDivisionUsesDisclosedCalculationPrecisionAndNoAccountingPolicy() {
        var result = preview(TARGET_MARGIN, "0.7", "1", "3");
        assertEquals("3.333333333333333333333333", result.plannedBatchPrice());
        assertEquals("1.111111111111111111111111", result.plannedPerUnitPrice());
        assertEquals(24, result.calculation().divisionScale()); assertEquals("HALF_EVEN", result.calculation().divisionRounding());
        assertNull(result.calculation().accountingRoundingPolicy()); assertTrue(result.planningOnly());
    }
    @Test void maximumInputsAndLineCountRemainFiniteExactAndBoundedWithoutOverflow() {
        String maximum = "999999999999.999999999999";
        var lines = Collections.nCopies(100, line(maximum, maximum));
        var result = calculator.preview(business, new PricingPreviewRequest(lines, "0.000000000001", TARGET_MARGIN, "0.999999999999"));
        BigDecimal expected = new BigDecimal(maximum).multiply(new BigDecimal(maximum)).multiply(BigDecimal.valueOf(100));
        assertEquals(0, expected.compareTo(new BigDecimal(result.totalPlannedCost())));
        assertEquals(0, expected.movePointRight(12).compareTo(new BigDecimal(result.plannedBatchPrice())));
        assertTrue(result.plannedPerUnitPrice().length() < 100); assertFalse(result.plannedBatchPrice().contains("E"));
        var marked = calculator.preview(business, new PricingPreviewRequest(lines, null, TARGET_MARKUP, maximum));
        assertEquals(0, expected.multiply(BigDecimal.ONE.add(new BigDecimal(maximum))).compareTo(new BigDecimal(marked.plannedBatchPrice())));
    }
}
