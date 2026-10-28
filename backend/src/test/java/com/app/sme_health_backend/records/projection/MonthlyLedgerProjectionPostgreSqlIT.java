package com.app.sme_health_backend.records.projection;

import com.app.sme_health_backend.accounting.draft.DraftCreateRequest;
import com.app.sme_health_backend.accounting.draft.RecordDraftService;
import com.app.sme_health_backend.accounting.dto.TransactionPostRequest;
import com.app.sme_health_backend.accounting.dto.TransactionReverseRequest;
import com.app.sme_health_backend.accounting.dto.TransferPostRequest;
import com.app.sme_health_backend.accounting.entity.AccountMovement;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialTransaction;
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
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.projection.dto.*;
import com.app.sme_health_backend.records.projection.model.ComparisonMetricStatus;
import com.app.sme_health_backend.records.projection.service.MonthlyLedgerProjectionService;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.recurring.dto.RecurringRuleCreateRequest;
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
@DisplayName("Monthly Ledger Projection Real PostgreSQL & Authenticated HTTP Integration Tests (C02 Phase A)")
class MonthlyLedgerProjectionPostgreSqlIT extends DisposablePostgres {

    private static final String RAW_PASSWORD = "secure-password-2026!";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;

    @Autowired private MonthlyLedgerProjectionService projectionService;
    @Autowired private FinancialAccountRepository accountRepository;
    @Autowired private FinancialTransactionRepository transactionRepository;
    @Autowired private AccountMovementRepository movementRepository;
    @Autowired private TransactionPostService transactionPostService;
    @Autowired private TransactionReverseService transactionReverseService;
    @Autowired private TransferService transferService;
    @Autowired private RecordDraftService recordDraftService;
    @Autowired private CounterpartyRepository counterpartyRepository;
    @Autowired private FinancialObligationService obligationService;
    @Autowired private FinancialObligationRepository obligationRepository;
    @Autowired private ObligationPaymentAllocationRepository allocationRepository;
    @Autowired private AdvanceService advanceService;
    @Autowired private RecurringRuleService recurringRuleService;
    @Autowired private MonthlyRecordRepository monthlyRecordRepository;
    @Autowired private BusinessService businessService;
    @Autowired private BusinessMembershipRepository membershipRepository;
    @Autowired private AppUserRepository userRepository;

    private BusinessAccessContext contextA;
    private UUID businessIdA;
    private AppUser ownerUserA;
    private UUID userIdA;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        ownerUserA = createActiveUser("owner-" + unique);
        userIdA = ownerUserA.getId();

        businessIdA = businessService.createBusiness(
                new CreateBusinessRequest("Projection Business " + unique, "retail", "en", null, false, null, null, null),
                userIdA
        ).businessId();
        contextA = new BusinessAccessContext(userIdA, businessIdA, MembershipRole.OWNER);
    }

    @Test
    @DisplayName("Fixture A: Basic monthly cash flow reconciles 100k opening, 30k receipt, 8k opex, 10k transfer, and 122k closing cash")
    void testFixtureA_BasicMonthlyCashFlowAndInternalTransferNeutrality() throws Exception {
        LocalDate d0 = LocalDate.of(2026, 9, 1);
        LocalDate dReceipt = LocalDate.of(2026, 10, 5);
        LocalDate dExpense = LocalDate.of(2026, 10, 10);
        LocalDate dTransfer = LocalDate.of(2026, 10, 15);

        // Account 1: HBL Main (Opening PKR 100,000.00 on Sep 1)
        FinancialAccount acc1 = accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "HBL Main", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), d0)
        );

        // Account 2: Meezan Reserve (Opening PKR 0.00 on Sep 1)
        FinancialAccount acc2 = accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "Meezan Reserve", AccountType.BANK, "PKR", 2, BigDecimal.ZERO, d0)
        );

        // 1. Direct Sale Receipt: PKR 30,000 on Oct 5
        transactionPostService.postTransaction(
                contextA, "TX-REC-" + UUID.randomUUID(),
                new TransactionPostRequest(acc1.getId(), "DIRECT_SALE_RECEIPT", "30000.00", dReceipt.toString(), "Cash sale October", null)
        );

        // 2. Operating Expense Payment: PKR 8,000 on Oct 10
        transactionPostService.postTransaction(
                contextA, "TX-EXP-" + UUID.randomUUID(),
                new TransactionPostRequest(acc1.getId(), "OPERATING_EXPENSE_PAYMENT", "8000.00", dExpense.toString(), "Office Supplies", null)
        );

        // 3. Internal Transfer: PKR 10,000 from acc1 to acc2 on Oct 15
        transferService.postTransfer(
                contextA, "TRF-" + UUID.randomUUID(),
                new TransferPostRequest(acc1.getId(), acc2.getId(), "10000.00", dTransfer.toString(), "Bank transfer")
        );

        // Evaluate via Service
        MonthlyLedgerProjectionResponse proj = projectionService.projectMonthlyLedger(contextA, "2026-10", null);

        assertNotNull(proj);
        assertEquals("2026-10", proj.month());
        assertEquals(DataCompleteness.COMPLETE, proj.completeness());

        MonthlyOperatingCashFlow cf = proj.cashFlow();
        assertEquals("30000.00", cf.grossDirectSaleReceipts());
        assertEquals("30000.00", cf.netDirectSaleReceipts());
        assertEquals("30000.00", cf.totalCashInflow());

        assertEquals("8000.00", cf.grossOperatingExpensePayments());
        assertEquals("8000.00", cf.netOperatingExpensePayments());
        assertEquals("8000.00", cf.totalCashOutflow());

        assertEquals("22000.00", cf.netOperatingCashFlow());
        assertEquals("10000.00", cf.totalInternalTransfersIn());
        assertEquals("10000.00", cf.totalInternalTransfersOut());
        assertEquals("0.00", cf.netInternalTransfersAtBusinessLevel());
        assertEquals("22000.00", cf.totalNetCashMovement());

        // Currency Cash Summary
        assertEquals(1, proj.currencySummaries().size());
        CurrencyMonthlyCashSummary pkr = proj.currencySummaries().get(0);
        assertEquals("PKR", pkr.currency());
        assertEquals("100000.00", pkr.totalOpeningBalance());
        assertEquals("0.00", pkr.totalStartingBalanceAdjustments());
        assertEquals("22000.00", pkr.totalNetMovements());
        assertEquals("122000.00", pkr.totalClosingBalance());
        assertEquals(2, pkr.accountCount());

        // Account positions: Acc 1 = 112,000.00; Acc 2 = 10,000.00
        AccountMonthlyCashPosition p1 = proj.accountPositions().stream().filter(a -> a.accountId().equals(acc1.getId())).findFirst().orElseThrow();
        assertEquals("100000.00", p1.openingBalanceAsOfStartOfMonth());
        assertEquals("12000.00", p1.netMovementDuringMonth());
        assertEquals("112000.00", p1.closingBalanceAsOfEndOfMonth());

        AccountMonthlyCashPosition p2 = proj.accountPositions().stream().filter(a -> a.accountId().equals(acc2.getId())).findFirst().orElseThrow();
        assertEquals("0.00", p2.openingBalanceAsOfStartOfMonth());
        assertEquals("10000.00", p2.netMovementDuringMonth());
        assertEquals("10000.00", p2.closingBalanceAsOfEndOfMonth());

        // Also verify via Authenticated HTTP
        LoginSession session = loginAndSelectBusiness(ownerUserA, businessIdA);
        mockMvc.perform(withSession(get("/api/records/monthly/projection")
                        .param("month", "2026-10"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.cashFlow.totalCashInflow").value("30000.00"))
                .andExpect(jsonPath("$.cashFlow.totalCashOutflow").value("8000.00"))
                .andExpect(jsonPath("$.cashFlow.netOperatingCashFlow").value("22000.00"))
                .andExpect(jsonPath("$.currencySummaries[0].totalClosingBalance").value("122000.00"));
    }

    @Test
    @DisplayName("Fixture B: Reversal of 8k expense restores 0 opex, 30k net cash, and 130k closing cash")
    void testFixtureB_ReversalOfOperatingExpenseRestoresCash() throws Exception {
        LocalDate d0 = LocalDate.of(2026, 9, 1);
        FinancialAccount acc1 = accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "HBL Main", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), d0)
        );

        transactionPostService.postTransaction(
                contextA, "TX-REC-" + UUID.randomUUID(),
                new TransactionPostRequest(acc1.getId(), "DIRECT_SALE_RECEIPT", "30000.00", "2026-10-05", "Cash sale", null)
        );

        var expResp = transactionPostService.postTransaction(
                contextA, "TX-EXP-" + UUID.randomUUID(),
                new TransactionPostRequest(acc1.getId(), "OPERATING_EXPENSE_PAYMENT", "8000.00", "2026-10-10", "Office Supplies", null)
        );

        // Pre-reversal check
        MonthlyLedgerProjectionResponse preRev = projectionService.projectMonthlyLedger(contextA, "2026-10", null);
        assertEquals("8000.00", preRev.cashFlow().totalCashOutflow());
        assertEquals("122000.00", preRev.currencySummaries().get(0).totalClosingBalance());

        // Reverse the PKR 8,000 expense
        transactionReverseService.postReversal(
                contextA, "REV-" + UUID.randomUUID(), expResp.id(),
                new TransactionReverseRequest("Returned office supplies")
        );

        // Post-reversal check
        MonthlyLedgerProjectionResponse postRev = projectionService.projectMonthlyLedger(contextA, "2026-10", null);
        assertEquals("8000.00", postRev.cashFlow().grossOperatingExpensePayments());
        assertEquals("8000.00", postRev.cashFlow().reversedOperatingExpensePayments());
        assertEquals("0.00", postRev.cashFlow().netOperatingExpensePayments());
        assertEquals("0.00", postRev.cashFlow().totalCashOutflow());
        assertEquals("30000.00", postRev.cashFlow().netOperatingCashFlow());
        assertEquals("130000.00", postRev.currencySummaries().get(0).totalClosingBalance());
    }

    @Test
    @DisplayName("Fixture C: Unposted drafts and recurring expectations are excluded from actual monthly metrics")
    void testFixtureC_UnpostedDraftsAndRecurringOccurrencesExcludedFromActualCash() {
        LocalDate d0 = LocalDate.of(2026, 9, 1);
        FinancialAccount acc = accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "HBL Main", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), d0)
        );

        // 1. Unposted draft
        recordDraftService.createDraft(contextA, "DRAFT-" + UUID.randomUUID(), new DraftCreateRequest(
                acc.getId(), "DIRECT_SALE_RECEIPT", "2026-10-15", "50000.00", "Draft receipt"
        ));

        // 2. Scheduled recurring rule
        recurringRuleService.createRule(contextA, new RecurringRuleCreateRequest(
                "Monthly Rent", "Office rent", "OPERATING_EXPENSE_PAYMENT",
                acc.getId(), null, "25000.00", "MONTHLY", "2026-10-01", null
        ));
        recurringRuleService.generateDueOccurrencesForBusiness(contextA);

        MonthlyLedgerProjectionResponse proj = projectionService.projectMonthlyLedger(contextA, "2026-10", null);

        // Actual cash metrics must remain 0 inflows, 0 outflows, 100k balance
        assertEquals("0.00", proj.cashFlow().totalCashInflow());
        assertEquals("0.00", proj.cashFlow().totalCashOutflow());
        assertEquals("100000.00", proj.currencySummaries().get(0).totalClosingBalance());

        // Draft summary reports estimates transparently
        assertTrue(proj.draftSummary().unpostedDraftCount() >= 1);
        assertEquals("75000.00", proj.draftSummary().unpostedDraftAmount());
        assertTrue(proj.draftSummary().recurringProjectedCount() >= 1);
        assertTrue(proj.draftSummary().isEstimate());
    }

    @Test
    @DisplayName("Fixture D: Receivables/payables, settlements, advances, and accrual vs cash recognition")
    void testFixtureD_ReceivablesPayablesAdvanceApplicationsAndAccrualVsCashRevenue() {
        LocalDate d0 = LocalDate.of(2026, 9, 1);
        FinancialAccount acc = accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "HBL Main", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), d0)
        );

        Counterparty cust = counterpartyRepository.saveAndFlush(
                new Counterparty(businessIdA, "ACME Corp", CounterpartyType.CUSTOMER, null, null, null, null, null)
        );

        // 1. Direct Sale Receipt: PKR 30,000 on Oct 5
        transactionPostService.postTransaction(
                contextA, "TX-REC-" + UUID.randomUUID(),
                new TransactionPostRequest(acc.getId(), "DIRECT_SALE_RECEIPT", "30000.00", "2026-10-05", "Direct cash sale", cust.getId())
        );

        // 2. Invoice issued in October: PKR 50,000 on Oct 2 (due Oct 25) with verified CUSTOMER_INVOICE provenance
        var obResp = obligationService.createObligation(contextA, new ObligationCreateRequest(
                cust.getId(), "RECEIVABLE", "50000.00", "2026-10-02", "2026-10-25", "INV-OCT-01", "October Invoice", "CUSTOMER_INVOICE", null
        ));

        // 3. Partial cash settlement: PKR 20,000 on Oct 12
        obligationService.recordSettlement(contextA, obResp.id(), "SETTLE-" + UUID.randomUUID(), new ObligationSettlementRequest(
                acc.getId(), "20000.00", "2026-10-12", "Partial payment", null
        ));

        // 4. Customer Advance: PKR 10,000 on Oct 14
        var advResp = advanceService.createAdvance(contextA, "ADV-" + UUID.randomUUID(), new AdvanceCreateRequest(
                cust.getId(), "RECEIVABLE", acc.getId(), "2026-10-14", "10000.00", "ADV-DEP-01", "Advance deposit"
        ));

        // 5. Apply PKR 5,000 advance to the invoice
        advanceService.applyAdvance(contextA, "APPLY-" + UUID.randomUUID(), advResp.id(), new AdvanceApplyRequest(
                obResp.id(), "5000.00", "2026-10-16", "Advance applied"
        ));

        MonthlyLedgerProjectionResponse proj = projectionService.projectMonthlyLedger(contextA, "2026-10", null);

        // Cash Flow:
        // Receipts = 30k (direct) + 20k (settlement) + 10k (advance cash) = 60,000.00
        assertEquals("60000.00", proj.cashFlow().totalCashInflow());
        assertEquals("30000.00", proj.cashFlow().netDirectSaleReceipts());
        assertEquals("30000.00", proj.cashFlow().netReceivableCollections());
        assertEquals("0.00", proj.cashFlow().unclassifiedCashInflows());
        assertEquals("160000.00", proj.currencySummaries().get(0).totalClosingBalance());

        // Recognized Figures:
        // Cash Revenue = 30,000.00 (direct sales only)
        // Accrual Revenue = 30,000.00 (direct sales) + 50,000.00 (invoice issued in Oct) = 80,000.00
        assertEquals("30000.00", proj.recognizedFigures().verifiedDirectSaleRevenue());
        assertEquals("50000.00", proj.recognizedFigures().invoiceIssuedEarnedRevenue());
        assertEquals("0.00", proj.recognizedFigures().provisionalReceivableCandidates());
        assertEquals(CapabilityStatus.AVAILABLE, proj.recognizedFigures().authoritativeAccrualRevenueStatus());
        assertEquals("80000.00", proj.recognizedFigures().authoritativeAccrualRevenue());
        assertEquals("80000.00", proj.recognizedFigures().revenueLessKnownOperatingExpensesBeforeCogs());
        assertEquals(CapabilityStatus.UNAVAILABLE, proj.recognizedFigures().cogsStatus());
        assertNull(proj.recognizedFigures().cogs());
        assertEquals(CapabilityStatus.UNAVAILABLE, proj.recognizedFigures().grossProfitStatus());
        assertNull(proj.recognizedFigures().grossProfit());
        assertEquals(CapabilityStatus.UNAVAILABLE, proj.recognizedFigures().operatingProfitStatus());
        assertNull(proj.recognizedFigures().operatingProfit());
        assertEquals(CapabilityStatus.UNAVAILABLE, proj.recognizedFigures().netProfitStatus());
        assertNull(proj.recognizedFigures().netProfit());

        // Outstanding Obligation Positions as of Oct 31:
        // Original 50k - 20k settlement - 5k advance applied = 25,000.00 remaining receivable!
        assertEquals("25000.00", proj.obligationPositions().totalReceivablesOutstanding());
        // Original 10k advance - 5k applied = 5,000.00 unapplied advance!
        assertEquals("5000.00", proj.obligationPositions().customerAdvancesUnapplied());
    }

    @Test
    @DisplayName("Fixture G: Non-revenue and manual obligations are classified as provisional candidates or excluded")
    void testFixtureG_NonRevenueObligationsAndProvenanceUncertainty() {
        LocalDate d0 = LocalDate.of(2026, 9, 1);
        FinancialAccount acc = accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "HBL Main", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), d0)
        );
        Counterparty cust = counterpartyRepository.saveAndFlush(
                new Counterparty(businessIdA, "Enterprise Client", CounterpartyType.CUSTOMER, null, null, null, null, null)
        );

        // 1. Direct Sale Receipt: PKR 30,000
        transactionPostService.postTransaction(
                contextA, "TX-REC-" + UUID.randomUUID(),
                new TransactionPostRequest(acc.getId(), "DIRECT_SALE_RECEIPT", "30000.00", "2026-10-05", "Direct cash sale", cust.getId())
        );

        // 2. Verified Customer Invoice: PKR 50,000
        obligationService.createObligation(contextA, new ObligationCreateRequest(
                cust.getId(), "RECEIVABLE", "50000.00", "2026-10-02", "2026-10-25", "INV-OCT-02", "Verified sales invoice", "CUSTOMER_INVOICE", null
        ));

        // 3. Manual Declaration (unprovenanced debt/deposit): PKR 20,000
        obligationService.createObligation(contextA, new ObligationCreateRequest(
                cust.getId(), "RECEIVABLE", "20000.00", "2026-10-10", "2026-10-30", "MAN-OCT-01", "Unprovenanced receivable", "MANUAL_DECLARATION", null
        ));

        // 4. Opening Balance migration: PKR 100,000
        obligationService.createObligation(contextA, new ObligationCreateRequest(
                cust.getId(), "RECEIVABLE", "100000.00", "2026-10-01", null, "OB-OCT-01", "Opening balance receivable", "OPENING_BALANCE", null
        ));

        MonthlyLedgerProjectionResponse proj = projectionService.projectMonthlyLedger(contextA, "2026-10", null);

        MonthlyRecognizedFigures rf = proj.recognizedFigures();
        assertEquals("30000.00", rf.verifiedDirectSaleRevenue());
        assertEquals("50000.00", rf.invoiceIssuedEarnedRevenue());
        assertEquals("20000.00", rf.provisionalReceivableCandidates());
        // Authoritative revenue includes only direct sales + verified invoices = 80,000.00
        assertEquals("80000.00", rf.authoritativeAccrualRevenue());
        // Provisional total includes manual candidates = 100,000.00
        assertEquals("100000.00", rf.provisionalTotalRevenue());
        // Status is AVAILABLE and Completeness is PARTIAL due to unprovenanced manual declaration
        assertEquals(CapabilityStatus.AVAILABLE, rf.authoritativeAccrualRevenueStatus());
        assertEquals(DataCompleteness.PARTIAL, rf.accrualRevenueCompleteness());
        // Balance sheet aging captures all 3 open obligations: 50k + 20k + 100k = 170,000.00!
        assertEquals("170000.00", proj.obligationPositions().totalReceivablesOutstanding());
    }

    @Test
    @DisplayName("Fixture E: Multiple currencies report distinct summaries with zero unapproved FX blending")
    void testFixtureE_MultiCurrencySeparation() {
        LocalDate d0 = LocalDate.of(2026, 9, 1);
        FinancialAccount accPkr = accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "HBL PKR", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), d0)
        );
        FinancialAccount accUsd = accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "Citibank USD", AccountType.BANK, "USD", 2, new BigDecimal("5000.00"), d0)
        );

        MonthlyLedgerProjectionResponse proj = projectionService.projectMonthlyLedger(contextA, "2026-10", null);

        assertEquals(2, proj.currencySummaries().size());
        CurrencyMonthlyCashSummary pkr = proj.currencySummaries().stream().filter(s -> "PKR".equals(s.currency())).findFirst().orElseThrow();
        assertEquals("100000.00", pkr.totalClosingBalance());

        CurrencyMonthlyCashSummary usd = proj.currencySummaries().stream().filter(s -> "USD".equals(s.currency())).findFirst().orElseThrow();
        assertEquals("5000.00", usd.totalClosingBalance());
    }

    @Test
    @DisplayName("Fixture F: Boundary transactions on 2026-08-31 and 2026-10-01 do not pollute September flows")
    void testFixtureF_HistoricalBoundaryTransactions() {
        LocalDate d0 = LocalDate.of(2026, 7, 1);
        FinancialAccount acc = accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "HBL Main", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), d0)
        );

        // Transaction on Aug 31 (prior month): +15,000 receipt
        transactionPostService.postTransaction(
                contextA, "TX-AUG-" + UUID.randomUUID(),
                new TransactionPostRequest(acc.getId(), "DIRECT_SALE_RECEIPT", "15000.00", "2026-08-31", "August sale", null)
        );

        // Transaction in September: +30,000 receipt
        transactionPostService.postTransaction(
                contextA, "TX-SEP-" + UUID.randomUUID(),
                new TransactionPostRequest(acc.getId(), "DIRECT_SALE_RECEIPT", "30000.00", "2026-09-15", "September sale", null)
        );

        // Transaction on Oct 01 (next month): +20,000 receipt
        transactionPostService.postTransaction(
                contextA, "TX-OCT-" + UUID.randomUUID(),
                new TransactionPostRequest(acc.getId(), "DIRECT_SALE_RECEIPT", "20000.00", "2026-10-01", "October sale", null)
        );

        MonthlyLedgerProjectionResponse sep = projectionService.projectMonthlyLedger(contextA, "2026-09", null);

        // Opening cash as of start of September = 100,000 + 15,000 (Aug 31) = 115,000.00!
        assertEquals("115000.00", sep.currencySummaries().get(0).totalOpeningBalance());

        // September cash inflows must be ONLY September transactions (30,000.00)
        assertEquals("30000.00", sep.cashFlow().totalCashInflow());

        // September closing cash = 115,000 + 30,000 = 145,000.00! (Oct 01 is excluded)
        assertEquals("145000.00", sep.currencySummaries().get(0).totalClosingBalance());
    }

    @Test
    @DisplayName("Shadow Comparison: Real MonthlyRecord comparison against ledger projection")
    void testShadowComparison_WithRealMonthlyRecord() throws Exception {
        LocalDate d0 = LocalDate.of(2026, 9, 1);
        FinancialAccount acc = accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "HBL Main", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), d0)
        );

        transactionPostService.postTransaction(
                contextA, "TX-REC-" + UUID.randomUUID(),
                new TransactionPostRequest(acc.getId(), "DIRECT_SALE_RECEIPT", "30000.00", "2026-10-05", "Direct cash sale", null)
        );
        transactionPostService.postTransaction(
                contextA, "TX-EXP-" + UUID.randomUUID(),
                new TransactionPostRequest(acc.getId(), "OPERATING_EXPENSE_PAYMENT", "8000.00", "2026-10-10", "Supplies", null)
        );

        MonthlyRecord mr = new MonthlyRecord();
        mr.setUserId(businessIdA);
        mr.setMonth("2026-10");
        mr.setCashInflow(new BigDecimal("30000.00"));
        mr.setCashOutflow(new BigDecimal("8000.00"));
        mr.setRevenue(new BigDecimal("30000.00"));
        mr.setOperatingExpenses(new BigDecimal("8000.00"));
        mr.setCashBalanceEom(new BigDecimal("122000.00"));
        mr.setCogs(new BigDecimal("12000.00"));
        mr.setUpdatedAt(LocalDateTime.now());
        monthlyRecordRepository.saveAndFlush(mr);

        MonthlyShadowComparisonResponse comp = projectionService.compareWithMonthlyRecord(contextA, "2026-10", null);

        assertTrue(comp.hasMonthlyRecord());
        assertTrue(comp.matchingMetricCount() >= 4);
        assertTrue(comp.unsupportedMetricCount() >= 1);

        // Verify via HTTP
        LoginSession session = loginAndSelectBusiness(ownerUserA, businessIdA);
        mockMvc.perform(withSession(get("/api/records/monthly/comparison")
                        .param("month", "2026-10"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMonthlyRecord").value(true))
                .andExpect(jsonPath("$.matchingMetricCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.unsupportedMetricCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("Read-Only Invariants: Bit-for-bit SHA-256 snapshots and row counts verify zero database mutations")
    void testReadOnlyInvariants_BitForBitSha256SnapshotsAcrossPopulatedTables() throws Exception {
        LocalDate d0 = LocalDate.of(2026, 9, 1);
        FinancialAccount acc = accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "HBL Main", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), d0)
        );
        Counterparty cust = counterpartyRepository.saveAndFlush(
                new Counterparty(businessIdA, "Customer Invariant", CounterpartyType.CUSTOMER, null, null, null, null, null)
        );

        transactionPostService.postTransaction(
                contextA, "TX-REC-" + UUID.randomUUID(),
                new TransactionPostRequest(acc.getId(), "DIRECT_SALE_RECEIPT", "25000.00", "2026-10-05", "Direct sale", cust.getId())
        );

        recordDraftService.createDraft(contextA, "DRAFT-" + UUID.randomUUID(), new DraftCreateRequest(
                acc.getId(), "OPERATING_EXPENSE_PAYMENT", "2026-10-15", "4500.00", "Draft opex"
        ));

        obligationService.createObligation(contextA, new ObligationCreateRequest(
                cust.getId(), "RECEIVABLE", "40000.00", "2026-10-02", "2026-10-25", "INV-INV-01", "Invoice", "MANUAL_DECLARATION", null
        ));

        MonthlyRecord mr = new MonthlyRecord();
        mr.setUserId(businessIdA);
        mr.setMonth("2026-10");
        mr.setCashInflow(new BigDecimal("25000.00"));
        mr.setCashOutflow(new BigDecimal("0.00"));
        mr.setRevenue(new BigDecimal("25000.00"));
        mr.setOperatingExpenses(new BigDecimal("0.00"));
        mr.setCashBalanceEom(new BigDecimal("125000.00"));
        mr.setUpdatedAt(LocalDateTime.now());
        monthlyRecordRepository.saveAndFlush(mr);

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
                "monthly_records",
                "score_results"
        );

        Map<String, TableSnapshot> beforeSnapshots = captureTableSnapshots(auditedTables);

        // Execute all projection and comparison operations
        projectionService.projectMonthlyLedger(contextA, "2026-10", null);
        projectionService.compareWithMonthlyRecord(contextA, "2026-10", null);

        mockMvc.perform(withSession(get("/api/records/monthly/projection").param("month", "2026-10"), session))
                .andExpect(status().isOk());
        mockMvc.perform(withSession(get("/api/records/monthly/comparison").param("month", "2026-10"), session))
                .andExpect(status().isOk());

        Map<String, TableSnapshot> afterSnapshots = captureTableSnapshots(auditedTables);

        for (String table : auditedTables) {
            TableSnapshot before = beforeSnapshots.get(table);
            TableSnapshot after = afterSnapshots.get(table);
            assertNotNull(before, "Missing before snapshot for " + table);
            assertNotNull(after, "Missing after snapshot for " + table);
            assertEquals(before.rowCount(), after.rowCount(), "Row count changed on read-only projection for " + table);
            assertEquals(before.sha256Digest(), after.sha256Digest(), "SHA-256 value hash changed on read-only projection for " + table);
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
