package com.app.sme_health_backend.financialquery;

import com.app.sme_health_backend.accounting.draft.DraftCreateRequest;
import com.app.sme_health_backend.accounting.draft.RecordDraftService;
import com.app.sme_health_backend.accounting.dto.TransactionPostRequest;
import com.app.sme_health_backend.accounting.dto.TransactionReverseRequest;
import com.app.sme_health_backend.accounting.dto.TransferPostRequest;
import com.app.sme_health_backend.accounting.entity.AccountMovement;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.accounting.repository.AccountMovementRepository;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialTransactionRepository;
import com.app.sme_health_backend.accounting.service.TransactionPostService;
import com.app.sme_health_backend.accounting.service.TransactionReverseService;
import com.app.sme_health_backend.accounting.service.TransferService;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.counterparty.CounterpartyType;
import com.app.sme_health_backend.financialquery.dto.*;
import com.app.sme_health_backend.financialquery.model.CapabilityStatus;
import com.app.sme_health_backend.financialquery.model.DataCompleteness;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.dto.CreateBusinessRequest;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.entity.BusinessMembership;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.model.MembershipStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.repository.BusinessMembershipRepository;
import com.app.sme_health_backend.identity.service.BusinessService;
import com.app.sme_health_backend.obligation.*;
import com.app.sme_health_backend.obligation.dto.*;
import com.app.sme_health_backend.reconciliation.dto.ReconciliationMatchRequest;
import com.app.sme_health_backend.reconciliation.dto.StatementEntryItemRequest;
import com.app.sme_health_backend.reconciliation.dto.StatementImportRequest;
import com.app.sme_health_backend.reconciliation.dto.StatementImportResponse;
import com.app.sme_health_backend.reconciliation.entity.BankStatementEntry;
import com.app.sme_health_backend.reconciliation.repository.BankStatementEntryRepository;
import com.app.sme_health_backend.reconciliation.service.BankReconciliationService;
import com.app.sme_health_backend.reconciliation.service.BankStatementImportService;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.recurring.dto.RecurringRuleCreateRequest;
import com.app.sme_health_backend.recurring.dto.RecurringRuleResponse;
import com.app.sme_health_backend.recurring.repository.RecurringRuleOccurrenceRepository;
import com.app.sme_health_backend.recurring.service.RecurringRuleService;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Financial Query Facade Real PostgreSQL & Authenticated HTTP Integration Tests")
class FinancialQueryPostgreSqlIT extends DisposablePostgres {

    private static final String RAW_PASSWORD = "secure-password-2026!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private FinancialQueryFacade financialQueryFacade;

    @Autowired
    private FinancialAccountRepository accountRepository;

    @Autowired
    private FinancialTransactionRepository transactionRepository;

    @Autowired
    private AccountMovementRepository movementRepository;

    @Autowired
    private TransactionPostService transactionPostService;

    @Autowired
    private TransactionReverseService transactionReverseService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private RecordDraftService recordDraftService;

    @Autowired
    private CounterpartyRepository counterpartyRepository;

    @Autowired
    private FinancialObligationService obligationService;

    @Autowired
    private FinancialObligationRepository obligationRepository;

    @Autowired
    private ObligationPaymentAllocationRepository allocationRepository;

    @Autowired
    private ObligationPaymentScheduleRepository scheduleRepository;

    @Autowired
    private AdvanceService advanceService;

    @Autowired
    private PaymentPlanService paymentPlanService;

    @Autowired
    private RecurringRuleService recurringRuleService;

    @Autowired
    private RecurringRuleOccurrenceRepository recurringOccurrenceRepository;

    @Autowired
    private BankStatementImportService statementImportService;

    @Autowired
    private BankStatementEntryRepository statementEntryRepository;

    @Autowired
    private BankReconciliationService bankReconciliationService;

    @Autowired
    private MonthlyRecordRepository monthlyRecordRepository;

    @Autowired
    private BusinessService businessService;

    @Autowired
    private BusinessMembershipRepository membershipRepository;

    @Autowired
    private AppUserRepository userRepository;

    private BusinessAccessContext contextA;
    private BusinessAccessContext contextB;
    private UUID businessIdA;
    private UUID businessIdB;
    private AppUser ownerUserA;
    private AppUser ownerUserB;
    private UUID userIdA;
    private UUID userIdB;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(ZoneId.of("Asia/Karachi"));
        String uniqueA = UUID.randomUUID().toString().substring(0, 8);
        String uniqueB = UUID.randomUUID().toString().substring(0, 8);

        ownerUserA = createActiveUser("owner-a-" + uniqueA);
        userIdA = ownerUserA.getId();

        businessIdA = businessService.createBusiness(
                new CreateBusinessRequest("Query Business A " + uniqueA, "retail", "en", null, false, null, null, null),
                userIdA
        ).businessId();
        contextA = new BusinessAccessContext(userIdA, businessIdA, MembershipRole.OWNER);

        ownerUserB = createActiveUser("owner-b-" + uniqueB);
        userIdB = ownerUserB.getId();

        businessIdB = businessService.createBusiness(
                new CreateBusinessRequest("Query Business B " + uniqueB, "retail", "en", null, false, null, null, null),
                userIdB
        ).businessId();
        contextB = new BusinessAccessContext(userIdB, businessIdB, MembershipRole.OWNER);
    }

    @Test
    @DisplayName("Fixture A: Cash Position reconciles PKR 115,000 across transfer accounts, excludes drafts/obligations/recurring, and tracks historical dates & reversals")
    void testNumericalCashPositionFixtureReconcilesCorrectly() {
        LocalDate d0 = today.minusDays(30); // Opening date
        LocalDate d1 = today.minusDays(20); // Receipt +20,000
        LocalDate d2 = today.minusDays(15); // Expense -5,000
        LocalDate d3 = today.minusDays(10); // Internal transfer 10,000 (Bank -> Cash)

        // Account 1: HBL Main (Opening PKR 100,000.00 on d0)
        FinancialAccount acc1 = accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "HBL Main", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), d0)
        );

        // Account 2: Cash in Hand (Opening PKR 0.00 on d0)
        FinancialAccount acc2 = accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "Cash in Hand", AccountType.CASH, "PKR", 2, BigDecimal.ZERO, d0)
        );

        // Actual receipt: PKR 20,000 on Account 1 on d1
        TransactionPostRequest rcvReq = new TransactionPostRequest(
                acc1.getId(), "DIRECT_SALE_RECEIPT", "20000.00", d1.toString(), "Customer payment", null
        );
        transactionPostService.postTransaction(contextA, "TX-RCV-" + UUID.randomUUID(), rcvReq);

        // Actual expense: PKR 5,000 on Account 1 on d2
        TransactionPostRequest expReq = new TransactionPostRequest(
                acc1.getId(), "OPERATING_EXPENSE_PAYMENT", "5000.00", d2.toString(), "Office supplies", null
        );
        var expTx = transactionPostService.postTransaction(contextA, "TX-EXP-" + UUID.randomUUID(), expReq);

        // Internal transfer: PKR 10,000 from Account 1 to Account 2 on d3
        TransferPostRequest xferReq = new TransferPostRequest(
                acc1.getId(), acc2.getId(), "10000.00", d3.toString(), "Cash withdrawal to petty cash"
        );
        transferService.postTransfer(contextA, "TX-XFER-" + UUID.randomUUID(), xferReq);

        // Create non-cash / unposted items that MUST NOT pollute actual cash position:
        // 1. Unposted draft (PKR 50,000)
        recordDraftService.createDraft(contextA, "DRAFT-CASH-" + UUID.randomUUID(), new DraftCreateRequest(
                acc1.getId(), "DIRECT_SALE_RECEIPT", today.toString(), "50000.00", "Unposted draft receipt"
        ));
        // 2. Unpaid receivable obligation (PKR 40,000)
        Counterparty cust = counterpartyRepository.saveAndFlush(
                new Counterparty(businessIdA, "Pending Customer", CounterpartyType.CUSTOMER, null, null, null, null, null)
        );
        obligationService.createObligation(contextA, new ObligationCreateRequest(
                cust.getId(), "RECEIVABLE", "40000.00", d2.toString(), today.plusDays(10).toString(), "INV-UNPAID-1", "Unpaid invoice", "MANUAL_DECLARATION", null
        ));
        // 3. Scheduled recurring rule + due unposted draft occurrence (PKR 15,000)
        recurringRuleService.createRule(contextA, new RecurringRuleCreateRequest(
                "Monthly Internet", "ISP bill", "OPERATING_EXPENSE_PAYMENT",
                acc1.getId(), null, "15000.00", "MONTHLY", today.toString(), null
        ));
        recurringRuleService.generateDueOccurrencesForBusiness(contextA);

        // 1) Verify exact Section 7.A Cash-Position Fixture BEFORE reversal:
        //    Account 1: 100,000 + 20,000 - 5,000 - 10,000 = 105,000.00
        //    Account 2: 0 + 10,000 = 10,000.00
        //    Combined PKR cash across included transfer accounts: 115,000.00!
        FinancialQueryEnvelope<CashPositionResult> env115 = financialQueryFacade.getCashPosition(
                contextA, new CashPositionQuery(today.toString(), null)
        );
        assertEquals(CapabilityStatus.AVAILABLE, env115.status());
        assertEquals(DataCompleteness.COMPLETE, env115.completeness());
        assertEquals(2, env115.data().totalAccounts());
        assertEquals(2, env115.evidence().size());

        AccountCashPosition p1BeforeRev = env115.data().accounts().stream()
                .filter(a -> a.accountId().equals(acc1.getId())).findFirst().orElseThrow();
        assertEquals("100000.00", p1BeforeRev.openingBalance());
        assertEquals("5000.00", p1BeforeRev.netMovements());
        assertEquals("105000.00", p1BeforeRev.currentBalance());

        AccountCashPosition p2BeforeRev = env115.data().accounts().stream()
                .filter(a -> a.accountId().equals(acc2.getId())).findFirst().orElseThrow();
        assertEquals("0.00", p2BeforeRev.openingBalance());
        assertEquals("10000.00", p2BeforeRev.netMovements());
        assertEquals("10000.00", p2BeforeRev.currentBalance());

        CurrencyCashSummary pkr115 = env115.data().currencySummaries().get(0);
        assertEquals("PKR", pkr115.currency());
        assertEquals("100000.00", pkr115.openingBalance());
        assertEquals("15000.00", pkr115.netMovements());
        assertEquals("115000.00", pkr115.currentBalance());

        // 2) Verify historical as-of dates:
        //    Before opening date -> currentBalance 0.00, while account openingBalance metadata stays preserved (100000.00)
        var envPreOpen = financialQueryFacade.getCashPosition(contextA, new CashPositionQuery(d0.minusDays(5).toString(), null));
        assertEquals("0.00", envPreOpen.data().currencySummaries().get(0).currentBalance());
        AccountCashPosition preOpenAcc1 = envPreOpen.data().accounts().stream()
                .filter(a -> a.accountId().equals(acc1.getId())).findFirst().orElseThrow();
        assertEquals("100000.00", preOpenAcc1.openingBalance());
        assertEquals("0.00", preOpenAcc1.currentBalance());
        assertTrue(envPreOpen.warnings().stream().anyMatch(w -> w.contains("opened after asOfDate")));

        //    On opening date d0 -> 100,000.00
        var envOnOpen = financialQueryFacade.getCashPosition(contextA, new CashPositionQuery(d0.toString(), null));
        assertEquals("100000.00", envOnOpen.data().currencySummaries().get(0).currentBalance());

        //    After receipt on d1 (before expense) -> 120,000.00
        var envOnD1 = financialQueryFacade.getCashPosition(contextA, new CashPositionQuery(d1.toString(), null));
        assertEquals("120000.00", envOnD1.data().currencySummaries().get(0).currentBalance());

        // 3) Reverse the PKR 5,000 d2 expense and verify canonical V23 reversal effective-date behavior:
        //    In FinSight V23, compensating reversal movements carry original.getEffectiveDate() (d2),
        //    neutralizing the reversed expense from d2 onward while preserving d0 (100,000.00).
        TransactionReverseRequest revReq = new TransactionReverseRequest("Supplies returned and refunded");
        transactionReverseService.postReversal(contextA, "REV-EXP-" + UUID.randomUUID(), expTx.id(), revReq);

        var envOnD0AfterRev = financialQueryFacade.getCashPosition(contextA, new CashPositionQuery(d0.toString(), null));
        assertEquals("100000.00", envOnD0AfterRev.data().currencySummaries().get(0).currentBalance());

        var envAsOfD2AfterRev = financialQueryFacade.getCashPosition(contextA, new CashPositionQuery(d2.toString(), null));
        assertEquals("120000.00", envAsOfD2AfterRev.data().currencySummaries().get(0).currentBalance());

        //    As of today (including reversal + internal transfer): 120,000.00 total (110,000.00 Bank + 10,000.00 Cash)
        var envTodayAfterRev = financialQueryFacade.getCashPosition(contextA, new CashPositionQuery(today.toString(), null));
        CurrencyCashSummary pkr120 = envTodayAfterRev.data().currencySummaries().get(0);
        assertEquals("120000.00", pkr120.currentBalance());
        AccountCashPosition p1AfterRev = envTodayAfterRev.data().accounts().stream()
                .filter(a -> a.accountId().equals(acc1.getId())).findFirst().orElseThrow();
        assertEquals("110000.00", p1AfterRev.currentBalance());
        AccountCashPosition p2AfterRev = envTodayAfterRev.data().accounts().stream()
                .filter(a -> a.accountId().equals(acc2.getId())).findFirst().orElseThrow();
        assertEquals("10000.00", p2AfterRev.currentBalance());
    }

    @Test
    @DisplayName("Fixture B: Overdue Receivables reconciles 50k original - 20k settlement = 30k overdue, plus advance applications, reversals, as-of aging & pagination")
    void testNumericalReceivablesFixtureWithAgingAdvanceReversalAndPagination() {
        FinancialAccount bankAcc = accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "Bank Account", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), today.minusMonths(2))
        );

        Counterparty customer = counterpartyRepository.saveAndFlush(
                new Counterparty(businessIdA, "Apex Traders", CounterpartyType.CUSTOMER, null, null, null, null, null)
        );

        // Create receivable obligation: PKR 50,000 issued 45 days ago, due 15 days ago
        LocalDate issueDate = today.minusDays(45);
        LocalDate dueDate = today.minusDays(15);
        ObligationDetailResponse obResp = obligationService.createObligation(contextA, new ObligationCreateRequest(
                customer.getId(),
                "RECEIVABLE",
                "50000.00",
                issueDate.toString(),
                dueDate.toString(),
                "INV-APEX-001",
                "Consulting service invoice",
                "MANUAL_DECLARATION",
                null
        ));

        // Partial settlement: PKR 20,000 on today - 5 days
        ObligationSettlementResponse settleResp = obligationService.recordSettlement(
                contextA,
                obResp.id(),
                "SETTLE-001-" + UUID.randomUUID(),
                new ObligationSettlementRequest(
                        bankAcc.getId(),
                        "20000.00",
                        today.minusDays(5).toString(),
                        "Cheque partial settlement",
                        null
                )
        );

        // 1) Verify exact Section 7.B Fixture as of today: 50,000 - 20,000 = 30,000 in OVERDUE_1_30
        FinancialQueryEnvelope<OverdueReceivablesResult> env = financialQueryFacade.getOverdueReceivables(
                contextA, new OverdueReceivablesQuery(today.toString(), 50)
        );
        assertEquals(CapabilityStatus.AVAILABLE, env.status());
        assertEquals(DataCompleteness.COMPLETE, env.completeness());
        assertEquals(1, env.evidence().size());

        OverdueReceivablesResult res = env.data();
        assertEquals("30000.00", res.totalOverdueAmount());
        assertEquals(1, res.overdueCount());

        OverdueReceivableItem item = res.items().get(0);
        assertEquals(obResp.id(), item.obligationId());
        assertEquals("Apex Traders", item.counterpartyName());
        assertEquals("50000.00", item.originalAmount());
        assertEquals("20000.00", item.settledAmount());
        assertEquals("30000.00", item.outstandingAmount());
        assertEquals(15, item.overdueDays());
        assertEquals("OVERDUE_1_30", item.bucketCode());

        AgingBucketSummary bucket1_30 = res.agingBuckets().stream()
                .filter(b -> "OVERDUE_1_30".equals(b.bucketCode())).findFirst().orElseThrow();
        assertEquals("30000.00", bucket1_30.amount());
        assertEquals(1, bucket1_30.count());

        // 2) Historical as-of date BEFORE partial settlement (today - 10 days -> 5 days overdue, full 50,000.00 outstanding)
        var envBeforeSettle = financialQueryFacade.getOverdueReceivables(
                contextA, new OverdueReceivablesQuery(today.minusDays(10).toString(), 50)
        );
        assertEquals("50000.00", envBeforeSettle.data().totalOverdueAmount());
        assertEquals(5, envBeforeSettle.data().items().get(0).overdueDays());

        // 3) Customer advance receipt (PKR 10,000) & application -> remaining overdue becomes 20,000.00
        AdvanceDetailResponse adv = advanceService.createAdvance(contextA, "ADV-" + UUID.randomUUID(), new AdvanceCreateRequest(
                customer.getId(), "RECEIVABLE", bankAcc.getId(), today.minusDays(4).toString(), "10000.00", "ADV-APEX-1", "Customer deposit"
        ));
        AdvanceDetailResponse appliedAdv = advanceService.applyAdvance(contextA, "APPLY-" + UUID.randomUUID(), adv.id(), new AdvanceApplyRequest(
                obResp.id(), "10000.00", today.minusDays(3).toString(), "Apply deposit"
        ));
        var envAfterAdvance = financialQueryFacade.getOverdueReceivables(
                contextA, new OverdueReceivablesQuery(today.toString(), 50)
        );
        assertEquals("20000.00", envAfterAdvance.data().totalOverdueAmount());
        assertEquals("30000.00", envAfterAdvance.data().items().get(0).settledAmount());
        assertEquals(obligationService.getTotalsSummary(contextA, today.minusDays(4).toString()).overdueReceivables(),
                financialQueryFacade.getOverdueReceivables(contextA, new OverdueReceivablesQuery(today.minusDays(4).toString(), 50)).data().totalOverdueAmount());

        // 4) Settlement reversal -> restores PKR 20,000 cash settlement, leaving 40,000.00 overdue (50,000 - 10,000 advance)
        transactionReverseService.postReversal(
                contextA,
                "REV-SETTLE-" + UUID.randomUUID(),
                settleResp.transactionId(),
                new TransactionReverseRequest("Cheque dishonoured")
        );
        var envAfterRev = financialQueryFacade.getOverdueReceivables(
                contextA, new OverdueReceivablesQuery(today.toString(), 50)
        );
        assertEquals("40000.00", envAfterRev.data().totalOverdueAmount());

        // 4b) Advance application reversal -> restores PKR 10,000 back to 50,000.00 and agrees with getTotalsSummary on both historical & current dates
        UUID advAppId = appliedAdv.applications().get(0).allocationId();
        advanceService.reverseApplication(contextA, "REV-ADV-" + UUID.randomUUID(), adv.id(), advAppId,
                new AdvanceApplicationReverseRequest("Deposit reallocated"));
        var envAfterAdvRev = financialQueryFacade.getOverdueReceivables(
                contextA, new OverdueReceivablesQuery(today.minusDays(2).toString(), 50)
        );
        assertEquals("50000.00", envAfterAdvRev.data().totalOverdueAmount());
        assertEquals(obligationService.getTotalsSummary(contextA, today.minusDays(2).toString()).overdueReceivables(),
                envAfterAdvRev.data().totalOverdueAmount());

        // Re-apply PKR 10,000 advance so step 5 pagination verifies 40,000 + 15,000 = 55,000.00
        advanceService.applyAdvance(contextA, "APPLY-2-" + UUID.randomUUID(), adv.id(), new AdvanceApplyRequest(
                obResp.id(), "10000.00", today.minusDays(2).toString(), "Re-apply deposit"
        ));

        // 5) Add a second older overdue receivable (PKR 15,000, due 40 days ago -> OVERDUE_31_60) and verify pagination
        obligationService.createObligation(contextA, new ObligationCreateRequest(
                customer.getId(), "RECEIVABLE", "15000.00", today.minusDays(60).toString(), today.minusDays(40).toString(),
                "INV-APEX-002", "Older consulting invoice", "MANUAL_DECLARATION", null
        ));
        var page1 = financialQueryFacade.getOverdueReceivables(
                contextA, new OverdueReceivablesQuery(today.toString(), 1, 0)
        );
        assertEquals(DataCompleteness.PARTIAL, page1.completeness());
        assertEquals("55000.00", page1.data().totalOverdueAmount());
        assertEquals(2, page1.data().overdueCount());
        assertEquals(1, page1.data().items().size());
        assertEquals("INV-APEX-002", page1.data().items().get(0).referenceNumber());

        var page2 = financialQueryFacade.getOverdueReceivables(
                contextA, new OverdueReceivablesQuery(today.toString(), 1, 1)
        );
        assertEquals(DataCompleteness.PARTIAL, page2.completeness());
        assertEquals(1, page2.data().items().size());
        assertEquals("INV-APEX-001", page2.data().items().get(0).referenceNumber());
    }

    @Test
    @DisplayName("Fixture C: Upcoming Obligations avoids parent/installment double counting, reflects partial settlements, and never classifies V29 recurring rules as liabilities")
    void testNumericalUpcomingObligationsWithInstallmentsPartialSettlementAndRecurringProjection() {
        FinancialAccount bankAcc = accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "Meezan Bank", AccountType.BANK, "PKR", 2, new BigDecimal("200000.00"), today.minusMonths(1))
        );

        Counterparty supplier = counterpartyRepository.saveAndFlush(
                new Counterparty(businessIdA, "Global Logistics", CounterpartyType.SUPPLIER, null, null, null, null, null)
        );

        // Payable obligation: PKR 50,000 due in 25 days
        ObligationDetailResponse obResp = obligationService.createObligation(contextA, new ObligationCreateRequest(
                supplier.getId(),
                "PAYABLE",
                "50000.00",
                today.minusDays(5).toString(),
                today.plusDays(25).toString(),
                "BILL-GL-55",
                "Freight charges",
                "MANUAL_DECLARATION",
                null
        ));

        // Set installment schedule: 2 x PKR 25,000 due in 10 and 20 days
        paymentPlanService.setPaymentPlan(contextA, "PLAN-" + UUID.randomUUID(), obResp.id(), new PaymentPlanRequest(List.of(
                new PaymentPlanInstallmentRequest(1, today.plusDays(10).toString(), "25000.00", "Installment 1"),
                new PaymentPlanInstallmentRequest(2, today.plusDays(20).toString(), "25000.00", "Installment 2")
        )));

        // Create a real active V29 recurring operating expense rule via RecurringRuleService (PKR 15,000 starting in 15 days)
        // Note: No artificial RecurringRuleOccurrence row is inserted; active rule template is projected cleanly!
        RecurringRuleResponse ruleResp = recurringRuleService.createRule(contextA, new RecurringRuleCreateRequest(
                "Warehouse Rent",
                "Monthly warehouse rental",
                "OPERATING_EXPENSE_PAYMENT",
                bankAcc.getId(),
                supplier.getId(),
                "15000.00",
                "MONTHLY",
                today.plusDays(15).toString(),
                null
        ));
        assertNotNull(ruleResp.id());

        // 1) Before partial settlement: 2 installments total 50,000.00 (no parent/installment double counting; no recurring liability!)
        UpcomingObligationsQuery query = new UpcomingObligationsQuery("PAYABLE", today.toString(), today.plusDays(30).toString());
        FinancialQueryEnvelope<UpcomingObligationsResult> env = financialQueryFacade.getUpcomingObligations(contextA, query);

        assertEquals(CapabilityStatus.AVAILABLE, env.status());
        UpcomingObligationsResult res = env.data();
        assertNotNull(res);
        assertEquals("50000.00", res.totalUpcomingPayables());
        assertEquals(2, res.obligations().size());

        // Recurring rule is reported strictly in recurringProjections as estimate, NOT in recognized obligations
        assertEquals(1, res.recurringProjections().size());
        RecurringProjectionItem proj = res.recurringProjections().get(0);
        assertTrue(proj.isEstimate());
        assertEquals("Warehouse Rent", proj.ruleName());
        assertEquals("15000.00", proj.amount());
        assertTrue(env.warnings().stream().anyMatch(w -> w.contains("non-binding estimates")));

        // 2) Record partial settlement of PKR 20,000 against BILL-GL-55 -> remaining obligation is PKR 30,000.00
        ObligationSettlementResponse paySettle = obligationService.recordSettlement(
                contextA,
                obResp.id(),
                "SETTLE-PAY-" + UUID.randomUUID(),
                new ObligationSettlementRequest(
                        bankAcc.getId(),
                        "20000.00",
                        today.toString(),
                        "Partial supplier payment",
                        null
                )
        );

        FinancialQueryEnvelope<UpcomingObligationsResult> envAfterPartial = financialQueryFacade.getUpcomingObligations(contextA, query);
        UpcomingObligationsResult resAfterPartial = envAfterPartial.data();
        assertEquals("30000.00", resAfterPartial.totalUpcomingPayables(),
                "Partial settlement of 20k on 50k scheduled payable must reduce upcoming payables to exact 30,000.00");
        assertEquals(2, resAfterPartial.obligations().size());
        assertEquals("5000.00", resAfterPartial.obligations().get(0).amount());
        assertEquals("20000.00", resAfterPartial.obligations().get(0).paidAmount());
        assertEquals("30000.00", resAfterPartial.obligations().get(0).outstandingAmount());
        assertEquals("25000.00", resAfterPartial.obligations().get(1).amount());

        // 3) Reverse the PKR 20,000 settlement -> restores upcoming payables back to exact 50,000.00 (25,000.00 + 25,000.00)
        transactionReverseService.postReversal(
                contextA,
                "REV-PAY-" + UUID.randomUUID(),
                paySettle.transactionId(),
                new TransactionReverseRequest("Payment recalled")
        );
        FinancialQueryEnvelope<UpcomingObligationsResult> envAfterPayRev = financialQueryFacade.getUpcomingObligations(contextA, query);
        assertEquals("50000.00", envAfterPayRev.data().totalUpcomingPayables());
        assertEquals("25000.00", envAfterPayRev.data().obligations().get(0).amount());
        assertEquals("25000.00", envAfterPayRev.data().obligations().get(1).amount());
    }

    @Test
    @DisplayName("Fixture D: Unavailable Tools return explicit typed UNAVAILABLE envelopes with null data and clear dependency reasons")
    void testUnavailableToolsReturnTypedUnavailableEnvelope() {
        UUID dummyId = UUID.randomUUID();

        var p1 = financialQueryFacade.getProjectFinancials(contextA, dummyId);
        assertEquals(CapabilityStatus.UNAVAILABLE, p1.status());
        assertEquals(DataCompleteness.EMPTY, p1.completeness());
        assertNull(p1.data());
        assertTrue(p1.unavailableReason().contains("F05"));

        var p2 = financialQueryFacade.getProjectFundingGap(contextA, dummyId);
        assertEquals(CapabilityStatus.UNAVAILABLE, p2.status());
        assertEquals(DataCompleteness.EMPTY, p2.completeness());
        assertNull(p2.data());
        assertTrue(p2.unavailableReason().contains("F07"));

        var p3 = financialQueryFacade.getMarginDrivers(contextA, "cogs");
        assertEquals(CapabilityStatus.UNAVAILABLE, p3.status());
        assertEquals(DataCompleteness.EMPTY, p3.completeness());
        assertNull(p3.data());
        assertTrue(p3.unavailableReason().contains("F08"));

        var p4 = financialQueryFacade.getFxSettlementImpact(contextA, "fx");
        assertEquals(CapabilityStatus.UNAVAILABLE, p4.status());
        assertEquals(DataCompleteness.EMPTY, p4.completeness());
        assertNull(p4.data());
        assertTrue(p4.unavailableReason().contains("F11"));
    }

    @Test
    @DisplayName("Fixture E: End-to-End Authenticated HTTP Security across OWNER, ACCOUNTANT, MANAGER, VIEWER, Session Boundaries, Cross-Tenant Overrides & Query Bounds")
    void testEndToEndHttpSecurityRolesTenantIsolationAndValidation() throws Exception {
        // Seed Business A account (PKR 75,000) and Business B account (PKR 88,888) + Business B overdue receivable
        FinancialAccount accA = accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "Business A Bank", AccountType.BANK, "PKR", 2, new BigDecimal("75000.00"), today.minusDays(20))
        );
        FinancialAccount accB = accountRepository.saveAndFlush(
                new FinancialAccount(businessIdB, "Business B Secret Bank", AccountType.BANK, "PKR", 2, new BigDecimal("88888.00"), today.minusDays(20))
        );
        Counterparty custB = counterpartyRepository.saveAndFlush(
                new Counterparty(businessIdB, "Secret Customer B", CounterpartyType.CUSTOMER, null, null, null, null, null)
        );
        obligationService.createObligation(contextB, new ObligationCreateRequest(
                custB.getId(), "RECEIVABLE", "99000.00", today.minusDays(30).toString(), today.minusDays(10).toString(),
                "INV-SECRET-B", "Business B invoice", "MANUAL_DECLARATION", null
        ));

        // 1) Unauthenticated HTTP requests must return 401 Unauthorized
        mockMvc.perform(get("/api/finance/query/capabilities")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/finance/query/cash-position")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/finance/query/overdue-receivables")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/finance/query/upcoming-obligations")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/finance/query/project-financials")).andExpect(status().isUnauthorized());

        // 2) Provision real users for ACCOUNTANT, MANAGER, VIEWER on Business A, plus an unaffiliated user with no active business
        AppUser accountantUser = createMemberUser("accountant-a", businessIdA, MembershipRole.ACCOUNTANT);
        AppUser managerUser = createMemberUser("manager-a", businessIdA, MembershipRole.MANAGER);
        AppUser viewerUser = createMemberUser("viewer-a", businessIdA, MembershipRole.VIEWER);
        AppUser noBizUser = createActiveUser("nobiz-" + UUID.randomUUID().toString().substring(0, 8));

        // Verify all 4 roles (OWNER, ACCOUNTANT, MANAGER, VIEWER) can read Business A's authorized endpoints
        // and CANNOT override tenant scope via businessId parameter, X-Business-Id header, or Business B entity IDs!
        for (AppUser roleUser : List.of(ownerUserA, accountantUser, managerUser, viewerUser)) {
            LoginSession session = loginAndSelectBusiness(roleUser, businessIdA);

            mockMvc.perform(withSession(get("/api/finance/query/capabilities"), session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.businessId").value(businessIdA.toString()))
                    .andExpect(jsonPath("$.capabilities.length()").value(7));

            mockMvc.perform(withSession(get("/api/finance/query/cash-position")
                            .param("businessId", businessIdB.toString())
                            .header("X-Business-Id", businessIdB.toString()), session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.businessId").value(businessIdA.toString()))
                    .andExpect(jsonPath("$.data.totalAccounts").value(1))
                    .andExpect(jsonPath("$.data.accounts[0].accountId").value(accA.getId().toString()))
                    .andExpect(jsonPath("$.data.currencySummaries[0].currentBalance").value("75000.00"));

            mockMvc.perform(withSession(get("/api/finance/query/overdue-receivables")
                            .param("businessId", businessIdB.toString()), session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.businessId").value(businessIdA.toString()))
                    .andExpect(jsonPath("$.data.overdueCount").value(0))
                    .andExpect(jsonPath("$.data.totalOverdueAmount").value("0.00"));

            mockMvc.perform(withSession(get("/api/finance/query/upcoming-obligations"), session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.businessId").value(businessIdA.toString()));

            mockMvc.perform(withSession(get("/api/finance/query/project-financials")
                            .param("projectId", accB.getId().toString()), session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.businessId").value(businessIdA.toString()))
                    .andExpect(jsonPath("$.status").value("UNAVAILABLE"));
        }

        // 3) User without active business membership cannot access Business A or query endpoints
        LoginSession noBizSession = loginOnly(noBizUser);
        int noBizStatus = mockMvc.perform(withSession(get("/api/finance/query/cash-position"), noBizSession))
                .andReturn().getResponse().getStatus();
        assertTrue(noBizStatus == 403 || noBizStatus == 409, "User with no active business must be rejected, got " + noBizStatus);

        // Attempting to activate Business B as Owner A must be denied
        LoginSession ownerASession = loginAndSelectBusiness(ownerUserA, businessIdA);
        int crossActivateStatus = mockMvc.perform(withSession(post("/api/businesses/active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessId\":\"" + businessIdB + "\"}"), ownerASession))
                .andReturn().getResponse().getStatus();
        assertTrue(crossActivateStatus == 403 || crossActivateStatus == 404,
                "Cross-business activation must be denied, got " + crossActivateStatus);

        // 4) Query bounds and input validation over HTTP -> 400 Bad Request
        mockMvc.perform(withSession(get("/api/finance/query/cash-position").param("asOfDate", "31/12/2026"), ownerASession))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withSession(get("/api/finance/query/cash-position").param("accountType", "INVALID"), ownerASession))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withSession(get("/api/finance/query/overdue-receivables").param("limit", "0"), ownerASession))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withSession(get("/api/finance/query/overdue-receivables").param("limit", "101"), ownerASession))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withSession(get("/api/finance/query/overdue-receivables").param("offset", "-1"), ownerASession))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withSession(get("/api/finance/query/upcoming-obligations")
                        .param("fromDate", "2026-10-20").param("toDate", "2026-10-10"), ownerASession))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withSession(get("/api/finance/query/upcoming-obligations")
                        .param("fromDate", "2026-01-01").param("toDate", "2026-05-01"), ownerASession))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withSession(get("/api/finance/query/upcoming-obligations").param("direction", "UNKNOWN"), ownerASession))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withSession(get("/api/finance/query/project-funding-gap").param("projectId", "bad-uuid"), ownerASession))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withSession(get("/api/finance/query/margin-drivers").param("query", "q".repeat(501)), ownerASession))
                .andExpect(status().isBadRequest());

        // 5) Logged-out session boundary -> 401 Unauthorized
        mockMvc.perform(withSession(post("/api/auth/logout"), ownerASession)).andExpect(status().isNoContent());
        mockMvc.perform(withSession(get("/api/finance/query/cash-position"), ownerASession)).andExpect(status().isUnauthorized());

        // 6) Direct Java call with null context or unauthorized null role -> AccessDeniedException
        assertThrows(AccessDeniedException.class, () -> financialQueryFacade.getCashPosition(null, null));
    }

    @Test
    @DisplayName("Fixture F: PostgreSQL Financial Integrity — bit-for-bit value-level snapshots remain strictly unchanged across all financial, reconciliation, monthly, score & audit tables")
    void testReadOnlyDatabaseValueLevelSnapshotInvariants() throws Exception {
        // Populate real records across all financial domains so value-level snapshots verify populated rows!
        FinancialAccount acc = accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "Integrity Bank", AccountType.BANK, "PKR", 2, new BigDecimal("250000.00"), today.minusDays(30))
        );
        var saleTx = transactionPostService.postTransaction(
                contextA, "INT-TX-" + UUID.randomUUID(),
                new TransactionPostRequest(acc.getId(), "DIRECT_SALE_RECEIPT", "35000.00", today.minusDays(5).toString(), "Integrity sale", null)
        );
        AccountMovement saleMov = movementRepository.findByTransactionIdOrderByCreatedAtAscIdAsc(saleTx.id()).get(0);

        recordDraftService.createDraft(contextA, "DRAFT-INT-" + UUID.randomUUID(), new DraftCreateRequest(
                acc.getId(), "OPERATING_EXPENSE_PAYMENT", today.toString(), "4500.00", "Integrity draft"
        ));

        Counterparty cust = counterpartyRepository.saveAndFlush(
                new Counterparty(businessIdA, "Integrity Customer", CounterpartyType.BOTH, null, null, null, null, null)
        );
        ObligationDetailResponse ob = obligationService.createObligation(contextA, new ObligationCreateRequest(
                cust.getId(), "RECEIVABLE", "60000.00", today.minusDays(25).toString(), today.minusDays(5).toString(),
                "INV-INT-1", "Integrity receivable", "MANUAL_DECLARATION", null
        ));
        obligationService.recordSettlement(contextA, ob.id(), "INT-SET-" + UUID.randomUUID(),
                new ObligationSettlementRequest(acc.getId(), "15000.00", today.minusDays(2).toString(), "Partial", null));

        ObligationDetailResponse payOb = obligationService.createObligation(contextA, new ObligationCreateRequest(
                cust.getId(), "PAYABLE", "40000.00", today.minusDays(3).toString(), today.plusDays(20).toString(),
                "BILL-INT-1", "Integrity payable", "MANUAL_DECLARATION", null
        ));
        paymentPlanService.setPaymentPlan(contextA, "INT-PLAN-" + UUID.randomUUID(), payOb.id(), new PaymentPlanRequest(List.of(
                new PaymentPlanInstallmentRequest(1, today.plusDays(10).toString(), "40000.00", "Single installment")
        )));

        recurringRuleService.createRule(contextA, new RecurringRuleCreateRequest(
                "Integrity Utility", "Power bill", "OPERATING_EXPENSE_PAYMENT",
                acc.getId(), cust.getId(), "8000.00", "MONTHLY", today.toString(), null
        ));
        recurringRuleService.generateDueOccurrencesForBusiness(contextA);

        StatementImportResponse imp = statementImportService.importStatement(contextA, null, new StatementImportRequest(
                acc.getId(), "integrity.csv", null, null, null, null,
                List.of(new StatementEntryItemRequest(1, today.minusDays(5), null, MovementDirection.INCREASE,
                        new BigDecimal("35000.00"), "Integrity sale", "REF-INT", null, null))
        ));
        BankStatementEntry entry = statementEntryRepository.findByBusinessIdAndImportIdOrderBySourceRowNumberAsc(businessIdA, imp.id()).get(0);
        bankReconciliationService.matchEntry(contextA, entry.getId(), new ReconciliationMatchRequest(saleMov.getId()));

        MonthlyRecord mr = new MonthlyRecord();
        mr.setUserId(businessIdA);
        mr.setMonth("2026-09");
        mr.setCashInflow(new BigDecimal("120000.00"));
        mr.setCashOutflow(new BigDecimal("80000.00"));
        mr.setRevenue(new BigDecimal("120000.00"));
        mr.setOperatingExpenses(new BigDecimal("80000.00"));
        mr.setCashBalanceEom(new BigDecimal("250000.00"));
        mr.setUpdatedAt(LocalDateTime.of(2026, 9, 30, 12, 0));
        monthlyRecordRepository.saveAndFlush(mr);

        jdbcTemplate.update("""
                INSERT INTO score_results (id, user_id, month, composite_score, band, component_scores, weakest_component, data_completeness, computed_at, methodology_version)
                VALUES (?, ?, '2026-09', 78.50, 'good', '{"cashFlowStability":80,"profitability":75,"repaymentCapacity":80,"financialTrend":78,"recordCompliance":80}'::jsonb, 'profitability', 1.00, TIMESTAMP '2026-09-30 12:05:00', 'health-score-v1')
                """, UUID.randomUUID(), businessIdA);

        // Authenticate HTTP session BEFORE taking snapshot so login session creation does not happen between snapshots
        LoginSession session = loginAndSelectBusiness(ownerUserA, businessIdA);

        List<String> auditedTables = List.of(
                "financial_accounts",
                "financial_transactions",
                "account_movements",
                "record_drafts",
                "financial_obligations",
                "obligation_payment_allocations",
                "obligation_payment_schedules",
                "counterparty_advances",
                "recurring_rules",
                "recurring_rule_occurrences",
                "bank_statement_imports",
                "bank_statement_entries",
                "bank_reconciliation_matches",
                "monthly_records",
                "score_results",
                "security_audit_events"
        );

        Map<String, TableSnapshot> beforeSnapshots = captureTableSnapshots(auditedTables);

        // Execute all 8 C11 facade methods directly
        financialQueryFacade.getCapabilities(contextA);
        financialQueryFacade.getCashPosition(contextA, new CashPositionQuery(today.toString(), null));
        financialQueryFacade.getOverdueReceivables(contextA, new OverdueReceivablesQuery(today.toString(), 50, 0));
        financialQueryFacade.getUpcomingObligations(contextA, new UpcomingObligationsQuery("ALL", today.toString(), today.plusDays(30).toString()));
        financialQueryFacade.getProjectFinancials(contextA, UUID.randomUUID());
        financialQueryFacade.getProjectFundingGap(contextA, UUID.randomUUID());
        financialQueryFacade.getMarginDrivers(contextA, "query");
        financialQueryFacade.getFxSettlementImpact(contextA, "query");

        // Execute all 8 C11 HTTP GET endpoints through full Spring Security filter chain
        mockMvc.perform(withSession(get("/api/finance/query/capabilities"), session)).andExpect(status().isOk());
        mockMvc.perform(withSession(get("/api/finance/query/cash-position"), session)).andExpect(status().isOk());
        mockMvc.perform(withSession(get("/api/finance/query/overdue-receivables"), session)).andExpect(status().isOk());
        mockMvc.perform(withSession(get("/api/finance/query/upcoming-obligations"), session)).andExpect(status().isOk());
        mockMvc.perform(withSession(get("/api/finance/query/project-financials"), session)).andExpect(status().isOk());
        mockMvc.perform(withSession(get("/api/finance/query/project-funding-gap"), session)).andExpect(status().isOk());
        mockMvc.perform(withSession(get("/api/finance/query/margin-drivers"), session)).andExpect(status().isOk());
        mockMvc.perform(withSession(get("/api/finance/query/fx-settlement-impact"), session)).andExpect(status().isOk());

        Map<String, TableSnapshot> afterSnapshots = captureTableSnapshots(auditedTables);

        for (String table : auditedTables) {
            TableSnapshot before = beforeSnapshots.get(table);
            TableSnapshot after = afterSnapshots.get(table);
            assertEquals(before.rowCount(), after.rowCount(), "Row count changed for table " + table);
            assertEquals(before.sha256Digest(), after.sha256Digest(), "Value-level row content or audit timestamp mutated in table " + table);
        }
    }

    private record TableSnapshot(long rowCount, String sha256Digest) {}

    private Map<String, TableSnapshot> captureTableSnapshots(List<String> tables) throws Exception {
        Map<String, TableSnapshot> snapshots = new LinkedHashMap<>();
        for (String table : tables) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM " + table + " ORDER BY id ASC"
            );
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (Map<String, Object> row : rows) {
                TreeMap<String, String> sortedCols = new TreeMap<>();
                for (Map.Entry<String, Object> e : row.entrySet()) {
                    sortedCols.put(e.getKey(), Objects.toString(e.getValue(), "<NULL>"));
                }
                md.update(sortedCols.toString().getBytes(StandardCharsets.UTF_8));
                md.update((byte) '\n');
            }
            snapshots.put(table, new TableSnapshot(rows.size(), HexFormat.of().formatHex(md.digest())));
        }
        return snapshots;
    }

    private AppUser createActiveUser(String prefix) {
        AppUser user = new AppUser();
        user.setEmail(prefix + "-" + UUID.randomUUID().toString().substring(0, 6) + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(RAW_PASSWORD));
        user.setFullName("Test " + prefix);
        user.setAccountStatus(com.app.sme_health_backend.identity.model.AccountStatus.ACTIVE);
        user.setMustChangePassword(false);
        return userRepository.saveAndFlush(user);
    }

    private AppUser createMemberUser(String prefix, UUID businessId, MembershipRole role) {
        AppUser user = createActiveUser(prefix);
        BusinessMembership membership = new BusinessMembership(user.getId(), businessId, role, MembershipStatus.ACTIVE);
        membershipRepository.saveAndFlush(membership);
        return user;
    }

    private record LoginSession(Cookie sessionCookie, Cookie xsrfCookie) {}

    private LoginSession loginOnly(AppUser user) throws Exception {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie preLoginCookie = csrfResult.getResponse().getCookie("FINSIGHT_SESSION");
        Cookie xsrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

        MockHttpServletRequestBuilder loginReq = post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + RAW_PASSWORD + "\"}");

        if (preLoginCookie != null) {
            loginReq.cookie(preLoginCookie);
        }
        if (xsrfCookie != null) {
            loginReq.cookie(xsrfCookie).header("X-XSRF-TOKEN", xsrfCookie.getValue());
        } else {
            loginReq.with(csrf());
        }

        MvcResult loginResult = mockMvc.perform(loginReq)
                .andExpect(status().isOk())
                .andReturn();

        Cookie postLoginCookie = loginResult.getResponse().getCookie("FINSIGHT_SESSION");
        assertNotNull(postLoginCookie, "FINSIGHT_SESSION cookie must be issued upon login");
        return new LoginSession(postLoginCookie, xsrfCookie);
    }

    private LoginSession loginAndSelectBusiness(AppUser user, UUID businessId) throws Exception {
        LoginSession session = loginOnly(user);
        mockMvc.perform(withSession(post("/api/businesses/active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessId\":\"" + businessId + "\"}"), session))
                .andExpect(status().isOk());
        return session;
    }

    private MockHttpServletRequestBuilder withSession(MockHttpServletRequestBuilder builder, LoginSession session) {
        builder.cookie(session.sessionCookie());
        if (session.xsrfCookie() != null) {
            builder.cookie(session.xsrfCookie()).header("X-XSRF-TOKEN", session.xsrfCookie().getValue());
        }
        builder.with(csrf());
        return builder;
    }
}
