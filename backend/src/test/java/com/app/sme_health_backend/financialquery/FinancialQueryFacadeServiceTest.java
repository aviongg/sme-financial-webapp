package com.app.sme_health_backend.financialquery;

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
import com.app.sme_health_backend.financialquery.dto.*;
import com.app.sme_health_backend.financialquery.exception.FinancialQueryValidationException;
import com.app.sme_health_backend.financialquery.model.CapabilityStatus;
import com.app.sme_health_backend.financialquery.model.DataCompleteness;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.obligation.FinancialObligation;
import com.app.sme_health_backend.obligation.FinancialObligationRepository;
import com.app.sme_health_backend.obligation.FinancialObligationService;
import com.app.sme_health_backend.obligation.ObligationSourceType;
import com.app.sme_health_backend.obligation.PaymentPlanService;
import com.app.sme_health_backend.obligation.aging.*;
import com.app.sme_health_backend.obligation.dto.PaymentCalendarItemResponse;
import com.app.sme_health_backend.obligation.dto.PaymentCalendarResponse;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinancialQueryFacadeServiceTest {

    @Mock
    private FinancialAccountRepository accountRepository;
    @Mock
    private AccountMovementRepository movementRepository;
    @Mock
    private FinancialObligationService obligationService;
    @Mock
    private FinancialObligationRepository obligationRepository;
    @Mock
    private PaymentPlanService paymentPlanService;
    @Mock
    private CounterpartyRepository counterpartyRepository;
    @Mock
    private RecurringRuleOccurrenceRepository recurringOccurrenceRepository;
    @Mock
    private RecurringRuleRepository recurringRuleRepository;

    private FinancialQueryFacadeService facadeService;

    private final UUID businessId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private BusinessAccessContext context;

    @BeforeEach
    void setUp() {
        context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        facadeService = new FinancialQueryFacadeService(
                accountRepository,
                movementRepository,
                obligationService,
                obligationRepository,
                paymentPlanService,
                counterpartyRepository,
                recurringOccurrenceRepository,
                recurringRuleRepository
        );
    }

    @Test
    @DisplayName("getCapabilities exposes 7 tools with exact available/unavailable statuses")
    void testGetCapabilities() {
        CapabilitiesResponse resp = facadeService.getCapabilities(context);

        assertNotNull(resp);
        assertEquals("1.0", resp.contractVersion());
        assertEquals(businessId, resp.businessId());
        assertEquals(7, resp.capabilities().size());

        Map<String, CapabilityStatus> statusMap = new HashMap<>();
        for (CapabilityItem item : resp.capabilities()) {
            statusMap.put(item.toolName(), item.status());
        }

        assertEquals(CapabilityStatus.AVAILABLE, statusMap.get("getCashPosition"));
        assertEquals(CapabilityStatus.AVAILABLE, statusMap.get("getOverdueReceivables"));
        assertEquals(CapabilityStatus.AVAILABLE, statusMap.get("getUpcomingObligations"));

        assertEquals(CapabilityStatus.UNAVAILABLE, statusMap.get("getProjectFinancials"));
        assertEquals(CapabilityStatus.UNAVAILABLE, statusMap.get("getProjectFundingGap"));
        assertEquals(CapabilityStatus.UNAVAILABLE, statusMap.get("getMarginDrivers"));
        assertEquals(CapabilityStatus.UNAVAILABLE, statusMap.get("getFxSettlementImpact"));
    }

    @Test
    @DisplayName("getCashPosition computes accurate balances and separates multi-currency totals across scales")
    void testGetCashPositionMultiCurrency() {
        LocalDate asOf = LocalDate.of(2026, 10, 20);

        UUID pkrAccId = UUID.randomUUID();
        FinancialAccount pkrAcc = new FinancialAccount(
                businessId, "Bank Alfalah", AccountType.BANK, "PKR", 2,
                new BigDecimal("100000.00"), LocalDate.of(2026, 1, 1),
                AccountStatus.ACTIVE, "Alfalah", "998877661234", null
        );
        setEntityId(pkrAcc, pkrAccId);

        UUID usdAccId = UUID.randomUUID();
        FinancialAccount usdAcc = new FinancialAccount(
                businessId, "USD Export", AccountType.BANK, "USD", 2,
                new BigDecimal("5000.00"), LocalDate.of(2026, 1, 1)
        );
        setEntityId(usdAcc, usdAccId);

        UUID jpyAccId = UUID.randomUUID();
        FinancialAccount jpyAcc = new FinancialAccount(
                businessId, "JPY Clearing", AccountType.BANK, "JPY", 0,
                new BigDecimal("250000"), LocalDate.of(2026, 1, 1)
        );
        setEntityId(jpyAcc, jpyAccId);

        UUID bhdAccId = UUID.randomUUID();
        FinancialAccount bhdAcc = new FinancialAccount(
                businessId, "BHD Trade", AccountType.BANK, "BHD", 3,
                new BigDecimal("100.500"), LocalDate.of(2026, 1, 1)
        );
        setEntityId(bhdAcc, bhdAccId);

        when(accountRepository.findByBusinessId(businessId)).thenReturn(List.of(usdAcc, pkrAcc, jpyAcc, bhdAcc));

        // Movements: PKR (+20,000, -5,000 = +15,000), USD (+1,000), JPY (-50,000), BHD (+24.125)
        AccountMovementSum sumIn = mockMovementSum(pkrAccId, MovementDirection.INCREASE, new BigDecimal("20000.00"));
        AccountMovementSum sumOut = mockMovementSum(pkrAccId, MovementDirection.DECREASE, new BigDecimal("5000.00"));
        AccountMovementSum sumUsdIn = mockMovementSum(usdAccId, MovementDirection.INCREASE, new BigDecimal("1000.00"));
        AccountMovementSum sumJpyOut = mockMovementSum(jpyAccId, MovementDirection.DECREASE, new BigDecimal("50000"));
        AccountMovementSum sumBhdIn = mockMovementSum(bhdAccId, MovementDirection.INCREASE, new BigDecimal("24.125"));

        when(movementRepository.findMovementSumsByBusinessIdAndCutoffDate(businessId, asOf))
                .thenReturn(List.of(sumIn, sumOut, sumUsdIn, sumJpyOut, sumBhdIn));

        CashPositionQuery query = new CashPositionQuery("2026-10-20", null);
        FinancialQueryEnvelope<CashPositionResult> envelope = facadeService.getCashPosition(context, query);

        assertEquals(CapabilityStatus.AVAILABLE, envelope.status());
        assertEquals(DataCompleteness.COMPLETE, envelope.completeness());
        CashPositionResult result = envelope.data();
        assertNotNull(result);
        assertEquals(asOf, result.asOfDate());
        assertEquals(4, result.totalAccounts());

        // Multi-currency warning must be present
        assertTrue(envelope.warnings().stream().anyMatch(w -> w.contains("Multi-currency business")));

        // Currencies must be sorted deterministically (BHD, JPY, PKR, USD) without cross-currency conversion
        assertEquals(4, result.currencySummaries().size());
        assertEquals(List.of("BHD", "JPY", "PKR", "USD"),
                result.currencySummaries().stream().map(CurrencyCashSummary::currency).toList());

        CurrencyCashSummary pkrSummary = result.currencySummaries().stream()
                .filter(c -> "PKR".equals(c.currency())).findFirst().orElseThrow();
        assertEquals("100000.00", pkrSummary.openingBalance());
        assertEquals("15000.00", pkrSummary.netMovements());
        assertEquals("115000.00", pkrSummary.currentBalance());

        CurrencyCashSummary usdSummary = result.currencySummaries().stream()
                .filter(c -> "USD".equals(c.currency())).findFirst().orElseThrow();
        assertEquals("5000.00", usdSummary.openingBalance());
        assertEquals("1000.00", usdSummary.netMovements());
        assertEquals("6000.00", usdSummary.currentBalance());

        CurrencyCashSummary jpySummary = result.currencySummaries().stream()
                .filter(c -> "JPY".equals(c.currency())).findFirst().orElseThrow();
        assertEquals("250000", jpySummary.openingBalance());
        assertEquals("-50000", jpySummary.netMovements());
        assertEquals("200000", jpySummary.currentBalance());

        CurrencyCashSummary bhdSummary = result.currencySummaries().stream()
                .filter(c -> "BHD".equals(c.currency())).findFirst().orElseThrow();
        assertEquals("100.500", bhdSummary.openingBalance());
        assertEquals("24.125", bhdSummary.netMovements());
        assertEquals("124.625", bhdSummary.currentBalance());

        // Sensitive identifier must be masked
        AccountCashPosition pkrPos = result.accounts().stream()
                .filter(a -> a.accountId().equals(pkrAccId)).findFirst().orElseThrow();
        assertEquals("****1234", pkrPos.maskedIdentifier());
    }

    @Test
    @DisplayName("getCashPosition respects opening date and validates invalid date format and accountType")
    void testGetCashPositionDateHandling() {
        UUID accId = UUID.randomUUID();
        FinancialAccount acc = new FinancialAccount(businessId, "Late Opening Account", AccountType.BANK, "PKR", 2, new BigDecimal("50000.00"), LocalDate.of(2026, 6, 1));
        setEntityId(acc, accId);
        when(accountRepository.findByBusinessId(businessId)).thenReturn(List.of(acc));

        // As of date before opening date: currentBalance and currency summary must be 0.00 while account openingBalance metadata stays preserved
        CashPositionQuery beforeQuery = new CashPositionQuery("2026-05-01", null);
        FinancialQueryEnvelope<CashPositionResult> envBefore = facadeService.getCashPosition(context, beforeQuery);
        assertEquals("50000.00", envBefore.data().accounts().get(0).openingBalance());
        assertEquals("0.00", envBefore.data().accounts().get(0).currentBalance());
        assertEquals("0.00", envBefore.data().currencySummaries().get(0).openingBalance());
        assertEquals("0.00", envBefore.data().currencySummaries().get(0).currentBalance());
        assertTrue(envBefore.warnings().stream().anyMatch(w -> w.contains("opened after asOfDate")));

        // Missing openingBalance or openingDate must NEVER fabricate 0.00
        FinancialAccount missingOpenAcc = new FinancialAccount(businessId, "Corrupted Account", AccountType.BANK, "PKR", 2, null, LocalDate.of(2026, 6, 1));
        setEntityId(missingOpenAcc, UUID.randomUUID());
        FinancialAccount missingDateAcc = new FinancialAccount(businessId, "Dateless Account", AccountType.CASH, "PKR", 2, new BigDecimal("1000.00"), null);
        setEntityId(missingDateAcc, UUID.randomUUID());
        when(accountRepository.findByBusinessId(businessId)).thenReturn(List.of(missingOpenAcc, missingDateAcc));

        FinancialQueryEnvelope<CashPositionResult> envCorrupt = facadeService.getCashPosition(context, new CashPositionQuery("2026-06-15", null));
        assertEquals(DataCompleteness.PARTIAL, envCorrupt.completeness());
        assertTrue(envCorrupt.data().accounts().isEmpty());
        assertTrue(envCorrupt.data().currencySummaries().isEmpty());
        assertEquals(2, envCorrupt.warnings().size());

        // Invalid date format must throw FinancialQueryValidationException
        CashPositionQuery badQuery = new CashPositionQuery("20-10-2026", null);
        assertThrows(FinancialQueryValidationException.class, () -> facadeService.getCashPosition(context, badQuery));

        // Invalid accountType must throw FinancialQueryValidationException
        CashPositionQuery badTypeQuery = new CashPositionQuery("2026-10-20", "CRYPTO_WALLET");
        assertThrows(FinancialQueryValidationException.class, () -> facadeService.getCashPosition(context, badTypeQuery));
    }

    @Test
    @DisplayName("getOverdueReceivables returns canonical 7-bucket aging, supports pagination and validates bounds")
    void testGetOverdueReceivables() {
        LocalDate asOf = LocalDate.of(2026, 10, 20);

        UUID ob1Id = UUID.randomUUID();
        UUID ob2Id = UUID.randomUUID();
        UUID cp1Id = UUID.randomUUID();
        Counterparty cp1 = new Counterparty(businessId, "Acme Corp", CounterpartyType.CUSTOMER, null, null, null, null, null);
        setEntityId(cp1, cp1Id);

        FinancialObligation ob1 = new FinancialObligation(
                businessId, cp1Id, ObligationDirection.RECEIVABLE, ObligationSourceType.MANUAL_DECLARATION,
                null, "INV-101", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                new BigDecimal("50000.00"), "PKR", "Sale invoice", userId
        );
        setEntityId(ob1, ob1Id);

        FinancialObligation ob2 = new FinancialObligation(
                businessId, cp1Id, ObligationDirection.RECEIVABLE, ObligationSourceType.MANUAL_DECLARATION,
                null, "INV-102", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                new BigDecimal("25000.00"), "PKR", "Older invoice", userId
        );
        setEntityId(ob2, ob2Id);

        ObligationAgingDetail detail1 = new ObligationAgingDetail(
                ob1Id.toString(), ObligationDirection.RECEIVABLE, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                new BigDecimal("50000.00"), new BigDecimal("30000.00"), 20,
                AgingBucket.OVERDUE_1_30, null
        );
        ObligationAgingDetail detail2 = new ObligationAgingDetail(
                ob2Id.toString(), ObligationDirection.RECEIVABLE, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                new BigDecimal("25000.00"), new BigDecimal("25000.00"), 50,
                AgingBucket.OVERDUE_31_60, null
        );

        BucketSummary bs1 = new BucketSummary(AgingBucket.OVERDUE_1_30, new BigDecimal("30000.00"), 1);
        BucketSummary bs2 = new BucketSummary(AgingBucket.OVERDUE_31_60, new BigDecimal("25000.00"), 1);
        DirectionAgingSummary recSummary = new DirectionAgingSummary(
                ObligationDirection.RECEIVABLE,
                new BigDecimal("55000.00"),
                new BigDecimal("55000.00"),
                2, 2,
                Map.of(AgingBucket.OVERDUE_1_30, bs1, AgingBucket.OVERDUE_31_60, bs2),
                0, 0, 0
        );

        ObligationAgingResult agingResult = new ObligationAgingResult(
                asOf, "PKR",
                recSummary, null,
                List.of(detail1, detail2)
        );

        when(obligationService.getAging(eq(context), eq("2026-10-20"))).thenReturn(agingResult);
        when(obligationRepository.findAllById(anySet())).thenReturn(List.of(ob1, ob2));
        when(counterpartyRepository.findAllById(anySet())).thenReturn(List.of(cp1));

        // Full result
        OverdueReceivablesQuery query = new OverdueReceivablesQuery("2026-10-20", 50);
        FinancialQueryEnvelope<OverdueReceivablesResult> envelope = facadeService.getOverdueReceivables(context, query);

        assertEquals(CapabilityStatus.AVAILABLE, envelope.status());
        assertEquals(DataCompleteness.COMPLETE, envelope.completeness());
        OverdueReceivablesResult res = envelope.data();
        assertNotNull(res);
        assertEquals("55000.00", res.totalOverdueAmount());
        assertEquals(2, res.overdueCount());
        assertEquals(7, res.agingBuckets().size());

        // Sorted by overdueDays DESC: ob2 (50 days) then ob1 (20 days)
        OverdueReceivableItem firstItem = res.items().get(0);
        assertEquals(ob2Id, firstItem.obligationId());
        assertEquals(50, firstItem.overdueDays());
        assertEquals("OVERDUE_31_60", firstItem.bucketCode());

        OverdueReceivableItem secondItem = res.items().get(1);
        assertEquals(ob1Id, secondItem.obligationId());
        assertEquals("Acme Corp", secondItem.counterpartyName());
        assertEquals("50000.00", secondItem.originalAmount());
        assertEquals("20000.00", secondItem.settledAmount());
        assertEquals("30000.00", secondItem.outstandingAmount());
        assertEquals(20, secondItem.overdueDays());
        assertEquals("OVERDUE_1_30", secondItem.bucketCode());

        // Paginated slice (offset=1, limit=1) -> PARTIAL completeness
        OverdueReceivablesQuery pagedQuery = new OverdueReceivablesQuery("2026-10-20", 1, 1);
        FinancialQueryEnvelope<OverdueReceivablesResult> pagedEnv = facadeService.getOverdueReceivables(context, pagedQuery);
        assertEquals(DataCompleteness.PARTIAL, pagedEnv.completeness());
        assertEquals(1, pagedEnv.data().items().size());
        assertEquals(ob1Id, pagedEnv.data().items().get(0).obligationId());

        // Bounds validation
        assertThrows(FinancialQueryValidationException.class,
                () -> facadeService.getOverdueReceivables(context, new OverdueReceivablesQuery("2026-10-20", 0)));
        assertThrows(FinancialQueryValidationException.class,
                () -> facadeService.getOverdueReceivables(context, new OverdueReceivablesQuery("2026-10-20", 101)));
        assertThrows(FinancialQueryValidationException.class,
                () -> facadeService.getOverdueReceivables(context, new OverdueReceivablesQuery("2026-10-20", 10, -1)));
    }

    @Test
    @DisplayName("getUpcomingObligations includes scheduled installments and labels recurring projections as non-binding estimates without duplication")
    void testGetUpcomingObligationsWithRecurringProjections() {
        LocalDate start = LocalDate.of(2026, 10, 20);
        LocalDate end = LocalDate.of(2026, 11, 19);

        UUID obId = UUID.randomUUID();
        PaymentCalendarItemResponse calItem = new PaymentCalendarItemResponse(
                obId, "PAYABLE", UUID.randomUUID(), "Supplier A", "BILL-001", "MANUAL_ENTRY",
                "SCHEDULED_INSTALLMENT", LocalDate.of(2026, 10, 25), "15000.00", "0.00", "15000.00",
                false, "Installment 1"
        );

        com.app.sme_health_backend.obligation.dto.PaymentCalendarSummaryResponse summary =
                new com.app.sme_health_backend.obligation.dto.PaymentCalendarSummaryResponse(
                        "0.00", "15000.00", "0.00", "0.00", "0.00", "15000.00"
                );
        PaymentCalendarResponse calResp = new PaymentCalendarResponse(
                start, end, summary, List.of(calItem)
        );

        when(paymentPlanService.getPaymentCalendar(eq(context), isNull(), eq("2026-10-20"), eq("2026-11-19")))
                .thenReturn(calResp);

        // Mock both an existing occurrence on Nov 1 AND an active rule for Nov 1 + another active weekly rule
        UUID ruleId1 = UUID.randomUUID();
        UUID ruleId2 = UUID.randomUUID();
        UUID accId = UUID.randomUUID();
        RecurringRule rule1 = new RecurringRule(
                businessId, "Office Lease", "Rent",
                TransactionEventType.OPERATING_EXPENSE_PAYMENT,
                accId, null, new BigDecimal("50000.00"), RecurringFrequency.MONTHLY,
                1, 11, LocalDate.of(2026, 10, 1), null, LocalDate.of(2026, 11, 1), userId
        );
        setEntityId(rule1, ruleId1);

        RecurringRule rule2 = new RecurringRule(
                businessId, "Security Retainer", "Monthly guard service",
                TransactionEventType.OPERATING_EXPENSE_PAYMENT,
                accId, null, new BigDecimal("12000.00"), RecurringFrequency.MONTHLY,
                10, 11, LocalDate.of(2026, 11, 10), null, LocalDate.of(2026, 11, 10), userId
        );
        setEntityId(rule2, ruleId2);

        RecurringRuleOccurrence occ = new RecurringRuleOccurrence();
        occ.setBusinessId(businessId);
        occ.setRecurringRuleId(ruleId1);
        occ.setRuleVersion(1L);
        occ.setScheduledDate(LocalDate.of(2026, 11, 1));
        occ.setAmount(new BigDecimal("50000.00"));
        occ.setEventType(TransactionEventType.OPERATING_EXPENSE_PAYMENT);
        occ.setAccountId(accId);
        occ.setStatus(RecurringOccurrenceStatus.SCHEDULED);
        occ.setCreatedByUserId(userId);
        setEntityId(occ, UUID.randomUUID());

        FinancialAccount acc = new FinancialAccount(businessId, "Current Account", AccountType.BANK, "PKR", 2, BigDecimal.ZERO, LocalDate.of(2026, 1, 1));
        setEntityId(acc, accId);

        when(recurringOccurrenceRepository.findByBusinessIdAndStatus(eq(businessId), eq(RecurringOccurrenceStatus.SCHEDULED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(occ)));
        when(recurringOccurrenceRepository.findByBusinessIdAndStatus(eq(businessId), eq(RecurringOccurrenceStatus.DRAFT_READY), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        when(recurringRuleRepository.findByBusinessIdAndStatus(eq(businessId), eq(RecurringRuleStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(rule1, rule2)));
        when(accountRepository.findAllById(anySet())).thenReturn(List.of(acc));

        UpcomingObligationsQuery query = new UpcomingObligationsQuery(null, "2026-10-20", "2026-11-19");
        FinancialQueryEnvelope<UpcomingObligationsResult> envelope = facadeService.getUpcomingObligations(context, query);

        assertEquals(CapabilityStatus.AVAILABLE, envelope.status());
        UpcomingObligationsResult data = envelope.data();
        assertNotNull(data);
        assertEquals("15000.00", data.totalUpcomingPayables());
        assertEquals(1, data.obligations().size());

        // Office Lease on Nov 1 must NOT be duplicated even though both occurrence and active rule exist;
        // Security Retainer on Nov 10 (no occurrence row yet) must be projected from active rule!
        assertEquals(2, data.recurringProjections().size());

        RecurringProjectionItem proj1 = data.recurringProjections().get(0);
        assertTrue(proj1.isEstimate());
        assertEquals("Office Lease", proj1.ruleName());
        assertEquals("50000.00", proj1.amount());
        assertEquals("PAYABLE", proj1.direction());

        RecurringProjectionItem proj2 = data.recurringProjections().get(1);
        assertTrue(proj2.isEstimate());
        assertEquals("Security Retainer", proj2.ruleName());
        assertEquals("12000.00", proj2.amount());

        // Warning must be present
        assertTrue(envelope.warnings().stream().anyMatch(w -> w.contains("non-binding estimates")));
    }

    @Test
    @DisplayName("getUpcomingObligations rejects windows exceeding 90 days, inverted ranges, and invalid directions")
    void testGetUpcomingObligationsWindowLimit() {
        UpcomingObligationsQuery overLimit = new UpcomingObligationsQuery(null, "2026-01-01", "2026-05-01"); // > 90 days
        assertThrows(FinancialQueryValidationException.class, () -> facadeService.getUpcomingObligations(context, overLimit));

        UpcomingObligationsQuery inverted = new UpcomingObligationsQuery(null, "2026-05-10", "2026-05-01");
        assertThrows(FinancialQueryValidationException.class, () -> facadeService.getUpcomingObligations(context, inverted));

        UpcomingObligationsQuery badDir = new UpcomingObligationsQuery("EQUITY", "2026-05-01", "2026-05-10");
        assertThrows(FinancialQueryValidationException.class, () -> facadeService.getUpcomingObligations(context, badDir));
    }

    @Test
    @DisplayName("Unavailable tools return typed UNAVAILABLE envelope with non-fabricated responses and validate query bounds")
    void testUnavailableTools() {
        UUID projId = UUID.randomUUID();

        var envProj = facadeService.getProjectFinancials(context, projId);
        assertEquals(CapabilityStatus.UNAVAILABLE, envProj.status());
        assertNull(envProj.data());
        assertTrue(envProj.unavailableReason().contains("F05"));

        var envGap = facadeService.getProjectFundingGap(context, projId);
        assertEquals(CapabilityStatus.UNAVAILABLE, envGap.status());
        assertNull(envGap.data());
        assertTrue(envGap.unavailableReason().contains("F07"));

        var envMargin = facadeService.getMarginDrivers(context, "cogs");
        assertEquals(CapabilityStatus.UNAVAILABLE, envMargin.status());
        assertNull(envMargin.data());
        assertTrue(envMargin.unavailableReason().contains("F08"));

        var envFx = facadeService.getFxSettlementImpact(context, "rates");
        assertEquals(CapabilityStatus.UNAVAILABLE, envFx.status());
        assertNull(envFx.data());
        assertTrue(envFx.unavailableReason().contains("F11"));

        String oversized = "x".repeat(501);
        assertThrows(FinancialQueryValidationException.class, () -> facadeService.getMarginDrivers(context, oversized));
        assertThrows(FinancialQueryValidationException.class, () -> facadeService.getFxSettlementImpact(context, oversized));
    }

    @Test
    @DisplayName("Service-layer authorization enforces active business context and role permissions for direct Java callers")
    void testServiceLayerAuthorizationEnforcement() {
        // Null context must be rejected across all 8 facade methods even if called outside HTTP controller
        assertThrows(AccessDeniedException.class, () -> facadeService.getCapabilities(null));
        assertThrows(AccessDeniedException.class, () -> facadeService.getCashPosition(null, null));
        assertThrows(AccessDeniedException.class, () -> facadeService.getOverdueReceivables(null, null));
        assertThrows(AccessDeniedException.class, () -> facadeService.getUpcomingObligations(null, null));
        assertThrows(AccessDeniedException.class, () -> facadeService.getProjectFinancials(null, null));
        assertThrows(AccessDeniedException.class, () -> facadeService.getProjectFundingGap(null, null));
        assertThrows(AccessDeniedException.class, () -> facadeService.getMarginDrivers(null, null));
        assertThrows(AccessDeniedException.class, () -> facadeService.getFxSettlementImpact(null, null));

        // Role denied by BusinessAuthorizationService (e.g., restricted role lacking financial read permission) must throw AccessDeniedException
        com.app.sme_health_backend.identity.service.BusinessAuthorizationService denyAuth =
                org.mockito.Mockito.mock(com.app.sme_health_backend.identity.service.BusinessAuthorizationService.class);
        when(denyAuth.hasRolePermission(org.mockito.Mockito.any(), org.mockito.Mockito.any())).thenReturn(false);
        FinancialQueryFacadeService deniedFacade = new FinancialQueryFacadeService(
                accountRepository, movementRepository, obligationService, obligationRepository,
                paymentPlanService, counterpartyRepository, recurringOccurrenceRepository, recurringRuleRepository, denyAuth
        );
        assertThrows(AccessDeniedException.class, () -> deniedFacade.getCapabilities(context));
        assertThrows(AccessDeniedException.class, () -> deniedFacade.getCashPosition(context, null));
        assertThrows(AccessDeniedException.class, () -> deniedFacade.getOverdueReceivables(context, null));
        assertThrows(AccessDeniedException.class, () -> deniedFacade.getUpcomingObligations(context, null));
        assertThrows(AccessDeniedException.class, () -> deniedFacade.getProjectFinancials(context, null));
        assertThrows(AccessDeniedException.class, () -> deniedFacade.getProjectFundingGap(context, null));
        assertThrows(AccessDeniedException.class, () -> deniedFacade.getMarginDrivers(context, null));
        assertThrows(AccessDeniedException.class, () -> deniedFacade.getFxSettlementImpact(context, null));

        // Authorized read roles (OWNER, ACCOUNTANT, MANAGER, VIEWER) all succeed
        for (MembershipRole role : List.of(MembershipRole.OWNER, MembershipRole.ACCOUNTANT, MembershipRole.MANAGER, MembershipRole.VIEWER)) {
            BusinessAccessContext roleCtx = new BusinessAccessContext(userId, businessId, role);
            assertNotNull(facadeService.getCapabilities(roleCtx));
            assertEquals(CapabilityStatus.UNAVAILABLE, facadeService.getProjectFinancials(roleCtx, null).status());
        }
    }

    private void setEntityId(Object entity, UUID id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private AccountMovementSum mockMovementSum(UUID accountId, MovementDirection direction, BigDecimal amount) {
        AccountMovementSum sum = mock(AccountMovementSum.class);
        when(sum.getAccountId()).thenReturn(accountId);
        when(sum.getMovementDirection()).thenReturn(direction);
        when(sum.getTotalAmount()).thenReturn(amount);
        return sum;
    }
}
