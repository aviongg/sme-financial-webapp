package com.app.sme_health_backend.accounting;

import com.app.sme_health_backend.accounting.dto.*;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialTransaction;
import com.app.sme_health_backend.accounting.exception.TransactionValidationException;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialTransactionRepository;
import com.app.sme_health_backend.accounting.service.*;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FinancialTransactionReversalPostgreSqlIT extends DisposablePostgres {

    @Autowired
    private FinancialAccountRepository accountRepository;

    @Autowired
    private FinancialTransactionRepository transactionRepository;

    @Autowired
    private AccountReadService accountReadService;

    @Autowired
    private TransactionPostService transactionPostService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private TransactionReverseService transactionReverseService;

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
        uA.setEmail("reversal-it-a-" + UUID.randomUUID() + "@example.test");
        uA.setFullName("Reversal User A");
        uA.setPasswordHash("hash-a");
        userA = userRepository.save(uA).getId();
        businessA = businessService.createBusiness(
                new CreateBusinessRequest("Reversal Business A", "retail", "en", null, false, null, null, null),
                userA
        ).businessId();
        contextA = new BusinessAccessContext(userA, businessA, MembershipRole.OWNER);

        AppUser uB = new AppUser();
        uB.setEmail("reversal-it-b-" + UUID.randomUUID() + "@example.test");
        uB.setFullName("Reversal User B");
        uB.setPasswordHash("hash-b");
        userB = userRepository.save(uB).getId();
        businessB = businessService.createBusiness(
                new CreateBusinessRequest("Reversal Business B", "services", "en", null, false, null, null, null),
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
    @DisplayName("IT-REV-01: Direct sale receipt reversal restores balance and nets moneyReceived summary to zero")
    void reverseReceipt_restoresBalance_andNetsSummary() {
        FinancialAccount bank = accountRepository.save(new FinancialAccount(
                businessA, "Meezan Bank", AccountType.BANK, "PKR", 2,
                new BigDecimal("10000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        ));

        TransactionDetailResponse orig = transactionPostService.postTransaction(
                contextA, "idem-rev-01-post",
                new TransactionPostRequest(bank.getId(), "DIRECT_SALE_RECEIPT", "2000.00", today.minusDays(2).toString(), "Erroneous sale entry")
        );

        assertEquals("12000.00", accountReadService.getAccountDetail(businessA, bank.getId()).currentBalance());

        // Reverse the transaction
        TransactionDetailResponse reversal = transactionReverseService.postReversal(
                contextA, "idem-rev-01-rev", orig.id(),
                new TransactionReverseRequest("Customer payment was recorded to wrong bank account")
        );

        assertNotNull(reversal);
        assertTrue(reversal.isReversal());
        assertEquals(orig.id(), reversal.reversedTransactionId());
        assertEquals("DIRECT_SALE_RECEIPT", reversal.eventType());
        assertEquals("DECREASE", reversal.movementDirection()); // Compensating movement is DECREASE
        assertEquals("2000.00", reversal.amount());
        assertEquals("10000.00", reversal.resultingBalance());
        assertEquals("Customer payment was recorded to wrong bank account", reversal.reversalReason());

        // Verify account balance restored to 10000.00
        assertEquals("10000.00", accountReadService.getAccountDetail(businessA, bank.getId()).currentBalance());

        // Verify original transaction read reflects reversed status
        TransactionDetailResponse origDetail = transactionReadService.getTransactionDetail(businessA, orig.id());
        assertTrue(origDetail.isReversed());
        assertEquals(reversal.id(), origDetail.reversedByTransactionId());
        assertFalse(origDetail.isReversal());

        // Verify summary calculations: receipt contribution nets to zero
        TransactionSummaryResponse summary = transactionReadService.getTransactionSummary(
                businessA, null, null, today.minusDays(5).toString(), today.toString()
        );
        assertEquals("0.00", summary.moneyReceived());
        assertEquals("0.00", summary.moneyPaid());
        assertEquals("0.00", summary.netAccountMovement());
        assertEquals(2, summary.transactionCount()); // 1 original + 1 reversal

        // Verify audit event written
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM security_audit_events WHERE business_id = ? AND event_type = 'FINANCIAL_TRANSACTION_REVERSED'",
                Integer.class, businessA
        );
        assertEquals(1, auditCount);
    }

    @Test
    @DisplayName("IT-REV-02: Operating expense reversal restores balance and nets moneyPaid summary to zero")
    void reverseExpense_restoresBalance_andNetsSummary() {
        FinancialAccount bank = accountRepository.save(new FinancialAccount(
                businessA, "Meezan Bank", AccountType.BANK, "PKR", 2,
                new BigDecimal("10000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        ));

        TransactionDetailResponse orig = transactionPostService.postTransaction(
                contextA, "idem-rev-02-post",
                new TransactionPostRequest(bank.getId(), "OPERATING_EXPENSE_PAYMENT", "1500.00", today.minusDays(1).toString(), "Office supplies")
        );

        assertEquals("8500.00", accountReadService.getAccountDetail(businessA, bank.getId()).currentBalance());

        // Reverse the expense
        TransactionDetailResponse reversal = transactionReverseService.postReversal(
                contextA, "idem-rev-02-rev", orig.id(),
                new TransactionReverseRequest("Duplicate expense entry by mistake")
        );

        assertNotNull(reversal);
        assertTrue(reversal.isReversal());
        assertEquals(orig.id(), reversal.reversedTransactionId());
        assertEquals("OPERATING_EXPENSE_PAYMENT", reversal.eventType());
        assertEquals("INCREASE", reversal.movementDirection()); // Compensating movement is INCREASE
        assertEquals("1500.00", reversal.amount());
        assertEquals("10000.00", reversal.resultingBalance());

        // Verify account balance restored to 10000.00
        assertEquals("10000.00", accountReadService.getAccountDetail(businessA, bank.getId()).currentBalance());

        // Verify summary calculations: moneyPaid nets to zero
        TransactionSummaryResponse summary = transactionReadService.getTransactionSummary(
                businessA, null, null, today.minusDays(5).toString(), today.toString()
        );
        assertEquals("0.00", summary.moneyReceived());
        assertEquals("0.00", summary.moneyPaid());
        assertEquals("0.00", summary.netAccountMovement());
        assertEquals(2, summary.transactionCount());
    }

    @Test
    @DisplayName("IT-REV-03: Internal transfer reversal restores both balances and nets transfer totals")
    void reverseTransfer_restoresBothBalances_andNetsTransfers() {
        FinancialAccount bank = accountRepository.save(new FinancialAccount(
                businessA, "Meezan Bank", AccountType.BANK, "PKR", 2,
                new BigDecimal("50000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        ));
        FinancialAccount cash = accountRepository.save(new FinancialAccount(
                businessA, "Petty Cash", AccountType.CASH, "PKR", 2,
                new BigDecimal("1000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        ));

        TransactionDetailResponse transfer = transferService.postTransfer(
                contextA, "idem-rev-03-post",
                new TransferPostRequest(bank.getId(), cash.getId(), "5000.00", today.toString(), "ATM cash withdrawal")
        );

        assertEquals("45000.00", accountReadService.getAccountDetail(businessA, bank.getId()).currentBalance());
        assertEquals("6000.00", accountReadService.getAccountDetail(businessA, cash.getId()).currentBalance());

        // Reverse the transfer
        TransactionDetailResponse reversal = transactionReverseService.postReversal(
                contextA, "idem-rev-03-rev", transfer.id(),
                new TransactionReverseRequest("Transfer entered with wrong amount")
        );

        assertNotNull(reversal);
        assertTrue(reversal.isReversal());
        assertEquals(transfer.id(), reversal.reversedTransactionId());
        assertEquals("INTERNAL_TRANSFER", reversal.eventType());
        // Source was bank, destination was cash. Reversal restores: bank INCREASE, cash DECREASE.
        // Primary account movement matches source (bank INCREASE)
        assertEquals("INCREASE", reversal.movementDirection());
        assertEquals("50000.00", reversal.resultingBalance());
        assertEquals(2, reversal.movements().size());

        // Verify account balances restored to initial
        assertEquals("50000.00", accountReadService.getAccountDetail(businessA, bank.getId()).currentBalance());
        assertEquals("1000.00", accountReadService.getAccountDetail(businessA, cash.getId()).currentBalance());

        // Verify summary calculations: transfers net to zero
        TransactionSummaryResponse summary = transactionReadService.getTransactionSummary(
                businessA, null, null, today.minusDays(5).toString(), today.toString()
        );
        assertEquals("0.00", summary.moneyReceived());
        assertEquals("0.00", summary.moneyPaid());
        assertEquals("0.00", summary.transferIn());
        assertEquals("0.00", summary.transferOut());
        assertEquals("0.00", summary.netAccountMovement());
        assertEquals(2, summary.transactionCount());
    }

    @Test
    @DisplayName("IT-REV-04: Cash deficit simulation prevents reversing receipt when cash would go negative")
    void reverseReceipt_chronologicalDeficit_rejected() {
        FinancialAccount cash = accountRepository.save(new FinancialAccount(
                businessA, "Drawer Cash", AccountType.CASH, "PKR", 2,
                new BigDecimal("100.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        ));

        // 1. Sale receipt 1,000 on today-2 -> balance 1,100
        TransactionDetailResponse sale = transactionPostService.postTransaction(
                contextA, "idem-rev-04-sale",
                new TransactionPostRequest(cash.getId(), "DIRECT_SALE_RECEIPT", "1000.00", today.minusDays(2).toString(), "Cash sale")
        );

        // 2. Expense 900 on today-1 -> balance 200
        transactionPostService.postTransaction(
                contextA, "idem-rev-04-exp",
                new TransactionPostRequest(cash.getId(), "OPERATING_EXPENSE_PAYMENT", "900.00", today.minusDays(1).toString(), "Fuel")
        );

        assertEquals("200.00", accountReadService.getAccountDetail(businessA, cash.getId()).currentBalance());

        // Reversing the 1,000 sale receipt would reduce cash balance to -800 (deficit)
        TransactionValidationException ex = assertThrows(TransactionValidationException.class, () ->
                transactionReverseService.postReversal(
                        contextA, "idem-rev-04-fail", sale.id(),
                        new TransactionReverseRequest("Attempt to reverse cash sale")
                )
        );

        assertTrue(ex.getErrors().containsKey("cashBalance"));
        // Account balance remains 200.00
        assertEquals("200.00", accountReadService.getAccountDetail(businessA, cash.getId()).currentBalance());
    }

    @Test
    @DisplayName("IT-REV-05: Reversal-of-reversal is rejected with 400")
    void reverseReversal_rejected() {
        FinancialAccount bank = accountRepository.save(new FinancialAccount(
                businessA, "Meezan Bank", AccountType.BANK, "PKR", 2,
                new BigDecimal("10000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        ));

        TransactionDetailResponse orig = transactionPostService.postTransaction(
                contextA, "idem-rev-05-post",
                new TransactionPostRequest(bank.getId(), "DIRECT_SALE_RECEIPT", "2000.00", today.minusDays(1).toString(), "Sale")
        );

        TransactionDetailResponse reversal = transactionReverseService.postReversal(
                contextA, "idem-rev-05-rev", orig.id(),
                new TransactionReverseRequest("Error in sale")
        );

        // Attempt to reverse the reversal
        TransactionValidationException ex = assertThrows(TransactionValidationException.class, () ->
                transactionReverseService.postReversal(
                        contextA, "idem-rev-05-rev2", reversal.id(),
                        new TransactionReverseRequest("Attempting reversal of reversal")
                )
        );

        assertTrue(ex.getErrors().containsKey("transactionId"));
        assertTrue(ex.getErrors().get("transactionId").toLowerCase().contains("cannot reverse a reversal"));
    }

    @Test
    @DisplayName("IT-REV-06: Already-reversed transaction is rejected with 409 Conflict")
    void doubleReverse_rejectedWithDuplicate() {
        FinancialAccount bank = accountRepository.save(new FinancialAccount(
                businessA, "Meezan Bank", AccountType.BANK, "PKR", 2,
                new BigDecimal("10000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        ));

        TransactionDetailResponse orig = transactionPostService.postTransaction(
                contextA, "idem-rev-06-post",
                new TransactionPostRequest(bank.getId(), "DIRECT_SALE_RECEIPT", "1000.00", today.minusDays(1).toString(), "Sale")
        );

        transactionReverseService.postReversal(
                contextA, "idem-rev-06-rev1", orig.id(),
                new TransactionReverseRequest("First reversal")
        );

        // Second reversal of same transaction
        assertThrows(DuplicateResourceException.class, () ->
                transactionReverseService.postReversal(
                        contextA, "idem-rev-06-rev2", orig.id(),
                        new TransactionReverseRequest("Second reversal attempt")
                )
        );
    }

    @Test
    @DisplayName("IT-REV-07: Idempotency replay returns identical response; changed payload returns 409")
    void idempotency_replayAndConflict() {
        FinancialAccount bank = accountRepository.save(new FinancialAccount(
                businessA, "Meezan Bank", AccountType.BANK, "PKR", 2,
                new BigDecimal("10000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        ));

        TransactionDetailResponse orig = transactionPostService.postTransaction(
                contextA, "idem-rev-07-post",
                new TransactionPostRequest(bank.getId(), "DIRECT_SALE_RECEIPT", "500.00", today.minusDays(1).toString(), "Sale")
        );

        TransactionReverseRequest req = new TransactionReverseRequest("Reason A");
        TransactionDetailResponse first = transactionReverseService.postReversal(
                contextA, "idem-rev-07-key", orig.id(), req
        );

        // Replay with identical key & payload
        TransactionDetailResponse second = transactionReverseService.postReversal(
                contextA, "idem-rev-07-key", orig.id(), req
        );

        assertEquals(first.id(), second.id());
        assertEquals(first.amount(), second.amount());
        assertEquals(first.resultingBalance(), second.resultingBalance());

        // Attempt replay with changed reason
        TransactionReverseRequest altered = new TransactionReverseRequest("Reason B changed");
        assertThrows(DuplicateResourceException.class, () ->
                transactionReverseService.postReversal(
                        contextA, "idem-rev-07-key", orig.id(), altered
                )
        );
    }

    @Test
    @DisplayName("IT-REV-08: Cross-business transaction reversal is denied with 404")
    void crossBusiness_reversalDenied() {
        FinancialAccount bankB = accountRepository.save(new FinancialAccount(
                businessB, "Faysal Bank", AccountType.BANK, "PKR", 2,
                new BigDecimal("10000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        ));

        TransactionDetailResponse txB = transactionPostService.postTransaction(
                contextB, "idem-rev-08-b",
                new TransactionPostRequest(bankB.getId(), "DIRECT_SALE_RECEIPT", "1000.00", today.minusDays(1).toString(), "Sale B")
        );

        // User A in Business A tries to reverse Business B's transaction
        assertThrows(ResourceNotFoundException.class, () ->
                transactionReverseService.postReversal(
                        contextA, "idem-rev-08-a", txB.id(),
                        new TransactionReverseRequest("Malicious cross-tenant reversal")
                )
        );
    }

    @Test
    @DisplayName("IT-REV-09: Capacity limit rejection on expense reversal")
    void expenseReversal_capacityLimit_rejected() {
        FinancialAccount bank = accountRepository.save(new FinancialAccount(
                businessA, "Meezan Bank", AccountType.BANK, "PKR", 2,
                new BigDecimal("10000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        ));

        // Post an expense of 100.00
        TransactionDetailResponse exp = transactionPostService.postTransaction(
                contextA, "idem-rev-09-exp",
                new TransactionPostRequest(bank.getId(), "OPERATING_EXPENSE_PAYMENT", "100.00", today.minusDays(1).toString(), "Small expense")
        );

        // Manually update account opening balance so that restoring the expense pushes running balance to 1,000,000,000,050.00 >= 10^12
        jdbcTemplate.update("UPDATE financial_accounts SET opening_balance = ? WHERE id = ?",
                new BigDecimal("1000000000050.00"), bank.getId());

        // Now reversing the 100.00 expense would increase balance beyond capacity (10^12)
        TransactionValidationException ex = assertThrows(TransactionValidationException.class, () ->
                transactionReverseService.postReversal(
                        contextA, "idem-rev-09-cap", exp.id(),
                        new TransactionReverseRequest("Reversal causes overflow")
                )
        );

        assertTrue(ex.getErrors().containsKey("amount"));
    }

    @Test
    @DisplayName("IT-REV-10: Database constraints: unique reversed_transaction_id enforces at most one reversal")
    void databaseConstraints_preventDuplicateReversalInsert() {
        FinancialAccount bank = accountRepository.save(new FinancialAccount(
                businessA, "Meezan Bank", AccountType.BANK, "PKR", 2,
                new BigDecimal("10000.00"), today.minusDays(10), AccountStatus.ACTIVE, null, null, null
        ));

        TransactionDetailResponse orig = transactionPostService.postTransaction(
                contextA, "idem-rev-10-post",
                new TransactionPostRequest(bank.getId(), "DIRECT_SALE_RECEIPT", "1000.00", today.minusDays(1).toString(), "Sale")
        );

        // Successfully reverse once
        transactionReverseService.postReversal(
                contextA, "idem-rev-10-rev", orig.id(),
                new TransactionReverseRequest("Legitimate reversal")
        );

        // Try direct SQL insert of second reversal pointing to same transaction
        assertThrows(Exception.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO financial_transactions (id, business_id, account_id, event_type, movement_direction, " +
                                "amount, currency, base_amount_pkr, effective_date, description, created_by_user_id, reversed_transaction_id, reversal_reason) " +
                                "VALUES (?, ?, ?, 'DIRECT_SALE_RECEIPT', 'DECREASE', 1000.00, 'PKR', 1000.00, ?, 'Direct duplicate insert', ?, ?, 'Another reason')",
                        UUID.randomUUID(), businessA, bank.getId(), today, userA, orig.id()
                )
        );
    }
}
