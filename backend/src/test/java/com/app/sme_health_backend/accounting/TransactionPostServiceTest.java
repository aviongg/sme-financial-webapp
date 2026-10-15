package com.app.sme_health_backend.accounting;

import com.app.sme_health_backend.accounting.dto.TransactionDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionPostRequest;
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
import com.app.sme_health_backend.accounting.repository.AccountMovementSum;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialIdempotencyRepository;
import com.app.sme_health_backend.accounting.repository.FinancialTransactionRepository;
import com.app.sme_health_backend.audit.entity.SecurityAuditEvent;
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
import java.time.ZoneId;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionPostServiceTest {

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
    private com.app.sme_health_backend.accounting.service.TransactionPostService postService;

    private UUID businessId;
    private UUID userId;
    private UUID accountId;
    private BusinessAccessContext context;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        postService = new com.app.sme_health_backend.accounting.service.TransactionPostService(
                accountRepository,
                transactionRepository,
                movementRepository,
                idempotencyRepository,
                securityAuditRepository,
                objectMapper
        );

        businessId = UUID.randomUUID();
        userId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        context = new BusinessAccessContext(userId, businessId, MembershipRole.ACCOUNTANT);
        today = LocalDate.now(ZoneId.of("Asia/Karachi"));
    }

    private FinancialAccount createAccount(AccountType type, BigDecimal openingBalance, LocalDate openingDate, String currency, AccountStatus status) {
        FinancialAccount account = new FinancialAccount();
        account.setId(accountId);
        account.setBusinessId(businessId);
        account.setAccountName("Test Account");
        account.setAccountType(type);
        account.setCurrency(currency);
        account.setCurrencyDecimals(2);
        account.setOpeningBalance(openingBalance);
        account.setOpeningDate(openingDate);
        account.setStatus(status);
        return account;
    }

    @Test
    @DisplayName("DIRECT_SALE_RECEIPT creates revenue transaction, INCREASE movement, and mandatory audit log")
    void postTransaction_directSaleReceipt_success() {
        FinancialAccount account = createAccount(AccountType.CASH, new BigDecimal("10000.00"), today.minusDays(5), "PKR", AccountStatus.ACTIVE);
        when(accountRepository.findForUpdateByIdAndBusinessId(accountId, businessId)).thenReturn(Optional.of(account));
        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(any(), any(), any())).thenReturn(Optional.empty());

        when(transactionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            FinancialTransaction tx = invocation.getArgument(0);
            tx.setId(UUID.randomUUID());
            return tx;
        });

        when(movementRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            AccountMovement m = invocation.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        AccountMovementSum sumMock = mock(AccountMovementSum.class);
        when(sumMock.getMovementDirection()).thenReturn(MovementDirection.INCREASE);
        when(sumMock.getTotalAmount()).thenReturn(new BigDecimal("5000.00"));
        when(movementRepository.findMovementSumsByAccountIdAndCutoffDate(accountId, today)).thenReturn(List.of(sumMock));

        TransactionPostRequest request = new TransactionPostRequest(
                accountId,
                "DIRECT_SALE_RECEIPT",
                "5000.00",
                today.toString(),
                "Store cash sale"
        );

        TransactionDetailResponse response = postService.postTransaction(context, "key-001", request);

        assertThat(response).isNotNull();
        assertThat(response.eventType()).isEqualTo("DIRECT_SALE_RECEIPT");
        assertThat(response.movementDirection()).isEqualTo("INCREASE");
        assertThat(response.amount()).isEqualTo("5000.00");
        assertThat(response.baseAmountPkr()).isEqualTo("5000.00");
        assertThat(response.resultingBalance()).isEqualTo("15000.00");
        assertThat(response.movements()).hasSize(1);
        assertThat(response.movements().get(0).movementDirection()).isEqualTo("INCREASE");

        // Verify audit event linkages
        ArgumentCaptor<SecurityAuditEvent> auditCaptor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(securityAuditRepository).saveAndFlush(auditCaptor.capture());
        SecurityAuditEvent savedAudit = auditCaptor.getValue();
        assertThat(savedAudit.getActorUserId()).isEqualTo(userId);
        assertThat(savedAudit.getBusinessId()).isEqualTo(businessId);
        assertThat(savedAudit.getEventType()).isEqualTo("FINANCIAL_TRANSACTION_POSTED");

        // Verify idempotency record
        verify(idempotencyRepository).saveAndFlush(any(FinancialIdempotencyRecord.class));
    }

    @Test
    @DisplayName("OPERATING_EXPENSE_PAYMENT creates expense transaction and DECREASE movement")
    void postTransaction_operatingExpense_success() {
        FinancialAccount account = createAccount(AccountType.BANK, new BigDecimal("20000.00"), today.minusDays(10), "PKR", AccountStatus.ACTIVE);
        when(accountRepository.findForUpdateByIdAndBusinessId(accountId, businessId)).thenReturn(Optional.of(account));
        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(any(), any(), any())).thenReturn(Optional.empty());

        when(transactionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            FinancialTransaction tx = invocation.getArgument(0);
            tx.setId(UUID.randomUUID());
            return tx;
        });

        when(movementRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            AccountMovement m = invocation.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        AccountMovementSum sumMock = mock(AccountMovementSum.class);
        when(sumMock.getMovementDirection()).thenReturn(MovementDirection.DECREASE);
        when(sumMock.getTotalAmount()).thenReturn(new BigDecimal("3000.00"));
        when(movementRepository.findMovementSumsByAccountIdAndCutoffDate(accountId, today)).thenReturn(List.of(sumMock));

        TransactionPostRequest request = new TransactionPostRequest(
                accountId,
                "OPERATING_EXPENSE_PAYMENT",
                "3000.00",
                today.toString(),
                "Vendor utility invoice"
        );

        TransactionDetailResponse response = postService.postTransaction(context, "key-002", request);

        assertThat(response).isNotNull();
        assertThat(response.eventType()).isEqualTo("OPERATING_EXPENSE_PAYMENT");
        assertThat(response.movementDirection()).isEqualTo("DECREASE");
        assertThat(response.amount()).isEqualTo("3000.00");
        assertThat(response.resultingBalance()).isEqualTo("17000.00");
    }

    @Test
    @DisplayName("Idempotent replay with same key and payload returns cached response verbatim")
    void postTransaction_idempotentReplay() throws Exception {
        TransactionPostRequest request = new TransactionPostRequest(
                accountId,
                "DIRECT_SALE_RECEIPT",
                "2500.00",
                today.toString(),
                "Retail receipt"
        );

        FinancialAccount account = createAccount(AccountType.BANK, new BigDecimal("5000.00"), today.minusDays(5), "PKR", AccountStatus.ACTIVE);
        when(accountRepository.findForUpdateByIdAndBusinessId(accountId, businessId)).thenReturn(Optional.of(account));

        String payloadHash = com.app.sme_health_backend.accounting.service.TransactionPostService.computePayloadHash(
                accountId,
                TransactionEventType.DIRECT_SALE_RECEIPT,
                new BigDecimal("2500.00"),
                today,
                "Retail receipt"
        );

        TransactionDetailResponse cached = new TransactionDetailResponse(
                UUID.randomUUID(), businessId, accountId, "Test Account", "BANK",
                "DIRECT_SALE_RECEIPT", "INCREASE", "2500.00", "PKR", "2500.00",
                today, "Retail receipt", "7500.00", Collections.emptyList(), userId, Instant.now().toString()
        );

        FinancialIdempotencyRecord record = new FinancialIdempotencyRecord(
                businessId, "FINANCIAL_TRANSACTION", "key-replay", payloadHash, cached.id(), 201, objectMapper.writeValueAsString(cached)
        );

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(businessId, "FINANCIAL_TRANSACTION", "key-replay"))
                .thenReturn(Optional.of(record));

        TransactionDetailResponse response = postService.postTransaction(context, "key-replay", request);

        assertThat(response.id()).isEqualTo(cached.id());
        assertThat(response.resultingBalance()).isEqualTo("7500.00");
        verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("Idempotency conflict on altered payload throws DuplicateResourceException")
    void postTransaction_idempotencyConflict() {
        TransactionPostRequest request = new TransactionPostRequest(
                accountId,
                "DIRECT_SALE_RECEIPT",
                "2500.00",
                today.toString(),
                "Original description"
        );

        FinancialAccount account = createAccount(AccountType.BANK, new BigDecimal("5000.00"), today.minusDays(5), "PKR", AccountStatus.ACTIVE);
        when(accountRepository.findForUpdateByIdAndBusinessId(accountId, businessId)).thenReturn(Optional.of(account));

        FinancialIdempotencyRecord record = new FinancialIdempotencyRecord(
                businessId, "FINANCIAL_TRANSACTION", "key-conflict", "different_hash", UUID.randomUUID(), 201, "{}"
        );

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(businessId, "FINANCIAL_TRANSACTION", "key-conflict"))
                .thenReturn(Optional.of(record));

        assertThatThrownBy(() -> postService.postTransaction(context, "key-conflict", request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("different request payload");
    }

    @Test
    @DisplayName("Non-PKR accounts reject transaction posting with validation error")
    void postTransaction_nonPkrRejected() {
        FinancialAccount account = createAccount(AccountType.BANK, new BigDecimal("1000.00"), today.minusDays(5), "USD", AccountStatus.ACTIVE);
        when(accountRepository.findForUpdateByIdAndBusinessId(accountId, businessId)).thenReturn(Optional.of(account));

        TransactionPostRequest request = new TransactionPostRequest(
                accountId, "DIRECT_SALE_RECEIPT", "100.00", today.toString(), null
        );

        assertThatThrownBy(() -> postService.postTransaction(context, "key-usd", request))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessageContaining("One or more request parameters are invalid");
    }

    @Test
    @DisplayName("Effective date preceding account opening date is rejected")
    void postTransaction_effectiveDatePrecedingOpening_rejected() {
        FinancialAccount account = createAccount(AccountType.BANK, new BigDecimal("1000.00"), today.minusDays(2), "PKR", AccountStatus.ACTIVE);
        when(accountRepository.findForUpdateByIdAndBusinessId(accountId, businessId)).thenReturn(Optional.of(account));

        TransactionPostRequest request = new TransactionPostRequest(
                accountId, "DIRECT_SALE_RECEIPT", "100.00", today.minusDays(5).toString(), null
        );

        assertThatThrownBy(() -> postService.postTransaction(context, "key-date", request))
                .isInstanceOf(TransactionValidationException.class);
    }

    @Test
    @DisplayName("Future effective date is rejected")
    void postTransaction_futureDate_rejected() {
        TransactionPostRequest request = new TransactionPostRequest(
                accountId, "DIRECT_SALE_RECEIPT", "100.00", today.plusDays(1).toString(), null
        );

        assertThatThrownBy(() -> postService.postTransaction(context, "key-future", request))
                .isInstanceOf(TransactionValidationException.class);
    }

    @Test
    @DisplayName("CASH account outflow causing current balance to drop below zero is rejected")
    void postTransaction_cashNegativeToday_rejected() {
        FinancialAccount account = createAccount(AccountType.CASH, new BigDecimal("1000.00"), today.minusDays(5), "PKR", AccountStatus.ACTIVE);
        when(accountRepository.findForUpdateByIdAndBusinessId(accountId, businessId)).thenReturn(Optional.of(account));
        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(accountId)).thenReturn(Collections.emptyList());

        TransactionPostRequest request = new TransactionPostRequest(
                accountId, "OPERATING_EXPENSE_PAYMENT", "1500.00", today.toString(), "Exceeds cash"
        );

        assertThatThrownBy(() -> postService.postTransaction(context, "key-cash-neg", request))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessageContaining("One or more request parameters are invalid");
    }

    @Test
    @DisplayName("CASH account backdated outflow causing subsequent historical balance sequence to drop below zero is rejected")
    void postTransaction_cashBackdatedSequenceNegative_rejected() {
        // Initial state:
        // Opening balance on Day -10: 10,000 PKR.
        // Day -5: Outflow of 8,000 -> balance on Day -5 was 2,000.
        // Day -2: Inflow of 5,000 -> balance on Day -2 was 7,000.
        // Today: balance is 7,000.
        // If backdating an outflow of 3,000 on Day -8:
        // Day -5 balance would drop to: 10,000 - 3,000 - 8,000 = -1,000 < 0!
        FinancialAccount account = createAccount(AccountType.CASH, new BigDecimal("10000.00"), today.minusDays(10), "PKR", AccountStatus.ACTIVE);
        when(accountRepository.findForUpdateByIdAndBusinessId(accountId, businessId)).thenReturn(Optional.of(account));

        AccountMovement mov1 = new AccountMovement(businessId, UUID.randomUUID(), accountId, MovementDirection.DECREASE, new BigDecimal("8000.00"), "PKR", today.minusDays(5));
        mov1.setId(UUID.randomUUID());
        mov1.setCreatedAt(Instant.now().minusSeconds(86400 * 5));

        AccountMovement mov2 = new AccountMovement(businessId, UUID.randomUUID(), accountId, MovementDirection.INCREASE, new BigDecimal("5000.00"), "PKR", today.minusDays(2));
        mov2.setId(UUID.randomUUID());
        mov2.setCreatedAt(Instant.now().minusSeconds(86400 * 2));

        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(accountId))
                .thenReturn(List.of(mov1, mov2));

        TransactionPostRequest request = new TransactionPostRequest(
                accountId, "OPERATING_EXPENSE_PAYMENT", "3000.00", today.minusDays(8).toString(), "Backdated cash outflow"
        );

        assertThatThrownBy(() -> postService.postTransaction(context, "key-backdate-neg", request))
                .isInstanceOf(TransactionValidationException.class)
                .satisfies(ex -> {
                    TransactionValidationException tve = (TransactionValidationException) ex;
                    assertThat(tve.getErrors()).containsKey("cashBalance");
                    assertThat(tve.getErrors().get("cashBalance")).contains("negative on " + today.minusDays(5));
                });
    }

    @Test
    @DisplayName("BANK account negative balance is permitted (e.g. approved credit line / overdraft)")
    void postTransaction_bankNegativePermitted() {
        FinancialAccount account = createAccount(AccountType.BANK, new BigDecimal("1000.00"), today.minusDays(5), "PKR", AccountStatus.ACTIVE);
        when(accountRepository.findForUpdateByIdAndBusinessId(accountId, businessId)).thenReturn(Optional.of(account));

        when(transactionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            FinancialTransaction tx = invocation.getArgument(0);
            tx.setId(UUID.randomUUID());
            return tx;
        });

        when(movementRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            AccountMovement m = invocation.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        AccountMovementSum sumMock = mock(AccountMovementSum.class);
        when(sumMock.getMovementDirection()).thenReturn(MovementDirection.DECREASE);
        when(sumMock.getTotalAmount()).thenReturn(new BigDecimal("5000.00"));
        when(movementRepository.findMovementSumsByAccountIdAndCutoffDate(accountId, today)).thenReturn(List.of(sumMock));

        TransactionPostRequest request = new TransactionPostRequest(
                accountId, "OPERATING_EXPENSE_PAYMENT", "5000.00", today.toString(), "Overdraft payment"
        );

        TransactionDetailResponse response = postService.postTransaction(context, "key-overdraft", request);

        assertThat(response).isNotNull();
        assertThat(response.resultingBalance()).isEqualTo("-4000.00");
    }

    @Test
    @DisplayName("Malformed amount strings like 1e2, +1.00, 01.00 are rejected before numeric parsing and write nothing")
    void postTransaction_malformedAmounts_rejected() {
        for (String malformed : List.of("1e2", "+1.00", "01.00", "10.0", "10.555", "0.00", "abc")) {
            TransactionPostRequest request = new TransactionPostRequest(
                    accountId, "DIRECT_SALE_RECEIPT", malformed, today.toString(), "Malformed"
            );

            assertThatThrownBy(() -> postService.postTransaction(context, "key-malformed-" + malformed, request))
                    .isInstanceOf(TransactionValidationException.class)
                    .satisfies(ex -> {
                        TransactionValidationException tve = (TransactionValidationException) ex;
                        assertThat(tve.getErrors()).containsKey("amount");
                    });
        }

        verifyNoInteractions(accountRepository);
        verifyNoInteractions(transactionRepository);
        verifyNoInteractions(movementRepository);
        verifyNoInteractions(idempotencyRepository);
        verifyNoInteractions(securityAuditRepository);
    }

    @Test
    @DisplayName("Transaction causing aggregate account balance to exceed 999,999,999,999.99 is rejected and persists nothing")
    void postTransaction_aggregateCapacityOverflow_rejected() {
        FinancialAccount account = createAccount(
                AccountType.BANK,
                new BigDecimal("999999999999.99"),
                today.minusDays(5),
                "PKR",
                AccountStatus.ACTIVE
        );
        when(accountRepository.findForUpdateByIdAndBusinessId(accountId, businessId)).thenReturn(Optional.of(account));
        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(accountId)).thenReturn(Collections.emptyList());

        TransactionPostRequest request = new TransactionPostRequest(
                accountId, "DIRECT_SALE_RECEIPT", "0.01", today.toString(), "Overflow attempt"
        );

        assertThatThrownBy(() -> postService.postTransaction(context, "key-overflow", request))
                .isInstanceOf(TransactionValidationException.class)
                .satisfies(ex -> {
                    TransactionValidationException tve = (TransactionValidationException) ex;
                    assertThat(tve.getErrors()).containsKey("amount");
                    assertThat(tve.getErrors().get("amount")).contains("exceed maximum capacity");
                });

        verify(transactionRepository, never()).saveAndFlush(any());
        verify(movementRepository, never()).saveAndFlush(any());
        verify(idempotencyRepository, never()).saveAndFlush(any());
        verify(securityAuditRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Transaction causing BANK negative balance to exceed -999,999,999,999.99 is rejected")
    void postTransaction_bankNegativeCapacityOverflow_rejected() {
        FinancialAccount account = createAccount(
                AccountType.BANK,
                new BigDecimal("-999999999999.99"),
                today.minusDays(5),
                "PKR",
                AccountStatus.ACTIVE
        );
        when(accountRepository.findForUpdateByIdAndBusinessId(accountId, businessId)).thenReturn(Optional.of(account));
        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(accountId)).thenReturn(Collections.emptyList());

        TransactionPostRequest request = new TransactionPostRequest(
                accountId, "OPERATING_EXPENSE_PAYMENT", "0.01", today.toString(), "Negative overflow"
        );

        assertThatThrownBy(() -> postService.postTransaction(context, "key-neg-overflow", request))
                .isInstanceOf(TransactionValidationException.class)
                .satisfies(ex -> {
                    TransactionValidationException tve = (TransactionValidationException) ex;
                    assertThat(tve.getErrors()).containsKey("amount");
                    assertThat(tve.getErrors().get("amount")).contains("exceed maximum capacity");
                });

        verify(transactionRepository, never()).saveAndFlush(any());
        verify(movementRepository, never()).saveAndFlush(any());
        verify(idempotencyRepository, never()).saveAndFlush(any());
        verify(securityAuditRepository, never()).saveAndFlush(any());
    }
}
