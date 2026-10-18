package com.app.sme_health_backend.accounting;

import com.app.sme_health_backend.accounting.dto.TransactionDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionReverseRequest;
import com.app.sme_health_backend.accounting.entity.AccountMovement;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialIdempotencyRecord;
import com.app.sme_health_backend.accounting.entity.FinancialTransaction;
import com.app.sme_health_backend.accounting.exception.TransactionValidationException;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.AccountMovementRepository;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialIdempotencyRepository;
import com.app.sme_health_backend.accounting.repository.FinancialTransactionRepository;
import com.app.sme_health_backend.accounting.service.TransactionReverseService;
import com.app.sme_health_backend.audit.repository.SecurityAuditRepository;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionReverseServiceTest {

    @Mock
    private FinancialAccountRepository accountRepository;

    @Mock
    private FinancialTransactionRepository transactionRepository;

    @Mock
    private AccountMovementRepository movementRepository;

    @Mock
    private FinancialIdempotencyRepository idempotencyRepository;

    @Mock
    private SecurityAuditRepository securityAuditRepository;

    private ObjectMapper objectMapper;
    private TransactionReverseService service;

    private UUID businessId;
    private UUID userId;
    private BusinessAccessContext context;
    private UUID accountId;
    private UUID destAccountId;
    private FinancialAccount bankAccount;
    private FinancialAccount cashAccount;
    private UUID txId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new TransactionReverseService(
                accountRepository,
                transactionRepository,
                movementRepository,
                idempotencyRepository,
                securityAuditRepository,
                objectMapper
        );

        businessId = UUID.randomUUID();
        userId = UUID.randomUUID();
        context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);

        accountId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        destAccountId = UUID.fromString("22222222-2222-4222-8222-222222222222");

        bankAccount = createAccount(accountId, "Meezan Bank", AccountType.BANK, new BigDecimal("10000.00"), LocalDate.of(2026, 1, 1), "PKR", AccountStatus.ACTIVE);
        cashAccount = createAccount(destAccountId, "Petty Cash", AccountType.CASH, new BigDecimal("1000.00"), LocalDate.of(2026, 1, 1), "PKR", AccountStatus.ACTIVE);

        txId = UUID.randomUUID();
    }

    private FinancialAccount createAccount(UUID id, String name, AccountType type, BigDecimal openingBalance, LocalDate openingDate, String currency, AccountStatus status) {
        FinancialAccount account = new FinancialAccount();
        account.setId(id);
        account.setBusinessId(businessId);
        account.setAccountName(name);
        account.setAccountType(type);
        account.setCurrency(currency);
        account.setCurrencyDecimals(2);
        account.setOpeningBalance(openingBalance);
        account.setOpeningDate(openingDate);
        account.setStatus(status);
        account.setCreatedAt(Instant.now());
        return account;
    }

    @Test
    @DisplayName("Reversing DIRECT_SALE_RECEIPT creates inverse DECREASE movement and links to original")
    void reverseReceipt_success() {
        FinancialTransaction original = new FinancialTransaction(
                businessId, accountId, TransactionEventType.DIRECT_SALE_RECEIPT,
                MovementDirection.INCREASE, new BigDecimal("2000.00"), "PKR",
                new BigDecimal("2000.00"), LocalDate.of(2026, 10, 15), "Customer cash sale", userId
        );
        original.setId(txId);

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(transactionRepository.findForUpdateByIdAndBusinessId(txId, businessId))
                .thenReturn(Optional.of(original));
        when(transactionRepository.findByReversedTransactionIdAndBusinessId(txId, businessId))
                .thenReturn(Optional.empty());
        when(accountRepository.findForUpdateByIdAndBusinessId(accountId, businessId))
                .thenReturn(Optional.of(bankAccount));
        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(accountId))
                .thenReturn(Collections.emptyList());

        when(transactionRepository.saveAndFlush(any())).thenAnswer(inv -> {
            FinancialTransaction t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        when(movementRepository.saveAndFlush(any())).thenAnswer(inv -> {
            AccountMovement m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        TransactionReverseRequest request = new TransactionReverseRequest("Customer sale was duplicate");
        TransactionDetailResponse response = service.postReversal(context, "rev-key-receipt-1", txId, request);

        assertThat(response).isNotNull();
        assertThat(response.isReversal()).isTrue();
        assertThat(response.reversedTransactionId()).isEqualTo(txId);
        assertThat(response.movementDirection()).isEqualTo("DECREASE");
        assertThat(response.amount()).isEqualTo("2000.00");
        assertThat(response.reversalReason()).isEqualTo("Customer sale was duplicate");

        ArgumentCaptor<AccountMovement> movCaptor = ArgumentCaptor.forClass(AccountMovement.class);
        verify(movementRepository).saveAndFlush(movCaptor.capture());
        AccountMovement mov = movCaptor.getValue();
        assertThat(mov.getMovementDirection()).isEqualTo(MovementDirection.DECREASE);
        assertThat(mov.getAmount()).isEqualByComparingTo("2000.00");
    }

    @Test
    @DisplayName("Reversing OPERATING_EXPENSE_PAYMENT creates inverse INCREASE movement")
    void reverseExpense_success() {
        FinancialTransaction original = new FinancialTransaction(
                businessId, accountId, TransactionEventType.OPERATING_EXPENSE_PAYMENT,
                MovementDirection.DECREASE, new BigDecimal("1500.00"), "PKR",
                new BigDecimal("1500.00"), LocalDate.of(2026, 10, 15), "Office stationery", userId
        );
        original.setId(txId);

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(transactionRepository.findForUpdateByIdAndBusinessId(txId, businessId))
                .thenReturn(Optional.of(original));
        when(transactionRepository.findByReversedTransactionIdAndBusinessId(txId, businessId))
                .thenReturn(Optional.empty());
        when(accountRepository.findForUpdateByIdAndBusinessId(accountId, businessId))
                .thenReturn(Optional.of(bankAccount));
        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(accountId))
                .thenReturn(Collections.emptyList());

        when(transactionRepository.saveAndFlush(any())).thenAnswer(inv -> {
            FinancialTransaction t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        when(movementRepository.saveAndFlush(any())).thenAnswer(inv -> {
            AccountMovement m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        TransactionReverseRequest request = new TransactionReverseRequest("Expense paid from petty cash instead");
        TransactionDetailResponse response = service.postReversal(context, "rev-key-expense-1", txId, request);

        assertThat(response.isReversal()).isTrue();
        assertThat(response.movementDirection()).isEqualTo("INCREASE");
        assertThat(response.amount()).isEqualTo("1500.00");

        ArgumentCaptor<AccountMovement> movCaptor = ArgumentCaptor.forClass(AccountMovement.class);
        verify(movementRepository).saveAndFlush(movCaptor.capture());
        AccountMovement mov = movCaptor.getValue();
        assertThat(mov.getMovementDirection()).isEqualTo(MovementDirection.INCREASE);
        assertThat(mov.getAmount()).isEqualByComparingTo("1500.00");
    }

    @Test
    @DisplayName("Reversing INTERNAL_TRANSFER creates two inverse movements (source INCREASE, dest DECREASE)")
    void reverseTransfer_success() {
        FinancialTransaction original = new FinancialTransaction(
                businessId, accountId, destAccountId, TransactionEventType.INTERNAL_TRANSFER,
                MovementDirection.DECREASE, new BigDecimal("5000.00"), "PKR",
                new BigDecimal("5000.00"), LocalDate.of(2026, 10, 16), "Float replenishment", userId
        );
        original.setId(txId);

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(transactionRepository.findForUpdateByIdAndBusinessId(txId, businessId))
                .thenReturn(Optional.of(original));
        when(transactionRepository.findByReversedTransactionIdAndBusinessId(txId, businessId))
                .thenReturn(Optional.empty());

        when(accountRepository.findForUpdateByIdAndBusinessId(accountId, businessId))
                .thenReturn(Optional.of(bankAccount));
        when(accountRepository.findForUpdateByIdAndBusinessId(destAccountId, businessId))
                .thenReturn(Optional.of(cashAccount));
        when(accountRepository.findByIdAndBusinessId(destAccountId, businessId))
                .thenReturn(Optional.of(cashAccount));

        // Cash account opening 1,000 + transfer in 5,000 = 6,000; simulation of reversal DECREASE 5,000 gives 1,000 >= 0
        AccountMovement prevTransferMov = new AccountMovement(
                businessId, txId, destAccountId, MovementDirection.INCREASE,
                new BigDecimal("5000.00"), "PKR", LocalDate.of(2026, 10, 16)
        );
        prevTransferMov.setId(UUID.randomUUID());
        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(destAccountId))
                .thenReturn(List.of(prevTransferMov));
        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(accountId))
                .thenReturn(Collections.emptyList());

        when(transactionRepository.saveAndFlush(any())).thenAnswer(inv -> {
            FinancialTransaction t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        when(movementRepository.saveAndFlush(any())).thenAnswer(inv -> {
            AccountMovement m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        TransactionReverseRequest request = new TransactionReverseRequest("Wrong account selected for float");
        TransactionDetailResponse response = service.postReversal(context, "rev-key-transfer-1", txId, request);

        assertThat(response.isReversal()).isTrue();
        assertThat(response.reversedTransactionId()).isEqualTo(txId);
        assertThat(response.sourceAccountId()).isEqualTo(accountId);
        assertThat(response.destinationAccountId()).isEqualTo(destAccountId);

        ArgumentCaptor<AccountMovement> movCaptor = ArgumentCaptor.forClass(AccountMovement.class);
        verify(movementRepository, times(2)).saveAndFlush(movCaptor.capture());
        List<AccountMovement> captured = movCaptor.getAllValues();
        AccountMovement srcMov = captured.get(0);
        AccountMovement dstMov = captured.get(1);

        assertThat(srcMov.getAccountId()).isEqualTo(accountId);
        assertThat(srcMov.getMovementDirection()).isEqualTo(MovementDirection.INCREASE);
        assertThat(dstMov.getAccountId()).isEqualTo(destAccountId);
        assertThat(dstMov.getMovementDirection()).isEqualTo(MovementDirection.DECREASE);
    }

    @Test
    @DisplayName("Reversal fails with 400 when reason is missing or shorter than 5 characters")
    void reverse_invalidReason() {
        TransactionReverseRequest tooShort = new TransactionReverseRequest("oops");
        assertThatThrownBy(() -> service.postReversal(context, "rev-short", txId, tooShort))
                .isInstanceOf(TransactionValidationException.class)
                .extracting(ex -> ((TransactionValidationException) ex).getErrors().get("reason"))
                .isEqualTo("Reason must be between 5 and 255 characters");

        TransactionReverseRequest blank = new TransactionReverseRequest("   ");
        assertThatThrownBy(() -> service.postReversal(context, "rev-blank", txId, blank))
                .isInstanceOf(TransactionValidationException.class)
                .extracting(ex -> ((TransactionValidationException) ex).getErrors().get("reason"))
                .isEqualTo("Reason is required");
    }

    @Test
    @DisplayName("Attempting to reverse an already reversed transaction throws 409 Conflict")
    void reverse_alreadyReversedConflict() {
        FinancialTransaction original = new FinancialTransaction(
                businessId, accountId, TransactionEventType.DIRECT_SALE_RECEIPT,
                MovementDirection.INCREASE, new BigDecimal("2000.00"), "PKR",
                new BigDecimal("2000.00"), LocalDate.of(2026, 10, 15), "Sale", userId
        );
        original.setId(txId);

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(transactionRepository.findForUpdateByIdAndBusinessId(txId, businessId))
                .thenReturn(Optional.of(original));

        FinancialTransaction existingRev = new FinancialTransaction();
        existingRev.setId(UUID.randomUUID());
        when(transactionRepository.findByReversedTransactionIdAndBusinessId(txId, businessId))
                .thenReturn(Optional.of(existingRev));

        TransactionReverseRequest req = new TransactionReverseRequest("Second reversal attempt");
        assertThatThrownBy(() -> service.postReversal(context, "rev-2", txId, req))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Transaction has already been reversed");
    }

    @Test
    @DisplayName("Attempting to reverse a reversal transaction throws 400")
    void reverse_reversalOfReversalDenied() {
        FinancialTransaction reversalTx = new FinancialTransaction(
                businessId, accountId, TransactionEventType.DIRECT_SALE_RECEIPT,
                MovementDirection.DECREASE, new BigDecimal("2000.00"), "PKR",
                new BigDecimal("2000.00"), LocalDate.of(2026, 10, 15), "Reversal", userId
        );
        reversalTx.setId(txId);
        reversalTx.setReversedTransactionId(UUID.randomUUID()); // It is a reversal!

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(transactionRepository.findForUpdateByIdAndBusinessId(txId, businessId))
                .thenReturn(Optional.of(reversalTx));

        TransactionReverseRequest req = new TransactionReverseRequest("Trying to reverse a reversal");
        assertThatThrownBy(() -> service.postReversal(context, "rev-3", txId, req))
                .isInstanceOf(TransactionValidationException.class)
                .extracting(ex -> ((TransactionValidationException) ex).getErrors().get("transactionId"))
                .isEqualTo("Cannot reverse a reversal transaction");
    }

    @Test
    @DisplayName("Reversal fails with 400 when subsequent CASH activity makes CASH balance negative")
    void reverse_cashDeficitSimulationFailure() {
        // Cash account opening 0. Day 1: Cash Receipt 5,000. Day 2: Cash Expense 4,000 (balance 1,000).
        // Attempting to reverse Day 1 receipt would drop Day 2 balance to -4,000!
        cashAccount.setOpeningBalance(BigDecimal.ZERO);

        FinancialTransaction day1Receipt = new FinancialTransaction(
                businessId, destAccountId, TransactionEventType.DIRECT_SALE_RECEIPT,
                MovementDirection.INCREASE, new BigDecimal("5000.00"), "PKR",
                new BigDecimal("5000.00"), LocalDate.of(2026, 10, 10), "Day 1 Cash receipt", userId
        );
        day1Receipt.setId(txId);

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(transactionRepository.findForUpdateByIdAndBusinessId(txId, businessId))
                .thenReturn(Optional.of(day1Receipt));
        when(transactionRepository.findByReversedTransactionIdAndBusinessId(txId, businessId))
                .thenReturn(Optional.empty());
        when(accountRepository.findForUpdateByIdAndBusinessId(destAccountId, businessId))
                .thenReturn(Optional.of(cashAccount));

        AccountMovement m1 = new AccountMovement(
                businessId, txId, destAccountId, MovementDirection.INCREASE,
                new BigDecimal("5000.00"), "PKR", LocalDate.of(2026, 10, 10)
        );
        m1.setId(UUID.randomUUID());
        AccountMovement m2 = new AccountMovement(
                businessId, UUID.randomUUID(), destAccountId, MovementDirection.DECREASE,
                new BigDecimal("4000.00"), "PKR", LocalDate.of(2026, 10, 12)
        );
        m2.setId(UUID.randomUUID());

        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(destAccountId))
                .thenReturn(List.of(m1, m2));

        TransactionReverseRequest req = new TransactionReverseRequest("Mistaken receipt on Day 1");
        assertThatThrownBy(() -> service.postReversal(context, "rev-cash-fail", txId, req))
                .isInstanceOf(TransactionValidationException.class)
                .extracting(ex -> ((TransactionValidationException) ex).getErrors().get("cashBalance"))
                .asString().contains("Reversal would cause cash account balance to become negative");
    }

    @Test
    @DisplayName("Idempotent replay returns cached successful response without duplicate writes")
    void reverse_idempotentReplay() {
        String reason = "Customer sale was duplicate";
        String payloadHash = TransactionReverseService.computePayloadHash(txId, reason);

        FinancialIdempotencyRecord cachedRecord = new FinancialIdempotencyRecord(
                businessId,
                TransactionReverseService.OPERATION_TRANSACTION_REVERSE,
                "rev-idemp-1",
                payloadHash,
                txId,
                201,
                "{\"id\":\"" + UUID.randomUUID() + "\",\"businessId\":\"" + businessId + "\",\"accountId\":\"" + accountId + "\",\"accountName\":\"Meezan Bank\",\"accountType\":\"BANK\",\"eventType\":\"DIRECT_SALE_RECEIPT\",\"movementDirection\":\"DECREASE\",\"amount\":\"2000.00\",\"currency\":\"PKR\",\"baseAmountPkr\":\"2000.00\",\"effectiveDate\":\"2026-10-15\",\"description\":\"" + reason + "\",\"resultingBalance\":\"10000.00\",\"movements\":[],\"createdByUserId\":\"" + userId + "\",\"createdAt\":\"2026-10-17T09:00:00Z\",\"isReversal\":true,\"reversedTransactionId\":\"" + txId + "\",\"isReversed\":false,\"reversalReason\":\"" + reason + "\"}"
        );

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(
                businessId, TransactionReverseService.OPERATION_TRANSACTION_REVERSE, "rev-idemp-1"
        )).thenReturn(Optional.of(cachedRecord));

        TransactionReverseRequest req = new TransactionReverseRequest(reason);
        TransactionDetailResponse response = service.postReversal(context, "rev-idemp-1", txId, req);

        assertThat(response).isNotNull();
        assertThat(response.isReversal()).isTrue();
        assertThat(response.reversedTransactionId()).isEqualTo(txId);
        verify(transactionRepository, never()).saveAndFlush(any());
        verify(movementRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Idempotent retry with changed payload throws 409 Conflict")
    void reverse_idempotentConflict() {
        String originalReason = "Original reason for reversal";
        String originalHash = TransactionReverseService.computePayloadHash(txId, originalReason);

        FinancialIdempotencyRecord cachedRecord = new FinancialIdempotencyRecord(
                businessId,
                TransactionReverseService.OPERATION_TRANSACTION_REVERSE,
                "rev-idemp-conflict",
                originalHash,
                txId,
                201,
                "{}"
        );

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(
                businessId, TransactionReverseService.OPERATION_TRANSACTION_REVERSE, "rev-idemp-conflict"
        )).thenReturn(Optional.of(cachedRecord));

        TransactionReverseRequest differentReq = new TransactionReverseRequest("Completely different reason");
        assertThatThrownBy(() -> service.postReversal(context, "rev-idemp-conflict", txId, differentReq))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("has already been used with a different request payload");
    }
}
