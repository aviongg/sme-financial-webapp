package com.app.sme_health_backend.obligation;

import com.app.sme_health_backend.accounting.dto.TransactionReverseRequest;
import com.app.sme_health_backend.accounting.dto.TransactionSummaryResponse;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.service.TransactionReadService;
import com.app.sme_health_backend.accounting.service.TransactionReverseService;
import com.app.sme_health_backend.counterparty.CounterpartyService;
import com.app.sme_health_backend.counterparty.dto.CounterpartyCreateRequest;
import com.app.sme_health_backend.counterparty.dto.CounterpartyDetailResponse;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.dto.CreateBusinessRequest;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.service.BusinessService;
import com.app.sme_health_backend.obligation.aging.AgingBucket;
import com.app.sme_health_backend.obligation.aging.ObligationAgingResult;
import com.app.sme_health_backend.obligation.dto.ObligationCreateRequest;
import com.app.sme_health_backend.obligation.dto.ObligationDetailResponse;
import com.app.sme_health_backend.obligation.dto.ObligationSettlementRequest;
import com.app.sme_health_backend.obligation.dto.ObligationSettlementResponse;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FinancialObligationPostgreSqlIT extends DisposablePostgres {

    @Autowired
    private FinancialObligationService obligationService;

    @Autowired
    private TransactionReverseService transactionReverseService;

    @Autowired
    private TransactionReadService transactionReadService;

    @Autowired
    private CounterpartyService counterpartyService;

    @Autowired
    private FinancialAccountRepository accountRepository;

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
        uA.setEmail("ob-it-a-" + UUID.randomUUID() + "@example.test");
        uA.setFullName("Obligation User A");
        uA.setPasswordHash("hash-a");
        userA = userRepository.save(uA).getId();
        businessA = businessService.createBusiness(
                new CreateBusinessRequest("OB Business A", "retail", "en", null, false, null, null, null),
                userA
        ).businessId();
        contextA = new BusinessAccessContext(userA, businessA, MembershipRole.OWNER);

        AppUser uB = new AppUser();
        uB.setEmail("ob-it-b-" + UUID.randomUUID() + "@example.test");
        uB.setFullName("Obligation User B");
        uB.setPasswordHash("hash-b");
        userB = userRepository.save(uB).getId();
        businessB = businessService.createBusiness(
                new CreateBusinessRequest("OB Business B", "services", "en", null, false, null, null, null),
                userB
        ).businessId();
        contextB = new BusinessAccessContext(userB, businessB, MembershipRole.OWNER);
    }

    private FinancialAccount createAccount(UUID businessId, String name, BigDecimal opening) {
        FinancialAccount account = new FinancialAccount(
                businessId,
                name,
                AccountType.BANK,
                "PKR",
                2,
                opening,
                today.minusDays(30),
                AccountStatus.ACTIVE,
                null,
                null,
                "Test Account"
        );
        return accountRepository.save(account);
    }

    private ObligationCreateRequest createReq(UUID counterpartyId, String direction, String amount, String issueDate, String dueDate, String referenceNumber, String description) {
        return new ObligationCreateRequest(counterpartyId, direction, amount, issueDate, dueDate, referenceNumber, description, null, null);
    }

    @Test
    @DisplayName("IT-OB-01: Flyway migration V26 grants SELECT, INSERT, UPDATE to finsight_app; DELETE is denied")
    void verifyFlywayV26AndGrants() {
        Boolean canSelectOb = jdbcTemplate.queryForObject(
                "SELECT has_table_privilege('finsight_app', 'financial_obligations', 'SELECT')",
                Boolean.class
        );
        assertTrue(canSelectOb, "finsight_app must have SELECT privilege on financial_obligations");

        Boolean canInsertOb = jdbcTemplate.queryForObject(
                "SELECT has_table_privilege('finsight_app', 'financial_obligations', 'INSERT')",
                Boolean.class
        );
        assertTrue(canInsertOb, "finsight_app must have INSERT privilege on financial_obligations");

        Boolean canUpdateOb = jdbcTemplate.queryForObject(
                "SELECT has_table_privilege('finsight_app', 'financial_obligations', 'UPDATE')",
                Boolean.class
        );
        assertTrue(canUpdateOb, "finsight_app must have UPDATE privilege on financial_obligations");

        Boolean canDeleteOb = jdbcTemplate.queryForObject(
                "SELECT has_table_privilege('finsight_app', 'financial_obligations', 'DELETE')",
                Boolean.class
        );
        assertFalse(canDeleteOb, "finsight_app must NOT have DELETE privilege on financial_obligations");

        Boolean canDeleteAlloc = jdbcTemplate.queryForObject(
                "SELECT has_table_privilege('finsight_app', 'obligation_payment_allocations', 'DELETE')",
                Boolean.class
        );
        assertFalse(canDeleteAlloc, "finsight_app must NOT have DELETE privilege on obligation_payment_allocations");

        // Attempting direct DELETE as runtime role finsight_app must be rejected
        DataAccessException ex = assertThrows(DataAccessException.class, () -> {
            jdbcTemplate.update("DELETE FROM financial_obligations WHERE id = ?", UUID.randomUUID());
        }, "Direct DELETE by finsight_app must be denied with permission denied");
        assertTrue(ex.getMostSpecificCause().getMessage().contains("permission denied"));
    }

    @Test
    @DisplayName("IT-OB-02: Zero financial mutation on obligation creation (no cash, no revenue/expense recognition)")
    void testZeroFinancialMutationOnCreation() {
        int initialTxCount = jdbcTemplate.queryForObject("SELECT count(*) FROM financial_transactions", Integer.class);
        int initialMovCount = jdbcTemplate.queryForObject("SELECT count(*) FROM account_movements", Integer.class);
        int initialMonthlyCount = jdbcTemplate.queryForObject("SELECT count(*) FROM monthly_records", Integer.class);

        CounterpartyDetailResponse customer = counterpartyService.createCounterparty(
                contextA,
                new CounterpartyCreateRequest("Customer Mutation Test", "CUSTOMER", null, null, null, null, null)
        );

        ObligationDetailResponse ob = obligationService.createObligation(
                contextA,
                createReq(
                        customer.id(), "RECEIVABLE", "100000.00",
                        today.toString(), today.plusDays(30).toString(),
                        "INV-MUT-1", "Zero mutation test"
                )
        );

        assertEquals("100000.00", ob.originalAmount());
        assertEquals("100000.00", ob.outstandingAmount());
        assertEquals("0.00", ob.paidAmount());

        int finalTxCount = jdbcTemplate.queryForObject("SELECT count(*) FROM financial_transactions", Integer.class);
        int finalMovCount = jdbcTemplate.queryForObject("SELECT count(*) FROM account_movements", Integer.class);
        int finalMonthlyCount = jdbcTemplate.queryForObject("SELECT count(*) FROM monthly_records", Integer.class);

        assertEquals(initialTxCount, finalTxCount, "Creating obligation must not create financial transactions");
        assertEquals(initialMovCount, finalMovCount, "Creating obligation must not create account movements");
        assertEquals(initialMonthlyCount, finalMonthlyCount, "Creating obligation must not alter monthly records");
    }

    @Test
    @DisplayName("IT-OB-03: Composite foreign key blocks cross-business counterparty linkage")
    void testCompositeForeignKey_CrossBusinessCounterpartyRejected() {
        // Counterparty in Business B
        CounterpartyDetailResponse cpB = counterpartyService.createCounterparty(
                contextB,
                new CounterpartyCreateRequest("Customer In B", "CUSTOMER", null, null, null, null, null)
        );

        // Attempting to create an obligation in Business A referencing cpB via service fails
        assertThrows(RuntimeException.class, () -> {
            obligationService.createObligation(
                    contextA,
                    createReq(
                            cpB.id(), "RECEIVABLE", "50000.00",
                            today.toString(), null, "REF-B", null
                    )
            );
        });

        // Direct SQL insert attempting cross-tenant counterparty linkage must be rejected by PostgreSQL FK
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update("""
                    INSERT INTO financial_obligations (
                        id, business_id, counterparty_id, direction, source_type,
                        issue_date, original_amount, currency, status, created_by_user_id
                    ) VALUES (?, ?, ?, 'RECEIVABLE', 'MANUAL_DECLARATION', ?, 50000.00, 'PKR', 'OPEN', ?)
                    """, UUID.randomUUID(), businessA, cpB.id(), today, userA);
        });
    }

    @Test
    @DisplayName("IT-OB-04: Unique source identity prevents duplicate sourced obligations")
    void testUniqueSourceIdentity_EnforcesDeduplication() {
        CounterpartyDetailResponse customer = counterpartyService.createCounterparty(
                contextA,
                new CounterpartyCreateRequest("Invoice Customer", "CUSTOMER", null, null, null, null, null)
        );

        UUID obId1 = UUID.randomUUID();
        UUID obId2 = UUID.randomUUID();
        String invoiceSourceId = "INV-2026-001";

        // Insert first sourced obligation
        jdbcTemplate.update("""
                INSERT INTO financial_obligations (
                    id, business_id, counterparty_id, direction, source_type, source_id,
                    issue_date, original_amount, currency, status, created_by_user_id
                ) VALUES (?, ?, ?, 'RECEIVABLE', 'CUSTOMER_INVOICE', ?, ?, 75000.00, 'PKR', 'OPEN', ?)
                """, obId1, businessA, customer.id(), invoiceSourceId, today, userA);

        // Attempting duplicate insert with same (business_id, source_type, source_id) must be rejected by uq_obligations_business_source
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update("""
                    INSERT INTO financial_obligations (
                        id, business_id, counterparty_id, direction, source_type, source_id,
                        issue_date, original_amount, currency, status, created_by_user_id
                    ) VALUES (?, ?, ?, 'RECEIVABLE', 'CUSTOMER_INVOICE', ?, ?, 75000.00, 'PKR', 'OPEN', ?)
                    """, obId2, businessA, customer.id(), invoiceSourceId, today, userA);
        });
    }

    @Test
    @DisplayName("IT-OB-05: Atomic settlement lifecycle with cash movement and no duplicate revenue")
    void testAtomicSettlementLifecycle() {
        FinancialAccount bankAccount = createAccount(businessA, "Main Bank", new BigDecimal("50000.00"));
        CounterpartyDetailResponse customer = counterpartyService.createCounterparty(
                contextA,
                new CounterpartyCreateRequest("Settlement Customer", "CUSTOMER", null, null, null, null, null)
        );

        ObligationDetailResponse obligation = obligationService.createObligation(
                contextA,
                createReq(
                        customer.id(), "RECEIVABLE", "100000.00",
                        today.minusDays(5).toString(), today.plusDays(10).toString(),
                        "INV-SETTLE-1", "Settlement test"
                )
        );

        // 1. Partial settlement of PKR 40,000
        ObligationSettlementResponse settlement1 = obligationService.recordSettlement(
                contextA,
                obligation.id(),
                "idemp-settle-1",
                new ObligationSettlementRequest(
                        bankAccount.getId(), "40000.00", today.toString(), "CHK-01", "Check part 1"
                )
        );

        assertEquals("40000.00", settlement1.amount());
        assertEquals("90000.00", settlement1.resultingAccountBalance());
        assertEquals("60000.00", settlement1.remainingOutstandingAmount());
        assertEquals("PARTIALLY_PAID", settlement1.settlementStatus());

        // Check account movement in DB
        BigDecimal movAmount = jdbcTemplate.queryForObject(
                "SELECT amount FROM account_movements WHERE account_id = ? AND movement_direction = 'INCREASE'",
                BigDecimal.class,
                bankAccount.getId()
        );
        assertEquals(0, new BigDecimal("40000.00").compareTo(movAmount));

        // Check transaction summary
        TransactionSummaryResponse summary1 = transactionReadService.getTransactionSummary(
                businessA, null, null, today.minusDays(30).toString(), today.plusDays(30).toString()
        );
        assertEquals("40000.00", summary1.moneyReceived());
        assertEquals("0.00", summary1.moneyPaid());
        assertEquals("40000.00", summary1.netCashMovement());

        // 2. Remaining settlement of PKR 60,000
        ObligationSettlementResponse settlement2 = obligationService.recordSettlement(
                contextA,
                obligation.id(),
                "idemp-settle-2",
                new ObligationSettlementRequest(
                        bankAccount.getId(), "60000.00", today.toString(), "CHK-02", "Check part 2"
                )
        );

        assertEquals("60000.00", settlement2.amount());
        assertEquals("150000.00", settlement2.resultingAccountBalance());
        assertEquals("0.00", settlement2.remainingOutstandingAmount());
        assertEquals("FULLY_PAID", settlement2.settlementStatus());

        // 3. Attempting third settlement of PKR 1,000 must be rejected
        assertThrows(ObligationConflictException.class, () -> {
            obligationService.recordSettlement(
                    contextA,
                    obligation.id(),
                    "idemp-settle-3",
                    new ObligationSettlementRequest(
                            bankAccount.getId(), "1000.00", today.toString(), null, null
                    )
            );
        });

        // 4. Same idempotency key replays cached response
        ObligationSettlementResponse replayed = obligationService.recordSettlement(
                contextA,
                obligation.id(),
                "idemp-settle-2",
                new ObligationSettlementRequest(
                        bankAccount.getId(), "60000.00", today.toString(), "CHK-02", "Check part 2"
                )
        );
        assertEquals(settlement2.allocationId(), replayed.allocationId());
        assertEquals("150000.00", replayed.resultingAccountBalance());
    }

    @Test
    @DisplayName("IT-OB-06: Settlement reversal cancels allocation, restores outstanding, and reverses cash movement")
    void testSettlementReversalLifecycle() {
        FinancialAccount bankAccount = createAccount(businessA, "Operating Bank", new BigDecimal("150000.00"));
        CounterpartyDetailResponse supplier = counterpartyService.createCounterparty(
                contextA,
                new CounterpartyCreateRequest("Reversal Supplier", "SUPPLIER", null, null, null, null, null)
        );

        ObligationDetailResponse payable = obligationService.createObligation(
                contextA,
                createReq(
                        supplier.id(), "PAYABLE", "80000.00",
                        today.minusDays(5).toString(), today.plusDays(10).toString(),
                        "BILL-REV-1", "Payable reversal test"
                )
        );

        // Pay supplier PKR 30,000
        ObligationSettlementResponse payment = obligationService.recordSettlement(
                contextA,
                payable.id(),
                "idemp-rev-pay-1",
                new ObligationSettlementRequest(
                        bankAccount.getId(), "30000.00", today.toString(), "PAY-01", "Supplier payment"
                )
        );

        assertEquals("50000.00", payment.remainingOutstandingAmount());
        assertEquals("120000.00", payment.resultingAccountBalance());

        // Check transaction details
        UUID txId = payment.transactionId();
        String eventType = jdbcTemplate.queryForObject(
                "SELECT event_type FROM financial_transactions WHERE id = ?",
                String.class,
                txId
        );
        assertEquals(TransactionEventType.PAYABLE_SETTLEMENT.name(), eventType);

        // Reverse the settlement transaction
        var reversedTx = transactionReverseService.postReversal(
                contextA,
                "idemp-tx-reversal-1",
                txId,
                new TransactionReverseRequest("Incorrect payment recorded")
        );
        assertNotNull(reversedTx.id());
        assertEquals(txId, reversedTx.reversedTransactionId());
        assertTrue(reversedTx.isReversal());

        // Verify account balance restored to 150,000 via resultingBalance and compensating movement
        assertEquals("150000.00", reversedTx.resultingBalance());
        BigDecimal compMovement = jdbcTemplate.queryForObject(
                "SELECT amount FROM account_movements WHERE account_id = ? AND movement_direction = 'INCREASE'",
                BigDecimal.class,
                bankAccount.getId()
        );
        assertEquals(0, new BigDecimal("30000.00").compareTo(compMovement));

        // Verify allocation status is CANCELLED and reversal_transaction_id is linked
        String allocStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM obligation_payment_allocations WHERE id = ?",
                String.class,
                payment.allocationId()
        );
        assertEquals("CANCELLED", allocStatus);

        UUID linkedRevTx = jdbcTemplate.queryForObject(
                "SELECT reversal_transaction_id FROM obligation_payment_allocations WHERE id = ?",
                UUID.class,
                payment.allocationId()
        );
        assertEquals(reversedTx.id(), linkedRevTx);

        // Verify obligation outstanding balance is restored to 80,000.00
        ObligationDetailResponse restoredPayable = obligationService.getObligation(contextA, payable.id());
        assertEquals("80000.00", restoredPayable.outstandingAmount());
        assertEquals("0.00", restoredPayable.paidAmount());
        assertEquals("UNPAID", restoredPayable.settlementStatus());

        // Reversal cannot happen twice
        assertThrows(Exception.class, () -> {
            transactionReverseService.postReversal(
                    contextA,
                    "idemp-tx-reversal-2",
                    txId,
                    new TransactionReverseRequest("Second reversal attempt")
            );
        });
    }

    @Test
    @DisplayName("IT-OB-07: Historical aging reconciliation as-of past and future dates")
    void testHistoricalAsOfAgingReconciliation() {
        CounterpartyDetailResponse customer = counterpartyService.createCounterparty(
                contextA,
                new CounterpartyCreateRequest("Aging Customer", "CUSTOMER", null, null, null, null, null)
        );

        // Obligation issued 15 days ago, due 5 days ago (OVERDUE_1_30)
        ObligationDetailResponse obligation = obligationService.createObligation(
                contextA,
                createReq(
                        customer.id(), "RECEIVABLE", "100000.00",
                        today.minusDays(15).toString(), today.minusDays(5).toString(),
                        "INV-AGE-1", "Aging test"
                )
        );

        FinancialAccount account = createAccount(businessA, "Aging Bank", new BigDecimal("50000.00"));

        // Payment made today
        obligationService.recordSettlement(
                contextA,
                obligation.id(),
                "idemp-aging-pay",
                new ObligationSettlementRequest(
                        account.getId(), "40000.00", today.toString(), null, null
                )
        );

        // As of 2 days ago (before payment was made): Outstanding was full 100,000
        ObligationAgingResult pastAging = obligationService.getAging(contextA, today.minusDays(2).toString());
        assertEquals(new BigDecimal("100000.00"), pastAging.receivableTotalOutstanding());

        // As of today (after payment was made): Outstanding is 60,000
        ObligationAgingResult todayAging = obligationService.getAging(contextA, today.toString());
        assertEquals(new BigDecimal("60000.00"), todayAging.receivableTotalOutstanding());
        assertEquals(new BigDecimal("60000.00"), todayAging.receivables().buckets().get(AgingBucket.OVERDUE_1_30).totalOutstanding());
    }

    @Test
    @DisplayName("IT-OB-08: Historical reversal verification (Jan 1 receivable, Jan 20 settlement, Feb 10 reversal)")
    void testHistoricalReversalScenarioJan1Jan20Feb10() {
        CounterpartyDetailResponse customer = counterpartyService.createCounterparty(
                contextA,
                new CounterpartyCreateRequest("Historical Reversal Customer", "CUSTOMER", null, null, null, null, null)
        );

        FinancialAccount bankAccount = new FinancialAccount(
                businessA,
                "Historical Bank",
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("100000.00"),
                LocalDate.of(2025, 12, 1),
                AccountStatus.ACTIVE,
                null,
                null,
                "Historical Account"
        );
        bankAccount = accountRepository.save(bankAccount);

        // 1. January 1: Create PKR 100,000 receivable
        LocalDate issueDate = LocalDate.of(2026, 1, 1);
        LocalDate dueDate = LocalDate.of(2026, 1, 31);
        ObligationDetailResponse receivable = obligationService.createObligation(
                contextA,
                createReq(
                        customer.id(), "RECEIVABLE", "100000.00",
                        issueDate.toString(), dueDate.toString(),
                        "REC-HIST-001", "Historical reversal test"
                )
        );

        // 2. January 20: Record PKR 40,000 settlement
        LocalDate settlementDate = LocalDate.of(2026, 1, 20);
        ObligationSettlementResponse settlement = obligationService.recordSettlement(
                contextA,
                receivable.id(),
                "idemp-settle-hist-1",
                new ObligationSettlementRequest(
                        bankAccount.getId(), "40000.00", settlementDate.toString(), "CHK-HIST-01", "Check on Jan 20"
                )
        );

        // Pre-reversal verification: as of Jan 10, Jan 25, Feb 11
        ObligationAgingResult preAgingJan10 = obligationService.getAging(contextA, "2026-01-10");
        assertEquals(new BigDecimal("100000.00"), preAgingJan10.receivableTotalOutstanding());

        ObligationAgingResult preAgingJan25 = obligationService.getAging(contextA, "2026-01-25");
        assertEquals(new BigDecimal("60000.00"), preAgingJan25.receivableTotalOutstanding());

        ObligationAgingResult preAgingFeb11 = obligationService.getAging(contextA, "2026-02-11");
        assertEquals(new BigDecimal("60000.00"), preAgingFeb11.receivableTotalOutstanding());

        // 3. February 10: Execute reversal of that settlement
        var reversedTx = transactionReverseService.postReversal(
                contextA,
                "idemp-rev-hist-1",
                settlement.transactionId(),
                new TransactionReverseRequest("Reversal of Jan 20 payment error")
        );

        // 4. Verify distinguishability of execution timestamps and accounting-effective dates:
        // Accounting-effective date of the compensating reversal equals original: Jan 20
        assertEquals(settlementDate, reversedTx.effectiveDate());
        // Execution timestamp recorded at runtime
        assertNotNull(reversedTx.createdAt());

        // 5. Query outstanding balances as of January 10, January 25, and February 11 after reversal:
        // Jan 10: Prior to settlement date -> PKR 100,000.00
        ObligationAgingResult postAgingJan10 = obligationService.getAging(contextA, "2026-01-10");
        assertEquals(new BigDecimal("100000.00"), postAgingJan10.receivableTotalOutstanding());

        // Jan 25: Under FinSight bookkeeping correction policy, reversal takes accounting effect on Jan 20,
        // so the Jan 20 settlement of 40,000 is restored on Jan 20. Outstanding balance as of Jan 25 is restated to 100,000.00
        ObligationAgingResult postAgingJan25 = obligationService.getAging(contextA, "2026-01-25");
        assertEquals(new BigDecimal("100000.00"), postAgingJan25.receivableTotalOutstanding());

        // Feb 11: After reversal execution -> PKR 100,000.00
        ObligationAgingResult postAgingFeb11 = obligationService.getAging(contextA, "2026-02-11");
        assertEquals(new BigDecimal("100000.00"), postAgingFeb11.receivableTotalOutstanding());

        // 6. Confirm Totals Summary agrees with the exact same policy
        var summaryJan10 = obligationService.getTotalsSummary(contextA, "2026-01-10");
        assertEquals("100000.00", summaryJan10.outstandingReceivables());

        var summaryJan25 = obligationService.getTotalsSummary(contextA, "2026-01-25");
        assertEquals("100000.00", summaryJan25.outstandingReceivables());

        var summaryFeb11 = obligationService.getTotalsSummary(contextA, "2026-02-11");
        assertEquals("100000.00", summaryFeb11.outstandingReceivables());

        // 7. Confirm Obligation Detail API applies the exact same policy and preserves audit timestamps
        ObligationDetailResponse detail = obligationService.getObligation(contextA, receivable.id());
        assertEquals("100000.00", detail.originalAmount());
        assertEquals("0.00", detail.paidAmount());
        assertEquals("100000.00", detail.outstandingAmount());
        assertEquals("UNPAID", detail.settlementStatus());
        assertEquals(1, detail.allocations().size());

        var alloc = detail.allocations().get(0);
        assertEquals("CANCELLED", alloc.status());
        assertEquals(settlementDate, alloc.effectiveDate()); // Jan 20 accounting effective date
        assertNotNull(alloc.cancelledAt()); // Reversal execution timestamp
        assertNotNull(alloc.createdAt());   // Settlement creation timestamp
        // Verify execution timestamp (Instant audit) and accounting-effective date (LocalDate) are distinguishable
        assertNotEquals(alloc.effectiveDate().toString(), alloc.cancelledAt().substring(0, 10));
    }
}
