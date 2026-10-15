package com.app.sme_health_backend.accounting;

import com.app.sme_health_backend.accounting.dto.AccountDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionListResponse;
import com.app.sme_health_backend.accounting.dto.TransactionPostRequest;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.exception.TransactionValidationException;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.service.AccountReadService;
import com.app.sme_health_backend.accounting.service.TransactionPostService;
import com.app.sme_health_backend.accounting.service.TransactionReadService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.dto.CreateBusinessRequest;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.service.BusinessService;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FinancialTransactionPostgreSqlIT extends DisposablePostgres {

    @Autowired
    private FinancialAccountRepository accountRepository;

    @Autowired
    private AccountReadService accountReadService;

    @Autowired
    private TransactionPostService transactionPostService;

    @Autowired
    private TransactionReadService transactionReadService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private BusinessService businessService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userA;
    private UUID businessA;
    private BusinessAccessContext contextA;

    private UUID userB;
    private UUID businessB;
    private BusinessAccessContext contextB;

    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(ZoneId.of("Asia/Karachi"));

        AppUser uA = new AppUser();
        uA.setEmail("tx-test-a-" + UUID.randomUUID() + "@example.test");
        uA.setFullName("Tx User A");
        uA.setPasswordHash("hash-a");
        userA = userRepository.save(uA).getId();
        businessA = businessService.createBusiness(
                new CreateBusinessRequest("Tx Business A", "retail", "en", null, false, null, null, null),
                userA
        ).businessId();
        contextA = new BusinessAccessContext(userA, businessA, MembershipRole.OWNER);

        AppUser uB = new AppUser();
        uB.setEmail("tx-test-b-" + UUID.randomUUID() + "@example.test");
        uB.setFullName("Tx User B");
        uB.setPasswordHash("hash-b");
        userB = userRepository.save(uB).getId();
        businessB = businessService.createBusiness(
                new CreateBusinessRequest("Tx Business B", "services", "en", null, false, null, null, null),
                userB
        ).businessId();
        contextB = new BusinessAccessContext(userB, businessB, MembershipRole.OWNER);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM account_movements WHERE business_id IN (?, ?)", businessA, businessB);
        jdbcTemplate.update("DELETE FROM financial_transactions WHERE business_id IN (?, ?)", businessA, businessB);
        jdbcTemplate.update("DELETE FROM financial_idempotency_records WHERE business_id IN (?, ?)", businessA, businessB);
        jdbcTemplate.update("DELETE FROM financial_accounts WHERE business_id IN (?, ?)", businessA, businessB);
        jdbcTemplate.update("DELETE FROM business_memberships WHERE business_id IN (?, ?)", businessA, businessB);
        jdbcTemplate.update("DELETE FROM business_profiles WHERE user_id IN (?, ?)", businessA, businessB);
        jdbcTemplate.update("DELETE FROM businesses WHERE id IN (?, ?)", businessA, businessB);
        jdbcTemplate.update("DELETE FROM app_users WHERE id IN (?, ?)", userA, userB);
    }

    @Test
    @DisplayName("IT-TX-01: Posting DIRECT_SALE_RECEIPT persists transaction, movement, and audit event, and updates account read balance")
    void postDirectSaleReceipt_persistsAndUpdatesAccountRead() {
        FinancialAccount account = new FinancialAccount(
                businessA,
                "Retail Cash Register",
                AccountType.CASH,
                "PKR",
                2,
                new BigDecimal("50000.00"),
                today.minusDays(5),
                AccountStatus.ACTIVE,
                null,
                null,
                "Cash counter"
        );
        FinancialAccount savedAccount = accountRepository.save(account);

        TransactionPostRequest request = new TransactionPostRequest(
                savedAccount.getId(),
                "DIRECT_SALE_RECEIPT",
                "15000.00",
                today.toString(),
                "Direct store receipt"
        );

        TransactionDetailResponse response = transactionPostService.postTransaction(
                contextA, "idem-it-01", request
        );

        assertNotNull(response.id());
        assertEquals("DIRECT_SALE_RECEIPT", response.eventType());
        assertEquals("INCREASE", response.movementDirection());
        assertEquals("15000.00", response.amount());
        assertEquals("65000.00", response.resultingBalance());

        // Verify DB rows
        Integer txCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM financial_transactions WHERE id = ?", Integer.class, response.id()
        );
        assertEquals(1, txCount);

        Integer movCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_movements WHERE transaction_id = ?", Integer.class, response.id()
        );
        assertEquals(1, movCount);

        // Verify audit linkage (distinct actor and business)
        Map<String, Object> auditRow = jdbcTemplate.queryForMap(
                "SELECT actor_user_id, business_id, event_type, target_type, target_id FROM security_audit_events WHERE target_id = ?",
                response.id().toString()
        );
        assertEquals(userA, auditRow.get("actor_user_id"));
        assertEquals(businessA, auditRow.get("business_id"));
        assertEquals("FINANCIAL_TRANSACTION_POSTED", auditRow.get("event_type"));
        assertEquals("FINANCIAL_TRANSACTION", auditRow.get("target_type"));

        // Verify account read incorporating movements
        AccountDetailResponse accountDetail = accountReadService.getAccountDetail(businessA, savedAccount.getId());
        assertEquals("50000.00", accountDetail.openingBalance());
        assertEquals("65000.00", accountDetail.currentBalance());
    }

    @Test
    @DisplayName("IT-TX-02: Posting OPERATING_EXPENSE_PAYMENT decreases balance and permits signed negative BANK balance")
    void postOperatingExpense_decreasesBalanceAndPermitsNegativeBank() {
        FinancialAccount account = new FinancialAccount(
                businessA,
                "Operating Chequing Account",
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("10000.00"),
                today.minusDays(5),
                AccountStatus.ACTIVE,
                "HBL",
                null,
                null
        );
        FinancialAccount savedAccount = accountRepository.save(account);

        TransactionPostRequest request = new TransactionPostRequest(
                savedAccount.getId(),
                "OPERATING_EXPENSE_PAYMENT",
                "25000.00",
                today.toString(),
                "Office rent payment"
        );

        TransactionDetailResponse response = transactionPostService.postTransaction(
                contextA, "idem-it-02", request
        );

        assertEquals("OPERATING_EXPENSE_PAYMENT", response.eventType());
        assertEquals("DECREASE", response.movementDirection());
        assertEquals("25000.00", response.amount());
        assertEquals("-15000.00", response.resultingBalance());

        AccountDetailResponse accountDetail = accountReadService.getAccountDetail(businessA, savedAccount.getId());
        assertEquals("-15000.00", accountDetail.currentBalance());
    }

    @Test
    @DisplayName("IT-TX-03: Eight concurrent same-key requests persist exactly one transaction and one movement")
    void concurrentEightSameKeyRequests_persistsExactlyOne() throws Exception {
        FinancialAccount account = new FinancialAccount(
                businessA,
                "Payroll Bank Account",
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("100000.00"),
                today.minusDays(5),
                AccountStatus.ACTIVE,
                "MBL",
                null,
                null
        );
        FinancialAccount savedAccount = accountRepository.save(account);

        String sharedKey = "idem-concurrent-" + UUID.randomUUID();
        TransactionPostRequest request = new TransactionPostRequest(
                savedAccount.getId(),
                "DIRECT_SALE_RECEIPT",
                "10000.00",
                today.toString(),
                "Concurrent receipt"
        );

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<TransactionDetailResponse>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                return transactionPostService.postTransaction(contextA, sharedKey, request);
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        Set<UUID> transactionIds = new HashSet<>();
        for (Future<TransactionDetailResponse> future : futures) {
            TransactionDetailResponse res = future.get(10, TimeUnit.SECONDS);
            assertNotNull(res);
            transactionIds.add(res.id());
        }
        executor.shutdown();

        // Exactly one unique transaction created
        assertEquals(1, transactionIds.size(), "All concurrent requests must return the identical transaction ID");

        UUID singleTxId = transactionIds.iterator().next();
        Integer dbTxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM financial_transactions WHERE id = ?", Integer.class, singleTxId
        );
        assertEquals(1, dbTxCount);

        Integer dbMovCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_movements WHERE transaction_id = ?", Integer.class, singleTxId
        );
        assertEquals(1, dbMovCount);

        // Account balance updated exactly once (+10,000 -> 110,000)
        AccountDetailResponse accountDetail = accountReadService.getAccountDetail(businessA, savedAccount.getId());
        assertEquals("110000.00", accountDetail.currentBalance());
    }

    @Test
    @DisplayName("IT-TX-04: Same key with differing payload returns 409 Conflict without modifying database")
    void sameKeyDifferingPayload_throwsConflict() {
        FinancialAccount account = new FinancialAccount(
                businessA,
                "Conflict Test Bank",
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("50000.00"),
                today.minusDays(5),
                AccountStatus.ACTIVE,
                null,
                null,
                null
        );
        FinancialAccount savedAccount = accountRepository.save(account);

        String key = "idem-conflict-" + UUID.randomUUID();
        TransactionPostRequest req1 = new TransactionPostRequest(
                savedAccount.getId(), "DIRECT_SALE_RECEIPT", "1000.00", today.toString(), "First"
        );
        transactionPostService.postTransaction(contextA, key, req1);

        TransactionPostRequest req2 = new TransactionPostRequest(
                savedAccount.getId(), "DIRECT_SALE_RECEIPT", "2000.00", today.toString(), "Altered"
        );

        assertThrows(DuplicateResourceException.class, () ->
                transactionPostService.postTransaction(contextA, key, req2)
        );

        // Balance remains 51000.00
        AccountDetailResponse accountDetail = accountReadService.getAccountDetail(businessA, savedAccount.getId());
        assertEquals("51000.00", accountDetail.currentBalance());
    }

    @Test
    @DisplayName("IT-TX-05: CASH account backdated outflow causing subsequent historical balance to drop below zero is rejected")
    void cashAccount_backdatedSequenceNegative_rejected() {
        // Initial state:
        // Opening balance on Day -10: 10,000 PKR.
        // Day -5: Outflow of 8,000 -> balance on Day -5 was 2,000.
        // Day -2: Inflow of 5,000 -> balance on Day -2 was 7,000.
        // Today: balance is 7,000.
        FinancialAccount account = new FinancialAccount(
                businessA,
                "Daily Cash Register",
                AccountType.CASH,
                "PKR",
                2,
                new BigDecimal("10000.00"),
                today.minusDays(10),
                AccountStatus.ACTIVE,
                null,
                null,
                null
        );
        FinancialAccount savedAccount = accountRepository.save(account);

        // Day -5 outflow
        transactionPostService.postTransaction(
                contextA, "idem-seq-1",
                new TransactionPostRequest(savedAccount.getId(), "OPERATING_EXPENSE_PAYMENT", "8000.00", today.minusDays(5).toString(), "Day -5 outflow")
        );

        // Day -2 inflow
        transactionPostService.postTransaction(
                contextA, "idem-seq-2",
                new TransactionPostRequest(savedAccount.getId(), "DIRECT_SALE_RECEIPT", "5000.00", today.minusDays(2).toString(), "Day -2 inflow")
        );

        // Now attempt backdated outflow of 3,000 on Day -8
        // If accepted: Day -5 balance would drop to 10000 - 3000 - 8000 = -1000 < 0!
        TransactionPostRequest backdatedRequest = new TransactionPostRequest(
                savedAccount.getId(), "OPERATING_EXPENSE_PAYMENT", "3000.00", today.minusDays(8).toString(), "Backdated outflow"
        );

        TransactionValidationException ex = assertThrows(TransactionValidationException.class, () ->
                transactionPostService.postTransaction(contextA, "idem-seq-3", backdatedRequest)
        );

        assertTrue(ex.getErrors().containsKey("cashBalance"));
        assertTrue(ex.getErrors().get("cashBalance").contains("negative on " + today.minusDays(5)));

        // Verify balance remained 7000.00
        AccountDetailResponse accountDetail = accountReadService.getAccountDetail(businessA, savedAccount.getId());
        assertEquals("7000.00", accountDetail.currentBalance());
    }

    @Test
    @DisplayName("IT-TX-06: Posting transactions leaves monthly_records, score_results, insights, and recommendations untouched")
    void postingDoesNotAffectMonthlyRecordsOrScores() {
        Integer preRecords = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM monthly_records", Integer.class);
        Integer preScores = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM score_results", Integer.class);
        Integer preInsights = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM insights", Integer.class);
        Integer preRecs = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM recommendations", Integer.class);

        FinancialAccount account = new FinancialAccount(
                businessA, "Integrity Account", AccountType.BANK, "PKR", 2,
                new BigDecimal("10000.00"), today.minusDays(3), AccountStatus.ACTIVE, null, null, null
        );
        FinancialAccount savedAccount = accountRepository.save(account);

        transactionPostService.postTransaction(
                contextA, "idem-integrity-1",
                new TransactionPostRequest(savedAccount.getId(), "DIRECT_SALE_RECEIPT", "5000.00", today.toString(), "Sale")
        );

        Integer postRecords = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM monthly_records", Integer.class);
        Integer postScores = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM score_results", Integer.class);
        Integer postInsights = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM insights", Integer.class);
        Integer postRecs = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM recommendations", Integer.class);

        assertEquals(preRecords, postRecords, "monthly_records count must not change");
        assertEquals(preScores, postScores, "score_results count must not change");
        assertEquals(preInsights, postInsights, "insights count must not change");
        assertEquals(preRecs, postRecs, "recommendations count must not change");
    }

    @Test
    @DisplayName("IT-TX-07: Multi-tenant isolation: Business B cannot access Business A transactions")
    void multiTenantIsolation() {
        FinancialAccount account = new FinancialAccount(
                businessA, "Tenant A Account", AccountType.BANK, "PKR", 2,
                new BigDecimal("10000.00"), today.minusDays(3), AccountStatus.ACTIVE, null, null, null
        );
        FinancialAccount savedAccount = accountRepository.save(account);

        TransactionDetailResponse tx = transactionPostService.postTransaction(
                contextA, "idem-tenant-1",
                new TransactionPostRequest(savedAccount.getId(), "DIRECT_SALE_RECEIPT", "5000.00", today.toString(), "Sale")
        );

        // Attempt retrieval by Business B
        assertThrows(ResourceNotFoundException.class, () ->
                transactionReadService.getTransactionDetail(businessB, tx.id())
        );

        // Attempt listing by Business B
        TransactionListResponse listB = transactionReadService.listTransactions(businessB, null, null, null, null, 0, 20);
        assertEquals(0, listB.totalElements());
    }

    @Test
    @DisplayName("IT-TX-08: List transactions returns filtered content with deterministic ordering effectiveDate DESC, createdAt DESC, id DESC")
    void listTransactions_filteredAndOrdered() {
        FinancialAccount account = new FinancialAccount(
                businessA, "Order Account", AccountType.BANK, "PKR", 2,
                new BigDecimal("50000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        );
        FinancialAccount savedAccount = accountRepository.save(account);

        // Tx 1: Day -5
        transactionPostService.postTransaction(
                contextA, "idem-ord-1",
                new TransactionPostRequest(savedAccount.getId(), "DIRECT_SALE_RECEIPT", "1000.00", today.minusDays(5).toString(), "Day -5 sale")
        );

        // Tx 2: Day -2
        transactionPostService.postTransaction(
                contextA, "idem-ord-2",
                new TransactionPostRequest(savedAccount.getId(), "OPERATING_EXPENSE_PAYMENT", "500.00", today.minusDays(2).toString(), "Day -2 expense")
        );

        // Tx 3: Today
        transactionPostService.postTransaction(
                contextA, "idem-ord-3",
                new TransactionPostRequest(savedAccount.getId(), "DIRECT_SALE_RECEIPT", "2000.00", today.toString(), "Today sale")
        );

        TransactionListResponse list = transactionReadService.listTransactions(businessA, savedAccount.getId(), null, null, null, 0, 20);
        assertEquals(3, list.totalElements());

        // Deterministic ordering: Today first, then Day -2, then Day -5
        assertEquals(today, list.content().get(0).effectiveDate());
        assertEquals(today.minusDays(2), list.content().get(1).effectiveDate());
        assertEquals(today.minusDays(5), list.content().get(2).effectiveDate());

        // Filter by eventType
        TransactionListResponse salesOnly = transactionReadService.listTransactions(
                businessA, savedAccount.getId(), "DIRECT_SALE_RECEIPT", null, null, 0, 20
        );
        assertEquals(2, salesOnly.totalElements());
        assertEquals("DIRECT_SALE_RECEIPT", salesOnly.content().get(0).eventType());
        assertEquals("DIRECT_SALE_RECEIPT", salesOnly.content().get(1).eventType());

        // Filter by date range
        TransactionListResponse range = transactionReadService.listTransactions(
                businessA, savedAccount.getId(), null, today.minusDays(4).toString(), today.minusDays(1).toString(), 0, 20
        );
        assertEquals(1, range.totalElements());
        assertEquals(today.minusDays(2), range.content().get(0).effectiveDate());
    }

    @Test
    @DisplayName("IT-TX-09: Aggregate overflow rejects atomically with 400 Bad Request and leaves all persistence paths unchanged")
    void aggregateOverflow_rejectedAtomically() {
        FinancialAccount capacityAccount = new FinancialAccount(
                businessA, "TX Capacity Boundary", AccountType.BANK, "PKR", 2,
                new BigDecimal("999999999999.99"), today.minusDays(5), AccountStatus.ACTIVE, null, null, null
        );
        FinancialAccount savedAccount = accountRepository.save(capacityAccount);

        int txBefore = jdbcTemplate.queryForObject("SELECT count(*) FROM financial_transactions", Integer.class);
        int movBefore = jdbcTemplate.queryForObject("SELECT count(*) FROM account_movements", Integer.class);
        int idemBefore = jdbcTemplate.queryForObject("SELECT count(*) FROM financial_idempotency_records", Integer.class);
        int auditBefore = jdbcTemplate.queryForObject("SELECT count(*) FROM security_audit_events WHERE event_type='FINANCIAL_TRANSACTION_POSTED'", Integer.class);

        TransactionPostRequest overflowRequest = new TransactionPostRequest(
                savedAccount.getId(), "DIRECT_SALE_RECEIPT", "0.01", today.toString(), "Overflow"
        );

        TransactionValidationException ex = assertThrows(
                TransactionValidationException.class,
                () -> transactionPostService.postTransaction(contextA, "idem-cap-overflow-1", overflowRequest)
        );

        assertTrue(ex.getErrors().containsKey("amount"));
        assertTrue(ex.getErrors().get("amount").contains("exceed maximum capacity"));

        int txAfter = jdbcTemplate.queryForObject("SELECT count(*) FROM financial_transactions", Integer.class);
        int movAfter = jdbcTemplate.queryForObject("SELECT count(*) FROM account_movements", Integer.class);
        int idemAfter = jdbcTemplate.queryForObject("SELECT count(*) FROM financial_idempotency_records", Integer.class);
        int auditAfter = jdbcTemplate.queryForObject("SELECT count(*) FROM security_audit_events WHERE event_type='FINANCIAL_TRANSACTION_POSTED'", Integer.class);

        assertEquals(txBefore, txAfter, "financial_transactions count must not change on capacity overflow rejection");
        assertEquals(movBefore, movAfter, "account_movements count must not change on capacity overflow rejection");
        assertEquals(idemBefore, idemAfter, "financial_idempotency_records count must not change on capacity overflow rejection");
        assertEquals(auditBefore, auditAfter, "security_audit_events count must not change on capacity overflow rejection");

        // Verify account read after rejected posting remains AVAILABLE with valid opening and current balance
        AccountDetailResponse accountDetail = accountReadService.getAccountDetail(businessA, savedAccount.getId());
        assertEquals("AVAILABLE", accountDetail.balanceStatus().name());
        assertEquals("999999999999.99", accountDetail.openingBalance());
        assertEquals("999999999999.99", accountDetail.currentBalance());
    }

    @Test
    @DisplayName("IT-TX-10: Malformed amount strings (1e2, +1.00, 01.00) are rejected with HTTP 400 before persistence")
    void malformedAmounts_rejectedBeforePersistence() {
        FinancialAccount account = new FinancialAccount(
                businessA, "Malformed Account", AccountType.BANK, "PKR", 2,
                new BigDecimal("1000.00"), today.minusDays(5), AccountStatus.ACTIVE, null, null, null
        );
        FinancialAccount savedAccount = accountRepository.save(account);

        int txBefore = jdbcTemplate.queryForObject("SELECT count(*) FROM financial_transactions", Integer.class);

        for (String malformed : List.of("1e2", "+1.00", "01.00")) {
            TransactionPostRequest request = new TransactionPostRequest(
                    savedAccount.getId(), "DIRECT_SALE_RECEIPT", malformed, today.toString(), "Malformed"
            );

            TransactionValidationException ex = assertThrows(
                    TransactionValidationException.class,
                    () -> transactionPostService.postTransaction(contextA, "idem-malformed-" + malformed, request)
            );
            assertTrue(ex.getErrors().containsKey("amount"));
        }

        int txAfter = jdbcTemplate.queryForObject("SELECT count(*) FROM financial_transactions", Integer.class);
        assertEquals(txBefore, txAfter, "Zero transactions should be written for malformed amounts");
    }

    @Test
    @DisplayName("IT-TX-11: GET /api/finance/transactions/summary calculates accurate period totals including negative net and empty states")
    void transactionSummary_calculationAndEmptyStates() {
        FinancialAccount account = new FinancialAccount(
                businessA, "Summary Account", AccountType.BANK, "PKR", 2,
                new BigDecimal("50000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        );
        FinancialAccount savedAccount = accountRepository.save(account);

        // Sale: 20,000.00
        transactionPostService.postTransaction(
                contextA, "idem-sum-1",
                new TransactionPostRequest(savedAccount.getId(), "DIRECT_SALE_RECEIPT", "20000.00", today.minusDays(3).toString(), "Sale")
        );

        // Expense: 35,000.00
        transactionPostService.postTransaction(
                contextA, "idem-sum-2",
                new TransactionPostRequest(savedAccount.getId(), "OPERATING_EXPENSE_PAYMENT", "35000.00", today.minusDays(1).toString(), "Expense")
        );

        // 1. Full summary for Business A
        com.app.sme_health_backend.accounting.dto.TransactionSummaryResponse summary =
                transactionReadService.getTransactionSummary(businessA, savedAccount.getId(), null, null, null);

        assertEquals("PKR", summary.currency());
        assertEquals(2, summary.transactionCount());
        assertEquals("20000.00", summary.moneyReceived());
        assertEquals("35000.00", summary.moneyPaid());
        assertEquals("-15000.00", summary.netCashMovement()); // Net is negative
        assertNotNull(summary.observationAt());

        // 2. Filtered summary by date: only Day -1 (expense)
        com.app.sme_health_backend.accounting.dto.TransactionSummaryResponse expenseSummary =
                transactionReadService.getTransactionSummary(
                        businessA, savedAccount.getId(), null, today.minusDays(1).toString(), today.toString()
                );
        assertEquals(1, expenseSummary.transactionCount());
        assertEquals("0.00", expenseSummary.moneyReceived());
        assertEquals("35000.00", expenseSummary.moneyPaid());
        assertEquals("-35000.00", expenseSummary.netCashMovement());

        // 3. Empty summary: future range
        com.app.sme_health_backend.accounting.dto.TransactionSummaryResponse emptySummary =
                transactionReadService.getTransactionSummary(
                        businessA, savedAccount.getId(), null, today.plusDays(10).toString(), today.plusDays(20).toString()
                );
        assertEquals(0, emptySummary.transactionCount());
        assertEquals("0.00", emptySummary.moneyReceived());
        assertEquals("0.00", emptySummary.moneyPaid());
        assertEquals("0.00", emptySummary.netCashMovement());

        // 4. Multi-tenant isolation: Business B summary sees zero Business A transactions
        com.app.sme_health_backend.accounting.dto.TransactionSummaryResponse tenantBSummary =
                transactionReadService.getTransactionSummary(businessB, null, null, null, null);
        assertEquals(0, tenantBSummary.transactionCount());
        assertEquals("0.00", tenantBSummary.moneyReceived());
        assertEquals("0.00", tenantBSummary.moneyPaid());
        assertEquals("0.00", tenantBSummary.netCashMovement());
    }
}
