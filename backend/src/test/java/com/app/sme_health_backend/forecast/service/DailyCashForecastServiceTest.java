package com.app.sme_health_backend.forecast.service;

import com.app.sme_health_backend.accounting.draft.DraftStatus;
import com.app.sme_health_backend.accounting.draft.RecordDraft;
import com.app.sme_health_backend.accounting.draft.RecordDraftRepository;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.AccountMovementRepository;
import com.app.sme_health_backend.accounting.repository.AccountMovementSum;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.counterparty.CounterpartyType;
import com.app.sme_health_backend.forecast.dto.*;
import com.app.sme_health_backend.forecast.exception.ForecastValidationException;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.obligation.*;
import com.app.sme_health_backend.obligation.aging.ObligationDirection;
import com.app.sme_health_backend.recurring.entity.RecurringRule;
import com.app.sme_health_backend.recurring.entity.RecurringRuleOccurrence;
import com.app.sme_health_backend.recurring.model.RecurringFrequency;
import com.app.sme_health_backend.recurring.model.RecurringOccurrenceStatus;
import com.app.sme_health_backend.recurring.model.RecurringRuleStatus;
import com.app.sme_health_backend.recurring.repository.RecurringRuleOccurrenceRepository;
import com.app.sme_health_backend.recurring.repository.RecurringRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Daily Cash Forecast Service Unit Tests (A-W6-ADVANCED-CASH-FORECAST-V1)")
class DailyCashForecastServiceTest {

    @Mock private FinancialAccountRepository accountRepository;
    @Mock private AccountMovementRepository movementRepository;
    @Mock private FinancialObligationRepository obligationRepository;
    @Mock private ObligationPaymentAllocationRepository allocationRepository;
    @Mock private ObligationPaymentScheduleRepository scheduleRepository;
    @Mock private CounterpartyRepository counterpartyRepository;
    @Mock private RecurringRuleRepository recurringRuleRepository;
    @Mock private RecurringRuleOccurrenceRepository recurringOccurrenceRepository;
    @Mock private RecordDraftRepository recordDraftRepository;
    @Mock private BusinessAuthorizationService authorizationService;

    private DailyCashForecastService forecastService;

    private final UUID businessId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);

    private final LocalDate evalDate = LocalDate.of(2026, 11, 1);

    @BeforeEach
    void setUp() {
        forecastService = new DailyCashForecastService(
                accountRepository,
                movementRepository,
                obligationRepository,
                allocationRepository,
                scheduleRepository,
                counterpartyRepository,
                recurringRuleRepository,
                recurringOccurrenceRepository,
                recordDraftRepository,
                authorizationService
        );

        lenient().when(authorizationService.hasRolePermission(eq(MembershipRole.OWNER), any())).thenReturn(true);
        lenient().when(movementRepository.findMovementSumsByBusinessIdAndCutoffDate(any(), any())).thenReturn(Collections.emptyList());
        lenient().when(obligationRepository.findByBusinessId(any())).thenReturn(Collections.emptyList());
        lenient().when(allocationRepository.findByBusinessId(any())).thenReturn(Collections.emptyList());
        lenient().when(scheduleRepository.findByBusinessId(any())).thenReturn(Collections.emptyList());
        lenient().when(recurringRuleRepository.findByBusinessIdAndStatus(any(), any())).thenReturn(Collections.emptyList());
        lenient().when(recurringOccurrenceRepository.findByBusinessId(any())).thenReturn(Collections.emptyList());
        lenient().when(recurringOccurrenceRepository.findByBusinessIdAndScheduledDateBetween(any(), any(), any())).thenReturn(Collections.emptyList());
        lenient().when(recordDraftRepository.findByBusinessIdAndStatus(any(), any())).thenReturn(Collections.emptyList());
        lenient().when(counterpartyRepository.findAllById(any())).thenReturn(Collections.emptyList());
    }

    private FinancialAccount createAccount(UUID id, String name, String currency, BigDecimal initialBalance, LocalDate balanceDate) {
        FinancialAccount acc = new FinancialAccount(businessId, name, AccountType.BANK, currency, 2, initialBalance, balanceDate);
        acc.setId(id);
        acc.setStatus(AccountStatus.ACTIVE);
        return acc;
    }

    private Counterparty createCounterparty(UUID id, String name) {
        Counterparty cp = new Counterparty(businessId, name, CounterpartyType.CUSTOMER, null, null, null, null, null);
        cp.setId(id);
        return cp;
    }

    private FinancialObligation createObligation(UUID id, UUID cpId, ObligationDirection dir, BigDecimal amount, LocalDate dueDate, String ref) {
        FinancialObligation ob = new FinancialObligation(
                businessId, cpId, dir, ObligationSourceType.CUSTOMER_INVOICE, "SRC-1", ref, dueDate.minusDays(5), dueDate, amount, "PKR", "Description", userId
        );
        ob.setId(id);
        ob.setStatus(ObligationStatus.OPEN);
        return ob;
    }

    private ObligationPaymentSchedule createSchedule(UUID id, UUID obId, int instNum, LocalDate date, BigDecimal amount) {
        ObligationPaymentSchedule s = new ObligationPaymentSchedule(
                businessId, obId, instNum, date, amount, "Note " + instNum, userId
        );
        s.setId(id);
        s.setStatus(ScheduleStatus.PLANNED);
        return s;
    }

    @Test
    @DisplayName("Fixture A: Baseline cash forecast reconciles 100k opening, 40k recv, 25k payable installment, 10k recurring expense to 105k closing")
    void testFixtureA_BaselineCashForecast() {
        UUID accId = UUID.randomUUID();
        FinancialAccount acc = createAccount(accId, "HBL Main", "PKR", new BigDecimal("100000.00"), evalDate);
        when(accountRepository.findByBusinessId(businessId)).thenReturn(List.of(acc));

        UUID cpCustId = UUID.randomUUID();
        Counterparty cust = createCounterparty(cpCustId, "Alpha Traders");
        UUID cpSuppId = UUID.randomUUID();
        Counterparty supp = createCounterparty(cpSuppId, "Beta Supplies");
        when(counterpartyRepository.findAllById(any())).thenReturn(List.of(cust, supp));

        // 1. Customer receivable: 40k on Nov 10
        UUID obRecvId = UUID.randomUUID();
        FinancialObligation obRecv = createObligation(obRecvId, cpCustId, ObligationDirection.RECEIVABLE, new BigDecimal("40000.00"), evalDate.plusDays(9), "INV-001");

        // 2. Supplier payable: 25k installment on Nov 15
        UUID obPayId = UUID.randomUUID();
        FinancialObligation obPay = createObligation(obPayId, cpSuppId, ObligationDirection.PAYABLE, new BigDecimal("25000.00"), evalDate.plusDays(20), "BILL-001");
        ObligationPaymentSchedule sPay = createSchedule(UUID.randomUUID(), obPayId, 1, evalDate.plusDays(14), new BigDecimal("25000.00"));

        when(obligationRepository.findByBusinessId(businessId)).thenReturn(List.of(obRecv, obPay));
        when(scheduleRepository.findByBusinessId(businessId)).thenReturn(List.of(sPay));

        // 3. Recurring operating expense: 10k on Nov 20
        RecurringRule rentRule = new RecurringRule();
        rentRule.setId(UUID.randomUUID());
        rentRule.setBusinessId(businessId);
        rentRule.setName("Office Rent");
        rentRule.setEventType(TransactionEventType.OPERATING_EXPENSE_PAYMENT);
        rentRule.setAccountId(accId);
        rentRule.setAmount(new BigDecimal("10000.00"));
        rentRule.setFrequency(RecurringFrequency.MONTHLY);
        rentRule.setAnchorDayOfMonth(20);
        rentRule.setAnchorMonth(1);
        rentRule.setStartDate(evalDate);
        rentRule.setNextDueDate(evalDate.plusDays(19));
        rentRule.setStatus(RecurringRuleStatus.ACTIVE);

        when(recurringRuleRepository.findByBusinessIdAndStatus(businessId, RecurringRuleStatus.ACTIVE))
                .thenReturn(List.of(rentRule));

        DailyCashForecastResponse res = forecastService.getDailyCashForecast(context, "PKR", 30, evalDate.toString());

        assertNotNull(res);
        assertEquals("PKR", res.currency());
        assertEquals("100000.00", res.summary().startingCash());
        assertEquals("105000.00", res.summary().endingProjectedCash());
        assertEquals("40000.00", res.summary().totalExpectedInflows());
        assertEquals("35000.00", res.summary().totalExpectedOutflows());
        assertEquals("5000.00", res.summary().netProjectedMovement());
        assertFalse(res.dailyRows().stream().anyMatch(DailyForecastRow::isDeficit));
    }

    @Test
    @DisplayName("Fixture B: Delayed collection shifts receipt beyond horizon, reducing horizon-end closing cash by 40k")
    void testFixtureB_DelayedCollectionScenario() {
        UUID accId = UUID.randomUUID();
        FinancialAccount acc = createAccount(accId, "HBL Main", "PKR", new BigDecimal("100000.00"), evalDate);
        when(accountRepository.findByBusinessId(businessId)).thenReturn(List.of(acc));

        UUID cpCustId = UUID.randomUUID();
        Counterparty cust = createCounterparty(cpCustId, "Alpha Traders");
        when(counterpartyRepository.findAllById(any())).thenReturn(List.of(cust));

        // Customer receivable: 40k on Nov 15 (day 14)
        UUID obRecvId = UUID.randomUUID();
        FinancialObligation obRecv = createObligation(obRecvId, cpCustId, ObligationDirection.RECEIVABLE, new BigDecimal("40000.00"), evalDate.plusDays(14), "INV-001");
        when(obligationRepository.findByBusinessId(businessId)).thenReturn(List.of(obRecv));

        // Delay collections by 20 days: Nov 15 + 20 days = Dec 5 (day 34), which is beyond 30-day horizon (Nov 1..Dec 1)
        ForecastScenarioRequest scenarioReq = new ForecastScenarioRequest(
                "PKR", 30, evalDate.toString(), 20, new BigDecimal("100.00"), 0, false, 1
        );

        ForecastScenarioResponse res = forecastService.previewScenario(context, scenarioReq);

        assertNotNull(res);
        assertEquals("140000.00", res.baseline().summary().endingProjectedCash());
        assertEquals("100000.00", res.scenario().summary().endingProjectedCash());
        assertEquals("-40000.00", res.comparison().endingCashVariance());
    }

    @Test
    @DisplayName("Fixture C: Reduced collection of 50% models receipt of 20k on 40k receivable with exact decimal math")
    void testFixtureC_ReducedCollectionScenario() {
        UUID accId = UUID.randomUUID();
        FinancialAccount acc = createAccount(accId, "HBL Main", "PKR", new BigDecimal("100000.00"), evalDate);
        when(accountRepository.findByBusinessId(businessId)).thenReturn(List.of(acc));

        UUID cpCustId = UUID.randomUUID();
        Counterparty cust = createCounterparty(cpCustId, "Alpha Traders");
        when(counterpartyRepository.findAllById(any())).thenReturn(List.of(cust));

        UUID obRecvId = UUID.randomUUID();
        FinancialObligation obRecv = createObligation(obRecvId, cpCustId, ObligationDirection.RECEIVABLE, new BigDecimal("40000.00"), evalDate.plusDays(10), "INV-001");
        when(obligationRepository.findByBusinessId(businessId)).thenReturn(List.of(obRecv));

        ForecastScenarioRequest scenarioReq = new ForecastScenarioRequest(
                "PKR", 30, evalDate.toString(), 0, new BigDecimal("50.00"), 0, false, 1
        );

        ForecastScenarioResponse res = forecastService.previewScenario(context, scenarioReq);

        assertNotNull(res);
        assertEquals("40000.00", res.baseline().summary().totalExpectedInflows());
        assertEquals("20000.00", res.scenario().summary().totalExpectedInflows());
        assertEquals("-20000.00", res.comparison().endingCashVariance());
        assertEquals("120000.00", res.scenario().summary().endingProjectedCash());
    }

    @Test
    @DisplayName("Fixture D: Partially paid installment (50k obligation, 2x 25k installments, 20k paid) models remaining 30k without parent double-counting")
    void testFixtureD_PartiallyPaidInstallmentAndParentDeduplication() {
        UUID accId = UUID.randomUUID();
        FinancialAccount acc = createAccount(accId, "HBL Main", "PKR", new BigDecimal("100000.00"), evalDate);
        when(accountRepository.findByBusinessId(businessId)).thenReturn(List.of(acc));

        UUID cpSuppId = UUID.randomUUID();
        Counterparty supp = createCounterparty(cpSuppId, "Beta Supplies");
        when(counterpartyRepository.findAllById(any())).thenReturn(List.of(supp));

        UUID obPayId = UUID.randomUUID();
        FinancialObligation obPay = createObligation(obPayId, cpSuppId, ObligationDirection.PAYABLE, new BigDecimal("50000.00"), evalDate.plusDays(25), "BILL-002");

        // Two planned installments: 25k on Nov 10, 25k on Nov 20
        ObligationPaymentSchedule s1 = createSchedule(UUID.randomUUID(), obPayId, 1, evalDate.plusDays(9), new BigDecimal("25000.00"));
        ObligationPaymentSchedule s2 = createSchedule(UUID.randomUUID(), obPayId, 2, evalDate.plusDays(19), new BigDecimal("25000.00"));

        // Effective allocation: 20k paid against obligation
        ObligationPaymentAllocation alloc = new ObligationPaymentAllocation(
                businessId, obPayId, UUID.randomUUID(), new BigDecimal("20000.00"), evalDate.minusDays(5), userId
        );
        alloc.setStatus(AllocationStatus.EFFECTIVE);

        when(obligationRepository.findByBusinessId(businessId)).thenReturn(List.of(obPay));
        when(allocationRepository.findByBusinessId(businessId)).thenReturn(List.of(alloc));
        when(scheduleRepository.findByBusinessId(businessId)).thenReturn(List.of(s1, s2));

        DailyCashForecastResponse res = forecastService.getDailyCashForecast(context, "PKR", 30, evalDate.toString());

        assertNotNull(res);
        // Installment 1 absorbs 20k -> remaining 5k. Installment 2 -> 25k. Total planned = 30k.
        // Residual = 30k outstanding - 30k planned = 0k.
        // Total expected outflows must be exactly 30k, NOT 50k and NOT 60k (no parent double-counting).
        assertEquals("30000.00", res.summary().totalExpectedOutflows());
        assertEquals("70000.00", res.summary().endingProjectedCash());
    }

    @Test
    @DisplayName("Fixture E: Active recurring rules deduplicate with generated occurrences and exclude paused/archived rules")
    void testFixtureE_RecurringRuleDeduplicationAndStateFiltering() {
        UUID accId = UUID.randomUUID();
        FinancialAccount acc = createAccount(accId, "HBL Main", "PKR", new BigDecimal("100000.00"), evalDate);
        when(accountRepository.findByBusinessId(businessId)).thenReturn(List.of(acc));

        UUID ruleId = UUID.randomUUID();
        RecurringRule activeRule = new RecurringRule();
        activeRule.setId(ruleId);
        activeRule.setBusinessId(businessId);
        activeRule.setName("Biweekly Cleaning");
        activeRule.setEventType(TransactionEventType.OPERATING_EXPENSE_PAYMENT);
        activeRule.setAccountId(accId);
        activeRule.setAmount(new BigDecimal("5000.00"));
        activeRule.setFrequency(RecurringFrequency.BIWEEKLY);
        activeRule.setAnchorDayOfMonth(5);
        activeRule.setAnchorMonth(1);
        activeRule.setStartDate(evalDate);
        activeRule.setNextDueDate(evalDate.plusDays(4));
        activeRule.setStatus(RecurringRuleStatus.ACTIVE);

        RecurringRule pausedRule = new RecurringRule();
        pausedRule.setId(UUID.randomUUID());
        pausedRule.setBusinessId(businessId);
        pausedRule.setName("Paused Advertising");
        pausedRule.setEventType(TransactionEventType.OPERATING_EXPENSE_PAYMENT);
        pausedRule.setAccountId(accId);
        pausedRule.setAmount(new BigDecimal("8000.00"));
        pausedRule.setFrequency(RecurringFrequency.MONTHLY);
        pausedRule.setAnchorDayOfMonth(11);
        pausedRule.setAnchorMonth(1);
        pausedRule.setStartDate(evalDate);
        pausedRule.setNextDueDate(evalDate.plusDays(10));
        pausedRule.setStatus(RecurringRuleStatus.PAUSED);

        when(recurringRuleRepository.findByBusinessIdAndStatus(businessId, RecurringRuleStatus.ACTIVE))
                .thenReturn(List.of(activeRule));

        // Occurrence already created on Nov 5 (evalDate + 4 days)
        RecurringRuleOccurrence occ = new RecurringRuleOccurrence();
        occ.setId(UUID.randomUUID());
        occ.setBusinessId(businessId);
        occ.setRecurringRuleId(ruleId);
        occ.setScheduledDate(evalDate.plusDays(4));
        occ.setAmount(new BigDecimal("5000.00"));
        occ.setStatus(RecurringOccurrenceStatus.SCHEDULED);

        when(recurringOccurrenceRepository.findByBusinessIdAndScheduledDateBetween(eq(businessId), any(), any()))
                .thenReturn(List.of(occ));

        DailyCashForecastResponse res = forecastService.getDailyCashForecast(context, "PKR", 30, evalDate.toString());

        assertNotNull(res);
        // Nov 5 has occurrence. Nov 19 has projected preview.
        // Nov 5 must NOT be duplicated between occurrence and template!
        // Total outflows = 5000 (Nov 5) + 5000 (Nov 19) = 10,000.00 PKR.
        assertEquals("10000.00", res.summary().totalExpectedOutflows());
        assertEquals("90000.00", res.summary().endingProjectedCash());
    }

    @Test
    @DisplayName("Fixture E2: Skipped and Reversed occurrences are excluded from cash outflows and not re-projected by template")
    void testFixtureE2_SkippedAndReversedOccurrencesExcluded() {
        UUID accId = UUID.randomUUID();
        FinancialAccount acc = createAccount(accId, "HBL Main", "PKR", new BigDecimal("100000.00"), evalDate);
        when(accountRepository.findByBusinessId(businessId)).thenReturn(List.of(acc));

        UUID ruleId = UUID.randomUUID();
        RecurringRule activeRule = new RecurringRule();
        activeRule.setId(ruleId);
        activeRule.setBusinessId(businessId);
        activeRule.setName("Biweekly Cleaning");
        activeRule.setEventType(TransactionEventType.OPERATING_EXPENSE_PAYMENT);
        activeRule.setAccountId(accId);
        activeRule.setAmount(new BigDecimal("5000.00"));
        activeRule.setFrequency(RecurringFrequency.BIWEEKLY);
        activeRule.setAnchorDayOfMonth(5);
        activeRule.setAnchorMonth(1);
        activeRule.setStartDate(evalDate);
        activeRule.setNextDueDate(evalDate.plusDays(4));
        activeRule.setStatus(RecurringRuleStatus.ACTIVE);

        when(recurringRuleRepository.findByBusinessIdAndStatus(businessId, RecurringRuleStatus.ACTIVE))
                .thenReturn(List.of(activeRule));

        // Nov 5 occurrence is explicitly SKIPPED
        RecurringRuleOccurrence occSkipped = new RecurringRuleOccurrence();
        occSkipped.setId(UUID.randomUUID());
        occSkipped.setBusinessId(businessId);
        occSkipped.setRecurringRuleId(ruleId);
        occSkipped.setScheduledDate(evalDate.plusDays(4));
        occSkipped.setAmount(new BigDecimal("5000.00"));
        occSkipped.setStatus(RecurringOccurrenceStatus.SKIPPED);

        when(recurringOccurrenceRepository.findByBusinessIdAndScheduledDateBetween(eq(businessId), any(), any()))
                .thenReturn(List.of(occSkipped));

        DailyCashForecastResponse res = forecastService.getDailyCashForecast(context, "PKR", 30, evalDate.toString());

        assertNotNull(res);
        // Nov 5 was SKIPPED, so it must NOT produce outflow (0k), and template must NOT re-project Nov 5.
        // Only Nov 19 unmaterialized occurrence produces outflow (5,000.00 PKR).
        assertEquals("5000.00", res.summary().totalExpectedOutflows());
        assertEquals("95000.00", res.summary().endingProjectedCash());
    }

    @Test
    @DisplayName("Fixture F: Reversed transactions provide exact financial neutrality in starting cash")
    void testFixtureF_ReversedTransactionNeutrality() {
        UUID accId = UUID.randomUUID();
        FinancialAccount acc = createAccount(accId, "HBL Main", "PKR", new BigDecimal("100000.00"), evalDate.minusDays(10));
        when(accountRepository.findByBusinessId(businessId)).thenReturn(List.of(acc));

        // Movement sums: 20k increase + 20k decrease = net 0 change
        AccountMovementSum mCredit = new AccountMovementSum() {
            @Override public UUID getAccountId() { return accId; }
            @Override public MovementDirection getMovementDirection() { return MovementDirection.DECREASE; }
            @Override public BigDecimal getTotalAmount() { return new BigDecimal("20000.00"); }
        };
        AccountMovementSum mDebit = new AccountMovementSum() {
            @Override public UUID getAccountId() { return accId; }
            @Override public MovementDirection getMovementDirection() { return MovementDirection.INCREASE; }
            @Override public BigDecimal getTotalAmount() { return new BigDecimal("20000.00"); }
        };
        when(movementRepository.findMovementSumsByBusinessIdAndCutoffDate(eq(businessId), any()))
                .thenReturn(List.of(mCredit, mDebit));

        DailyCashForecastResponse res = forecastService.getDailyCashForecast(context, "PKR", 30, evalDate.toString());

        assertNotNull(res);
        assertEquals("100000.00", res.summary().startingCash());
        assertEquals("100000.00", res.summary().endingProjectedCash());
    }

    @Test
    @DisplayName("Fixture G: Currency isolation ensures USD accounts do not consolidate into PKR forecast")
    void testFixtureG_CurrencyIsolation() {
        UUID accPkrId = UUID.randomUUID();
        FinancialAccount accPkr = createAccount(accPkrId, "HBL PKR", "PKR", new BigDecimal("100000.00"), evalDate);

        UUID accUsdId = UUID.randomUUID();
        FinancialAccount accUsd = createAccount(accUsdId, "Citi USD", "USD", new BigDecimal("5000.00"), evalDate);

        when(accountRepository.findByBusinessId(businessId)).thenReturn(List.of(accPkr, accUsd));

        DailyCashForecastResponse resPkr = forecastService.getDailyCashForecast(context, "PKR", 30, evalDate.toString());
        DailyCashForecastResponse resUsd = forecastService.getDailyCashForecast(context, "USD", 30, evalDate.toString());

        assertEquals("PKR", resPkr.currency());
        assertEquals("100000.00", resPkr.summary().startingCash());

        assertEquals("USD", resUsd.currency());
        assertEquals("5000.00", resUsd.summary().startingCash());
    }

    @Test
    @DisplayName("Liquidity deficit detection flags first deficit date and maximum shortfall when outflows exceed cash")
    void testLiquidityDeficitDetection() {
        UUID accId = UUID.randomUUID();
        FinancialAccount acc = createAccount(accId, "HBL Main", "PKR", new BigDecimal("10000.00"), evalDate);
        when(accountRepository.findByBusinessId(businessId)).thenReturn(List.of(acc));

        UUID cpSuppId = UUID.randomUUID();
        Counterparty supp = createCounterparty(cpSuppId, "Mega Landlord");
        when(counterpartyRepository.findAllById(any())).thenReturn(List.of(supp));

        // 50k payable on Nov 5 (evalDate + 4 days) -> cash drops from 10k to -40k!
        UUID obPayId = UUID.randomUUID();
        FinancialObligation obPay = createObligation(obPayId, cpSuppId, ObligationDirection.PAYABLE, new BigDecimal("50000.00"), evalDate.plusDays(4), "RENT-NOV");
        when(obligationRepository.findByBusinessId(businessId)).thenReturn(List.of(obPay));

        DailyCashForecastResponse res = forecastService.getDailyCashForecast(context, "PKR", 30, evalDate.toString());

        assertNotNull(res);
        assertEquals("-40000.00", res.summary().endingProjectedCash());
        assertEquals("-40000.00", res.summary().minimumProjectedCash());
        assertEquals(evalDate.plusDays(4), res.summary().firstDeficitDate());
        assertEquals("40000.00", res.summary().maximumExpectedShortfall());

        assertTrue(res.completeness().warnings().stream().anyMatch(w -> "LIQUIDITY_DEFICIT".equals(w.code())));
    }

    @Test
    @DisplayName("Validation bounds reject invalid horizon days, malformed date, and invalid scenario parameters")
    void testValidationBounds() {
        assertThrows(ForecastValidationException.class, () ->
                forecastService.getDailyCashForecast(context, "PKR", 0, evalDate.toString())
        );
        assertThrows(ForecastValidationException.class, () ->
                forecastService.getDailyCashForecast(context, "PKR", 366, evalDate.toString())
        );
        assertThrows(ForecastValidationException.class, () ->
                forecastService.getDailyCashForecast(context, "INVALID", 30, evalDate.toString())
        );
        assertThrows(ForecastValidationException.class, () ->
                forecastService.getDailyCashForecast(context, "PKR", 30, "not-a-date")
        );

        ForecastScenarioRequest invalidScenario = new ForecastScenarioRequest(
                "PKR", 30, evalDate.toString(), -5, new BigDecimal("100.00"), 0, false, 1
        );
        assertThrows(ForecastValidationException.class, () ->
                forecastService.previewScenario(context, invalidScenario)
        );
    }

    @Test
    @DisplayName("Security RBAC throws AccessDeniedException when role lacks permission or context is missing")
    void testSecurityRbac() {
        when(authorizationService.hasRolePermission(eq(MembershipRole.VIEWER), eq(BusinessPermission.FINANCIAL_TRANSACTION_READ)))
                .thenReturn(false);

        BusinessAccessContext viewerContext = new BusinessAccessContext(userId, businessId, MembershipRole.VIEWER);

        assertThrows(AccessDeniedException.class, () ->
                forecastService.getDailyCashForecast(viewerContext, "PKR", 30, evalDate.toString())
        );
    }
}
