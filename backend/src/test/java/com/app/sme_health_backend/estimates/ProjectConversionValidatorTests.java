package com.app.sme_health_backend.estimates;

import com.app.sme_health_backend.estimates.dto.EstimateLineRequest.CostGroup;
import com.app.sme_health_backend.estimates.model.EstimateRevisionRecord;
import com.app.sme_health_backend.estimates.service.ConversionPrecisionException;
import com.app.sme_health_backend.estimates.service.EstimateService;
import com.app.sme_health_backend.estimates.service.PricingCalculator;
import com.app.sme_health_backend.estimates.service.ProjectConversionValidator;
import com.app.sme_health_backend.estimates.service.ProjectConversionValidator.Issue;
import com.app.sme_health_backend.projects.model.ProjectCostCategory;
import com.app.sme_health_backend.projects.service.ProjectBudgetCalculator;
import com.app.sme_health_backend.projects.service.ProjectValidationException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.app.sme_health_backend.estimates.EstimateTestRevisions.*;
import static com.app.sme_health_backend.estimates.dto.EstimateLineRequest.CostGroup.*;
import static com.app.sme_health_backend.estimates.dto.PricingPreviewRequest.PricingMode.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectConversionValidatorTests {
    private static final ProjectBudgetCalculator F05 = new ProjectBudgetCalculator();

    private static List<String> fields(List<Issue> issues) { return issues.stream().map(Issue::field).toList(); }

    @Test void exactCompatibleQuantityAndUnitCostConvertAndF05ComputesIdenticalTotals() {
        EstimateRevisionRecord revision = stored(request(PROPOSED_PRICE, "1500.25", "10",
                line(MATERIAL, "2.5", "120.125"), line(LABOUR, "8", "62.5")));
        assertEquals(List.of(), ProjectConversionValidator.validate(revision));
        var conversion = ProjectConversionValidator.convert(revision);
        assertEquals("1500.25", conversion.plannedRevenue());
        assertEquals(List.of("2.5", "120.125", "8", "62.5"), conversion.lines().stream()
                .flatMap(line -> java.util.stream.Stream.of(line.quantity(), line.unitCost())).toList());
        // F05's own authoritative calculator accepts the strings unchanged and reproduces every amount exactly.
        var f05 = F05.calculate(UUID.randomUUID(), conversion.plannedRevenue(), conversion.lines());
        assertEquals(0, f05.rawTotals().plannedRevenue().compareTo(revision.plannedBatchPrice()));
        assertEquals(0, f05.rawTotals().plannedCost().compareTo(revision.totalPlannedCost()));
        assertEquals(0, f05.rawTotals().plannedMargin().compareTo(revision.plannedContribution()));
        for (int index = 0; index < revision.lines().size(); index++)
            assertEquals(0, f05.lines().get(index).rawPlannedLineCost().compareTo(revision.lines().get(index).plannedCost()));
    }

    @Test void quantityWithMoreThanSixDecimalsIsRejectedWithLineDetails() {
        EstimateRevisionRecord revision = stored(request(PROPOSED_PRICE, "100", null, line(MATERIAL, "1.0000001", "10")));
        List<Issue> issues = ProjectConversionValidator.validate(revision);
        Issue quantity = issues.getFirst();
        assertEquals(1, quantity.lineNumber());
        assertEquals("lines[0].quantity", quantity.field());
        assertEquals("1.0000001", quantity.sourceValue());
        assertEquals("scale_exceeded", quantity.reason());
        assertEquals("project_budget_lines.quantity", quantity.target().column());
        assertEquals(15, quantity.target().precision());
        assertEquals(6, quantity.target().scale());
        // The derived line cost 10.000001 is itself representable; only the quantity is reported.
        assertEquals(List.of("lines[0].quantity"), fields(issues));
        var thrown = assertThrows(ConversionPrecisionException.class, () -> ProjectConversionValidator.convert(revision));
        assertEquals("conversion_precision_unrepresentable", thrown.code());
        assertEquals(422, thrown.status());
    }

    @Test void unitCostWithMoreThanSixDecimalsIsRejected() {
        EstimateRevisionRecord revision = stored(request(PROPOSED_PRICE, "100", null,
                line(MATERIAL, "1", "10"), line(OVERHEAD, "2", "0.1234567")));
        List<Issue> issues = ProjectConversionValidator.validate(revision);
        assertEquals(List.of("lines[1].unitCost"), fields(issues));
        assertEquals(2, issues.getFirst().lineNumber());
        assertEquals("0.1234567", issues.getFirst().sourceValue());
        assertEquals(6, issues.getFirst().target().scale());
    }

    @Test void integerRangeBeyondF05CapacityIsRejected() {
        // F06 accepts 12 integer digits for quantity; F05 permits 9.
        EstimateRevisionRecord revision = stored(request(PROPOSED_PRICE, "1", null, line(FREIGHT, "1000000000", "1")));
        Issue issue = ProjectConversionValidator.validate(revision).getFirst();
        assertEquals("lines[0].quantity", issue.field());
        assertEquals("integer_digits_exceeded", issue.reason());
        assertEquals(9, issue.target().maxIntegerDigits());
    }

    @Test void targetMarginRevenueWithMoreThanFourDecimalsIsRejectedAsInexact() {
        // 1000 / (1 - 0.30) = 1428.571428... : the stored scale-24 quotient is not the exact price.
        EstimateRevisionRecord revision = stored(request(TARGET_MARGIN, "0.30", "10", line(MATERIAL, "1", "1000")));
        assertEquals(0, new BigDecimal("1428.571428571428571428571429").compareTo(revision.plannedBatchPrice()));
        List<Issue> issues = ProjectConversionValidator.validate(revision);
        assertEquals(List.of("plannedBatchPrice"), fields(issues));
        Issue price = issues.getFirst();
        assertNull(price.lineNumber());
        assertEquals("inexact_division", price.reason());
        assertEquals("1428.571428571428571428571429", price.sourceValue());
        assertEquals("project_budget_versions.planned_revenue", price.target().column());
        assertEquals(4, price.target().scale());
    }

    @Test void terminatingTargetMarginAndMarkupPricesThatFitAreConvertible() {
        assertEquals(List.of(), ProjectConversionValidator.validate(stored(request(TARGET_MARGIN, "0.20", "10", line(MATERIAL, "1", "800.00")))));
        assertEquals(List.of(), ProjectConversionValidator.validate(stored(request(TARGET_MARKUP, "0.20", "10", line(MATERIAL, "1", "800.00")))));
        // A terminating markup price with too many decimals is a scale failure, not a division failure.
        Issue markup = ProjectConversionValidator.validate(stored(request(TARGET_MARKUP, "0.123", null, line(MATERIAL, "1", "1.01")))).getFirst();
        assertEquals("plannedBatchPrice", markup.field());
        assertEquals("1.13423", markup.sourceValue());
        assertEquals("scale_exceeded", markup.reason());
    }

    @Test void manuallyCompatibleProposedPriceIsConvertible() {
        EstimateRevisionRecord rejected = stored(request(TARGET_MARGIN, "0.30", "10", line(MATERIAL, "1", "1000")));
        assertFalse(ProjectConversionValidator.validate(rejected).isEmpty());
        EstimateRevisionRecord revised = stored(request(PROPOSED_PRICE, "1428.5714", "10", line(MATERIAL, "1", "1000")));
        assertEquals(List.of(), ProjectConversionValidator.validate(revised));
        assertEquals("1428.5714", ProjectConversionValidator.convert(revised).plannedRevenue());
    }

    @Test void estimateRemainsValidAndReadableDespiteConversionRejection() {
        var request = request(TARGET_MARGIN, "0.30", "7", line(MATERIAL, "1.0000001", "1000"));
        PricingCalculator.Result calculated = CALCULATOR.calculate(request);
        EstimateRevisionRecord revision = stored(calculated);
        assertFalse(ProjectConversionValidator.validate(revision).isEmpty());
        // The stored revision reproduces the full f06-preview-v1 response exactly; nothing is narrowed.
        UUID business = UUID.randomUUID();
        assertEquals(CALCULATOR.preview(business, request), PricingCalculator.response(business, EstimateService.stored(revision)));
        assertTrue(revision.matches(calculated));
    }

    @Test void noRoundingAnywhereInConversion() {
        // Every accepted string parses through F05 unchanged, and every value one digit past F05's scale is rejected,
        // never adjusted: no HALF_UP, HALF_EVEN, truncation or silent normalization.
        for (String[] fixture : new String[][] {{"0.000001", "0.000001"}, {"999999999.999999", "999999999999.999999"}}) {
            EstimateRevisionRecord ok = stored(request(PROPOSED_PRICE, "1.0001", null, line(OTHER, fixture[0], fixture[1])));
            var conversion = ProjectConversionValidator.convert(ok);
            assertEquals(new BigDecimal(fixture[0]).stripTrailingZeros().toPlainString(), conversion.lines().getFirst().quantity());
            assertEquals(new BigDecimal(fixture[1]).stripTrailingZeros().toPlainString(), conversion.lines().getFirst().unitCost());
            assertDoesNotThrow(() -> F05.calculate(UUID.randomUUID(), conversion.plannedRevenue(), conversion.lines()));
        }
        for (String quantity : List.of("0.0000005", "0.0000004", "0.9999995", "1.0000009")) {
            Issue issue = ProjectConversionValidator.validate(stored(request(PROPOSED_PRICE, "1", null, line(OTHER, quantity, "1")))).getFirst();
            assertEquals(new BigDecimal(quantity).toPlainString(), issue.sourceValue(), "Reported value is the exact source value");
        }
        for (String price : List.of("0.00005", "1.99995", "1.00001")) {
            assertEquals(List.of("plannedBatchPrice"), fields(ProjectConversionValidator.validate(stored(
                    request(PROPOSED_PRICE, price, null, line(OTHER, "1", "1"))))));
            // F05 itself also refuses the value instead of rounding it.
            assertThrows(ProjectValidationException.class, () -> F05.calculate(UUID.randomUUID(), price, List.of()));
        }
        // Trailing zeros are not precision: 1.500000000000 is exactly 1.5.
        EstimateRevisionRecord padded = stored(request(PROPOSED_PRICE, "10.500000000000", null, line(OTHER, "1.500000000000", "2.000000")));
        assertEquals(List.of(), ProjectConversionValidator.validate(padded));
        assertEquals("10.5", ProjectConversionValidator.convert(padded).plannedRevenue());
    }

    @Test void costGroupsMapDirectlyOneToOne() {
        assertEquals(Arrays.stream(CostGroup.values()).map(Enum::name).toList(),
                Arrays.stream(ProjectCostCategory.values()).map(Enum::name).toList());
        EstimateRevisionRecord revision = stored(request(PROPOSED_PRICE, "5", null, line(MATERIAL, "1", "1"), line(LABOUR, "1", "1"),
                line(OVERHEAD, "1", "1"), line(FREIGHT, "1", "1"), line(OTHER, "1", "1")));
        assertEquals(List.of(ProjectCostCategory.values()),
                ProjectConversionValidator.convert(revision).lines().stream().map(line -> line.category()).toList());
    }
}
