package com.app.sme_health_backend.funding;

import com.app.sme_health_backend.financialquery.FinancialQueryFacade;
import com.app.sme_health_backend.financialquery.dto.CashPositionQuery;
import com.app.sme_health_backend.financialquery.dto.CashPositionResult;
import com.app.sme_health_backend.financialquery.dto.CurrencyCashSummary;
import com.app.sme_health_backend.financialquery.dto.FinancialQueryEnvelope;
import com.app.sme_health_backend.financialquery.model.CapabilityStatus;
import com.app.sme_health_backend.financialquery.model.DataCompleteness;
import com.app.sme_health_backend.funding.dto.*;
import com.app.sme_health_backend.funding.exception.FundingScenarioValidationException;
import com.app.sme_health_backend.funding.service.FundingScenarioService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundingScenarioServiceTest {

    @Mock
    private FinancialQueryFacade financialQuery;

    private FundingScenarioService service;

    private final UUID businessId = UUID.randomUUID();
    private final BusinessAccessContext context = new BusinessAccessContext(
            UUID.randomUUID(),
            businessId,
            MembershipRole.OWNER
    );

    @BeforeEach
    void setUp() {
        service = new FundingScenarioService(financialQuery);
    }

    @Test
    @DisplayName("LEDGER_CASH source successfully retrieves posted PKR balance and tags POSTED_ACTUAL")
    void ledgerCashSuccess() {
        LocalDate asOf = LocalDate.of(2026, 11, 1);
        List<CurrencyCashSummary> summaries = List.of(
                new CurrencyCashSummary("PKR", 2, "105000.00", "0.00", "105000.00", 2)
        );
        CashPositionResult cashResult = new CashPositionResult(
                asOf,
                summaries,
                List.of(),
                2
        );
        FinancialQueryEnvelope<CashPositionResult> envelope = FinancialQueryEnvelope.available(
                "getCashPosition",
                "1.0.0",
                businessId,
                asOf.toString(),
                DataCompleteness.COMPLETE,
                List.of(),
                List.of(),
                cashResult
        );

        when(financialQuery.getCashPosition(eq(context), any(CashPositionQuery.class)))
                .thenReturn(envelope);

        FundingScenarioCompareRequest request = new FundingScenarioCompareRequest(
                "PKR",
                asOf.toString(),
                30,
                new OpeningLiquidityRequest(OpeningLiquiditySource.LEDGER_CASH, null, null),
                new ScenarioDefinition("Base", "Baseline plan", List.of(
                        new PlannedCashEventInput("evt-1", asOf.plusDays(2).toString(), CashFlowDirection.OUTFLOW, new BigDecimal("25000.00"), "Rent", "OPERATING")
                )),
                null
        );

        FundingScenarioCompareResponse response = service.compareScenarios(context, request);

        assertNotNull(response);
        assertEquals("PKR", response.currency());
        assertEquals("POSTED_ACTUAL", response.openingLiquidity().source());
        assertEquals("105000.00", response.openingLiquidity().amount());
        assertEquals("80000.00", response.baseline().closingProjectedLiquidity());
        assertFalse(response.baseline().hasDeficit());
        assertEquals("0.00", response.baseline().peakFundingRequirement());
        assertNull(response.alternative());
        assertNull(response.comparison());
    }

    @Test
    @DisplayName("LEDGER_CASH throws validation exception when no PKR account exists")
    void ledgerCashNoMatchingCurrency() {
        LocalDate asOf = LocalDate.of(2026, 11, 1);
        List<CurrencyCashSummary> summaries = List.of(
                new CurrencyCashSummary("USD", 2, "5000.00", "0.00", "5000.00", 1)
        );
        CashPositionResult cashResult = new CashPositionResult(
                asOf,
                summaries,
                List.of(),
                1
        );
        FinancialQueryEnvelope<CashPositionResult> envelope = FinancialQueryEnvelope.available(
                "getCashPosition",
                "1.0.0",
                businessId,
                asOf.toString(),
                DataCompleteness.COMPLETE,
                List.of(),
                List.of(),
                cashResult
        );

        when(financialQuery.getCashPosition(eq(context), any(CashPositionQuery.class)))
                .thenReturn(envelope);

        FundingScenarioCompareRequest request = new FundingScenarioCompareRequest(
                "PKR",
                asOf.toString(),
                30,
                new OpeningLiquidityRequest(OpeningLiquiditySource.LEDGER_CASH, null, null),
                new ScenarioDefinition("Base", "Desc", List.of()),
                null
        );

        FundingScenarioValidationException ex = assertThrows(FundingScenarioValidationException.class,
                () -> service.compareScenarios(context, request));
        assertTrue(ex.getErrors().containsKey("openingLiquidity.source"));
        assertTrue(ex.getErrors().get("openingLiquidity.source").contains("No authoritative ledger cash accounts found"));
    }

    @Test
    @DisplayName("LEDGER_CASH throws validation exception when FinancialQueryEnvelope is unavailable")
    void ledgerCashUnavailableEnvelope() {
        FinancialQueryEnvelope<CashPositionResult> envelope = FinancialQueryEnvelope.unavailable(
                "getCashPosition",
                "1.0.0",
                businessId,
                "Accounting ledger is currently undergoing maintenance"
        );

        when(financialQuery.getCashPosition(eq(context), any(CashPositionQuery.class)))
                .thenReturn(envelope);

        FundingScenarioCompareRequest request = new FundingScenarioCompareRequest(
                "PKR",
                "2026-11-01",
                30,
                new OpeningLiquidityRequest(OpeningLiquiditySource.LEDGER_CASH, null, null),
                new ScenarioDefinition("Base", "Desc", List.of()),
                null
        );

        FundingScenarioValidationException ex = assertThrows(FundingScenarioValidationException.class,
                () -> service.compareScenarios(context, request));
        assertTrue(ex.getErrors().get("openingLiquidity.source").contains("Accounting ledger is currently undergoing maintenance"));
    }

    @Test
    @DisplayName("MANUAL_ASSUMPTION source correctly tags MANUAL_ASSUMPTION and uses user amount")
    void manualAssumptionSuccess() {
        FundingScenarioCompareRequest request = new FundingScenarioCompareRequest(
                "PKR",
                "2026-11-01",
                14,
                new OpeningLiquidityRequest(OpeningLiquiditySource.MANUAL_ASSUMPTION, new BigDecimal("50000.00"), "Seed capital assumption"),
                new ScenarioDefinition("Base", "Desc", List.of()),
                null
        );

        FundingScenarioCompareResponse response = service.compareScenarios(context, request);

        assertNotNull(response);
        assertEquals("MANUAL_ASSUMPTION", response.openingLiquidity().source());
        assertEquals("50000.00", response.openingLiquidity().amount());
        assertEquals("Seed capital assumption", response.openingLiquidity().note());
        assertEquals("50000.00", response.baseline().closingProjectedLiquidity());
    }

    @Test
    @DisplayName("MANUAL_ASSUMPTION rejects negative amount")
    void manualAssumptionRejectsNegative() {
        FundingScenarioCompareRequest request = new FundingScenarioCompareRequest(
                "PKR",
                "2026-11-01",
                14,
                new OpeningLiquidityRequest(OpeningLiquiditySource.MANUAL_ASSUMPTION, new BigDecimal("-1000.00"), null),
                new ScenarioDefinition("Base", "Desc", List.of()),
                null
        );

        FundingScenarioValidationException ex = assertThrows(FundingScenarioValidationException.class,
                () -> service.compareScenarios(context, request));
        assertTrue(ex.getErrors().containsKey("openingLiquidity.manualAmount"));
        assertTrue(ex.getErrors().get("openingLiquidity.manualAmount").contains("must be non-negative"));
    }

    @Test
    @DisplayName("MANUAL_ASSUMPTION rejects null manualAmount")
    void manualAssumptionRejectsNullAmount() {
        FundingScenarioCompareRequest request = new FundingScenarioCompareRequest(
                "PKR",
                "2026-11-01",
                14,
                new OpeningLiquidityRequest(OpeningLiquiditySource.MANUAL_ASSUMPTION, null, null),
                new ScenarioDefinition("Base", "Desc", List.of()),
                null
        );

        FundingScenarioValidationException ex = assertThrows(FundingScenarioValidationException.class,
                () -> service.compareScenarios(context, request));
        assertTrue(ex.getErrors().containsKey("openingLiquidity.manualAmount"));
    }

    @Test
    @DisplayName("MANUAL_ASSUMPTION rejects more than 2 decimal places")
    void manualAssumptionRejectsExcessiveDecimals() {
        FundingScenarioCompareRequest request = new FundingScenarioCompareRequest(
                "PKR",
                "2026-11-01",
                14,
                new OpeningLiquidityRequest(OpeningLiquiditySource.MANUAL_ASSUMPTION, new BigDecimal("50000.125"), null),
                new ScenarioDefinition("Base", "Desc", List.of()),
                null
        );

        FundingScenarioValidationException ex = assertThrows(FundingScenarioValidationException.class,
                () -> service.compareScenarios(context, request));
        assertTrue(ex.getErrors().containsKey("openingLiquidity.manualAmount"));
        assertTrue(ex.getErrors().get("openingLiquidity.manualAmount").contains("at most 2 decimal places"));
    }

    @Test
    @DisplayName("Currency validation rejects non-PKR currencies")
    void rejectsNonPkrCurrency() {
        FundingScenarioCompareRequest request = new FundingScenarioCompareRequest(
                "USD",
                "2026-11-01",
                14,
                new OpeningLiquidityRequest(OpeningLiquiditySource.MANUAL_ASSUMPTION, new BigDecimal("50000.00"), null),
                new ScenarioDefinition("Base", "Desc", List.of()),
                null
        );

        FundingScenarioValidationException ex = assertThrows(FundingScenarioValidationException.class,
                () -> service.compareScenarios(context, request));
        assertTrue(ex.getErrors().containsKey("currency"));
        assertTrue(ex.getErrors().get("currency").contains("Unsupported currency 'USD'"));
    }

    @Test
    @DisplayName("Horizon bounds: rejects < 7 and > 90 days")
    void rejectsOutOfBoundHorizons() {
        FundingScenarioCompareRequest tooShort = new FundingScenarioCompareRequest(
                "PKR", "2026-11-01", 6,
                new OpeningLiquidityRequest(OpeningLiquiditySource.MANUAL_ASSUMPTION, BigDecimal.ZERO, null),
                new ScenarioDefinition("Base", "Desc", List.of()), null
        );
        FundingScenarioValidationException exShort = assertThrows(FundingScenarioValidationException.class,
                () -> service.compareScenarios(context, tooShort));
        assertTrue(exShort.getErrors().containsKey("horizonDays"));

        FundingScenarioCompareRequest tooLong = new FundingScenarioCompareRequest(
                "PKR", "2026-11-01", 91,
                new OpeningLiquidityRequest(OpeningLiquiditySource.MANUAL_ASSUMPTION, BigDecimal.ZERO, null),
                new ScenarioDefinition("Base", "Desc", List.of()), null
        );
        FundingScenarioValidationException exLong = assertThrows(FundingScenarioValidationException.class,
                () -> service.compareScenarios(context, tooLong));
        assertTrue(exLong.getErrors().containsKey("horizonDays"));
    }

    @Test
    @DisplayName("Invalid asOfDate format throws validation exception")
    void rejectsInvalidAsOfDate() {
        FundingScenarioCompareRequest request = new FundingScenarioCompareRequest(
                "PKR", "2026/11/01", 14,
                new OpeningLiquidityRequest(OpeningLiquiditySource.MANUAL_ASSUMPTION, BigDecimal.ZERO, null),
                new ScenarioDefinition("Base", "Desc", List.of()), null
        );

        FundingScenarioValidationException ex = assertThrows(FundingScenarioValidationException.class,
                () -> service.compareScenarios(context, request));
        assertTrue(ex.getErrors().containsKey("asOfDate"));
    }

    @Test
    @DisplayName("Null baseline scenario throws validation exception")
    void rejectsNullBaseline() {
        FundingScenarioCompareRequest request = new FundingScenarioCompareRequest(
                "PKR", "2026-11-01", 14,
                new OpeningLiquidityRequest(OpeningLiquiditySource.MANUAL_ASSUMPTION, BigDecimal.ZERO, null),
                null, null
        );

        FundingScenarioValidationException ex = assertThrows(FundingScenarioValidationException.class,
                () -> service.compareScenarios(context, request));
        assertTrue(ex.getErrors().containsKey("baselineScenario"));
    }

    @Test
    @DisplayName("Evaluates alternative scenario and outputs comparison summary and difference timeline")
    void evaluatesAlternativeScenarioComparison() {
        LocalDate asOf = LocalDate.of(2026, 11, 1);
        FundingScenarioCompareRequest request = new FundingScenarioCompareRequest(
                "PKR",
                asOf.toString(),
                14,
                new OpeningLiquidityRequest(OpeningLiquiditySource.MANUAL_ASSUMPTION, new BigDecimal("10000.00"), null),
                new ScenarioDefinition("Baseline", "Baseline plan", List.of(
                        new PlannedCashEventInput("b1", asOf.plusDays(1).toString(), CashFlowDirection.INFLOW, new BigDecimal("5000.00"), "Deposit", "REVENUE")
                )),
                new ScenarioDefinition("Alternative", "Alternative plan", List.of(
                        new PlannedCashEventInput("a1", asOf.plusDays(1).toString(), CashFlowDirection.INFLOW, new BigDecimal("15000.00"), "Larger Deposit", "REVENUE")
                ))
        );

        FundingScenarioCompareResponse response = service.compareScenarios(context, request);

        assertNotNull(response.alternative());
        assertNotNull(response.comparison());
        assertEquals("Baseline", response.comparison().baselineScenarioName());
        assertEquals("Alternative", response.comparison().alternativeScenarioName());
        assertEquals("10000.00", response.comparison().closingLiquidityDifference());
        assertEquals(15, response.comparison().dailyDifferences().size()); // day 0 through 14
    }

    @Test
    @DisplayName("Emits warnings when deficit occurs and when scenario does not recover")
    void emitsDeficitAndNonRecoveryWarnings() {
        LocalDate asOf = LocalDate.of(2026, 11, 1);
        FundingScenarioCompareRequest request = new FundingScenarioCompareRequest(
                "PKR",
                asOf.toString(),
                14,
                new OpeningLiquidityRequest(OpeningLiquiditySource.MANUAL_ASSUMPTION, BigDecimal.ZERO, null),
                new ScenarioDefinition("Deficit Plan", "Plan with gap", List.of(
                        new PlannedCashEventInput("d1", asOf.plusDays(2).toString(), CashFlowDirection.OUTFLOW, new BigDecimal("50000.00"), "Capex", "CAPEX")
                )),
                null
        );

        FundingScenarioCompareResponse response = service.compareScenarios(context, request);

        assertTrue(response.baseline().hasDeficit());
        assertFalse(response.baseline().isRecovered());
        assertTrue(response.warnings().stream().anyMatch(w -> w.contains("Funding gap detected")));
        assertTrue(response.warnings().stream().anyMatch(w -> w.contains("does not recover")));
    }
}
