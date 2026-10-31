package com.app.sme_health_backend.samples;

import com.app.sme_health_backend.samples.dto.*;
import com.app.sme_health_backend.samples.model.*;
import com.app.sme_health_backend.samples.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.BigDecimal;
import java.util.*;
import static com.app.sme_health_backend.projects.model.ProjectCostCategory.*;
import static org.junit.jupiter.api.Assertions.*;

class SamplePlanCalculatorTests {
    private final SamplePlanCalculator calculator = new SamplePlanCalculator();
    private final UUID business = UUID.randomUUID();
    private final List<SamplePlanLineRequest> lines = List.of(
            new SamplePlanLineRequest(MATERIAL, "Material", "2", "kg", "300"),
            new SamplePlanLineRequest(LABOUR, "Labour", "4", "hour", "50"));

    @Test void paidPlanUsesExactServerArithmeticAndAllCategorySubtotals() {
        var plan = calculator.calculate(business, RecoveryIntent.PAID, "1000", lines);
        assertEquals("800.00", plan.plannedCost());
        assertEquals("1000.00", plan.plannedRecovery());
        assertEquals("200.00", plan.plannedContribution());
        assertEquals("600.00", plan.lines().getFirst().plannedLineCost());
        assertEquals("200.00", plan.categorySubtotals().get(LABOUR));
        assertEquals("0.00", plan.categorySubtotals().get(OVERHEAD));
        assertEquals(5, plan.categorySubtotals().size());
        assertNull(plan.unavailableReasons().plannedContribution());
        assertEquals("PKR", plan.baseCurrency());
        assertTrue(plan.planningOnly());
        assertNull(plan.calculation().accountingRoundingPolicy());
        assertEquals("f09-plan-v1", plan.calculation().version());
    }
    @Test void fractionalInputsAreNotRoundedToCashMinorUnits() {
        var plan = calculator.calculate(business, RecoveryIntent.PARTIAL, "1.0001", List.of(
                new SamplePlanLineRequest(OTHER, "Fraction", "0.123456", "u", "0.123456")));
        assertEquals("0.015241383936", plan.plannedCost());
        assertEquals("0.984858616064", plan.plannedContribution());
    }
    @ParameterizedTest @EnumSource(value=RecoveryIntent.class, names={"PAID","PARTIAL"})
    void unknownRecoveryIsDistinctFromZero(RecoveryIntent intent) {
        var unknown = calculator.calculate(business, intent, null, lines);
        assertNull(unknown.plannedRecovery()); assertNull(unknown.plannedContribution());
        assertEquals("PLANNED_RECOVERY_UNKNOWN", unknown.unavailableReasons().plannedContribution());
        var zero = calculator.calculate(business, intent, "0", lines);
        assertEquals("0.00", zero.plannedRecovery()); assertEquals("-800.00", zero.plannedContribution());
        assertNull(zero.unavailableReasons().plannedContribution());
    }
    @Test void freeCanonicalizesOmittedRecoveryToExplicitZeroAndRejectsNonzero() {
        assertEquals("0.00", calculator.calculate(business, RecoveryIntent.FREE, null, lines).plannedRecovery());
        assertEquals("-800.00", calculator.calculate(business, RecoveryIntent.FREE, "0.0000", lines).plannedContribution());
        var ex = assertThrows(SampleValidationException.class,
                () -> calculator.calculate(business, RecoveryIntent.FREE, "0.0001", lines));
        assertEquals("free_recovery_must_be_zero", ex.errors().get("plannedRecovery"));
    }
    @Test void fullInputBoundsHaveExactRepresentableTotals() {
        var max = new SamplePlanLineRequest(MATERIAL, "Max", "999999999.999999", "u", "999999999999.999999");
        var plan = calculator.calculate(business, RecoveryIntent.PAID, "999999999999.9999", Collections.nCopies(100,max));
        BigDecimal expected = new BigDecimal(max.quantity()).multiply(new BigDecimal(max.unitCost())).multiply(new BigDecimal("100"));
        assertEquals(0, expected.compareTo(plan.rawTotals().plannedCost()));
        assertTrue(plan.plannedContribution().startsWith("-"));
    }
    @ParameterizedTest @ValueSource(strings={"-1","+1","1e2","NaN","Infinity"," 1","1 ","1,000","1000000000000","0.00001"})
    void invalidRecoveryIsRejected(String value) {
        assertThrows(SampleValidationException.class, () -> calculator.calculate(business,RecoveryIntent.PAID,value,lines));
    }
    @ParameterizedTest @ValueSource(strings={"1000000000","0.0000001","-1","1e2"})
    void invalidQuantityIsRejected(String value) {
        assertThrows(SampleValidationException.class, () -> calculator.calculate(business,RecoveryIntent.FREE,null,
                List.of(new SamplePlanLineRequest(MATERIAL,"D",value,"u","1"))));
    }
    @ParameterizedTest @ValueSource(strings={"1000000000000","0.0000001","-1","1e2"})
    void invalidUnitCostIsRejected(String value) {
        assertThrows(SampleValidationException.class, () -> calculator.calculate(business,RecoveryIntent.FREE,null,
                List.of(new SamplePlanLineRequest(MATERIAL,"D","1","u",value))));
    }
    @Test void lineBoundsAndRequiredFieldsAreEnforced() {
        assertThrows(SampleValidationException.class, () -> calculator.calculate(business,RecoveryIntent.FREE,null,List.of()));
        assertThrows(SampleValidationException.class, () -> calculator.calculate(business,RecoveryIntent.FREE,null,Collections.nCopies(101,lines.getFirst())));
        assertThrows(SampleValidationException.class, () -> calculator.calculate(business,RecoveryIntent.FREE,null,List.of(new SamplePlanLineRequest(null,"D","1","u","1"))));
        assertThrows(SampleValidationException.class, () -> calculator.calculate(business,RecoveryIntent.FREE,null,List.of(new SamplePlanLineRequest(OTHER," ","1","u","1"))));
    }
    @Test void metadataIsNormalizedAndDatesAndVersionValidated() {
        UUID project = UUID.randomUUID();
        var valid = calculator.validateCreateMetadata(new SampleCreateRequest(" sam-1 ", " Name ",SampleKind.SAMPLE,null,RecoveryIntent.FREE,project,"2026-10-28","2026-10-29"));
        assertEquals("SAM-1",valid.code()); assertEquals("Name",valid.name()); assertEquals(project, valid.projectId());
        assertThrows(SampleValidationException.class, () -> calculator.validateCreateMetadata(new SampleCreateRequest("S","N",SampleKind.SAMPLE,null,RecoveryIntent.FREE,null,"2026-10-29","2026-10-28")));
        assertThrows(SampleValidationException.class, () -> calculator.validateCreateMetadata(new SampleCreateRequest("S","N",SampleKind.SAMPLE,null,null,null,null,null)));
        assertThrows(SampleValidationException.class, () -> calculator.validateUpdateMetadata(new SampleUpdateRequest("S","N",SampleKind.SAMPLE,null,RecoveryIntent.FREE,null,null,null,-1L)));
    }
}
