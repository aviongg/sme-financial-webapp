package com.app.sme_health_backend.obligation;

import com.app.sme_health_backend.accounting.dto.*;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.service.*;
import com.app.sme_health_backend.counterparty.CounterpartyService;
import com.app.sme_health_backend.counterparty.dto.CounterpartyCreateRequest;
import com.app.sme_health_backend.counterparty.dto.CounterpartyDetailResponse;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.dto.CreateBusinessRequest;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.identity.service.BusinessService;
import com.app.sme_health_backend.obligation.dto.*;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * High-concurrency financial stress tests executing on ephemeral embedded PostgreSQL (DisposablePostgres).
 * Verifies non-negotiable financial invariants under race conditions:
 * 1. Concurrent Double Reversal: Exactly one reversal succeeds; account balance never double-credited/debited.
 * 2. Concurrent Same-Key Settlement: Exactly one allocation persisted; all threads receive cached response.
 * 3. Concurrent Over-Allocation: Competing payments cannot drive outstanding balance below zero.
 * 4. Concurrent Advance Applications: Competing applications cannot drive unapplied advance balance negative.
 * 5. Concurrent Posting Idempotency & Changed Payload Replay: Consistent replay + 409 conflict on mutation.
 * 6. Advance Application Integrity: Cash recognized on deposit only, zero cash on application, no duplicate revenue.
 * 7. Server-Side RBAC Enforcement: Strict matrix validation for OWNER, ACCOUNTANT, MANAGER, and VIEWER.
 */
@SpringBootTest
class FinancialConcurrencyStressPostgreSqlIT extends DisposablePostgres {

    @Autowired
    private FinancialAccountRepository accountRepository;

    @Autowired
    private AccountReadService accountReadService;

    @Autowired
    private TransactionPostService transactionPostService;

    @Autowired
    private TransactionReverseService transactionReverseService;

    @Autowired
    private FinancialObligationService obligationService;

    @Autowired
    private PaymentAllocationService paymentAllocationService;

    @Autowired
    private AdvanceService advanceService;

    @Autowired
    private CounterpartyService counterpartyService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private BusinessService businessService;

    @Autowired
    private BusinessAuthorizationService authorizationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID businessId;
    private BusinessAccessContext context;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(ZoneId.of("Asia/Karachi"));

        AppUser user = new AppUser();
        user.setEmail("concurrency-stress-" + UUID.randomUUID() + "@example.test");
        user.setFullName("Concurrency Stress Tester");
        user.setPasswordHash("fixture-hash");
        userId = userRepository.save(user).getId();

        businessId = businessService.createBusiness(
                new CreateBusinessRequest("Stress Test Enterprise", "manufacturing", "en", null, false, null, null, null),
                userId
        ).businessId();

        context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
    }

    private FinancialAccount createAccount(String name, BigDecimal openingBalance) {
        return accountRepository.save(new FinancialAccount(
                businessId, name, AccountType.BANK, "PKR", 2,
                openingBalance, today.minusDays(30), AccountStatus.ACTIVE,
                "Bank Ltd", "0123456789", "Stress account"
        ));
    }

    private CounterpartyDetailResponse createCounterparty(String name, String type) {
        return counterpartyService.createCounterparty(
                context, new CounterpartyCreateRequest(name, type, null, null, null, null, null)
        );
    }

    @Test
    @DisplayName("STRESS-CONC-01: Four concurrent threads attempt double reversal of same receipt with distinct keys")
    void concurrentSimultaneousDoubleReversalRace() throws Exception {
        FinancialAccount bank = createAccount("Reversal Race Bank", new BigDecimal("100000.00"));

        // Post original receipt of 20,000 PKR -> Balance becomes 120,000 PKR
        TransactionDetailResponse origTx = transactionPostService.postTransaction(
                context, "orig-tx-rev-race-" + UUID.randomUUID(),
                new TransactionPostRequest(bank.getId(), "DIRECT_SALE_RECEIPT", "20000.00", today.toString(), "Original customer receipt")
        );
        assertEquals("120000.00", accountReadService.getAccountDetail(businessId, bank.getId()).currentBalance());

        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger conflicts = new AtomicInteger(0);
        List<UUID> reversalTxIds = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            final int threadIdx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TransactionDetailResponse rev = transactionReverseService.postReversal(
                            context, "idemp-rev-thread-" + threadIdx + "-" + UUID.randomUUID(), origTx.id(),
                            new TransactionReverseRequest("Simultaneous reversal thread " + threadIdx)
                    );
                    successes.incrementAndGet();
                    reversalTxIds.add(rev.id());
                } catch (DuplicateResourceException e) {
                    conflicts.incrementAndGet();
                } catch (Exception e) {
                    // unexpected error
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS), "Concurrent reversals timed out");
        executor.shutdown();

        // Exactly one reversal must succeed, and 3 must fail with DuplicateResourceException
        assertEquals(1, successes.get(), "Exactly one reversal request must succeed");
        assertEquals(3, conflicts.get(), "Competing reversal requests must be rejected with 409 Conflict");

        // Account balance must be restored to 100,000.00 exactly (never double-reversed to 80,000.00)
        String finalBalance = accountReadService.getAccountDetail(businessId, bank.getId()).currentBalance();
        assertEquals("100000.00", finalBalance, "Account balance must be restored exactly once");

        // Database must contain exactly one reversal transaction linked to original
        Integer reversalCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM financial_transactions WHERE business_id = ? AND reversed_transaction_id = ?",
                Integer.class, businessId, origTx.id()
        );
        assertEquals(1, reversalCount, "Database must contain exactly 1 reversal transaction record");
    }

    @Test
    @DisplayName("STRESS-CONC-02: Four concurrent threads send identical idempotency key settlement")
    void concurrentSimultaneousSettlementSameKeyRace() throws Exception {
        FinancialAccount bank = createAccount("Settlement Replay Bank", new BigDecimal("50000.00"));
        CounterpartyDetailResponse cp = createCounterparty("Replay Customer", "CUSTOMER");

        ObligationDetailResponse ob = obligationService.createObligation(
                context, new ObligationCreateRequest(
                        cp.id(), "RECEIVABLE", "50000.00",
                        today.minusDays(5).toString(), today.plusDays(10).toString(),
                        "INV-STRESS-02", "Replay settlement invariant", null, null
                )
        );

        String sharedKey = "shared-settle-key-" + UUID.randomUUID();
        ObligationSettlementRequest req = new ObligationSettlementRequest(
                bank.getId(), "25000.00", today.toString(), "CHK-REPLAY-1", "Concurrent replay settlement"
        );

        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        List<UUID> allocationIds = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ObligationSettlementResponse res = obligationService.recordSettlement(context, ob.id(), sharedKey, req);
                    allocationIds.add(res.allocationId());
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS), "Concurrent settlement replays timed out");
        executor.shutdown();

        // Zero unexpected failures; all successful responses must return the same allocationId
        assertEquals(0, failures.get(), "Concurrent idempotent replays must not fail");
        assertEquals(4, allocationIds.size(), "All threads must return valid responses");
        UUID firstId = allocationIds.get(0);
        for (UUID id : allocationIds) {
            assertEquals(firstId, id, "All idempotent replays must return the exact same allocation ID");
        }

        // Obligation outstanding must be reduced exactly once (50,000 - 25,000 = 25,000)
        ObligationDetailResponse finalOb = obligationService.getObligation(context, ob.id());
        assertEquals("25000.00", finalOb.outstandingAmount());
        assertEquals("25000.00", finalOb.paidAmount());

        // Bank balance increased by 25,000 once (50,000 + 25,000 = 75,000)
        assertEquals("75000.00", accountReadService.getAccountDetail(businessId, bank.getId()).currentBalance());
    }

    @Test
    @DisplayName("STRESS-CONC-03: Two concurrent allocations competing for remaining obligation balance cannot over-allocate")
    void concurrentSimultaneousOverAllocationRace() throws Exception {
        FinancialAccount bank = createAccount("Over-Allocation Bank", new BigDecimal("100000.00"));
        CounterpartyDetailResponse cp = createCounterparty("Bulk Buyer", "CUSTOMER");

        // Obligation of 50,000 PKR
        ObligationDetailResponse ob = obligationService.createObligation(
                context, new ObligationCreateRequest(
                        cp.id(), "RECEIVABLE", "50000.00",
                        today.minusDays(10).toString(), today.plusDays(10).toString(),
                        "INV-STRESS-03", "Over-allocation protection", null, null
                )
        );

        // Two competing allocations of 35,000 PKR each (Total 70,000 > 50,000)
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger conflicts = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startLatch.await();
                paymentAllocationService.allocatePayment(
                        context, "alloc-race-key-1-" + UUID.randomUUID(),
                        new PaymentAllocateRequest(
                                cp.id(), "RECEIVABLE", bank.getId(), today.toString(), "35000.00",
                                List.of(new AllocationItemRequest(ob.id(), "35000.00")), "ALLOC-1", "Thread 1", false
                        )
                );
                successes.incrementAndGet();
            } catch (ObligationConflictException e) {
                conflicts.incrementAndGet();
            } catch (Exception e) {
                // other failure
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                paymentAllocationService.allocatePayment(
                        context, "alloc-race-key-2-" + UUID.randomUUID(),
                        new PaymentAllocateRequest(
                                cp.id(), "RECEIVABLE", bank.getId(), today.toString(), "35000.00",
                                List.of(new AllocationItemRequest(ob.id(), "35000.00")), "ALLOC-2", "Thread 2", false
                        )
                );
                successes.incrementAndGet();
            } catch (ObligationConflictException e) {
                conflicts.incrementAndGet();
            } catch (Exception e) {
                // other failure
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS), "Allocation race timed out");
        executor.shutdown();

        // Exactly one must succeed, and one must conflict
        assertEquals(1, successes.get(), "Only one allocation may succeed");
        assertEquals(1, conflicts.get(), "Over-allocation must be rejected with conflict");

        // Outstanding balance must never be negative! 50,000 - 35,000 = 15,000
        ObligationDetailResponse finalOb = obligationService.getObligation(context, ob.id());
        BigDecimal outstanding = new BigDecimal(finalOb.outstandingAmount());
        assertTrue(outstanding.compareTo(BigDecimal.ZERO) >= 0, "Outstanding balance must never be negative");
        assertEquals("15000.00", finalOb.outstandingAmount());
        assertEquals("35000.00", finalOb.paidAmount());
    }

    @Test
    @DisplayName("STRESS-CONC-04: Concurrent advance applications cannot exceed available advance balance")
    void concurrentAdvanceApplicationRaceToZero() throws Exception {
        FinancialAccount bank = createAccount("Advance App Bank", new BigDecimal("50000.00"));
        CounterpartyDetailResponse cp = createCounterparty("Advance Partner", "CUSTOMER");

        // Advance deposit of 30,000 PKR
        AdvanceDetailResponse adv = advanceService.createAdvance(
                context, "adv-race-base-" + UUID.randomUUID(),
                new AdvanceCreateRequest(cp.id(), "RECEIVABLE", bank.getId(), today.toString(), "30000.00", "ADV-STRESS-04", "Advance")
        );

        // Two obligations of 20,000 PKR each
        ObligationDetailResponse ob1 = obligationService.createObligation(
                context, new ObligationCreateRequest(
                        cp.id(), "RECEIVABLE", "20000.00",
                        today.minusDays(5).toString(), today.plusDays(10).toString(),
                        "INV-ADV-1", "Adv app 1", null, null
                )
        );
        ObligationDetailResponse ob2 = obligationService.createObligation(
                context, new ObligationCreateRequest(
                        cp.id(), "RECEIVABLE", "20000.00",
                        today.minusDays(5).toString(), today.plusDays(10).toString(),
                        "INV-ADV-2", "Adv app 2", null, null
                )
        );

        // Competing threads attempt to apply 20,000 each against the 30,000 advance
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger conflicts = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startLatch.await();
                advanceService.applyAdvance(
                        context, "apply-race-1-" + UUID.randomUUID(), adv.id(),
                        new AdvanceApplyRequest(ob1.id(), "20000.00", today.toString(), "App 1")
                );
                successes.incrementAndGet();
            } catch (ObligationConflictException e) {
                conflicts.incrementAndGet();
            } catch (Exception e) {
                // other failure
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                advanceService.applyAdvance(
                        context, "apply-race-2-" + UUID.randomUUID(), adv.id(),
                        new AdvanceApplyRequest(ob2.id(), "20000.00", today.toString(), "App 2")
                );
                successes.incrementAndGet();
            } catch (ObligationConflictException e) {
                conflicts.incrementAndGet();
            } catch (Exception e) {
                // other failure
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS), "Advance application race timed out");
        executor.shutdown();

        // Exactly one must succeed, one must conflict
        assertEquals(1, successes.get(), "Only one advance application may succeed");
        assertEquals(1, conflicts.get(), "Excess advance application must be rejected with conflict");

        // Advance unapplied balance must be exactly 10,000.00 (30,000 - 20,000 = 10,000)
        AdvanceDetailResponse finalAdv = advanceService.getAdvance(context, adv.id());
        BigDecimal unapplied = new BigDecimal(finalAdv.unappliedAmount());
        assertTrue(unapplied.compareTo(BigDecimal.ZERO) >= 0, "Unapplied amount must never be negative");
        assertEquals("10000.00", finalAdv.unappliedAmount());
        assertEquals("20000.00", finalAdv.appliedAmount());
    }

    @Test
    @DisplayName("STRESS-CONC-05: Concurrent posting with identical key replays cleanly; changed payload triggers 409 Conflict")
    void concurrentDirectPostingWithChangedPayloadReplayConflict() throws Exception {
        FinancialAccount bank = createAccount("Posting Replay Bank", new BigDecimal("10000.00"));
        String sharedKey = "post-race-shared-" + UUID.randomUUID();

        TransactionPostRequest postReq = new TransactionPostRequest(
                bank.getId(), "DIRECT_SALE_RECEIPT", "15000.00", today.toString(), "Standard receipt batch"
        );

        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        List<UUID> transactionIds = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TransactionDetailResponse res = transactionPostService.postTransaction(context, sharedKey, postReq);
                    transactionIds.add(res.id());
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS), "Concurrent posting timed out");
        executor.shutdown();

        assertEquals(0, failures.get(), "Idempotent replays must not fail");
        assertEquals(4, transactionIds.size());
        UUID firstTxId = transactionIds.get(0);
        for (UUID id : transactionIds) {
            assertEquals(firstTxId, id, "All identical key requests must return the identical transaction ID");
        }

        // Account balance increased exactly once: 10,000 + 15,000 = 25,000
        assertEquals("25000.00", accountReadService.getAccountDetail(businessId, bank.getId()).currentBalance());

        // Verify exactly one movement in database
        Integer movCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM account_movements WHERE business_id = ? AND transaction_id = ?",
                Integer.class, businessId, firstTxId
        );
        assertEquals(1, movCount, "Database must record exactly one movement for the idempotent transaction");

        // Replay with CHANGED payload (same key, modified amount) must throw DuplicateResourceException (409)
        TransactionPostRequest changedReq = new TransactionPostRequest(
                bank.getId(), "DIRECT_SALE_RECEIPT", "9999.00", today.toString(), "Modified amount"
        );
        assertThrows(DuplicateResourceException.class, () ->
                transactionPostService.postTransaction(context, sharedKey, changedReq),
                "Replaying key with modified payload must be rejected with 409 Conflict"
        );
    }

    @Test
    @DisplayName("STRESS-CONC-06: Advance application does not duplicate cash recognition or alter bank balance")
    void advanceApplicationDoesNotCreateCashOrDuplicateRevenue() {
        FinancialAccount bank = createAccount("Advance Isolation Bank", new BigDecimal("50000.00"));
        CounterpartyDetailResponse cp = createCounterparty("Advance Customer", "CUSTOMER");

        ObligationDetailResponse ob = obligationService.createObligation(
                context, new ObligationCreateRequest(
                        cp.id(), "RECEIVABLE", "100000.00",
                        today.minusDays(5).toString(), today.plusDays(10).toString(),
                        "INV-ADV-ISOLATION", "Invoice for advance", null, null
                )
        );

        // 1. Record customer advance: 40,000 PKR -> Bank balance increases to 90,000 PKR
        AdvanceDetailResponse adv = advanceService.createAdvance(
                context, "adv-cash-rec-" + UUID.randomUUID(),
                new AdvanceCreateRequest(cp.id(), "RECEIVABLE", bank.getId(), today.toString(), "40000.00", "ADV-DEP-1", "Advance deposit")
        );
        assertEquals("90000.00", accountReadService.getAccountDetail(businessId, bank.getId()).currentBalance(), "Advance deposit increases cash");

        // 2. Apply 40,000 PKR advance to obligation -> Bank balance must NOT change!
        AdvanceDetailResponse applied = advanceService.applyAdvance(
                context, "adv-apply-no-cash-" + UUID.randomUUID(), adv.id(),
                new AdvanceApplyRequest(ob.id(), "40000.00", today.toString(), "Apply full advance")
        );

        assertEquals("0.00", applied.unappliedAmount());
        assertEquals("40000.00", applied.appliedAmount());

        // Verify Cash Balance is STILL exactly 90,000.00 (ZERO additional cash recognized)
        assertEquals("90000.00", accountReadService.getAccountDetail(businessId, bank.getId()).currentBalance(),
                "Applying existing advance must NEVER create new cash movements");

        // Verify Obligation reflects partial payment
        ObligationDetailResponse updatedOb = obligationService.getObligation(context, ob.id());
        assertEquals("60000.00", updatedOb.outstandingAmount());
        assertEquals("40000.00", updatedOb.paidAmount());
        assertEquals("PARTIALLY_PAID", updatedOb.settlementStatus());
    }

    @Test
    @DisplayName("STRESS-CONC-07: Server-side RBAC matrix enforcement for OWNER, ACCOUNTANT, MANAGER, and VIEWER")
    void rbacServerSideAuthorizationChecks() {
        // 1. OWNER permissions: full authority
        assertTrue(authorizationService.hasRolePermission(MembershipRole.OWNER, BusinessPermission.FINANCIAL_TRANSACTION_POST));
        assertTrue(authorizationService.hasRolePermission(MembershipRole.OWNER, BusinessPermission.FINANCIAL_ACCOUNT_CREATE));
        assertTrue(authorizationService.hasRolePermission(MembershipRole.OWNER, BusinessPermission.FINANCIAL_TRANSACTION_READ));
        assertTrue(authorizationService.hasRolePermission(MembershipRole.OWNER, BusinessPermission.SCORE_CALCULATE));

        // 2. ACCOUNTANT permissions: operational ledger posting + reading
        assertTrue(authorizationService.hasRolePermission(MembershipRole.ACCOUNTANT, BusinessPermission.FINANCIAL_TRANSACTION_POST));
        assertTrue(authorizationService.hasRolePermission(MembershipRole.ACCOUNTANT, BusinessPermission.FINANCIAL_ACCOUNT_CREATE));
        assertTrue(authorizationService.hasRolePermission(MembershipRole.ACCOUNTANT, BusinessPermission.FINANCIAL_TRANSACTION_READ));
        assertTrue(authorizationService.hasRolePermission(MembershipRole.ACCOUNTANT, BusinessPermission.RECORD_CREATE_UPDATE));

        // 3. MANAGER permissions: read financial accounts/transactions, but strictly PROHIBITED from posting
        assertTrue(authorizationService.hasRolePermission(MembershipRole.MANAGER, BusinessPermission.FINANCIAL_TRANSACTION_READ));
        assertTrue(authorizationService.hasRolePermission(MembershipRole.MANAGER, BusinessPermission.FINANCIAL_ACCOUNT_READ));
        assertFalse(authorizationService.hasRolePermission(MembershipRole.MANAGER, BusinessPermission.FINANCIAL_TRANSACTION_POST),
                "MANAGER must NOT have permission to post financial transactions");
        assertFalse(authorizationService.hasRolePermission(MembershipRole.MANAGER, BusinessPermission.FINANCIAL_ACCOUNT_CREATE),
                "MANAGER must NOT have permission to create financial accounts");
        assertFalse(authorizationService.hasRolePermission(MembershipRole.MANAGER, BusinessPermission.SCORE_CALCULATE),
                "MANAGER must NOT have permission to calculate scores");

        // 4. VIEWER permissions: strictly read-only, zero mutation authority
        assertTrue(authorizationService.hasRolePermission(MembershipRole.VIEWER, BusinessPermission.FINANCIAL_TRANSACTION_READ));
        assertTrue(authorizationService.hasRolePermission(MembershipRole.VIEWER, BusinessPermission.FINANCIAL_ACCOUNT_READ));
        assertTrue(authorizationService.hasRolePermission(MembershipRole.VIEWER, BusinessPermission.FINANCIAL_DATA_READ));
        assertFalse(authorizationService.hasRolePermission(MembershipRole.VIEWER, BusinessPermission.FINANCIAL_TRANSACTION_POST),
                "VIEWER must NOT have permission to post transactions");
        assertFalse(authorizationService.hasRolePermission(MembershipRole.VIEWER, BusinessPermission.FINANCIAL_ACCOUNT_CREATE),
                "VIEWER must NOT have permission to create accounts");
        assertFalse(authorizationService.hasRolePermission(MembershipRole.VIEWER, BusinessPermission.RECORD_CREATE_UPDATE),
                "VIEWER must NOT have permission to create or update records");
        assertFalse(authorizationService.hasRolePermission(MembershipRole.VIEWER, BusinessPermission.SCORE_CALCULATE),
                "VIEWER must NOT have permission to calculate scores");
    }
}
