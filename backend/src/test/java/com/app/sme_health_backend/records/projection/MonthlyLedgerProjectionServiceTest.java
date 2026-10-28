package com.app.sme_health_backend.records.projection;

import com.app.sme_health_backend.accounting.draft.DraftStatus;
import com.app.sme_health_backend.accounting.draft.RecordDraft;
import com.app.sme_health_backend.accounting.draft.RecordDraftRepository;
import com.app.sme_health_backend.accounting.entity.AccountMovement;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialTransaction;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.AccountMovementRepository;
import com.app.sme_health_backend.accounting.repository.AccountMovementSum;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialTransactionRepository;
import com.app.sme_health_backend.financialquery.exception.FinancialQueryValidationException;
import com.app.sme_health_backend.financialquery.model.CapabilityStatus;
import com.app.sme_health_backend.financialquery.model.DataCompleteness;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.obligation.CounterpartyAdvance;
import com.app.sme_health_backend.obligation.CounterpartyAdvanceRepository;
import com.app.sme_health_backend.obligation.FinancialObligation;
import com.app.sme_health_backend.obligation.FinancialObligationRepository;
import com.app.sme_health_backend.obligation.FinancialObligationService;
import com.app.sme_health_backend.obligation.ObligationPaymentAllocationRepository;
import com.app.sme_health_backend.obligation.ObligationSourceType;
import com.app.sme_health_backend.obligation.ObligationStatus;
import com.app.sme_health_backend.obligation.aging.DirectionAgingSummary;
import com.app.sme_health_backend.obligation.aging.ObligationAgingResult;
import com.app.sme_health_backend.obligation.aging.ObligationDirection;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.projection.dto.*;
import com.app.sme_health_backend.records.projection.model.ComparisonMetricStatus;
import com.app.sme_health_backend.records.projection.model.RecognitionBasis;
import com.app.sme_health_backend.records.projection.service.MonthlyLedgerProjectionService;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.recurring.repository.RecurringRuleOccurrenceRepository;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonthlyLedgerProjectionServiceTest {

    @Mock private FinancialAccountRepository accountRepository;
    @Mock private FinancialTransactionRepository transactionRepository;
    @Mock private AccountMovementRepository movementRepository;
    @Mock private FinancialObligationService obligationService;
    @Mock private FinancialObligationRepository obligationRepository;
    @Mock private ObligationPaymentAllocationRepository allocationRepository;
    @Mock private CounterpartyAdvanceRepository advanceRepository;
    @Mock private MonthlyRecordRepository monthlyRecordRepository;
    @Mock private BusinessAuthorizationService authorizationService;
    @Mock private RecordDraftRepository recordDraftRepository;
    @Mock private RecurringRuleOccurrenceRepository recurringOccurrenceRepository;

    private MonthlyLedgerProjectionService projectionService;

    private final UUID businessId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private BusinessAccessContext context;

    @BeforeEach
    void setUp() {
        projectionService = new MonthlyLedgerProjectionService(
                accountRepository,
                transactionRepository,
                movementRepository,
                obligationService,
                obligationRepository,
                allocationRepository,
                advanceRepository,
                monthlyRecordRepository,
                authorizationService,
                recordDraftRepository,
                recurringOccurrenceRepository
        );
        context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        lenient().when(authorizationService.hasRolePermission(eq(MembershipRole.OWNER), eq(BusinessPermission.FINANCIAL_DATA_READ)))
                .thenReturn(true);
        lenient().when(movementRepository.findMovementSumsByBusinessIdAndCutoffDate(any(), any()))
                .thenReturn(List.of());
        lenient().when(movementRepository.findMovementSumsByBusinessIdAndDateRange(any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(transactionRepository.findByBusinessIdAndDateRange(any(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("Fixture A: Basic monthly cash flow reconciles 30k receipt, 8k opex, 10k transfer, and 122k closing cash")
    void testProjectMonthlyLedger_BasicCashFlowFixtureA() {
        UUID acc1Id = UUID.randomUUID();
        UUID acc2Id = UUID.randomUUID();
        LocalDate sep1 = LocalDate.of(2026, 9, 1);

        FinancialAccount acc1 = new FinancialAccount(
                businessId, "HBL Operating", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), sep1
        );
        acc1.setId(acc1Id);

        FinancialAccount acc2 = new FinancialAccount(
                businessId, "Meezan Reserve", AccountType.BANK, "PKR", 2, BigDecimal.ZERO.setScale(2), sep1
        );
        acc2.setId(acc2Id);

        when(accountRepository.findByBusinessId(businessId)).thenReturn(List.of(acc1, acc2));

        // Movements during October:
        // Account 1: +30k (receipt), -8k (expense), -10k (transfer out) -> net +12k
        // Account 2: +10k (transfer in) -> net +10k
        AccountMovementSum sum1Inc = mockMovementSum(acc1Id, MovementDirection.INCREASE, new BigDecimal("30000.00"));
        AccountMovementSum sum1Dec = mockMovementSum(acc1Id, MovementDirection.DECREASE, new BigDecimal("18000.00")); // 8k opex + 10k transfer
        AccountMovementSum sum2Inc = mockMovementSum(acc2Id, MovementDirection.INCREASE, new BigDecimal("10000.00"));

        when(movementRepository.findMovementSumsByBusinessIdAndDateRange(eq(businessId), any(), any()))
                .thenReturn(List.of(sum1Inc, sum1Dec, sum2Inc));

        // Up to end of Oct (Oct 31):
        // Acc 1: 100k + 12k = 112k
        // Acc 2: 0 + 10k = 10k
        AccountMovementSum end1Inc = mockMovementSum(acc1Id, MovementDirection.INCREASE, new BigDecimal("30000.00"));
        AccountMovementSum end1Dec = mockMovementSum(acc1Id, MovementDirection.DECREASE, new BigDecimal("18000.00"));
        AccountMovementSum end2Inc = mockMovementSum(acc2Id, MovementDirection.INCREASE, new BigDecimal("10000.00"));

        lenient().when(movementRepository.findMovementSumsByBusinessIdAndCutoffDate(eq(businessId), eq(LocalDate.of(2026, 10, 31))))
                .thenReturn(List.of(end1Inc, end1Dec, end2Inc));

        // Transactions in October:
        FinancialTransaction tx1 = new FinancialTransaction(
                businessId, acc1Id, TransactionEventType.DIRECT_SALE_RECEIPT, MovementDirection.INCREASE,
                new BigDecimal("30000.00"), "PKR", new BigDecimal("30000.00"), LocalDate.of(2026, 10, 5), "Cash sale", userId
        );
        tx1.setId(UUID.randomUUID());
        FinancialTransaction tx2 = new FinancialTransaction(
                businessId, acc1Id, TransactionEventType.OPERATING_EXPENSE_PAYMENT, MovementDirection.DECREASE,
                new BigDecimal("8000.00"), "PKR", new BigDecimal("8000.00"), LocalDate.of(2026, 10, 10), "Office supplies", userId
        );
        tx2.setId(UUID.randomUUID());
        FinancialTransaction tx3 = new FinancialTransaction(
                businessId, acc1Id, acc2Id, TransactionEventType.INTERNAL_TRANSFER, MovementDirection.DECREASE,
                new BigDecimal("10000.00"), "PKR", new BigDecimal("10000.00"), LocalDate.of(2026, 10, 15), "Liquidity transfer", userId
        );
        tx3.setId(UUID.randomUUID());

        when(transactionRepository.findByBusinessIdAndDateRange(eq(businessId), any(), any()))
                .thenReturn(List.of(tx1, tx2, tx3));

        mockEmptyObligationsAndAging();

        MonthlyLedgerProjectionResponse response = projectionService.projectMonthlyLedger(context, "2026-10", null);

        assertNotNull(response);
        assertEquals("2026-10", response.month());
        assertEquals(DataCompleteness.COMPLETE, response.completeness());

        // Verify Cash Flow numbers:
        MonthlyOperatingCashFlow cf = response.cashFlow();
        assertEquals("30000.00", cf.grossDirectSaleReceipts());
        assertEquals("0.00", cf.reversedDirectSaleReceipts());
        assertEquals("30000.00", cf.netDirectSaleReceipts());
        assertEquals("30000.00", cf.totalCashInflow());

        assertEquals("8000.00", cf.grossOperatingExpensePayments());
        assertEquals("0.00", cf.reversedOperatingExpensePayments());
        assertEquals("8000.00", cf.netOperatingExpensePayments());
        assertEquals("8000.00", cf.totalCashOutflow());

        assertEquals("22000.00", cf.netOperatingCashFlow());
        assertEquals("10000.00", cf.totalInternalTransfersIn());
        assertEquals("10000.00", cf.totalInternalTransfersOut());
        assertEquals("0.00", cf.netInternalTransfersAtBusinessLevel());
        assertEquals("22000.00", cf.totalNetCashMovement());

        // Verify Currency Cash Summary:
        assertEquals(1, response.currencySummaries().size());
        CurrencyMonthlyCashSummary pkrSummary = response.currencySummaries().get(0);
        assertEquals("PKR", pkrSummary.currency());
        assertEquals("100000.00", pkrSummary.totalOpeningBalance());
        assertEquals("0.00", pkrSummary.totalStartingBalanceAdjustments());
        assertEquals("22000.00", pkrSummary.totalNetMovements());
        assertEquals("122000.00", pkrSummary.totalClosingBalance());
        assertEquals(2, pkrSummary.accountCount());

        // Verify Individual Account Positions:
        AccountMonthlyCashPosition p1 = response.accountPositions().stream().filter(a -> a.accountId().equals(acc1Id)).findFirst().orElseThrow();
        assertEquals("100000.00", p1.openingBalanceAsOfStartOfMonth());
        assertEquals("12000.00", p1.netMovementDuringMonth());
        assertEquals("112000.00", p1.closingBalanceAsOfEndOfMonth());

        AccountMonthlyCashPosition p2 = response.accountPositions().stream().filter(a -> a.accountId().equals(acc2Id)).findFirst().orElseThrow();
        assertEquals("0.00", p2.openingBalanceAsOfStartOfMonth());
        assertEquals("10000.00", p2.netMovementDuringMonth());
        assertEquals("10000.00", p2.closingBalanceAsOfEndOfMonth());
    }

    @Test
    @DisplayName("Fixture B: Reversal of 8k expense restores 0 opex, 30k net cash, and 130k closing cash")
    void testProjectMonthlyLedger_ReversalFixtureB() {
        UUID acc1Id = UUID.randomUUID();
        LocalDate sep1 = LocalDate.of(2026, 9, 1);

        FinancialAccount acc1 = new FinancialAccount(
                businessId, "HBL Operating", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), sep1
        );
        acc1.setId(acc1Id);

        when(accountRepository.findByBusinessId(businessId)).thenReturn(List.of(acc1));

        // Net movement in October: +30k (receipt) + (-8k opex) + (+8k reversal) = +30k
        AccountMovementSum sumInc = mockMovementSum(acc1Id, MovementDirection.INCREASE, new BigDecimal("38000.00")); // 30k + 8k rev
        AccountMovementSum sumDec = mockMovementSum(acc1Id, MovementDirection.DECREASE, new BigDecimal("8000.00"));
        when(movementRepository.findMovementSumsByBusinessIdAndDateRange(eq(businessId), any(), any()))
                .thenReturn(List.of(sumInc, sumDec));

        AccountMovementSum endInc = mockMovementSum(acc1Id, MovementDirection.INCREASE, new BigDecimal("38000.00"));
        AccountMovementSum endDec = mockMovementSum(acc1Id, MovementDirection.DECREASE, new BigDecimal("8000.00"));
        lenient().when(movementRepository.findMovementSumsByBusinessIdAndCutoffDate(eq(businessId), eq(LocalDate.of(2026, 10, 31))))
                .thenReturn(List.of(endInc, endDec));

        UUID tx2Id = UUID.randomUUID();
        FinancialTransaction tx1 = new FinancialTransaction(
                businessId, acc1Id, TransactionEventType.DIRECT_SALE_RECEIPT, MovementDirection.INCREASE,
                new BigDecimal("30000.00"), "PKR", new BigDecimal("30000.00"), LocalDate.of(2026, 10, 5), "Cash sale", userId
        );
        tx1.setId(UUID.randomUUID());
        FinancialTransaction tx2 = new FinancialTransaction(
                businessId, acc1Id, TransactionEventType.OPERATING_EXPENSE_PAYMENT, MovementDirection.DECREASE,
                new BigDecimal("8000.00"), "PKR", new BigDecimal("8000.00"), LocalDate.of(2026, 10, 10), "Office supplies", userId
        );
        tx2.setId(tx2Id);

        FinancialTransaction revTx = new FinancialTransaction(
                businessId, acc1Id, TransactionEventType.OPERATING_EXPENSE_PAYMENT, MovementDirection.INCREASE,
                new BigDecimal("8000.00"), "PKR", new BigDecimal("8000.00"), LocalDate.of(2026, 10, 10), "Reversal of supplies", userId
        );
        revTx.setId(UUID.randomUUID());
        revTx.setReversedTransactionId(tx2Id);

        when(transactionRepository.findByBusinessIdAndDateRange(eq(businessId), any(), any()))
                .thenReturn(List.of(tx1, tx2, revTx));

        mockEmptyObligationsAndAging();

        MonthlyLedgerProjectionResponse response = projectionService.projectMonthlyLedger(context, "2026-10", null);

        MonthlyOperatingCashFlow cf = response.cashFlow();
        assertEquals("30000.00", cf.totalCashInflow());
        assertEquals("8000.00", cf.grossOperatingExpensePayments());
        assertEquals("8000.00", cf.reversedOperatingExpensePayments());
        assertEquals("0.00", cf.netOperatingExpensePayments());
        assertEquals("0.00", cf.totalCashOutflow());
        assertEquals("30000.00", cf.netOperatingCashFlow());

        CurrencyMonthlyCashSummary pkr = response.currencySummaries().get(0);
        assertEquals("100000.00", pkr.totalOpeningBalance());
        assertEquals("30000.00", pkr.totalNetMovements());
        assertEquals("130000.00", pkr.totalClosingBalance());
    }

    @Test
    @DisplayName("Fixture C: Unposted drafts and recurring expectations are segregated into draftSummary and do not pollute cash")
    void testProjectMonthlyLedger_ExcludesDraftsAndRecurringEstimates() {
        UUID acc1Id = UUID.randomUUID();
        LocalDate sep1 = LocalDate.of(2026, 9, 1);

        FinancialAccount acc1 = new FinancialAccount(
                businessId, "HBL Operating", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), sep1
        );
        acc1.setId(acc1Id);
        when(accountRepository.findByBusinessId(businessId)).thenReturn(List.of(acc1));

        // No posted movements
        when(movementRepository.findMovementSumsByBusinessIdAndDateRange(eq(businessId), any(), any())).thenReturn(List.of());
        when(movementRepository.findMovementSumsByBusinessIdAndCutoffDate(eq(businessId), any())).thenReturn(List.of());
        when(transactionRepository.findByBusinessIdAndDateRange(eq(businessId), any(), any())).thenReturn(List.of());
        mockEmptyObligationsAndAging();

        // 1 unposted draft of PKR 50,000
        RecordDraft draft = new RecordDraft();
        draft.setAmountInput("50000.00");
        draft.setStatus(DraftStatus.DRAFT);
        when(recordDraftRepository.findByBusinessIdAndStatus(eq(businessId), eq(DraftStatus.DRAFT)))
                .thenReturn(List.of(draft));

        // 2 recurring occurrences
        when(recurringOccurrenceRepository.countByBusinessIdAndStatus(eq(businessId), any()))
                .thenReturn(1L);

        MonthlyLedgerProjectionResponse response = projectionService.projectMonthlyLedger(context, "2026-10", null);

        // Actual cash is completely clean
        assertEquals("0.00", response.cashFlow().totalCashInflow());
        assertEquals("0.00", response.cashFlow().totalCashOutflow());
        assertEquals("100000.00", response.currencySummaries().get(0).totalClosingBalance());

        // Draft summary reports estimates accurately
        MonthlyDraftSummary ds = response.draftSummary();
        assertEquals(1, ds.unpostedDraftCount());
        assertEquals("50000.00", ds.unpostedDraftAmount());
        assertTrue(ds.recurringProjectedCount() >= 1);
        assertTrue(ds.isEstimate());
    }

    @Test
    @DisplayName("Fixture D: Accrual vs cash distinction separates collections/settlements from recognized obligation issuance")
    void testProjectMonthlyLedger_AccrualVsCashRevenueAndExpenses() {
        UUID acc1Id = UUID.randomUUID();
        FinancialAccount acc1 = new FinancialAccount(
                businessId, "HBL Operating", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), LocalDate.of(2026, 9, 1)
        );
        acc1.setId(acc1Id);
        when(accountRepository.findByBusinessId(businessId)).thenReturn(List.of(acc1));

        // 1. Direct cash sale: PKR 30,000
        // 2. Settlement of old receivable: PKR 20,000 (RECEIVABLE_COLLECTION)
        FinancialTransaction tx1 = new FinancialTransaction(
                businessId, acc1Id, TransactionEventType.DIRECT_SALE_RECEIPT, MovementDirection.INCREASE,
                new BigDecimal("30000.00"), "PKR", new BigDecimal("30000.00"), LocalDate.of(2026, 10, 5), "Cash sale", userId
        );
        tx1.setId(UUID.randomUUID());
        FinancialTransaction tx2 = new FinancialTransaction(
                businessId, acc1Id, TransactionEventType.RECEIVABLE_COLLECTION, MovementDirection.INCREASE,
                new BigDecimal("20000.00"), "PKR", new BigDecimal("20000.00"), LocalDate.of(2026, 10, 12), "Collection", userId
        );
        tx2.setId(UUID.randomUUID());
        when(transactionRepository.findByBusinessIdAndDateRange(eq(businessId), any(), any()))
                .thenReturn(List.of(tx1, tx2));

        // 3. New obligation issued in October: PKR 50,000 verified customer invoice
        FinancialObligation ob1 = new FinancialObligation(
                businessId, UUID.randomUUID(), ObligationDirection.RECEIVABLE, ObligationSourceType.CUSTOMER_INVOICE, null, "INV-101",
                LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 25), new BigDecimal("50000.00"), "PKR", "October invoice", userId
        );
        ob1.setId(UUID.randomUUID());
        when(obligationRepository.findByBusinessId(businessId)).thenReturn(List.of(ob1));

        mockEmptyObligationsAndAging();

        MonthlyLedgerProjectionResponse response = projectionService.projectMonthlyLedger(context, "2026-10", null);

        // Cash flow: Inflows = 30k direct sales + 20k collection = 50,000.00
        assertEquals("50000.00", response.cashFlow().totalCashInflow());
        assertEquals("30000.00", response.cashFlow().netDirectSaleReceipts());
        assertEquals("20000.00", response.cashFlow().netReceivableCollections());
        assertEquals("0.00", response.cashFlow().unclassifiedCashInflows());

        // Recognized Figures:
        // Cash Revenue = 30k (direct sales only; collections are settlement of asset, not revenue)
        // Accrual Revenue = 30k direct sales + 50k invoice issued in October = 80,000.00
        MonthlyRecognizedFigures rf = response.recognizedFigures();
        assertEquals("30000.00", rf.verifiedDirectSaleRevenue());
        assertEquals("50000.00", rf.invoiceIssuedEarnedRevenue());
        assertEquals("0.00", rf.provisionalReceivableCandidates());
        assertEquals(CapabilityStatus.AVAILABLE, rf.authoritativeAccrualRevenueStatus());
        assertEquals("80000.00", rf.authoritativeAccrualRevenue());
        assertEquals("80000.00", rf.revenueLessKnownOperatingExpensesBeforeCogs());
        assertEquals(CapabilityStatus.UNAVAILABLE, rf.cogsStatus());
        assertNull(rf.cogs());
        assertEquals(CapabilityStatus.UNAVAILABLE, rf.grossProfitStatus());
        assertNull(rf.grossProfit());
        assertEquals(CapabilityStatus.UNAVAILABLE, rf.operatingProfitStatus());
        assertNull(rf.operatingProfit());
        assertEquals(CapabilityStatus.UNAVAILABLE, rf.netProfitStatus());
        assertNull(rf.netProfit());
    }

    @Test
    @DisplayName("Revenue Provenance: Non-revenue and manual obligations are classified as provisional candidates or excluded")
    void testProjectMonthlyLedger_NonRevenueObligationsAndProvenanceUncertainty() {
        UUID acc1Id = UUID.randomUUID();
        FinancialAccount acc1 = new FinancialAccount(
                businessId, "HBL Operating", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), LocalDate.of(2026, 9, 1)
        );
        acc1.setId(acc1Id);
        when(accountRepository.findByBusinessId(businessId)).thenReturn(List.of(acc1));

        FinancialTransaction tx1 = new FinancialTransaction(
                businessId, acc1Id, TransactionEventType.DIRECT_SALE_RECEIPT, MovementDirection.INCREASE,
                new BigDecimal("30000.00"), "PKR", new BigDecimal("30000.00"), LocalDate.of(2026, 10, 5), "Cash sale", userId
        );
        tx1.setId(UUID.randomUUID());
        when(transactionRepository.findByBusinessIdAndDateRange(eq(businessId), any(), any()))
                .thenReturn(List.of(tx1));

        // 1. Approved invoice: PKR 50,000 (earned revenue)
        FinancialObligation obInvoice = new FinancialObligation(
                businessId, UUID.randomUUID(), ObligationDirection.RECEIVABLE, ObligationSourceType.CUSTOMER_INVOICE, "INV-01", "INV-101",
                LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 25), new BigDecimal("50000.00"), "PKR", "Earned sales", userId
        );
        obInvoice.setId(UUID.randomUUID());

        // 2. Manual declaration (unprovenanced debt/deposit/reimbursement): PKR 20,000 (provisional candidate)
        FinancialObligation obManual = new FinancialObligation(
                businessId, UUID.randomUUID(), ObligationDirection.RECEIVABLE, ObligationSourceType.MANUAL_DECLARATION, null, "MAN-01",
                LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 30), new BigDecimal("20000.00"), "PKR", "Security deposit receivable", userId
        );
        obManual.setId(UUID.randomUUID());

        // 3. Opening balance receivable from prior period: PKR 100,000 (balance sheet only, NOT earned revenue)
        FinancialObligation obOpening = new FinancialObligation(
                businessId, UUID.randomUUID(), ObligationDirection.RECEIVABLE, ObligationSourceType.OPENING_BALANCE, null, "OP-01",
                LocalDate.of(2026, 10, 1), null, new BigDecimal("100000.00"), "PKR", "Opening balance migration", userId
        );
        obOpening.setId(UUID.randomUUID());

        when(obligationRepository.findByBusinessId(businessId)).thenReturn(List.of(obInvoice, obManual, obOpening));
        mockEmptyObligationsAndAging();

        MonthlyLedgerProjectionResponse response = projectionService.projectMonthlyLedger(context, "2026-10", null);

        MonthlyRecognizedFigures rf = response.recognizedFigures();
        assertEquals("30000.00", rf.verifiedDirectSaleRevenue());
        assertEquals("50000.00", rf.invoiceIssuedEarnedRevenue());
        assertEquals("20000.00", rf.provisionalReceivableCandidates());
        // Authoritative revenue is ONLY verified direct sales + approved invoices (80,000.00)
        assertEquals("80000.00", rf.authoritativeAccrualRevenue());
        // Provisional total includes the manual candidate (100,000.00)
        assertEquals("100000.00", rf.provisionalTotalRevenue());
        // Status is AVAILABLE and Completeness is PARTIAL due to unprovenanced candidate
        assertEquals(CapabilityStatus.AVAILABLE, rf.authoritativeAccrualRevenueStatus());
        assertEquals(DataCompleteness.PARTIAL, rf.accrualRevenueCompleteness());
        // The 100k opening balance is completely excluded from revenue!
    }

    @Test
    @DisplayName("Cash Reconciliation: Unclassified posted cash transactions are accounted for and flagged as partial")
    void testProjectMonthlyLedger_UnclassifiedCashMovementsHandledAndFlagged() {
        UUID acc1Id = UUID.randomUUID();
        FinancialAccount acc1 = new FinancialAccount(
                businessId, "HBL Operating", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), LocalDate.of(2026, 9, 1)
        );
        acc1.setId(acc1Id);
        when(accountRepository.findByBusinessId(businessId)).thenReturn(List.of(acc1));

        // Transaction with null event type
        FinancialTransaction unclassifiedTx = new FinancialTransaction(
                businessId, acc1Id, null, MovementDirection.INCREASE,
                new BigDecimal("15000.00"), "PKR", new BigDecimal("15000.00"), LocalDate.of(2026, 10, 5), "Unclassified credit", userId
        );
        unclassifiedTx.setId(UUID.randomUUID());

        when(transactionRepository.findByBusinessIdAndDateRange(eq(businessId), any(), any()))
                .thenReturn(List.of(unclassifiedTx));
        mockEmptyObligationsAndAging();

        MonthlyLedgerProjectionResponse response = projectionService.projectMonthlyLedger(context, "2026-10", null);

        assertEquals("15000.00", response.cashFlow().unclassifiedCashInflows());
        assertEquals("15000.00", response.cashFlow().totalCashInflow());
        assertEquals(DataCompleteness.PARTIAL, response.completeness());
        assertTrue(response.warnings().stream().anyMatch(w -> w.contains("unclassified posted cash transactions")));
    }

    @Test
    @DisplayName("Fixture E: Multiple currencies are cleanly separated with zero cross-currency aggregation")
    void testProjectMonthlyLedger_MultiCurrencySeparation() {
        UUID accPkrId = UUID.randomUUID();
        UUID accUsdId = UUID.randomUUID();

        FinancialAccount accPkr = new FinancialAccount(
                businessId, "PKR Operating", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), LocalDate.of(2026, 9, 1)
        );
        accPkr.setId(accPkrId);

        FinancialAccount accUsd = new FinancialAccount(
                businessId, "USD Foreign", AccountType.BANK, "USD", 2, new BigDecimal("5000.00"), LocalDate.of(2026, 9, 1)
        );
        accUsd.setId(accUsdId);

        when(accountRepository.findByBusinessId(businessId)).thenReturn(List.of(accPkr, accUsd));
        when(movementRepository.findMovementSumsByBusinessIdAndDateRange(eq(businessId), any(), any())).thenReturn(List.of());
        when(movementRepository.findMovementSumsByBusinessIdAndCutoffDate(eq(businessId), any())).thenReturn(List.of());
        when(transactionRepository.findByBusinessIdAndDateRange(eq(businessId), any(), any())).thenReturn(List.of());
        mockEmptyObligationsAndAging();

        MonthlyLedgerProjectionResponse response = projectionService.projectMonthlyLedger(context, "2026-10", null);

        assertEquals(2, response.currencySummaries().size());
        CurrencyMonthlyCashSummary pkr = response.currencySummaries().stream().filter(s -> "PKR".equals(s.currency())).findFirst().orElseThrow();
        assertEquals("100000.00", pkr.totalClosingBalance());

        CurrencyMonthlyCashSummary usd = response.currencySummaries().stream().filter(s -> "USD".equals(s.currency())).findFirst().orElseThrow();
        assertEquals("5000.00", usd.totalClosingBalance());
    }

    @Test
    @DisplayName("Shadow Comparison: Identifies matching, differing, and unsupported metrics against historical MonthlyRecord")
    void testCompareWithMonthlyRecord_MatchingAndDifferingMetrics() {
        UUID acc1Id = UUID.randomUUID();
        FinancialAccount acc1 = new FinancialAccount(
                businessId, "HBL Operating", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), LocalDate.of(2026, 9, 1)
        );
        acc1.setId(acc1Id);
        when(accountRepository.findByBusinessId(businessId)).thenReturn(List.of(acc1));

        // Ledger: Inflow 30k, Outflow 8k, Net 22k, Closing 122k
        AccountMovementSum sumInc = mockMovementSum(acc1Id, MovementDirection.INCREASE, new BigDecimal("30000.00"));
        AccountMovementSum sumDec = mockMovementSum(acc1Id, MovementDirection.DECREASE, new BigDecimal("8000.00"));
        when(movementRepository.findMovementSumsByBusinessIdAndDateRange(eq(businessId), any(), any()))
                .thenReturn(List.of(sumInc, sumDec));
        when(movementRepository.findMovementSumsByBusinessIdAndCutoffDate(eq(businessId), any()))
                .thenReturn(List.of(sumInc, sumDec));

        FinancialTransaction tx1 = new FinancialTransaction(
                businessId, acc1Id, TransactionEventType.DIRECT_SALE_RECEIPT, MovementDirection.INCREASE,
                new BigDecimal("30000.00"), "PKR", new BigDecimal("30000.00"), LocalDate.of(2026, 10, 5), "Sale", userId
        );
        tx1.setId(UUID.randomUUID());
        FinancialTransaction tx2 = new FinancialTransaction(
                businessId, acc1Id, TransactionEventType.OPERATING_EXPENSE_PAYMENT, MovementDirection.DECREASE,
                new BigDecimal("8000.00"), "PKR", new BigDecimal("8000.00"), LocalDate.of(2026, 10, 10), "Opex", userId
        );
        tx2.setId(UUID.randomUUID());
        when(transactionRepository.findByBusinessIdAndDateRange(eq(businessId), any(), any()))
                .thenReturn(List.of(tx1, tx2));
        mockEmptyObligationsAndAging();

        // Historical MonthlyRecord with:
        // Cash Inflow: 30,000.00 (MATCH)
        // Cash Outflow: 10,000.00 (DIFFERENCE: ledger has 8,000.00)
        // Revenue: 30,000.00 (MATCH)
        // COGS: 5,000.00 (NOT_SUPPORTED_IN_LEDGER)
        MonthlyRecord record = new MonthlyRecord();
        record.setId(UUID.randomUUID());
        record.setUserId(businessId);
        record.setMonth("2026-10");
        record.setCashInflow(new BigDecimal("30000.00"));
        record.setCashOutflow(new BigDecimal("10000.00"));
        record.setRevenue(new BigDecimal("30000.00"));
        record.setOperatingExpenses(new BigDecimal("8000.00"));
        record.setCashBalanceEom(new BigDecimal("122000.00"));
        record.setCogs(new BigDecimal("5000.00"));

        when(monthlyRecordRepository.findByUserIdAndMonth(businessId, "2026-10")).thenReturn(Optional.of(record));

        MonthlyShadowComparisonResponse comp = projectionService.compareWithMonthlyRecord(context, "2026-10", null);

        assertTrue(comp.hasMonthlyRecord());
        assertNotNull(comp.monthlyRecordId());
        assertTrue(comp.matchingMetricCount() >= 3);
        assertTrue(comp.differingMetricCount() >= 1);
        assertTrue(comp.unsupportedMetricCount() >= 1);

        // Verify metric-level statuses
        MonthlyMetricComparison inflowComp = comp.comparisons().stream().filter(m -> "cashInflow".equals(m.metricKey())).findFirst().orElseThrow();
        assertEquals(ComparisonMetricStatus.MATCH, inflowComp.status());
        assertEquals("0.00", inflowComp.difference());
        assertEquals(RecognitionBasis.CASH_FLOW, inflowComp.recognitionBasis());

        MonthlyMetricComparison outflowComp = comp.comparisons().stream().filter(m -> "cashOutflow".equals(m.metricKey())).findFirst().orElseThrow();
        assertEquals(ComparisonMetricStatus.DIFFERENCE, outflowComp.status());
        assertEquals("-2000.00", outflowComp.difference());

        MonthlyMetricComparison cogsComp = comp.comparisons().stream().filter(m -> "cogs".equals(m.metricKey())).findFirst().orElseThrow();
        assertEquals(ComparisonMetricStatus.NOT_SUPPORTED_IN_LEDGER, cogsComp.status());
        assertEquals(RecognitionBasis.UNINTEGRATED_MODULE, cogsComp.recognitionBasis());
    }

    @Test
    @DisplayName("Shadow Comparison: Missing MonthlyRecord returns clear sole-source status")
    void testCompareWithMonthlyRecord_WhenNoMonthlyRecordExists() {
        when(accountRepository.findByBusinessId(businessId)).thenReturn(List.of());
        when(movementRepository.findMovementSumsByBusinessIdAndDateRange(eq(businessId), any(), any())).thenReturn(List.of());
        when(movementRepository.findMovementSumsByBusinessIdAndCutoffDate(eq(businessId), any())).thenReturn(List.of());
        when(transactionRepository.findByBusinessIdAndDateRange(eq(businessId), any(), any())).thenReturn(List.of());
        mockEmptyObligationsAndAging();

        when(monthlyRecordRepository.findByUserIdAndMonth(businessId, "2026-10")).thenReturn(Optional.empty());

        MonthlyShadowComparisonResponse comp = projectionService.compareWithMonthlyRecord(context, "2026-10", null);

        assertFalse(comp.hasMonthlyRecord());
        assertNull(comp.monthlyRecordId());
        assertTrue(comp.comparisons().isEmpty());
        assertTrue(comp.summary().contains("sole financial source"));
    }

    @Test
    @DisplayName("Security: Missing FINANCIAL_DATA_READ permission throws AccessDeniedException")
    void testProjectMonthlyLedger_MissingPermission_ThrowsAccessDenied() {
        when(authorizationService.hasRolePermission(eq(MembershipRole.OWNER), eq(BusinessPermission.FINANCIAL_DATA_READ)))
                .thenReturn(false);

        assertThrows(AccessDeniedException.class, () ->
                projectionService.projectMonthlyLedger(context, "2026-10", null));
    }

    @Test
    @DisplayName("Validation: Invalid month format throws FinancialQueryValidationException")
    void testProjectMonthlyLedger_InvalidMonth_ThrowsValidationException() {
        assertThrows(FinancialQueryValidationException.class, () ->
                projectionService.projectMonthlyLedger(context, "invalid-month", null));
    }

    private void mockEmptyObligationsAndAging() {
        ObligationAgingResult emptyAging = new ObligationAgingResult(
                LocalDate.of(2026, 10, 31),
                "PKR",
                new DirectionAgingSummary(ObligationDirection.RECEIVABLE, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), 0, 0, Map.of(), 0, 0, 0),
                new DirectionAgingSummary(ObligationDirection.PAYABLE, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), 0, 0, Map.of(), 0, 0, 0),
                List.of()
        );
        lenient().when(obligationService.getAging(any(), any())).thenReturn(emptyAging);
        lenient().when(advanceRepository.findByBusinessId(businessId)).thenReturn(List.of());
    }

    private AccountMovementSum mockMovementSum(UUID accountId, MovementDirection direction, BigDecimal amount) {
        AccountMovementSum sum = mock(AccountMovementSum.class);
        lenient().when(sum.getAccountId()).thenReturn(accountId);
        lenient().when(sum.getMovementDirection()).thenReturn(direction);
        lenient().when(sum.getTotalAmount()).thenReturn(amount);
        return sum;
    }
}
