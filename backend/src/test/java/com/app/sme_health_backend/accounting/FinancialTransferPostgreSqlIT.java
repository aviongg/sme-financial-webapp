package com.app.sme_health_backend.accounting;

import com.app.sme_health_backend.accounting.dto.AccountDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionListResponse;
import com.app.sme_health_backend.accounting.dto.TransactionPostRequest;
import com.app.sme_health_backend.accounting.dto.TransactionSummaryResponse;
import com.app.sme_health_backend.accounting.dto.TransferPostRequest;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.exception.TransactionValidationException;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.service.AccountReadService;
import com.app.sme_health_backend.accounting.service.TransactionPostService;
import com.app.sme_health_backend.accounting.service.TransactionReadService;
import com.app.sme_health_backend.accounting.service.TransferService;
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
class FinancialTransferPostgreSqlIT extends DisposablePostgres {

    @Autowired
    private FinancialAccountRepository accountRepository;

    @Autowired
    private AccountReadService accountReadService;

    @Autowired
    private TransactionPostService transactionPostService;

    @Autowired
    private TransactionReadService transactionReadService;

    @Autowired
    private TransferService transferService;

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
        uA.setEmail("transfer-it-a-" + UUID.randomUUID() + "@example.test");
        uA.setFullName("Transfer User A");
        uA.setPasswordHash("hash-a");
        userA = userRepository.save(uA).getId();
        businessA = businessService.createBusiness(
                new CreateBusinessRequest("Transfer Business A", "retail", "en", null, false, null, null, null),
                userA
        ).businessId();
        contextA = new BusinessAccessContext(userA, businessA, MembershipRole.OWNER);

        AppUser uB = new AppUser();
        uB.setEmail("transfer-it-b-" + UUID.randomUUID() + "@example.test");
        uB.setFullName("Transfer User B");
        uB.setPasswordHash("hash-b");
        userB = userRepository.save(uB).getId();
        businessB = businessService.createBusiness(
                new CreateBusinessRequest("Transfer Business B", "services", "en", null, false, null, null, null),
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
    @DisplayName("IT-TR-01: Transfer 5,000 PKR from Meezan Bank to Petty Cash updates both balances and creates 1 transaction with 2 movements")
    void postTransfer_bankToCash_success() {
        FinancialAccount meezan = accountRepository.save(new FinancialAccount(
                businessA, "Meezan Bank", AccountType.BANK, "PKR", 2,
                new BigDecimal("50000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        ));
        FinancialAccount pettyCash = accountRepository.save(new FinancialAccount(
                businessA, "Petty Cash", AccountType.CASH, "PKR", 2,
                new BigDecimal("1000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        ));

        TransferPostRequest request = new TransferPostRequest(
                meezan.getId(), pettyCash.getId(), "5000.00", today.toString(), "Replenish petty cash"
        );

        TransactionDetailResponse response = transferService.postTransfer(contextA, "idem-tr-01", request);

        assertNotNull(response);
        assertEquals("INTERNAL_TRANSFER", response.eventType());
        assertEquals("DECREASE", response.movementDirection());
        assertEquals("5000.00", response.amount());
        assertEquals("45000.00", response.resultingBalance());
        assertEquals(meezan.getId(), response.sourceAccountId());
        assertEquals("Meezan Bank", response.sourceAccountName());
        assertEquals(pettyCash.getId(), response.destinationAccountId());
        assertEquals("Petty Cash", response.destinationAccountName());
        assertEquals(2, response.movements().size());

        // Verify account read services reflect new balances
        AccountDetailResponse meezanDetail = accountReadService.getAccountDetail(businessA, meezan.getId());
        assertEquals("45000.00", meezanDetail.currentBalance());

        AccountDetailResponse cashDetail = accountReadService.getAccountDetail(businessA, pettyCash.getId());
        assertEquals("6000.00", cashDetail.currentBalance());

        // Verify DB rows
        Integer txCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM financial_transactions WHERE id = ? AND event_type = 'INTERNAL_TRANSFER' AND destination_account_id = ?",
                Integer.class, response.id(), pettyCash.getId()
        );
        assertEquals(1, txCount);

        Integer movCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM account_movements WHERE transaction_id = ?",
                Integer.class, response.id()
        );
        assertEquals(2, movCount);

        // Verify audit event
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM security_audit_events WHERE business_id = ? AND event_type = 'FINANCIAL_TRANSFER_POSTED'",
                Integer.class, businessA
        );
        assertEquals(1, auditCount);
    }

    @Test
    @DisplayName("IT-TR-02: Summary calculations: external cash flow unchanged, transfers aggregated accurately")
    void postTransfer_summaryCalculations() {
        FinancialAccount bank = accountRepository.save(new FinancialAccount(
                businessA, "Meezan Bank", AccountType.BANK, "PKR", 2,
                new BigDecimal("50000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        ));
        FinancialAccount cash = accountRepository.save(new FinancialAccount(
                businessA, "Petty Cash", AccountType.CASH, "PKR", 2,
                new BigDecimal("1000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        ));

        // 1. External sale: 20,000 to bank
        transactionPostService.postTransaction(
                contextA, "idem-sum-sale",
                new TransactionPostRequest(bank.getId(), "DIRECT_SALE_RECEIPT", "20000.00", today.minusDays(3).toString(), "Direct sale")
        );

        // 2. External expense: 5,000 from bank
        transactionPostService.postTransaction(
                contextA, "idem-sum-exp",
                new TransactionPostRequest(bank.getId(), "OPERATING_EXPENSE_PAYMENT", "5000.00", today.minusDays(2).toString(), "Supplier bill")
        );

        // 3. Transfer 5,000 from bank to cash
        transferService.postTransfer(
                contextA, "idem-sum-trans",
                new TransferPostRequest(bank.getId(), cash.getId(), "5000.00", today.toString(), "Petty cash transfer")
        );

        // Business-wide summary
        TransactionSummaryResponse busSummary = transactionReadService.getTransactionSummary(businessA, null, null, null, null);
        assertEquals(3, busSummary.transactionCount());
        assertEquals("20000.00", busSummary.moneyReceived());
        assertEquals("5000.00", busSummary.moneyPaid());
        assertEquals("15000.00", busSummary.netCashMovement());
        assertEquals("5000.00", busSummary.transferIn());
        assertEquals("5000.00", busSummary.transferOut());
        assertEquals("15000.00", busSummary.netAccountMovement());

        // Bank filtered summary
        TransactionSummaryResponse bankSummary = transactionReadService.getTransactionSummary(businessA, bank.getId(), null, null, null);
        assertEquals(3, bankSummary.transactionCount());
        assertEquals("20000.00", bankSummary.moneyReceived());
        assertEquals("5000.00", bankSummary.moneyPaid());
        assertEquals("0.00", bankSummary.transferIn());
        assertEquals("5000.00", bankSummary.transferOut());
        assertEquals("10000.00", bankSummary.netAccountMovement()); // 20000 - 5000 - 5000

        // Cash filtered summary
        TransactionSummaryResponse cashSummary = transactionReadService.getTransactionSummary(businessA, cash.getId(), null, null, null);
        assertEquals(1, cashSummary.transactionCount());
        assertEquals("0.00", cashSummary.moneyReceived());
        assertEquals("0.00", cashSummary.moneyPaid());
        assertEquals("5000.00", cashSummary.transferIn());
        assertEquals("0.00", cashSummary.transferOut());
        assertEquals("5000.00", cashSummary.netAccountMovement());
    }

    @Test
    @DisplayName("IT-TR-03: Activity list: transfer appears once business-wide and in both participating accounts")
    void listTransactions_transferVisibility() {
        FinancialAccount bank = accountRepository.save(new FinancialAccount(
                businessA, "Meezan Bank", AccountType.BANK, "PKR", 2,
                new BigDecimal("50000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        ));
        FinancialAccount cash = accountRepository.save(new FinancialAccount(
                businessA, "Petty Cash", AccountType.CASH, "PKR", 2,
                new BigDecimal("1000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        ));

        transferService.postTransfer(
                contextA, "idem-list-trans",
                new TransferPostRequest(bank.getId(), cash.getId(), "5000.00", today.toString(), "Bank to cash")
        );

        // 1. Business-wide list: exactly 1 transaction (no duplicate due to dual movements)
        TransactionListResponse busList = transactionReadService.listTransactions(businessA, null, null, null, null, 0, 20);
        assertEquals(1, busList.totalElements());
        assertEquals("INTERNAL_TRANSFER", busList.content().get(0).eventType());

        // 2. Bank filtered list: 1 transaction, direction DECREASE, resulting balance 45000.00
        TransactionListResponse bankList = transactionReadService.listTransactions(businessA, bank.getId(), null, null, null, 0, 20);
        assertEquals(1, bankList.totalElements());
        assertEquals("DECREASE", bankList.content().get(0).movementDirection());
        assertEquals("45000.00", bankList.content().get(0).resultingBalance());

        // 3. Cash filtered list: 1 transaction, direction INCREASE, resulting balance 6000.00
        TransactionListResponse cashList = transactionReadService.listTransactions(businessA, cash.getId(), null, null, null, 0, 20);
        assertEquals(1, cashList.totalElements());
        assertEquals("INCREASE", cashList.content().get(0).movementDirection());
        assertEquals("6000.00", cashList.content().get(0).resultingBalance());
    }

    @Test
    @DisplayName("IT-TR-04: Insufficient cash balance throws validation error and rolls back completely")
    void postTransfer_cashDeficitRollback() {
        FinancialAccount bank = accountRepository.save(new FinancialAccount(
                businessA, "Meezan Bank", AccountType.BANK, "PKR", 2,
                new BigDecimal("50000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        ));
        FinancialAccount cash = accountRepository.save(new FinancialAccount(
                businessA, "Petty Cash", AccountType.CASH, "PKR", 2,
                new BigDecimal("1000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        ));

        // Attempt to move 2,000 from Cash to Bank when Cash only has 1,000
        TransferPostRequest request = new TransferPostRequest(
                cash.getId(), bank.getId(), "2000.00", today.toString(), "Overdraw cash"
        );

        TransactionValidationException ex = assertThrows(
                TransactionValidationException.class,
                () -> transferService.postTransfer(contextA, "idem-cash-neg", request)
        );
        assertTrue(ex.getErrors().containsKey("cashBalance"));

        // Verify zero transactions, movements, or audit events persisted
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM financial_transactions WHERE business_id = ?", Integer.class, businessA));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM account_movements WHERE business_id = ?", Integer.class, businessA));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM security_audit_events WHERE business_id = ? AND event_type = 'FINANCIAL_TRANSFER_POSTED'", Integer.class, businessA));

        // Balances remain untouched
        assertEquals("1000.00", accountReadService.getAccountDetail(businessA, cash.getId()).currentBalance());
        assertEquals("50000.00", accountReadService.getAccountDetail(businessA, bank.getId()).currentBalance());
    }

    @Test
    @DisplayName("IT-TR-05: Same-account transfer is rejected")
    void postTransfer_sameAccountRejected() {
        FinancialAccount bank = accountRepository.save(new FinancialAccount(
                businessA, "Meezan Bank", AccountType.BANK, "PKR", 2,
                new BigDecimal("50000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        ));

        TransferPostRequest request = new TransferPostRequest(
                bank.getId(), bank.getId(), "5000.00", today.toString(), "Self transfer"
        );

        TransactionValidationException ex = assertThrows(
                TransactionValidationException.class,
                () -> transferService.postTransfer(contextA, "idem-same-acc", request)
        );
        assertTrue(ex.getErrors().containsKey("destinationAccountId"));
    }

    @Test
    @DisplayName("IT-TR-06: Cross-business transfer is rejected with 404 (non-disclosing)")
    void postTransfer_crossBusinessRejected() {
        FinancialAccount bankA = accountRepository.save(new FinancialAccount(
                businessA, "Meezan Bank A", AccountType.BANK, "PKR", 2,
                new BigDecimal("50000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        ));
        FinancialAccount bankB = accountRepository.save(new FinancialAccount(
                businessB, "Meezan Bank B", AccountType.BANK, "PKR", 2,
                new BigDecimal("20000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        ));

        TransferPostRequest request = new TransferPostRequest(
                bankA.getId(), bankB.getId(), "5000.00", today.toString(), "Cross business transfer"
        );

        assertThrows(
                ResourceNotFoundException.class,
                () -> transferService.postTransfer(contextA, "idem-cross-biz", request)
        );
    }

    @Test
    @DisplayName("IT-TR-07: Concurrent opposite-direction transfers do not deadlock and preserve exact balance arithmetic")
    void postTransfer_concurrentOppositeDirection_noDeadlock() throws Exception {
        FinancialAccount bank = accountRepository.save(new FinancialAccount(
                businessA, "Concurrent Bank", AccountType.BANK, "PKR", 2,
                new BigDecimal("50000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        ));
        FinancialAccount cash = accountRepository.save(new FinancialAccount(
                businessA, "Concurrent Cash", AccountType.CASH, "PKR", 2,
                new BigDecimal("10000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        ));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<TransactionDetailResponse>> futures = new ArrayList<>();

        // 5 threads: Bank -> Cash (100 PKR each = 500 PKR transferred)
        // 5 threads: Cash -> Bank (50 PKR each = 250 PKR transferred)
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                return transferService.postTransfer(
                        contextA, "idem-opp-b2c-" + idx,
                        new TransferPostRequest(bank.getId(), cash.getId(), "100.00", today.toString(), "B2C " + idx)
                );
            }));
        }
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                return transferService.postTransfer(
                        contextA, "idem-opp-c2b-" + idx,
                        new TransferPostRequest(cash.getId(), bank.getId(), "50.00", today.toString(), "C2B " + idx)
                );
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<TransactionDetailResponse> future : futures) {
            TransactionDetailResponse res = future.get(15, TimeUnit.SECONDS);
            assertNotNull(res);
        }
        executor.shutdown();

        // Net change: Bank -500 + 250 = -250 -> 49,750.00
        // Cash +500 - 250 = +250 -> 10,250.00
        assertEquals("49750.00", accountReadService.getAccountDetail(businessA, bank.getId()).currentBalance());
        assertEquals("10250.00", accountReadService.getAccountDetail(businessA, cash.getId()).currentBalance());
    }

    @Test
    @DisplayName("IT-TR-08: Concurrent identical requests with same key produce exactly one transfer")
    void postTransfer_concurrentIdenticalKey_singleExecution() throws Exception {
        FinancialAccount bank = accountRepository.save(new FinancialAccount(
                businessA, "Idem Bank", AccountType.BANK, "PKR", 2,
                new BigDecimal("50000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        ));
        FinancialAccount cash = accountRepository.save(new FinancialAccount(
                businessA, "Idem Cash", AccountType.CASH, "PKR", 2,
                new BigDecimal("1000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        ));

        String sharedKey = "idem-concurrent-tr-" + UUID.randomUUID();
        TransferPostRequest request = new TransferPostRequest(
                bank.getId(), cash.getId(), "3000.00", today.toString(), "Concurrent transfer"
        );

        int threadCount = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<TransactionDetailResponse>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                return transferService.postTransfer(contextA, sharedKey, request);
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        Set<UUID> txIds = new HashSet<>();
        for (Future<TransactionDetailResponse> future : futures) {
            TransactionDetailResponse res = future.get(10, TimeUnit.SECONDS);
            assertNotNull(res);
            txIds.add(res.id());
        }
        executor.shutdown();

        // Exactly one unique transaction created
        assertEquals(1, txIds.size());
        UUID singleTxId = txIds.iterator().next();

        Integer dbTxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM financial_transactions WHERE id = ?", Integer.class, singleTxId
        );
        assertEquals(1, dbTxCount);

        Integer dbMovCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_movements WHERE transaction_id = ?", Integer.class, singleTxId
        );
        assertEquals(2, dbMovCount);

        // Balances: Bank decreased by 3000 once (47000.00), Cash increased by 3000 once (4000.00)
        assertEquals("47000.00", accountReadService.getAccountDetail(businessA, bank.getId()).currentBalance());
        assertEquals("4000.00", accountReadService.getAccountDetail(businessA, cash.getId()).currentBalance());
    }

    @Test
    @DisplayName("IT-TR-09: Replay with same idempotency key and changed payload returns 409 Conflict")
    void postTransfer_changedPayload_duplicateResourceException() {
        FinancialAccount bank = accountRepository.save(new FinancialAccount(
                businessA, "Conflict Bank", AccountType.BANK, "PKR", 2,
                new BigDecimal("50000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        ));
        FinancialAccount cash = accountRepository.save(new FinancialAccount(
                businessA, "Conflict Cash", AccountType.CASH, "PKR", 2,
                new BigDecimal("1000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        ));

        String key = "idem-conflict-" + UUID.randomUUID();

        // 1. Initial successful transfer
        transferService.postTransfer(
                contextA, key,
                new TransferPostRequest(bank.getId(), cash.getId(), "1000.00", today.toString(), "Initial transfer")
        );

        // 2. Replay same key with different amount (2000.00)
        assertThrows(
                DuplicateResourceException.class,
                () -> transferService.postTransfer(
                        contextA, key,
                        new TransferPostRequest(bank.getId(), cash.getId(), "2000.00", today.toString(), "Changed amount")
                )
        );
    }
}
