package com.app.sme_health_backend.obligation;

import com.app.sme_health_backend.accounting.dto.TransactionDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionReverseRequest;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.exception.TransactionValidationException;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PaymentCyclesPostgreSqlIT extends DisposablePostgres {

    @Autowired
    private PaymentAllocationService paymentAllocationService;

    @Autowired
    private AdvanceService advanceService;

    @Autowired
    private PaymentPlanService paymentPlanService;

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

    private BusinessAccessContext context;
    private UUID customerId;
    private UUID supplierId;
    private UUID bankAccountId;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(ZoneId.of("Asia/Karachi"));
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        AppUser user = new AppUser();
        user.setEmail("owner-" + uniqueSuffix + "@example.com");
        user.setPasswordHash("hashed_secret");
        user.setFullName("Owner " + uniqueSuffix);
        user = userRepository.saveAndFlush(user);

        UUID businessId = businessService.createBusiness(
                new CreateBusinessRequest("Biz " + uniqueSuffix, "retail", "en", null, false, null, null, null),
                user.getId()
        ).businessId();

        context = new BusinessAccessContext(user.getId(), businessId, MembershipRole.OWNER);

        CounterpartyDetailResponse cust = counterpartyService.createCounterparty(context, new CounterpartyCreateRequest(
                "Customer " + uniqueSuffix, "CUSTOMER"
        ));
        customerId = cust.id();

        CounterpartyDetailResponse supp = counterpartyService.createCounterparty(context, new CounterpartyCreateRequest(
                "Supplier " + uniqueSuffix, "SUPPLIER"
        ));
        supplierId = supp.id();

        FinancialAccount bank = new FinancialAccount(
                businessId,
                "Meezan Bank " + uniqueSuffix,
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("1000000.00"),
                today.minusDays(60),
                AccountStatus.ACTIVE,
                null,
                null,
                "Test Account"
        );
        bank = accountRepository.saveAndFlush(bank);
        bankAccountId = bank.getId();
    }

    private ObligationCreateRequest createReq(UUID counterpartyId, String direction, String amount, String issueDate, String dueDate, String ref, String desc) {
        return new ObligationCreateRequest(counterpartyId, direction, amount, issueDate, dueDate, ref, desc, null, null);
    }

    @Test
    @DisplayName("Workflow A: One payment covering multiple customer obligations")
    void testOnePaymentCoveringMultipleCustomerObligations() {
        // Receivable 1: PKR 60,000
        ObligationDetailResponse ob1 = obligationService.createObligation(context, createReq(
                customerId, "RECEIVABLE", "60000.00", "2026-10-01", "2026-11-15", "INV-001", "Batch 1"
        ));
        // Receivable 2: PKR 90,000
        ObligationDetailResponse ob2 = obligationService.createObligation(context, createReq(
                customerId, "RECEIVABLE", "90000.00", "2026-10-01", "2026-11-20", "INV-002", "Batch 2"
        ));

        // Customer pays PKR 100,000 (Allocate 60,000 to ob1, 40,000 to ob2)
        PaymentAllocateRequest req = new PaymentAllocateRequest(
                customerId,
                "RECEIVABLE",
                bankAccountId,
                "2026-10-02",
                "100000.00",
                List.of(
                        new AllocationItemRequest(ob1.id(), "60000.00"),
                        new AllocationItemRequest(ob2.id(), "40000.00")
                ),
                "PAY-001",
                "Batch settlement",
                false
        );

        PaymentAllocateResponse resp = paymentAllocationService.allocatePayment(context, "idemp-batch-1", req);
        assertNotNull(resp);
        assertEquals("100000.00", resp.totalCashAmount());
        assertEquals("100000.00", resp.totalAllocatedAmount());
        assertEquals("0.00", resp.unappliedAdvanceAmount());
        assertNull(resp.advanceId());
        assertEquals(2, resp.allocations().size());

        // Verify balances: Receivable 1 is 0.00, Receivable 2 is 50,000.00
        ObligationDetailResponse checkOb1 = obligationService.getObligation(context, ob1.id());
        assertEquals("0.00", checkOb1.outstandingAmount());
        assertEquals("FULLY_PAID", checkOb1.settlementStatus());

        ObligationDetailResponse checkOb2 = obligationService.getObligation(context, ob2.id());
        assertEquals("50000.00", checkOb2.outstandingAmount());
        assertEquals("PARTIALLY_PAID", checkOb2.settlementStatus());

        // Verify bank cash movement: exactly one financial transaction of +100,000
        TransactionDetailResponse txSummary = transactionReadService.getTransactionDetail(context.businessId(), resp.financialTransactionId());
        assertEquals("RECEIVABLE_COLLECTION", txSummary.eventType());
        assertEquals("100000.00", txSummary.amount());
        assertFalse(TransactionEventType.valueOf(txSummary.eventType()).isRevenue(), "Settlement must NOT recognize duplicate revenue");

        // Verify count of account movements for this transaction is exactly 1
        Integer movementCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM account_movements WHERE business_id = ? AND transaction_id = ?",
                Integer.class, context.businessId(), resp.financialTransactionId()
        );
        assertEquals(1, movementCount, "Exactly one account movement must be posted for cash receipt");
    }

    @Test
    @DisplayName("Workflow B: Customer advance receipt and later application")
    void testCustomerAdvanceReceiptAndLaterApplication() {
        // Customer pays PKR 50,000 before an obligation exists
        AdvanceCreateRequest advReq = new AdvanceCreateRequest(
                customerId,
                "RECEIVABLE",
                bankAccountId,
                "2026-10-01",
                "50000.00",
                "ADV-CUST-001",
                "Advance for upcoming order"
        );

        AdvanceDetailResponse adv = advanceService.createAdvance(context, "idemp-adv-1", advReq);
        assertNotNull(adv);
        assertEquals("50000.00", adv.originalAmount());
        assertEquals("0.00", adv.appliedAmount());
        assertEquals("50000.00", adv.unappliedAmount());
        assertEquals("ACTIVE", adv.status());

        // Verify cash increased, no revenue recognized
        TransactionDetailResponse tx = transactionReadService.getTransactionDetail(context.businessId(), adv.financialTransactionId());
        assertEquals("RECEIVABLE_COLLECTION", tx.eventType());
        assertEquals("50000.00", tx.amount());
        assertFalse(TransactionEventType.valueOf(tx.eventType()).isRevenue(), "Advance receipt must NOT be revenue");

        // Later: New customer receivable of PKR 120,000
        ObligationDetailResponse ob = obligationService.createObligation(context, createReq(
                customerId, "RECEIVABLE", "120000.00", "2026-10-05", "2026-11-25", "INV-NEW-1", null
        ));

        // Apply PKR 50,000 advance
        AdvanceApplyRequest applyReq = new AdvanceApplyRequest(
                ob.id(),
                "50000.00",
                "2026-10-06",
                "Apply advance"
        );

        AdvanceDetailResponse updatedAdv = advanceService.applyAdvance(context, "idemp-apply-1", adv.id(), applyReq);
        assertEquals("50000.00", updatedAdv.appliedAmount());
        assertEquals("0.00", updatedAdv.unappliedAmount());
        assertEquals("EXHAUSTED", updatedAdv.status());
        assertEquals(1, updatedAdv.applications().size());

        // Verify obligation outstanding is now 70,000.00
        ObligationDetailResponse checkOb = obligationService.getObligation(context, ob.id());
        assertEquals("70000.00", checkOb.outstandingAmount());
        assertEquals("PARTIALLY_PAID", checkOb.settlementStatus());

        // CRITICAL INVARIANT: Zero new AccountMovements or cash transactions were created by the advance application!
        Integer totalMovements = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM account_movements WHERE business_id = ? AND transaction_id IS NOT NULL",
                Integer.class, context.businessId()
        );
        assertEquals(1, totalMovements, "Only original advance receipt cash movement should exist!");
    }

    @Test
    @DisplayName("Workflow C: Supplier advance payment and later application")
    void testSupplierAdvancePaymentAndLaterApplication() {
        // Business pays PKR 30,000 in advance to supplier
        AdvanceCreateRequest advReq = new AdvanceCreateRequest(
                supplierId,
                "PAYABLE",
                bankAccountId,
                "2026-10-01",
                "30000.00",
                "ADV-SUPP-001",
                "Advance for raw materials"
        );

        AdvanceDetailResponse adv = advanceService.createAdvance(context, "idemp-supp-adv-1", advReq);
        assertEquals("30000.00", adv.originalAmount());
        assertEquals("30000.00", adv.unappliedAmount());
        assertEquals("ACTIVE", adv.status());

        TransactionDetailResponse tx = transactionReadService.getTransactionDetail(context.businessId(), adv.financialTransactionId());
        assertEquals("PAYABLE_SETTLEMENT", tx.eventType());
        assertFalse(TransactionEventType.valueOf(tx.eventType()).isOperatingExpense(), "Advance payment must NOT be operating expense");

        // Later: Supplier obligation recorded: 80,000
        ObligationDetailResponse ob = obligationService.createObligation(context, createReq(
                supplierId, "PAYABLE", "80000.00", "2026-10-04", "2026-11-30", "BILL-001", null
        ));

        // Apply PKR 30,000 advance
        AdvanceDetailResponse appliedAdv = advanceService.applyAdvance(context, "idemp-supp-apply-1", adv.id(), new AdvanceApplyRequest(
                ob.id(), "30000.00", "2026-10-05", "Applied advance"
        ));
        assertEquals("0.00", appliedAdv.unappliedAmount());

        // Outstanding payable: 50,000.00
        ObligationDetailResponse checkOb = obligationService.getObligation(context, ob.id());
        assertEquals("50000.00", checkOb.outstandingAmount());

        // No new cash movements
        Integer totalMovements = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM account_movements WHERE business_id = ? AND transaction_id IS NOT NULL",
                Integer.class, context.businessId()
        );
        assertEquals(1, totalMovements);
    }

    @Test
    @DisplayName("Workflow D: Payment with unapplied residual creates advance automatically")
    void testPaymentWithUnappliedResidual() {
        ObligationDetailResponse ob = obligationService.createObligation(context, createReq(
                customerId, "RECEIVABLE", "60000.00", "2026-10-01", "2026-11-15", "INV-RES", null
        ));

        // Customer pays 100,000; allocates 60,000 to ob; retains residual 40,000 as advance
        PaymentAllocateRequest req = new PaymentAllocateRequest(
                customerId,
                "RECEIVABLE",
                bankAccountId,
                "2026-10-02",
                "100000.00",
                List.of(new AllocationItemRequest(ob.id(), "60000.00")),
                "PAY-RES-1",
                "Overpayment retained as advance",
                true // retainAdvance
        );

        PaymentAllocateResponse resp = paymentAllocationService.allocatePayment(context, "idemp-res-1", req);
        assertEquals("100000.00", resp.totalCashAmount());
        assertEquals("60000.00", resp.totalAllocatedAmount());
        assertEquals("40000.00", resp.unappliedAdvanceAmount());
        assertNotNull(resp.advanceId());

        // Check advance record
        AdvanceDetailResponse adv = advanceService.getAdvance(context, resp.advanceId());
        assertEquals("40000.00", adv.originalAmount());
        assertEquals("40000.00", adv.unappliedAmount());
        assertEquals("ACTIVE", adv.status());
    }

    @Test
    @DisplayName("Workflow E: Reversal of multi-obligation payment reverses cash once and cancels all allocations")
    void testMultiObligationPaymentReversal() {
        ObligationDetailResponse ob1 = obligationService.createObligation(context, createReq(
                customerId, "RECEIVABLE", "50000.00", "2026-10-01", "2026-11-15", "INV-REV-1", null
        ));
        ObligationDetailResponse ob2 = obligationService.createObligation(context, createReq(
                customerId, "RECEIVABLE", "50000.00", "2026-10-01", "2026-11-15", "INV-REV-2", null
        ));

        PaymentAllocateResponse resp = paymentAllocationService.allocatePayment(context, "idemp-rev-1", new PaymentAllocateRequest(
                customerId,
                "RECEIVABLE",
                bankAccountId,
                "2026-10-02",
                "70000.00",
                List.of(
                        new AllocationItemRequest(ob1.id(), "40000.00"),
                        new AllocationItemRequest(ob2.id(), "30000.00")
                ),
                "PAY-REV-01",
                "Settlement to be reversed",
                false
        ));

        assertEquals("10000.00", obligationService.getObligation(context, ob1.id()).outstandingAmount());
        assertEquals("20000.00", obligationService.getObligation(context, ob2.id()).outstandingAmount());

        // Reverse the payment
        TransactionDetailResponse revSummary = transactionReverseService.postReversal(
                context,
                "idemp-rev-tx-1",
                resp.financialTransactionId(),
                new TransactionReverseRequest("Customer check bounced")
        );
        assertNotNull(revSummary);

        // Verify allocations are CANCELLED and obligations restored
        ObligationDetailResponse restoredOb1 = obligationService.getObligation(context, ob1.id());
        assertEquals("50000.00", restoredOb1.outstandingAmount());
        assertEquals("UNPAID", restoredOb1.settlementStatus());

        ObligationDetailResponse restoredOb2 = obligationService.getObligation(context, ob2.id());
        assertEquals("50000.00", restoredOb2.outstandingAmount());
        assertEquals("UNPAID", restoredOb2.settlementStatus());

        // Verify only 1 reversal transaction was created
        Integer revTxCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM financial_transactions WHERE business_id = ? AND reversed_transaction_id = ?",
                Integer.class, context.businessId(), resp.financialTransactionId()
        );
        assertEquals(1, revTxCount);
    }

    @Test
    @DisplayName("Workflow F: Advance application correction / reversal restores balance without cash movement")
    void testAdvanceApplicationCorrectionRestoresBalance() {
        // Customer advance 50,000
        AdvanceDetailResponse adv = advanceService.createAdvance(context, "idemp-adv-corr-1", new AdvanceCreateRequest(
                customerId, "RECEIVABLE", bankAccountId, "2026-10-01", "50000.00", "ADV-CORR", "Advance"
        ));

        // Obligation 80,000
        ObligationDetailResponse ob = obligationService.createObligation(context, createReq(
                customerId, "RECEIVABLE", "80000.00", "2026-10-05", "2026-11-20", "INV-CORR", null
        ));

        // Apply 30,000
        AdvanceDetailResponse applied = advanceService.applyAdvance(context, "idemp-apply-corr-1", adv.id(), new AdvanceApplyRequest(
                ob.id(), "30000.00", "2026-10-06", "Applied 30k"
        ));
        assertEquals("20000.00", applied.unappliedAmount());
        UUID applicationId = applied.applications().get(0).allocationId();

        assertEquals("50000.00", obligationService.getObligation(context, ob.id()).outstandingAmount());

        // Reverse the application
        AdvanceDetailResponse corrected = advanceService.reverseApplication(
                context,
                "idemp-rev-app-1",
                adv.id(),
                applicationId,
                new AdvanceApplicationReverseRequest("Applied to wrong invoice")
        );

        // Advance unapplied balance is restored to 50,000.00
        assertEquals("50000.00", corrected.unappliedAmount());
        assertEquals("0.00", corrected.appliedAmount());
        assertEquals("ACTIVE", corrected.status());

        // Obligation outstanding is restored to 80,000.00
        assertEquals("80000.00", obligationService.getObligation(context, ob.id()).outstandingAmount());

        // Total account movements remains 1 (the initial advance cash receipt)
        Integer movements = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM account_movements WHERE business_id = ? AND transaction_id IS NOT NULL",
                Integer.class, context.businessId()
        );
        assertEquals(1, movements);
    }

    @Test
    @DisplayName("Workflow G: Reversing originating cash payment is blocked when advance has active downstream applications")
    void testPaymentReversalBlockedWhenAdvanceHasDownstreamApplications() {
        ObligationDetailResponse ob1 = obligationService.createObligation(context, createReq(
                customerId, "RECEIVABLE", "50000.00", "2026-10-01", "2026-11-15", "INV-G-1", null
        ));

        // Payment 100,000 with 50,000 allocated and 50,000 unapplied advance
        PaymentAllocateResponse resp = paymentAllocationService.allocatePayment(context, "idemp-g-1", new PaymentAllocateRequest(
                customerId,
                "RECEIVABLE",
                bankAccountId,
                "2026-10-02",
                "100000.00",
                List.of(new AllocationItemRequest(ob1.id(), "50000.00")),
                "PAY-G",
                "Payment with advance",
                true
        ));

        // Downstream obligation 40,000
        ObligationDetailResponse ob2 = obligationService.createObligation(context, createReq(
                customerId, "RECEIVABLE", "40000.00", "2026-10-03", "2026-11-20", "INV-G-2", null
        ));

        // Apply 30,000 from the residual advance to ob2
        advanceService.applyAdvance(context, "idemp-g-apply-1", resp.advanceId(), new AdvanceApplyRequest(
                ob2.id(), "30000.00", "2026-10-04", "Apply residual"
        ));

        // Now attempt to reverse the originating payment
        TransactionValidationException ex = assertThrows(TransactionValidationException.class, () ->
                transactionReverseService.postReversal(
                        context,
                        "idemp-g-rev-1",
                        resp.financialTransactionId(),
                        new TransactionReverseRequest("Try to reverse originating payment")
                )
        );
        assertTrue(ex.getErrors().containsKey("advance"), "Errors map must contain 'advance'");
        assertTrue(ex.getErrors().get("advance").contains("active downstream applications"), "Must block reversal if advance is applied downstream");
    }

    @Test
    @DisplayName("Workflow H: Payment scheduling and calendar aggregation")
    void testPaymentSchedulingAndCalendar() {
        ObligationDetailResponse ob = obligationService.createObligation(context, createReq(
                supplierId, "PAYABLE", "150000.00", "2026-10-01", "2026-12-31", "BILL-SCHED-1", null
        ));

        PaymentPlanRequest planReq = new PaymentPlanRequest(List.of(
                new PaymentPlanInstallmentRequest(1, "2026-11-10", "50000.00", "Installment 1"),
                new PaymentPlanInstallmentRequest(2, "2026-11-25", "50000.00", "Installment 2"),
                new PaymentPlanInstallmentRequest(3, "2026-12-10", "50000.00", "Installment 3")
        ));

        PaymentPlanResponse plan = paymentPlanService.setPaymentPlan(context, "idemp-plan-1", ob.id(), planReq);
        assertNotNull(plan);
        assertEquals("150000.00", plan.totalPlannedAmount());
        assertEquals("FULL_COVERAGE", plan.coverageStatus());
        assertEquals(3, plan.installments().size());

        // Zero financial transactions posted by setting a payment plan!
        Integer txCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM financial_transactions WHERE business_id = ?",
                Integer.class, context.businessId()
        );
        assertEquals(0, txCount, "Payment plans must NOT create financial transactions");

        // Query calendar
        PaymentCalendarResponse cal = paymentPlanService.getPaymentCalendar(context, "PAYABLE", "2026-10-01", "2026-12-31");
        assertNotNull(cal);
        assertTrue(cal.items().size() >= 3, "Calendar should contain scheduled installments");
    }

    @Test
    @DisplayName("Workflow I: Idempotency replay with same key returns cached response; different payload fails")
    void testIdempotencyReplayAndConflict() {
        AdvanceCreateRequest advReq = new AdvanceCreateRequest(
                customerId, "RECEIVABLE", bankAccountId, "2026-10-01", "25000.00", "ADV-IDEMP", "Note"
        );

        AdvanceDetailResponse first = advanceService.createAdvance(context, "idemp-exact-key-1", advReq);
        assertNotNull(first);

        // Replay same payload and same key
        AdvanceDetailResponse replay = advanceService.createAdvance(context, "idemp-exact-key-1", advReq);
        assertEquals(first.id(), replay.id());

        // Changed payload with same key
        AdvanceCreateRequest changedReq = new AdvanceCreateRequest(
                customerId, "RECEIVABLE", bankAccountId, "2026-10-01", "30000.00", "ADV-IDEMP-DIFF", "Different"
        );
        assertThrows(DuplicateResourceException.class, () ->
                advanceService.createAdvance(context, "idemp-exact-key-1", changedReq)
        );
    }

    @Test
    @DisplayName("Part VII: Residual Payment Exact Arithmetic (100k cash, 40k + 35k allocated, 25k advance)")
    void testPartVII_ResidualPaymentExactArithmetic() {
        ObligationDetailResponse obA = obligationService.createObligation(context, createReq(
                customerId, "RECEIVABLE", "60000.00", "2026-10-01", "2026-11-15", "INV-ARITH-A", null
        ));
        ObligationDetailResponse obB = obligationService.createObligation(context, createReq(
                customerId, "RECEIVABLE", "50000.00", "2026-10-01", "2026-11-20", "INV-ARITH-B", null
        ));

        // 1. Rejection when retainAdvance is false and residual exists
        PaymentAllocateRequest reqWithoutRetain = new PaymentAllocateRequest(
                customerId, "RECEIVABLE", bankAccountId, "2026-10-02", "100000.00",
                List.of(
                        new AllocationItemRequest(obA.id(), "40000.00"),
                        new AllocationItemRequest(obB.id(), "35000.00")
                ),
                "PAY-ARITH-1", "Without retainAdvance", false
        );
        ObligationValidationException ex = assertThrows(ObligationValidationException.class, () ->
                paymentAllocationService.allocatePayment(context, "idemp-arith-err-1", reqWithoutRetain)
        );
        assertTrue(ex.getErrors().containsKey("retainAdvance"));

        // 2. Successful payment with explicit retainAdvance = true
        PaymentAllocateRequest reqWithRetain = new PaymentAllocateRequest(
                customerId, "RECEIVABLE", bankAccountId, "2026-10-02", "100000.00",
                List.of(
                        new AllocationItemRequest(obA.id(), "40000.00"),
                        new AllocationItemRequest(obB.id(), "35000.00")
                ),
                "PAY-ARITH-1", "With explicit advance retention", true
        );
        PaymentAllocateResponse resp = paymentAllocationService.allocatePayment(context, "idemp-arith-key-1", reqWithRetain);
        assertNotNull(resp);
        assertEquals("100000.00", resp.totalCashAmount());
        assertEquals("75000.00", resp.totalAllocatedAmount());
        assertEquals("25000.00", resp.unappliedAdvanceAmount());
        assertNotNull(resp.advanceId());

        // Exactly one cash receipt for 100,000.00
        TransactionDetailResponse tx = transactionReadService.getTransactionDetail(context.businessId(), resp.financialTransactionId());
        assertEquals("RECEIVABLE_COLLECTION", tx.eventType());
        assertEquals("100000.00", tx.amount());
        assertFalse(TransactionEventType.valueOf(tx.eventType()).isRevenue());

        // Exactly one account movement
        Integer movements = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM account_movements WHERE business_id = ? AND transaction_id = ?",
                Integer.class, context.businessId(), resp.financialTransactionId()
        );
        assertEquals(1, movements);

        // Same-key replay returns identical response
        PaymentAllocateResponse replay = paymentAllocationService.allocatePayment(context, "idemp-arith-key-1", reqWithRetain);
        assertEquals(resp.paymentId(), replay.paymentId());
        assertEquals(resp.financialTransactionId(), replay.financialTransactionId());

        // Changed payload under same key is rejected with DuplicateResourceException
        PaymentAllocateRequest changedReq = new PaymentAllocateRequest(
                customerId, "RECEIVABLE", bankAccountId, "2026-10-02", "100000.00",
                List.of(
                        new AllocationItemRequest(obA.id(), "50000.00"),
                        new AllocationItemRequest(obB.id(), "25000.00")
                ),
                "PAY-ARITH-1", "Changed allocations", true
        );
        assertThrows(DuplicateResourceException.class, () ->
                paymentAllocationService.allocatePayment(context, "idemp-arith-key-1", changedReq)
        );
    }

    @Test
    @DisplayName("Part VII: Calendar Double-Counting Protection (150k payable, 3x 50k installments)")
    void testPartVII_CalendarDoubleCountingProtection() {
        // Supplier payable 150,000 due 2026-12-15
        ObligationDetailResponse ob = obligationService.createObligation(context, createReq(
                supplierId, "PAYABLE", "150000.00", "2026-10-01", "2026-12-15", "BILL-NO-DOUBLE", null
        ));

        // 3 planned installments of 50,000 each
        paymentPlanService.setPaymentPlan(context, "idemp-cal-nodbl", ob.id(), new PaymentPlanRequest(List.of(
                new PaymentPlanInstallmentRequest(1, "2026-11-01", "50000.00", "Inst 1"),
                new PaymentPlanInstallmentRequest(2, "2026-11-15", "50000.00", "Inst 2"),
                new PaymentPlanInstallmentRequest(3, "2026-12-01", "50000.00", "Inst 3")
        )));

        // Query calendar covering the period
        PaymentCalendarResponse cal = paymentPlanService.getPaymentCalendar(context, "PAYABLE", "2026-10-01", "2026-12-31");
        assertNotNull(cal);

        // Projected outflow must NOT be 300,000 from counting both base obligation and schedule!
        assertEquals("150000.00", cal.summary().totalExpectedPayable(),
                "Projected outflow must be exactly 150,000.00, not 300,000.00!");

        // All items for this obligation must be SCHEDULED_INSTALLMENT, zero unscheduled base items
        long scheduledItems = cal.items().stream()
                .filter(i -> i.obligationId().equals(ob.id()) && "SCHEDULED_INSTALLMENT".equals(i.itemType()))
                .count();
        long unscheduledItems = cal.items().stream()
                .filter(i -> i.obligationId().equals(ob.id()) && !"SCHEDULED_INSTALLMENT".equals(i.itemType()))
                .count();
        assertEquals(3, scheduledItems);
        assertEquals(0, unscheduledItems, "Base obligation must NOT be double-counted alongside installments");
    }

    @Test
    @DisplayName("Part VII: Historical Advance Corrections Restatement (Jan 1 advance, Jan 10 apply, Feb 5 reverse)")
    void testPartVII_HistoricalAdvanceCorrectionsRestatement() {
        FinancialAccount oldAccount = accountRepository.saveAndFlush(new FinancialAccount(
                context.businessId(),
                "Hist Bank " + UUID.randomUUID().toString().substring(0, 6),
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("500000.00"),
                LocalDate.of(2025, 12, 1),
                AccountStatus.ACTIVE,
                null, null, "Historical Fixture"
        ));

        // Jan 1: Customer advance 50,000
        AdvanceDetailResponse adv = advanceService.createAdvance(context, "idemp-hist-adv-1", new AdvanceCreateRequest(
                customerId, "RECEIVABLE", oldAccount.getId(), "2026-01-01", "50000.00", "ADV-HIST", "Jan 1 deposit"
        ));
        assertEquals("50000.00", adv.unappliedAmount());

        // Obligation of 100,000
        ObligationDetailResponse ob = obligationService.createObligation(context, createReq(
                customerId, "RECEIVABLE", "100000.00", "2026-01-05", "2026-02-15", "INV-HIST", null
        ));

        // Jan 10: Apply 30,000
        AdvanceDetailResponse applied = advanceService.applyAdvance(context, "idemp-hist-apply-1", adv.id(), new AdvanceApplyRequest(
                ob.id(), "30000.00", "2026-01-10", "Jan 10 application"
        ));
        assertEquals("20000.00", applied.unappliedAmount());
        assertEquals("70000.00", obligationService.getObligation(context, ob.id()).outstandingAmount());

        UUID allocId = applied.applications().get(0).allocationId();

        // Feb 5: Correction / Reversal of application
        AdvanceDetailResponse reversed = advanceService.reverseApplication(context, "idemp-hist-revapp-1", adv.id(), allocId,
                new AdvanceApplicationReverseRequest("Application correction on Feb 5"));

        // Verify advance balance restored
        assertEquals("50000.00", reversed.unappliedAmount());
        assertEquals("0.00", reversed.appliedAmount());
        assertEquals("ACTIVE", reversed.status());

        // Verify obligation balance restored
        assertEquals("100000.00", obligationService.getObligation(context, ob.id()).outstandingAmount());

        // Verify application status is CANCELLED and cancellation metadata is recorded
        AdvanceApplicationItemResponse appItem = reversed.applications().get(0);
        assertEquals("CANCELLED", appItem.status());
        assertNotNull(appItem.cancelledAt());
        assertEquals("Application correction on Feb 5", appItem.cancellationReason());

        // Zero additional cash movements
        Integer movements = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM account_movements WHERE business_id = ? AND transaction_id = ?",
                Integer.class, context.businessId(), adv.financialTransactionId()
        );
        assertEquals(1, movements);
    }

    @Test
    @DisplayName("Part VII Concurrency: V1 single settlement vs V2 multi settlement race on same obligation")
    void testPartVII_Concurrency_V1_vs_V2_Race() throws Exception {
        // Obligation of 50,000
        ObligationDetailResponse ob = obligationService.createObligation(context, createReq(
                customerId, "RECEIVABLE", "50000.00", "2026-10-01", "2026-11-15", "INV-RACE-1", null
        ));

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger conflicts = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startLatch.await();
                obligationService.recordSettlement(context, ob.id(), "idemp-race-v1-" + UUID.randomUUID(),
                        new ObligationSettlementRequest(bankAccountId, "30000.00", today.toString(), "V1-RACE", "Race V1"));
                successes.incrementAndGet();
            } catch (Exception e) {
                conflicts.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                paymentAllocationService.allocatePayment(context, "idemp-race-v2-" + UUID.randomUUID(),
                        new PaymentAllocateRequest(customerId, "RECEIVABLE", bankAccountId, today.toString(), "30000.00",
                                List.of(new AllocationItemRequest(ob.id(), "30000.00")), "V2-RACE", "Race V2", false));
                successes.incrementAndGet();
            } catch (Exception e) {
                conflicts.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS));
        executor.shutdown();

        // Exactly one should succeed and one should fail with conflict, or outstanding balance must never be negative
        ObligationDetailResponse check = obligationService.getObligation(context, ob.id());
        BigDecimal outstanding = new BigDecimal(check.outstandingAmount());
        assertTrue(outstanding.compareTo(BigDecimal.ZERO) >= 0, "Outstanding balance must never be negative!");
        assertEquals("20000.00", check.outstandingAmount());
        assertEquals(1, successes.get());
        assertEquals(1, conflicts.get());
    }

    @Test
    @DisplayName("Part VII Concurrency: Two concurrent advance applications against the same available advance")
    void testPartVII_Concurrency_AdvanceApplicationsRace() throws Exception {
        // Customer advance: 50,000
        AdvanceDetailResponse adv = advanceService.createAdvance(context, "idemp-race-adv-avail", new AdvanceCreateRequest(
                customerId, "RECEIVABLE", bankAccountId, "2026-10-01", "50000.00", "ADV-RACE-APP", null
        ));

        // Two obligations: 40,000 each
        ObligationDetailResponse ob1 = obligationService.createObligation(context, createReq(
                customerId, "RECEIVABLE", "40000.00", "2026-10-01", "2026-11-15", "INV-RACE-OB1", null
        ));
        ObligationDetailResponse ob2 = obligationService.createObligation(context, createReq(
                customerId, "RECEIVABLE", "40000.00", "2026-10-01", "2026-11-15", "INV-RACE-OB2", null
        ));

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger conflicts = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startLatch.await();
                advanceService.applyAdvance(context, "idemp-apply-race-1-" + UUID.randomUUID(), adv.id(),
                        new AdvanceApplyRequest(ob1.id(), "40000.00", today.toString(), "Thread 1"));
                successes.incrementAndGet();
            } catch (Exception e) {
                conflicts.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                advanceService.applyAdvance(context, "idemp-apply-race-2-" + UUID.randomUUID(), adv.id(),
                        new AdvanceApplyRequest(ob2.id(), "40000.00", today.toString(), "Thread 2"));
                successes.incrementAndGet();
            } catch (Exception e) {
                conflicts.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS));
        executor.shutdown();

        // Advance unapplied balance must never be negative!
        AdvanceDetailResponse checkAdv = advanceService.getAdvance(context, adv.id());
        BigDecimal unapplied = new BigDecimal(checkAdv.unappliedAmount());
        assertTrue(unapplied.compareTo(BigDecimal.ZERO) >= 0, "Unapplied advance balance must never be negative!");
        assertEquals("10000.00", checkAdv.unappliedAmount());
        assertEquals(1, successes.get());
        assertEquals(1, conflicts.get());
    }

    @Test
    @DisplayName("Part VII Concurrency: Identical idempotency key requested concurrently")
    void testPartVII_Concurrency_IdempotentReplayRace() throws Exception {
        AdvanceCreateRequest advReq = new AdvanceCreateRequest(
                customerId, "RECEIVABLE", bankAccountId, "2026-10-01", "15000.00", "ADV-CONC-IDEMP", "Concurrent Idemp"
        );
        String sharedKey = "idemp-conc-key-" + UUID.randomUUID();

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        List<UUID> createdIds = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    AdvanceDetailResponse res = advanceService.createAdvance(context, sharedKey, advReq);
                    createdIds.add(res.id());
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS));
        executor.shutdown();

        assertTrue(createdIds.size() >= 1);
        if (createdIds.size() == 2) {
            assertEquals(createdIds.get(0), createdIds.get(1), "Both threads must return the identical advance ID");
        }

        // Verify exactly one record in database
        Integer advCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM counterparty_advances WHERE business_id = ? AND reference_number = 'ADV-CONC-IDEMP'",
                Integer.class, context.businessId()
        );
        assertEquals(1, advCount, "Exactly one advance record must exist in DB");
    }
}
