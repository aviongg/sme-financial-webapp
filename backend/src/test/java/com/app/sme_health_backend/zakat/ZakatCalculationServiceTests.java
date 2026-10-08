package com.app.sme_health_backend.zakat;

import com.app.sme_health_backend.zakat.dto.ZakatPreviewRequest;
import com.app.sme_health_backend.zakat.dto.ZakatPreviewResponse;
import com.app.sme_health_backend.zakat.service.ZakatCalculationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.app.sme_health_backend.zakat.ZakatTestFixtures.*;
import static com.app.sme_health_backend.zakat.dto.ZakatPreviewRequest.*;
import static com.app.sme_health_backend.zakat.dto.ZakatTypes.*;
import static org.junit.jupiter.api.Assertions.*;

class ZakatCalculationServiceTests {
    private final ZakatCalculationService service = new ZakatCalculationService();

    @Test
    void freezesApprovedPolicyAndReturnsAuditableCashOnlyPreview() {
        Fixture fixture = new Fixture();
        ZakatPreviewResponse result = service.calculate(fixture.request());
        assertEquals("HANAFI_PK_BUSINESS_V1", result.ruleProfile());
        assertEquals("1.0.0", result.ruleVersion());
        assertEquals("SILVER", result.nisabMetal());
        assertEquals("SILVER_WEIGHT_X_PRICE_PER_GRAM", result.nisabMethod());
        assertEquals(money("612.36"), result.metalWeightGrams());
        assertEquals(money("0.025"), result.zakatRate());
        assertEquals(money("61236.00"), result.nisabValue());
        assertEquals(money("2500.00"), result.zakatDue());
        assertEquals(CalculationStatus.CALCULATED, result.calculationStatus());
        assertEquals(fixture.priceSource, result.priceSource());
        assertEquals(fixture.priceTimestamp, result.priceTimestamp());
        assertEquals(fixture.request(), result.inputs());
        assertEquals("CURRENT_PAYABLES_AND_PRINCIPAL_DUE_WITHIN_12_LUNAR_MONTHS", result.debtPolicy());
        assertEquals("A calculation based on the selected Zakat rule profile and information provided. "
                + "Complex or disputed cases may require review by a qualified Islamic scholar.", result.disclosure());
        assertTrue(result.missingFields().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"61235.99, BELOW_NISAB, false, 0.00", "61236, CALCULATED, true, 1530.90",
            "61236.01, CALCULATED, true, 1530.90", "0, BELOW_NISAB, false, 0.00"})
    void includesExactNisabAndHandlesAdjacentMinorUnits(String cash, CalculationStatus status, boolean met, String due) {
        Fixture fixture = new Fixture();
        fixture.cash = money(cash);
        ZakatPreviewResponse result = service.calculate(fixture.request());
        assertEquals(status, result.calculationStatus());
        assertEquals(met, result.nisabMet());
        assertEquals(money(due), result.zakatDue());
    }

    @ParameterizedTest
    @EnumSource(HaulStatus.class)
    void distinguishesHaulStates(HaulStatus haul) {
        Fixture fixture = new Fixture();
        fixture.haul = haul;
        ZakatPreviewResponse result = service.calculate(fixture.request());
        assertEquals(fixture.cash, result.netZakatableAssets());
        switch (haul) {
            case CONFIRMED -> assertEquals(CalculationStatus.CALCULATED, result.calculationStatus());
            case NOT_COMPLETED -> {
                assertEquals(CalculationStatus.NOT_DUE_HAUL_NOT_COMPLETED, result.calculationStatus());
                assertEquals(money("0.00"), result.zakatDue());
            }
            case UNKNOWN -> {
                assertEquals(CalculationStatus.INCOMPLETE_HAUL_CONFIRMATION_REQUIRED, result.calculationStatus());
                assertNull(result.zakatDue());
                assertTrue(result.missingFields().contains("assessment.haulStatus"));
            }
        }
    }

    @Test
    void absentHaulIsUnknownEvenBelowNisab() {
        Fixture fixture = new Fixture();
        fixture.haul = null;
        fixture.cash = BigDecimal.ZERO;
        ZakatPreviewResponse result = service.calculate(fixture.request());
        assertEquals(CalculationStatus.INCOMPLETE_HAUL_CONFIRMATION_REQUIRED, result.calculationStatus());
        assertNull(result.zakatDue());
    }

    @Test
    void includesResaleInventoryAndExcludesOperatingFixedAssets() {
        Fixture fixture = new Fixture();
        fixture.inventory = List.of(
                new InventoryItem("stock", InventoryType.RESALE, money("20000"), InventoryValuation.CURRENT_SELLING_VALUE),
                new InventoryItem("machine", InventoryType.FIXED_ASSET, money("900000"), null));
        ZakatPreviewResponse result = service.calculate(fixture.request());
        assertEquals(money("120000"), result.grossZakatableAssets());
        assertEquals(money("3000.00"), result.zakatDue());
        assertEquals("EXCLUDED_FIXED_ASSET", result.assetBreakdown().get(2).treatment());
    }

    @ParameterizedTest
    @EnumSource(value = InventoryValuation.class, names = {"HISTORICAL_COST", "UNKNOWN"})
    @NullSource
    void doesNotSubstituteBookCostForCurrentSellingValuation(InventoryValuation valuation) {
        Fixture fixture = new Fixture();
        fixture.inventory = List.of(new InventoryItem("stock", InventoryType.RESALE, money("20000"), valuation));
        assertIncomplete(service.calculate(fixture.request()), "assets.inventory[0].valuation");
    }

    @ParameterizedTest
    @EnumSource(value = InventoryType.class, names = {"RAW_MATERIAL", "WORK_IN_PROGRESS"})
    void requiresReviewForUnapprovedInventoryRules(InventoryType type) {
        Fixture fixture = new Fixture();
        fixture.inventory = List.of(new InventoryItem("stock", type, money("20000"), InventoryValuation.CURRENT_SELLING_VALUE));
        ZakatPreviewResponse result = service.calculate(fixture.request());
        assertEquals(CalculationStatus.MANUAL_REVIEW_REQUIRED, result.calculationStatus());
        assertNull(result.zakatDue());
        assertNull(result.netZakatableAssets());
    }

    @ParameterizedTest
    @EnumSource(ReceivableClassification.class)
    void appliesReceivableClassification(ReceivableClassification classification) {
        Fixture fixture = new Fixture();
        fixture.receivables = List.of(new Receivable("invoice", money("20000"), classification));
        ZakatPreviewResponse result = service.calculate(fixture.request());
        if (classification == ReceivableClassification.UNKNOWN) {
            assertIncomplete(result, "assets.receivables[0].classification");
            assertNull(result.grossZakatableAssets());
        } else {
            boolean included = classification == ReceivableClassification.GOOD || classification == ReceivableClassification.COLLECTIBLE;
            assertEquals(money(included ? "120000" : "100000"), result.grossZakatableAssets());
            assertEquals(classification == ReceivableClassification.DOUBTFUL,
                    result.warnings().contains("DOUBTFUL_RECEIVABLE_EXCLUDED:invoice"));
        }
    }

    @Test
    void deductsOnlyDeclaredEligiblePayablesAndPrincipal() {
        Fixture fixture = debtFixture();
        ZakatPreviewResponse result = service.calculate(fixture.request());
        assertEquals(money("30000"), result.deductibleLiabilities());
        assertEquals(money("70000"), result.netZakatableAssets());
        assertEquals(money("1750.00"), result.zakatDue());
        assertEquals(2, result.liabilityBreakdown().size());
    }

    @Test
    void outstandingLoanWithoutMaturityInformationIsIncomplete() {
        Fixture fixture = new Fixture();
        fixture.loan = money("1000000");
        fixture.principal = null;
        ZakatPreviewResponse result = service.calculate(fixture.request());
        assertIncomplete(result, "liabilities.principalDueWithin12LunarMonths");
        assertNull(result.deductibleLiabilities());
        assertTrue(result.warnings().contains("LOAN_MATURITY_INPUT_REQUIRED_TOTAL_BALANCE_NOT_DEDUCTED"));
    }

    @Test
    void explicitlyEmptyMaturityDeclarationMeansNoEligiblePrincipal() {
        Fixture fixture = new Fixture();
        fixture.loan = money("1000000");
        assertEquals(money("2500.00"), service.calculate(fixture.request()).zakatDue());
    }

    @Test
    void liabilitiesCannotMakeNetAssetsNegative() {
        Fixture fixture = new Fixture();
        fixture.accountsPayable = money("200000");
        fixture.payables = List.of(new LiabilityItem("supplier", money("200000")));
        ZakatPreviewResponse result = service.calculate(fixture.request());
        assertEquals(BigDecimal.ZERO, result.netZakatableAssets());
        assertEquals(money("0.00"), result.zakatDue());
    }

    @ParameterizedTest
    @CsvSource({"none, NO_FINANCING_DECLARED, NO_FINANCING_DECLARED_WITH_OUTSTANDING_LOAN",
            "conventional, CONVENTIONAL_REQUIRES_REVIEW, CONVENTIONAL_FINANCING_MAY_INVOLVE_RIBA",
            "islamic, USER_DECLARED_ISLAMIC, absent"})
    void financingLabelDoesNotControlPrincipalDeduction(String type, FinancingComplianceStatus expected, String warning) {
        Fixture fixture = debtFixture();
        fixture.financingType = type;
        ZakatPreviewResponse result = service.calculate(fixture.request());
        assertEquals(expected, result.financingComplianceStatus());
        assertEquals(money("1750.00"), result.zakatDue());
        if (!warning.equals("absent")) {
            assertTrue(result.warnings().contains(warning));
        } else {
            assertTrue(result.warnings().isEmpty());
        }
    }

    @Test
    void interestExpenseTriggersWarningWithoutChangingAssetsOrDeductions() {
        Fixture fixture = new Fixture();
        fixture.interest = money("999999");
        ZakatPreviewResponse result = service.calculate(fixture.request());
        assertEquals(fixture.cash, result.grossZakatableAssets());
        assertEquals(BigDecimal.ZERO, result.deductibleLiabilities());
        assertEquals(money("2500.00"), result.zakatDue());
        assertTrue(result.warnings().contains("RIBA_RELATED_EXPENSE_RECORDED"));
    }

    @Test
    void unknownWarningOnlyFieldsRemainUnknownWithoutBlockingKnownArithmetic() {
        Fixture fixture = new Fixture();
        fixture.interest = null;
        fixture.financingType = null;
        ZakatPreviewResponse result = service.calculate(fixture.request());
        assertNull(result.inputs().financing().interestExpense());
        assertEquals(FinancingComplianceStatus.UNKNOWN, result.financingComplianceStatus());
        assertTrue(result.warnings().contains("INTEREST_EXPENSE_UNKNOWN"));
        assertEquals(money("2500.00"), result.zakatDue());
    }

    @Test
    void rejectsDuplicateObligationsWithinAndAcrossCategories() {
        Fixture fixture = debtFixture();
        fixture.principal = List.of(new LiabilityItem("supplier", money("20000")));
        assertThrows(IllegalArgumentException.class, () -> service.calculate(fixture.request()));
        fixture.principal = List.of();
        fixture.payables = List.of(new LiabilityItem("supplier", money("1000")), new LiabilityItem("supplier", money("1000")));
        assertThrows(IllegalArgumentException.class, () -> service.calculate(fixture.request()));
    }

    @Test
    void requiresNonOverlapDeclarationAndRejectsKnownOverlap() {
        Fixture fixture = debtFixture();
        fixture.noOverlap = null;
        assertIncomplete(service.calculate(fixture.request()), "liabilities.principalExcludedFromPayables");
        fixture.noOverlap = false;
        assertThrows(IllegalArgumentException.class, () -> service.calculate(fixture.request()));
    }

    @ParameterizedTest
    @MethodSource("unknownInputs")
    void unknownRequiredInputsAreNotZero(String path, Consumer<Fixture> change) {
        Fixture fixture = new Fixture();
        change.accept(fixture);
        assertIncomplete(service.calculate(fixture.request()), path);
    }

    static Stream<Arguments> unknownInputs() {
        return Stream.of(
                change("assets.cashAndBankBalances", f -> f.cash = null),
                change("assets.inventory", f -> f.inventory = null),
                change("assets.receivables", f -> f.receivables = null),
                change("liabilities.accountsPayable", f -> f.accountsPayable = null),
                change("financing.loanOutstanding", f -> f.loan = null),
                change("assessment.assessmentDate", f -> f.date = null),
                change("assessment.currency", f -> f.currency = null),
                change("assessment.nisabMetal", f -> f.metal = null),
                change("assessment.metalWeightGrams", f -> f.weight = null),
                change("assessment.metalPricePerGram", f -> f.price = null),
                change("assessment.priceSource", f -> f.priceSource = " "),
                change("assessment.priceTimestamp", f -> f.priceTimestamp = null),
                change("assets.inventory[0].amount", f -> f.inventory = List.of(new InventoryItem("stock", InventoryType.FIXED_ASSET, null, null))),
                change("assets.inventory[0].type", f -> f.inventory = List.of(new InventoryItem("stock", null, money("10"), null))),
                change("assets.receivables[0].amount", f -> f.receivables = List.of(new Receivable("invoice", null, ReceivableClassification.BAD))),
                change("assets.receivables[0].classification", f -> f.receivables = List.of(new Receivable("invoice", money("10"), null))),
                change("liabilities.currentPayables[0].amount", f -> f.payables = List.of(new LiabilityItem("supplier", null))));
    }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    void rejectsInvalidKnownInputs(String reason, Consumer<Fixture> change) {
        Fixture fixture = new Fixture();
        change.accept(fixture);
        assertThrows(IllegalArgumentException.class, () -> service.calculate(fixture.request()), reason);
    }

    static Stream<Arguments> invalidInputs() {
        return Stream.of(
                change("negative cash", f -> f.cash = money("-1")),
                change("negative inventory", f -> f.inventory = List.of(new InventoryItem("stock", InventoryType.RESALE, money("-1"), InventoryValuation.CURRENT_SELLING_VALUE))),
                change("negative receivable", f -> f.receivables = List.of(new Receivable("invoice", money("-1"), ReceivableClassification.BAD))),
                change("negative payables", f -> f.accountsPayable = money("-1")),
                change("negative loan", f -> f.loan = money("-1")),
                change("negative interest", f -> f.interest = money("-1")),
                change("negative deduction", f -> f.payables = List.of(new LiabilityItem("supplier", money("-1")))),
                change("negative principal", f -> f.principal = List.of(new LiabilityItem("loan", money("-1")))),
                change("zero price", f -> f.price = BigDecimal.ZERO),
                change("negative price", f -> f.price = money("-1")),
                change("gold conflicts with policy", f -> f.metal = "GOLD"),
                change("weight conflicts with policy", f -> f.weight = money("595")),
                change("unsupported currency", f -> f.currency = "NOT_A_CURRENCY"),
                change("non monetary currency", f -> f.currency = "XAU"),
                change("financing contract", f -> f.financingType = "other"),
                change("excess payable deduction", f -> f.payables = List.of(new LiabilityItem("supplier", money("1")))),
                change("excess principal deduction", f -> f.principal = List.of(new LiabilityItem("loan", money("1")))),
                change("null list entry", f -> f.inventory = Arrays.asList((InventoryItem) null)),
                change("blank liability id", f -> f.payables = List.of(new LiabilityItem(" ", BigDecimal.ZERO))),
                change("blank asset reference", f -> f.receivables = List.of(new Receivable("", BigDecimal.ZERO, ReceivableClassification.GOOD))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"AGRICULTURE", "LIVESTOCK", "CRYPTO", "PERSONAL_JEWELLERY", "PENSION", "COMPLEX_SECURITIES"})
    void unsupportedAssetsDoNotProduceDefinitiveZakat(String category) {
        Fixture fixture = new Fixture();
        fixture.unsupported = List.of(category);
        ZakatPreviewResponse result = service.calculate(fixture.request());
        assertEquals(CalculationStatus.UNSUPPORTED, result.calculationStatus());
        assertNull(result.zakatDue());
        assertNull(result.nisabMet());
        assertTrue(result.warnings().contains("UNSUPPORTED_ASSET_CATEGORY:" + category));
    }

    @Test
    void reportsPriceDateMismatchInsteadOfSilentlyUsingUnalignedPrice() {
        Fixture fixture = new Fixture();
        fixture.priceTimestamp = fixture.priceTimestamp.minusDays(1);
        ZakatPreviewResponse result = service.calculate(fixture.request());
        assertTrue(result.warnings().contains("PRICE_DATE_DIFFERS_FROM_ASSESSMENT_DATE"));
        assertEquals(fixture.priceTimestamp, result.priceTimestamp());
    }

    @ParameterizedTest
    @CsvSource({"PKR,100000.2,2500.01", "JPY,100020,2501", "BHD,100000.02,2500.001"})
    void roundsOnlyFinalAmountHalfUpUsingCurrencyMinorUnits(String currency, String cash, String due) {
        Fixture fixture = new Fixture();
        fixture.currency = currency;
        fixture.cash = money(cash);
        ZakatPreviewResponse result = service.calculate(fixture.request());
        assertEquals(money(cash).multiply(money("0.025")), result.unroundedZakatDue());
        assertEquals(money(due), result.zakatDue());
    }

    @Test
    void aggregatesUnroundedComponentsBeforeThresholdComparison() {
        Fixture fixture = new Fixture();
        fixture.cash = money("61235.995");
        fixture.inventory = List.of(new InventoryItem("stock", InventoryType.RESALE, money("0.005"), InventoryValuation.CURRENT_SELLING_VALUE));
        ZakatPreviewResponse result = service.calculate(fixture.request());
        assertEquals(money("61236.000"), result.netZakatableAssets());
        assertTrue(result.nisabMet());
    }

    @Test
    void calculationIsDeterministicAndDoesNotMutateInput() {
        ZakatPreviewRequest request = debtFixture().request();
        assertEquals(service.calculate(request), service.calculate(request));
        assertEquals(money("1000000"), request.financing().loanOutstanding());
        assertThrows(IllegalArgumentException.class, () -> service.calculate(null));
        ZakatPreviewResponse absent = service.calculate(new ZakatPreviewRequest(null, null, null, null));
        assertEquals(CalculationStatus.INCOMPLETE, absent.calculationStatus());
        assertNull(absent.zakatDue());
    }

    private static Fixture debtFixture() {
        Fixture fixture = new Fixture();
        fixture.accountsPayable = money("50000");
        fixture.payables = List.of(new LiabilityItem("supplier", money("10000")));
        fixture.loan = money("1000000");
        fixture.principal = List.of(new LiabilityItem("loan-principal", money("20000")));
        fixture.financingType = "islamic";
        fixture.noOverlap = true;
        return fixture;
    }

    private static Arguments change(String reason, Consumer<Fixture> change) {
        return Arguments.of(reason, change);
    }

    private void assertIncomplete(ZakatPreviewResponse response, String field) {
        assertEquals(CalculationStatus.INCOMPLETE, response.calculationStatus());
        assertTrue(response.missingFields().contains(field), response.missingFields().toString());
        assertNull(response.zakatDue());
    }
}
