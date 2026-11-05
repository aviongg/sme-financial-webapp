package com.app.sme_health_backend.projects;

import com.app.sme_health_backend.projects.dto.ProjectBudgetCalculationResponse;
import com.app.sme_health_backend.projects.dto.ProjectBudgetLineRequest;
import com.app.sme_health_backend.projects.dto.ProjectCreateRequest;
import com.app.sme_health_backend.projects.dto.ProjectUpdateRequest;
import com.app.sme_health_backend.projects.model.ProjectCostCategory;
import com.app.sme_health_backend.projects.model.ProjectKind;
import com.app.sme_health_backend.projects.service.ProjectBudgetCalculator;
import com.app.sme_health_backend.projects.service.ProjectValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.app.sme_health_backend.projects.model.ProjectCostCategory.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectBudgetCalculatorTests {
    private final ProjectBudgetCalculator calculator = new ProjectBudgetCalculator();
    private final UUID businessId = UUID.randomUUID();

    private static ProjectBudgetLineRequest line(ProjectCostCategory category, String quantity, String unitCost) {
        return new ProjectBudgetLineRequest(category, "Planned " + category.name().toLowerCase() + " item", quantity, "unit", unitCost);
    }

    @Test
    void plannedBudgetCalculatesExactLineCostTotalCostMarginAndMarginPct() {
        var result = calculator.calculate(businessId, "1000.00", List.of(
                line(MATERIAL, "2", "300.00"),
                line(LABOUR, "4", "50.00")
        ));

        assertEquals(businessId, result.businessId());
        assertEquals("PKR", result.baseCurrency());
        assertEquals("APPLICATION_FIXED_PKR", result.currencyBasis());
        assertTrue(result.planningOnly());
        assertEquals("1000.00", result.plannedRevenue());
        assertEquals("600.00", result.lines().get(0).plannedLineCost());
        assertEquals("200.00", result.lines().get(1).plannedLineCost());
        assertEquals("800.00", result.plannedCost());
        assertEquals("200.00", result.plannedMargin());
        assertEquals("0.2", result.plannedMarginPct());
        assertNull(result.unavailableReasons().plannedMarginPct());
        assertEquals("f05-plan-v1", result.calculation().version());
        assertEquals(12, result.calculation().divisionScale());
        assertEquals("HALF_EVEN", result.calculation().divisionRounding());
        assertNull(result.calculation().accountingRoundingPolicy());
    }

    @Test
    void zeroPlannedRevenueReturnsNullMarginPctWithExplicitReason() {
        var withCost = calculator.calculate(businessId, "0", List.of(line(MATERIAL, "1", "800.00")));
        assertEquals("0.00", withCost.plannedRevenue());
        assertEquals("800.00", withCost.plannedCost());
        assertEquals("-800.00", withCost.plannedMargin());
        assertNull(withCost.plannedMarginPct());
        assertEquals("ZERO_PLANNED_REVENUE", withCost.unavailableReasons().plannedMarginPct());

        var zeroCostToo = calculator.calculate(businessId, "0.00", List.of(line(OTHER, "0", "0")));
        assertEquals("0.00", zeroCostToo.plannedRevenue());
        assertEquals("0.00", zeroCostToo.plannedCost());
        assertEquals("0.00", zeroCostToo.plannedMargin());
        assertNull(zeroCostToo.plannedMarginPct());
        assertEquals("ZERO_PLANNED_REVENUE", zeroCostToo.unavailableReasons().plannedMarginPct());
    }

    @Test
    void fractionalQuantitiesAndAllFiveCostCategoriesUseExactProductAndSum() {
        var lines = List.of(
                new ProjectBudgetLineRequest(MATERIAL, "Steel sheets", "1.25", "sheet", "12.345"),
                new ProjectBudgetLineRequest(LABOUR, "Assembly crew", "2", "hours", "3.25"),
                new ProjectBudgetLineRequest(OVERHEAD, "Workshop allocation", "1", "share", "2"),
                new ProjectBudgetLineRequest(FREIGHT, "Site delivery", "1", "trip", "4"),
                new ProjectBudgetLineRequest(OTHER, "Protective crate", "0.5", "pack", "3")
        );
        ProjectBudgetCalculationResponse result = calculator.calculate(businessId, "30", lines);

        assertEquals("15.43125", result.lines().getFirst().plannedLineCost());
        assertEquals("15.43125", result.categorySubtotals().get(MATERIAL));
        assertEquals("6.50", result.categorySubtotals().get(LABOUR));
        assertEquals("2.00", result.categorySubtotals().get(OVERHEAD));
        assertEquals("4.00", result.categorySubtotals().get(FREIGHT));
        assertEquals("1.50", result.categorySubtotals().get(OTHER));
        assertEquals("29.43125", result.plannedCost());
        assertEquals("0.56875", result.plannedMargin());
        assertEquals(5, result.categorySubtotals().size());
    }

    @Test
    void lossPlannedBudgetRetainsNegativeMarginAndNegativeMarginPctWithoutClamping() {
        var result = calculator.calculate(businessId, "400", List.of(line(MATERIAL, "1", "800")));
        assertEquals("400.00", result.plannedRevenue());
        assertEquals("800.00", result.plannedCost());
        assertEquals("-400.00", result.plannedMargin());
        assertEquals("-1", result.plannedMarginPct());
        assertNull(result.unavailableReasons().plannedMarginPct());
    }

    @Test
    void repeatingDivisionUsesDisclosed12PlaceHalfEvenCalculationPrecision() {
        var result = calculator.calculate(businessId, "960.00", List.of(line(MATERIAL, "1", "800.00")));
        assertEquals("160.00", result.plannedMargin());
        assertEquals("0.166666666667", result.plannedMarginPct());
    }

    @Test
    void smallestProductIsNeverQuantizedToCurrencyMinorUnits() {
        var result = calculator.calculate(
                businessId,
                "1",
                List.of(line(MATERIAL, "0.000001", "0.000001"))
        );
        assertEquals("0.000000000001", result.lines().getFirst().plannedLineCost());
        assertEquals("0.000000000001", result.plannedCost());
    }

    @Test
    void maximumBoundedInputsAcross100LinesRemainExactWithoutOverflow() {
        String maxRevenue = "999999999999.9999";
        String maxQuantity = "999999999.999999";
        String maxUnitCost = "999999999999.999999";
        var lines = Collections.nCopies(100, line(MATERIAL, maxQuantity, maxUnitCost));
        var result = calculator.calculate(businessId, maxRevenue, lines);
        BigDecimal expectedCost = new BigDecimal(maxQuantity).multiply(new BigDecimal(maxUnitCost)).multiply(BigDecimal.valueOf(100));
        assertEquals(0, expectedCost.compareTo(result.rawTotals().plannedCost()));
        assertEquals(0, expectedCost.compareTo(new BigDecimal(result.plannedCost())));
        assertFalse(result.plannedCost().contains("E"));
        assertFalse(result.plannedMarginPct().contains("E"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "-0", "1e2", "+1", "NaN", "Infinity", "", " ", " 1", "1 ", "1,000", ".5", "1.", "١", "1000000000000", "0.1234567"})
    void malformedNegativeOrOutOfBoundDecimalsAreRejectedAcrossRevenueQuantityAndUnitCost(String invalid) {
        assertThrows(ProjectValidationException.class,
                () -> calculator.calculate(businessId, invalid, List.of(line(MATERIAL, "1", "1"))));
        assertThrows(ProjectValidationException.class,
                () -> calculator.calculate(businessId, "100", List.of(line(MATERIAL, invalid, "1"))));
        assertThrows(ProjectValidationException.class,
                () -> calculator.calculate(businessId, "100", List.of(line(MATERIAL, "1", invalid))));
    }

    @Test
    void fieldSpecificPrecisionBoundsAreEnforced() {
        // Revenue allows up to 4 fractional digits, rejects 5:
        assertDoesNotThrow(() -> calculator.calculate(businessId, "100.1234", List.of(line(MATERIAL, "1.123456", "10.123456"))));
        assertThrows(ProjectValidationException.class,
                () -> calculator.calculate(businessId, "100.12345", List.of(line(MATERIAL, "1", "1"))));
        // Quantity allows up to 9 whole digits, rejects 10:
        assertThrows(ProjectValidationException.class,
                () -> calculator.calculate(businessId, "100", List.of(line(MATERIAL, "1000000000", "1"))));
    }

    @Test
    void nullInputsAndLineBoundsAreRejectedDeterministic() {
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(null, "100", List.of(line(MATERIAL, "1", "1"))));
        assertThrows(ProjectValidationException.class,
                () -> calculator.calculate(businessId, null, List.of(line(MATERIAL, "1", "1"))));
        assertThrows(ProjectValidationException.class,
                () -> calculator.calculate(businessId, "100", null));
        assertThrows(ProjectValidationException.class,
                () -> calculator.calculate(businessId, "100", List.of()));
        assertThrows(ProjectValidationException.class,
                () -> calculator.calculate(businessId, "100", Collections.nCopies(101, line(MATERIAL, "1", "1"))));
        assertThrows(ProjectValidationException.class,
                () -> calculator.calculate(businessId, "100", Arrays.asList((ProjectBudgetLineRequest) null)));
        assertThrows(ProjectValidationException.class,
                () -> calculator.calculate(businessId, "100", List.of(new ProjectBudgetLineRequest(null, "Desc", "1", "unit", "1"))));
    }

    @Test
    void projectMetadataValidationNormalizesCodeAndEnforcesDateOrderAndTextBounds() {
        var valid = calculator.validateCreateMetadata(new ProjectCreateRequest(
                "  prj-2026/01_a  ",
                "  Commercial Solar Order  ",
                ProjectKind.ORDER,
                "  Customer roof batch  ",
                "2026-11-01",
                "2026-11-30"
        ));
        assertEquals("PRJ-2026/01_A", valid.code());
        assertEquals("Commercial Solar Order", valid.name());
        assertEquals(ProjectKind.ORDER, valid.kind());
        assertEquals("Customer roof batch", valid.description());
        assertEquals(LocalDate.of(2026, 11, 1), valid.startDate());
        assertEquals(LocalDate.of(2026, 11, 30), valid.targetEndDate());

        var dateOrderError = assertThrows(ProjectValidationException.class, () -> calculator.validateCreateMetadata(
                new ProjectCreateRequest("PRJ-1", "Name", ProjectKind.PROJECT, null, "2026-12-01", "2026-11-30")
        ));
        assertEquals("date_order_invalid", dateOrderError.errors().get("targetEndDate"));

        var badCode = assertThrows(ProjectValidationException.class, () -> calculator.validateCreateMetadata(
                new ProjectCreateRequest("BAD CODE WITH SPACES", "Name", ProjectKind.JOB, null, null, null)
        ));
        assertEquals("code_invalid", badCode.errors().get("code"));

        var missingVersion = assertThrows(ProjectValidationException.class, () -> calculator.validateUpdateMetadata(
                new ProjectUpdateRequest("PRJ-1", "Name", ProjectKind.PROJECT, null, null, null, null)
        ));
        assertEquals("version_invalid", missingVersion.errors().get("expectedVersion"));
    }
}
