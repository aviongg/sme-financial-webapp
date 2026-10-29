package com.app.sme_health_backend.risk.service;

import com.app.sme_health_backend.accounting.draft.DraftStatus;
import com.app.sme_health_backend.accounting.draft.RecordDraft;
import com.app.sme_health_backend.accounting.draft.RecordDraftRepository;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.AccountMovementRepository;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.counterparty.CounterpartyStatus;
import com.app.sme_health_backend.counterparty.CounterpartyType;
import com.app.sme_health_backend.forecast.dto.DailyCashForecastResponse;
import com.app.sme_health_backend.forecast.dto.DailyForecastRow;
import com.app.sme_health_backend.forecast.service.DailyCashForecastService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.obligation.*;
import com.app.sme_health_backend.obligation.aging.*;
import com.app.sme_health_backend.reconciliation.model.StatementEntryStatus;
import com.app.sme_health_backend.reconciliation.repository.BankStatementEntryRepository;
import com.app.sme_health_backend.recurring.entity.RecurringRule;
import com.app.sme_health_backend.recurring.model.RecurringFrequency;
import com.app.sme_health_backend.recurring.model.RecurringRuleStatus;
import com.app.sme_health_backend.recurring.repository.RecurringRuleOccurrenceRepository;
import com.app.sme_health_backend.recurring.repository.RecurringRuleRepository;
import com.app.sme_health_backend.risk.dto.FinancialRiskInsight;
import com.app.sme_health_backend.risk.dto.RiskAssessmentResponse;
import com.app.sme_health_backend.risk.exception.RiskValidationException;
import com.app.sme_health_backend.risk.model.RiskCategory;
import com.app.sme_health_backend.risk.model.RiskSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FinancialRiskAssessmentService Unit Tests (A-W6-FINANCIAL-RISK-INTELLIGENCE-V1)")
class FinancialRiskAssessmentServiceTest {

    @Mock
    private DailyCashForecastService forecastService;
    @Mock
    private FinancialAccountRepository accountRepository;
    @Mock
    private AccountMovementRepository movementRepository;
    @Mock
    private FinancialObligationService obligationService;
    @Mock
    private FinancialObligationRepository obligationRepository;
    @Mock
    private ObligationPaymentScheduleRepository scheduleRepository;
    @Mock
    private ObligationPaymentAllocationRepository allocationRepository;
    @Mock
    private CounterpartyRepository counterpartyRepository;
    @Mock
    private RecurringRuleRepository recurringRuleRepository;
    @Mock
    private RecurringRuleOccurrenceRepository recurringOccurrenceRepository;
    @Mock
    private RecordDraftRepository recordDraftRepository;
    @Mock
    private BankStatementEntryRepository statementEntryRepository;
    @Mock
    private BusinessAuthorizationService authorizationService;

    private FinancialRiskAssessmentService riskService;

    private final UUID businessId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private BusinessAccessContext context;
    private final LocalDate evalDate = LocalDate.of(2026, 11, 1);

    @BeforeEach
    void setUp() {
        context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);

        riskService = new FinancialRiskAssessmentService(
                forecastService,
                accountRepository,
                movementRepository,
                obligationService,
                obligationRepository,
                scheduleRepository,
                allocationRepository,
                counterpartyRepository,
                recurringRuleRepository,
                recurringOccurrenceRepository,
                recordDraftRepository,
                statementEntryRepository,
                authorizationService
        );

        when(authorizationService.hasRolePermission(any(), eq(BusinessPermission.FINANCIAL_DATA_READ))).thenReturn(true);
        when(accountRepository.findByBusinessId(businessId)).thenReturn(Collections.emptyList());
        when(movementRepository.findMovementSumsByBusinessIdAndCutoffDate(any(), any())).thenReturn(Collections.emptyList());
        when(obligationRepository.findByBusinessIdAndDirection(any(), any())).thenReturn(Collections.emptyList());
        when(obligationRepository.findByBusinessId(any())).thenReturn(Collections.emptyList());
        when(scheduleRepository.findByBusinessId(any())).thenReturn(Collections.emptyList());
        when(allocationRepository.findByBusinessId(any())).thenReturn(Collections.emptyList());
        when(counterpartyRepository.findAllByBusinessIdAndStatusOrderByDisplayNameAsc(any(), any())).thenReturn(Collections.emptyList());
        when(recurringRuleRepository.findByBusinessIdAndStatus(any(), any())).thenReturn(Collections.emptyList());
        when(recurringOccurrenceRepository.findByBusinessIdAndScheduledDateBetween(any(), any(), any())).thenReturn(Collections.emptyList());
        when(recordDraftRepository.findByBusinessIdAndStatus(any(), any())).thenReturn(Collections.emptyList());

        ObligationAgingResult emptyAging = createEmptyAging(evalDate);
        when(obligationService.getAging(any(), any())).thenReturn(emptyAging);

        DailyCashForecastResponse emptyForecast = new DailyCashForecastResponse(
                "1.0", "PKR", evalDate, 30, evalDate.plusDays(30), null, null, Collections.emptyList(), "Disclaimer"
        );
        when(forecastService.getDailyCashForecast(any(), any(), anyInt(), any())).thenReturn(emptyForecast);
    }

    @Test
    @DisplayName("Fixture A: Forecast liquidity shortage generates CRITICAL deficit insight")
    void fixtureA_forecastLiquidityShortfall_generatesCriticalDeficitInsight() {
        DailyForecastRow deficitRow = new DailyForecastRow(
                evalDate.plusDays(10), 10, "100000.00", "20000.00", "0.00", "20000.00",
                "150000.00", "0.00", "150000.00", "-130000.00", "-30000.00",
                true, "30000.00", Collections.emptyList()
        );

        DailyCashForecastResponse deficitForecast = new DailyCashForecastResponse(
                "1.0", "PKR", evalDate, 30, evalDate.plusDays(30), null, null, List.of(deficitRow), "Disclaimer"
        );
        when(forecastService.getDailyCashForecast(any(), eq("PKR"), anyInt(), any())).thenReturn(deficitForecast);

        RiskAssessmentResponse response = riskService.getAssessment(context, evalDate.toString(), 30, "PKR", null, null);

        assertNotNull(response);
        assertEquals(1, response.summary().criticalCount());
        Optional<FinancialRiskInsight> r01 = response.insights().stream()
                .filter(i -> "R01_PROJECTED_CASH_DEFICIT".equals(i.riskCode()))
                .findFirst();
        assertTrue(r01.isPresent(), "R01 insight must be present");
        assertEquals(RiskSeverity.CRITICAL, r01.get().severity());
        assertEquals(new BigDecimal("30000.00"), r01.get().affectedAmount());
        assertEquals(evalDate.plusDays(10), r01.get().startDate());
    }

    @Test
    @DisplayName("Fixture B: Overdue receivables generates structured aging insight")
    void fixtureB_overdueReceivables_generatesAgedOverdueInsight() {
        Map<AgingBucket, BucketSummary> buckets = new EnumMap<>(AgingBucket.class);
        for (AgingBucket b : AgingBucket.values()) {
            buckets.put(b, new BucketSummary(b, BigDecimal.ZERO, 0));
        }
        buckets.put(AgingBucket.OVERDUE_31_60, new BucketSummary(AgingBucket.OVERDUE_31_60, new BigDecimal("60000.00"), 1));
        buckets.put(AgingBucket.NOT_YET_DUE, new BucketSummary(AgingBucket.NOT_YET_DUE, new BigDecimal("40000.00"), 1));

        DirectionAgingSummary recSummary = new DirectionAgingSummary(
                ObligationDirection.RECEIVABLE, new BigDecimal("100000.00"), new BigDecimal("60000.00"),
                2, 1, buckets, 0, 0, 0
        );
        DirectionAgingSummary paySummary = new DirectionAgingSummary(
                ObligationDirection.PAYABLE, BigDecimal.ZERO, BigDecimal.ZERO,
                0, 0, buckets, 0, 0, 0
        );

        UUID obId = UUID.randomUUID();
        ObligationAgingDetail detail = new ObligationAgingDetail(
                obId.toString(), ObligationDirection.RECEIVABLE, evalDate.minusDays(45), evalDate.minusDays(35),
                new BigDecimal("60000.00"), new BigDecimal("60000.00"), 35, AgingBucket.OVERDUE_31_60, null
        );

        ObligationAgingResult agingResult = new ObligationAgingResult(
                evalDate, "PKR", recSummary, paySummary, List.of(detail)
        );
        when(obligationService.getAging(any(), eq(evalDate.toString()))).thenReturn(agingResult);

        RiskAssessmentResponse response = riskService.getAssessment(context, evalDate.toString(), 30, "PKR", null, null);

        Optional<FinancialRiskInsight> r03 = response.insights().stream()
                .filter(i -> "R03_OVERDUE_RECEIVABLES".equals(i.riskCode()))
                .findFirst();
        assertTrue(r03.isPresent(), "R03 insight must be generated");
        assertEquals(new BigDecimal("60000.00"), r03.get().affectedAmount());
        assertEquals(new BigDecimal("60.00"), r03.get().affectedPercentage());
        assertEquals(RiskSeverity.CRITICAL, r03.get().severity());
    }

    @Test
    @DisplayName("Fixture C: Customer concentration calculates exact percentage and assigns CRITICAL for 75%")
    void fixtureC_customerConcentration_calculatesCorrectPercentageAndSeverity() {
        UUID cpA = UUID.randomUUID();
        UUID cpB = UUID.randomUUID();

        Counterparty counterpartyA = new Counterparty();
        counterpartyA.setBusinessId(businessId);
        counterpartyA.setDisplayName("Customer Alpha");
        counterpartyA.setType(CounterpartyType.CUSTOMER);
        ReflectionTestUtils.setField(counterpartyA, "id", cpA);

        Counterparty counterpartyB = new Counterparty();
        counterpartyB.setBusinessId(businessId);
        counterpartyB.setDisplayName("Customer Beta");
        counterpartyB.setType(CounterpartyType.CUSTOMER);
        ReflectionTestUtils.setField(counterpartyB, "id", cpB);

        when(counterpartyRepository.findAllByBusinessIdAndStatusOrderByDisplayNameAsc(businessId, CounterpartyStatus.ACTIVE))
                .thenReturn(List.of(counterpartyA, counterpartyB));

        UUID obA = UUID.randomUUID();
        UUID obB = UUID.randomUUID();

        FinancialObligation obligationA = new FinancialObligation();
        obligationA.setBusinessId(businessId);
        obligationA.setCounterpartyId(cpA);
        obligationA.setDirection(ObligationDirection.RECEIVABLE);
        obligationA.setOriginalAmount(new BigDecimal("75000.00"));
        obligationA.setCurrency("PKR");
        obligationA.setStatus(ObligationStatus.OPEN);
        ReflectionTestUtils.setField(obligationA, "id", obA);

        FinancialObligation obligationB = new FinancialObligation();
        obligationB.setBusinessId(businessId);
        obligationB.setCounterpartyId(cpB);
        obligationB.setDirection(ObligationDirection.RECEIVABLE);
        obligationB.setOriginalAmount(new BigDecimal("25000.00"));
        obligationB.setCurrency("PKR");
        obligationB.setStatus(ObligationStatus.OPEN);
        ReflectionTestUtils.setField(obligationB, "id", obB);

        when(obligationRepository.findByIdAndBusinessId(obA, businessId)).thenReturn(Optional.of(obligationA));
        when(obligationRepository.findByIdAndBusinessId(obB, businessId)).thenReturn(Optional.of(obligationB));

        ObligationAgingDetail detailA = new ObligationAgingDetail(obA.toString(), ObligationDirection.RECEIVABLE, evalDate.minusDays(10), evalDate.plusDays(20), new BigDecimal("75000.00"), new BigDecimal("75000.00"), null, AgingBucket.NOT_YET_DUE, null);
        ObligationAgingDetail detailB = new ObligationAgingDetail(obB.toString(), ObligationDirection.RECEIVABLE, evalDate.minusDays(10), evalDate.plusDays(20), new BigDecimal("25000.00"), new BigDecimal("25000.00"), null, AgingBucket.NOT_YET_DUE, null);

        Map<AgingBucket, BucketSummary> buckets = new EnumMap<>(AgingBucket.class);
        for (AgingBucket b : AgingBucket.values()) buckets.put(b, new BucketSummary(b, BigDecimal.ZERO, 0));
        buckets.put(AgingBucket.NOT_YET_DUE, new BucketSummary(AgingBucket.NOT_YET_DUE, new BigDecimal("100000.00"), 2));

        DirectionAgingSummary recSummary = new DirectionAgingSummary(
                ObligationDirection.RECEIVABLE, new BigDecimal("100000.00"), BigDecimal.ZERO, 2, 0, buckets, 0, 0, 0
        );
        DirectionAgingSummary paySummary = new DirectionAgingSummary(
                ObligationDirection.PAYABLE, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, buckets, 0, 0, 0
        );

        ObligationAgingResult agingResult = new ObligationAgingResult(
                evalDate, "PKR", recSummary, paySummary, List.of(detailA, detailB)
        );
        when(obligationService.getAging(any(), eq(evalDate.toString()))).thenReturn(agingResult);

        RiskAssessmentResponse response = riskService.getAssessment(context, evalDate.toString(), 30, "PKR", null, null);

        Optional<FinancialRiskInsight> r04 = response.insights().stream()
                .filter(i -> "R04_CUSTOMER_CONCENTRATION".equals(i.riskCode()))
                .findFirst();
        assertTrue(r04.isPresent(), "R04 customer concentration insight must be generated");
        assertEquals(new BigDecimal("75.00"), r04.get().affectedPercentage());
        assertEquals(new BigDecimal("75000.00"), r04.get().affectedAmount());
        assertEquals(RiskSeverity.CRITICAL, r04.get().severity());
        assertTrue(r04.get().title().contains("Customer Alpha"));
    }

    @Test
    @DisplayName("Fixture C (zero): Total receivables zero does not generate customer concentration warning")
    void fixtureC_zeroReceivables_doesNotGenerateConcentrationWarning() {
        ObligationAgingResult emptyAging = createEmptyAging(evalDate);
        when(obligationService.getAging(any(), any())).thenReturn(emptyAging);

        RiskAssessmentResponse response = riskService.getAssessment(context, evalDate.toString(), 30, "PKR", null, null);

        boolean hasR04 = response.insights().stream().anyMatch(i -> "R04_CUSTOMER_CONCENTRATION".equals(i.riskCode()));
        assertFalse(hasR04, "No R04 insight should be generated when total receivables is 0");
    }

    @Test
    @DisplayName("Fixture D: Payment pressure in 7 days generates CRITICAL insight with coverage gap")
    void fixtureD_paymentPressure_7DayGap_generatesCriticalInsight() {
        FinancialAccount account = new FinancialAccount(businessId, "Main Cash", AccountType.CASH, "PKR", 2, new BigDecimal("30000.00"), evalDate.minusDays(10));
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        when(accountRepository.findByBusinessId(businessId)).thenReturn(List.of(account));

        UUID payableId = UUID.randomUUID();
        FinancialObligation payable = new FinancialObligation();
        payable.setBusinessId(businessId);
        payable.setDirection(ObligationDirection.PAYABLE);
        payable.setCurrency("PKR");
        payable.setOriginalAmount(new BigDecimal("75000.00"));
        payable.setDueDate(evalDate.plusDays(4));
        payable.setStatus(ObligationStatus.OPEN);
        ReflectionTestUtils.setField(payable, "id", payableId);

        when(obligationRepository.findByBusinessIdAndDirection(businessId, ObligationDirection.PAYABLE))
                .thenReturn(List.of(payable));

        RiskAssessmentResponse response = riskService.getAssessment(context, evalDate.toString(), 30, "PKR", null, null);

        Optional<FinancialRiskInsight> r02 = response.insights().stream()
                .filter(i -> "R02_UPCOMING_PAYMENT_PRESSURE".equals(i.riskCode()))
                .findFirst();
        assertTrue(r02.isPresent(), "R02 insight must be generated");
        assertEquals(RiskSeverity.CRITICAL, r02.get().severity());
        assertEquals(new BigDecimal("45000.00"), r02.get().affectedAmount());
    }

    @Test
    @DisplayName("Fixture D (covered): Payments covered by cash do not trigger payment pressure")
    void fixtureD_paymentPressure_coveredByCash_doesNotGeneratePressureInsight() {
        FinancialAccount account = new FinancialAccount(businessId, "Main Cash", AccountType.CASH, "PKR", 2, new BigDecimal("100000.00"), evalDate.minusDays(10));
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        when(accountRepository.findByBusinessId(businessId)).thenReturn(List.of(account));

        UUID payableId = UUID.randomUUID();
        FinancialObligation payable = new FinancialObligation();
        payable.setBusinessId(businessId);
        payable.setDirection(ObligationDirection.PAYABLE);
        payable.setCurrency("PKR");
        payable.setOriginalAmount(new BigDecimal("20000.00"));
        payable.setDueDate(evalDate.plusDays(4));
        payable.setStatus(ObligationStatus.OPEN);
        ReflectionTestUtils.setField(payable, "id", payableId);

        when(obligationRepository.findByBusinessIdAndDirection(businessId, ObligationDirection.PAYABLE))
                .thenReturn(List.of(payable));

        RiskAssessmentResponse response = riskService.getAssessment(context, evalDate.toString(), 30, "PKR", null, null);

        boolean hasR02 = response.insights().stream().anyMatch(i -> "R02_UPCOMING_PAYMENT_PRESSURE".equals(i.riskCode()));
        assertFalse(hasR02, "No payment pressure should be generated when cash comfortably covers payables");
    }

    @Test
    @DisplayName("Fixture E: Recurring commitments include active rent and exclude paused subscription")
    void fixtureE_recurringCommitments_includesActiveExcludesPaused() {
        FinancialAccount account = new FinancialAccount(businessId, "Main Cash", AccountType.CASH, "PKR", 2, new BigDecimal("15000.00"), evalDate.minusDays(10));
        UUID accId = UUID.randomUUID();
        ReflectionTestUtils.setField(account, "id", accId);
        when(accountRepository.findByBusinessId(businessId)).thenReturn(List.of(account));

        RecurringRule activeRent = new RecurringRule();
        activeRent.setBusinessId(businessId);
        activeRent.setName("Office Rent");
        activeRent.setEventType(TransactionEventType.OPERATING_EXPENSE_PAYMENT);
        activeRent.setAccountId(accId);
        activeRent.setAmount(new BigDecimal("20000.00"));
        activeRent.setCurrency("PKR");
        activeRent.setFrequency(RecurringFrequency.MONTHLY);
        activeRent.setAnchorDayOfMonth(5);
        activeRent.setAnchorMonth(11);
        activeRent.setStatus(RecurringRuleStatus.ACTIVE);
        activeRent.setNextDueDate(evalDate.plusDays(4));
        ReflectionTestUtils.setField(activeRent, "id", UUID.randomUUID());

        RecurringRule pausedSub = new RecurringRule();
        pausedSub.setBusinessId(businessId);
        pausedSub.setName("Software Subscription");
        pausedSub.setEventType(TransactionEventType.OPERATING_EXPENSE_PAYMENT);
        pausedSub.setAccountId(accId);
        pausedSub.setAmount(new BigDecimal("5000.00"));
        pausedSub.setCurrency("PKR");
        pausedSub.setFrequency(RecurringFrequency.MONTHLY);
        pausedSub.setAnchorDayOfMonth(10);
        pausedSub.setAnchorMonth(11);
        pausedSub.setStatus(RecurringRuleStatus.PAUSED);
        pausedSub.setNextDueDate(evalDate.plusDays(9));
        ReflectionTestUtils.setField(pausedSub, "id", UUID.randomUUID());

        // Repository returns only ACTIVE rules
        when(recurringRuleRepository.findByBusinessIdAndStatus(businessId, RecurringRuleStatus.ACTIVE))
                .thenReturn(List.of(activeRent));

        RiskAssessmentResponse response = riskService.getAssessment(context, evalDate.toString(), 30, "PKR", null, null);

        Optional<FinancialRiskInsight> r05 = response.insights().stream()
                .filter(i -> "R05_RECURRING_CASH_PRESSURE".equals(i.riskCode()))
                .findFirst();
        assertTrue(r05.isPresent(), "R05 recurring pressure insight must be generated");
        assertEquals(new BigDecimal("20000.00"), r05.get().affectedAmount());
        assertEquals(RiskSeverity.HIGH, r05.get().severity());
    }

    @Test
    @DisplayName("Fixture F: Currency separation evaluates requested currency only")
    void fixtureF_currencySeparation_evaluatesRequestedCurrencyOnly() {
        FinancialAccount pkrAccount = new FinancialAccount(businessId, "PKR Cash", AccountType.CASH, "PKR", 2, new BigDecimal("50000.00"), evalDate.minusDays(10));
        ReflectionTestUtils.setField(pkrAccount, "id", UUID.randomUUID());
        FinancialAccount usdAccount = new FinancialAccount(businessId, "USD Account", AccountType.BANK, "USD", 2, new BigDecimal("-1000.00"), evalDate.minusDays(10));
        ReflectionTestUtils.setField(usdAccount, "id", UUID.randomUUID());

        when(accountRepository.findByBusinessId(businessId)).thenReturn(List.of(pkrAccount, usdAccount));

        // When evaluating PKR, the negative USD account must NOT appear
        RiskAssessmentResponse pkrResponse = riskService.getAssessment(context, evalDate.toString(), 30, "PKR", null, null);
        boolean pkrHasNegativeCash = pkrResponse.insights().stream().anyMatch(i -> "R06_NEGATIVE_ACTUAL_CASH".equals(i.riskCode()));
        assertFalse(pkrHasNegativeCash, "Negative USD account should not appear in PKR evaluation");

        // When evaluating USD, the negative USD account MUST appear
        DailyCashForecastResponse usdForecast = new DailyCashForecastResponse(
                "1.0", "USD", evalDate, 30, evalDate.plusDays(30), null, null, Collections.emptyList(), "Disclaimer"
        );
        when(forecastService.getDailyCashForecast(any(), eq("USD"), anyInt(), any())).thenReturn(usdForecast);

        RiskAssessmentResponse usdResponse = riskService.getAssessment(context, evalDate.toString(), 30, "USD", null, null);
        boolean usdHasNegativeCash = usdResponse.insights().stream().anyMatch(i -> "R06_NEGATIVE_ACTUAL_CASH".equals(i.riskCode()));
        assertTrue(usdHasNegativeCash, "Negative USD account must appear in USD evaluation");
    }

    @Test
    @DisplayName("Fixture G: Incomplete data generates explicit completeness warnings, not false LOW RISK")
    void fixtureG_incompleteData_marksDataIntegrityLimited_notLowRisk() {
        FinancialObligation unscheduled = new FinancialObligation();
        unscheduled.setBusinessId(businessId);
        unscheduled.setDirection(ObligationDirection.PAYABLE);
        unscheduled.setCurrency("PKR");
        unscheduled.setOriginalAmount(new BigDecimal("10000.00"));
        unscheduled.setStatus(ObligationStatus.OPEN);
        unscheduled.setDueDate(null);
        ReflectionTestUtils.setField(unscheduled, "id", UUID.randomUUID());

        RecordDraft draft = new RecordDraft();
        draft.setBusinessId(businessId);
        draft.setStatus(DraftStatus.DRAFT);
        draft.setAmountInput("5000.00");
        draft.setDescription("Unreviewed invoice draft");
        ReflectionTestUtils.setField(draft, "id", UUID.randomUUID());

        when(obligationRepository.findByBusinessId(businessId)).thenReturn(List.of(unscheduled));
        when(recordDraftRepository.findByBusinessIdAndStatus(businessId, DraftStatus.DRAFT)).thenReturn(List.of(draft));

        RiskAssessmentResponse response = riskService.getAssessment(context, evalDate.toString(), 30, "PKR", null, null);

        assertTrue(response.summary().hasDataCompletenessIssues(), "Summary must indicate completeness issues");
        Optional<FinancialRiskInsight> r07 = response.insights().stream()
                .filter(i -> "R07_DATA_COMPLETENESS".equals(i.riskCode()))
                .findFirst();
        assertTrue(r07.isPresent(), "R07 data completeness insight must exist");
        assertEquals(RiskSeverity.HIGH, r07.get().severity());
    }

    @Test
    @DisplayName("Validation: Invalid parameters throw RiskValidationException")
    void validation_invalidParameters_throwRiskValidationException() {
        assertThrows(RiskValidationException.class, () ->
                riskService.getAssessment(context, "invalid-date", 30, "PKR", null, null));

        assertThrows(RiskValidationException.class, () ->
                riskService.getAssessment(context, evalDate.toString(), 0, "PKR", null, null));

        assertThrows(RiskValidationException.class, () ->
                riskService.getAssessment(context, evalDate.toString(), 400, "PKR", null, null));

        assertThrows(RiskValidationException.class, () ->
                riskService.getAssessment(context, evalDate.toString(), 30, "TOOLONG", null, null));

        assertThrows(RiskValidationException.class, () ->
                riskService.getAssessment(context, evalDate.toString(), 30, "PKR", "NON_EXISTENT_CAT", null));

        assertThrows(RiskValidationException.class, () ->
                riskService.getAssessment(context, evalDate.toString(), 30, "PKR", null, "NON_EXISTENT_SEV"));
    }

    @Test
    @DisplayName("Authorization: Missing permission throws AccessDeniedException")
    void authorization_missingPermission_throwsAccessDeniedException() {
        when(authorizationService.hasRolePermission(eq(MembershipRole.OWNER), eq(BusinessPermission.FINANCIAL_DATA_READ)))
                .thenReturn(false);

        assertThrows(AccessDeniedException.class, () ->
                riskService.getAssessment(context, evalDate.toString(), 30, "PKR", null, null));
    }

    private ObligationAgingResult createEmptyAging(LocalDate asOfDate) {
        Map<AgingBucket, BucketSummary> buckets = new EnumMap<>(AgingBucket.class);
        for (AgingBucket b : AgingBucket.values()) {
            buckets.put(b, new BucketSummary(b, BigDecimal.ZERO, 0));
        }
        DirectionAgingSummary rec = new DirectionAgingSummary(
                ObligationDirection.RECEIVABLE, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, buckets, 0, 0, 0
        );
        DirectionAgingSummary pay = new DirectionAgingSummary(
                ObligationDirection.PAYABLE, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, buckets, 0, 0, 0
        );
        return new ObligationAgingResult(asOfDate, "PKR", rec, pay, Collections.emptyList());
    }
}
