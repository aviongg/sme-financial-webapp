package com.app.sme_health_backend.accounting;

import com.app.sme_health_backend.accounting.dto.TransactionDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransferPostRequest;
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
import com.app.sme_health_backend.accounting.service.TransferService;
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
class TransferServiceTest {

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
    private TransferService transferService;

    private UUID businessId;
    private UUID userId;
    private UUID sourceAccountId;
    private UUID destAccountId;
    private BusinessAccessContext context;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        transferService = new TransferService(
                accountRepository,
                transactionRepository,
                movementRepository,
                idempotencyRepository,
                securityAuditRepository,
                objectMapper
        );

        businessId = UUID.randomUUID();
        userId = UUID.randomUUID();
        sourceAccountId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        destAccountId = UUID.fromString("22222222-2222-4222-8222-222222222222");
        context = new BusinessAccessContext(userId, businessId, MembershipRole.ACCOUNTANT);
        today = LocalDate.now(ZoneId.of("Asia/Karachi"));
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
    @DisplayName("Successfully posts transfer from bank to cash (5,000 PKR)")
    void postTransfer_success_bankToCash() {
        FinancialAccount bank = createAccount(sourceAccountId, "Meezan Bank", AccountType.BANK, new BigDecimal("50000.00"), today.minusDays(10), "PKR", AccountStatus.ACTIVE);
        FinancialAccount cash = createAccount(destAccountId, "Petty Cash", AccountType.CASH, new BigDecimal("1000.00"), today.minusDays(10), "PKR", AccountStatus.ACTIVE);

        when(accountRepository.findForUpdateByIdAndBusinessId(sourceAccountId, businessId)).thenReturn(Optional.of(bank));
        when(accountRepository.findForUpdateByIdAndBusinessId(destAccountId, businessId)).thenReturn(Optional.of(cash));
        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(sourceAccountId)).thenReturn(Collections.emptyList());
        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(destAccountId)).thenReturn(Collections.emptyList());

        UUID txId = UUID.randomUUID();
        when(transactionRepository.saveAndFlush(any(FinancialTransaction.class))).thenAnswer(inv -> {
            FinancialTransaction t = inv.getArgument(0);
            t.setId(txId);
            t.setCreatedAt(Instant.now());
            return t;
        });

        when(movementRepository.saveAndFlush(any(AccountMovement.class))).thenAnswer(inv -> {
            AccountMovement m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            m.setCreatedAt(Instant.now());
            return m;
        });

        // After transfer, movements sums for bank
        AccountMovementSum bankSum = mock(AccountMovementSum.class);
        when(bankSum.getMovementDirection()).thenReturn(MovementDirection.DECREASE);
        when(bankSum.getTotalAmount()).thenReturn(new BigDecimal("5000.00"));
        when(movementRepository.findMovementSumsByAccountIdAndCutoffDate(eq(sourceAccountId), any(LocalDate.class)))
                .thenReturn(List.of(bankSum));

        TransferPostRequest request = new TransferPostRequest(sourceAccountId, destAccountId, "5000.00", today.toString(), "Replenish petty cash");
        TransactionDetailResponse response = transferService.postTransfer(context, "idemp-key-1", request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(txId);
        assertThat(response.eventType()).isEqualTo("INTERNAL_TRANSFER");
        assertThat(response.movementDirection()).isEqualTo("DECREASE");
        assertThat(response.amount()).isEqualTo("5000.00");
        assertThat(response.resultingBalance()).isEqualTo("45000.00");
        assertThat(response.sourceAccountId()).isEqualTo(sourceAccountId);
        assertThat(response.sourceAccountName()).isEqualTo("Meezan Bank");
        assertThat(response.destinationAccountId()).isEqualTo(destAccountId);
        assertThat(response.destinationAccountName()).isEqualTo("Petty Cash");
        assertThat(response.movements()).hasSize(2);

        // Verify transaction saved
        ArgumentCaptor<FinancialTransaction> txCaptor = ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(transactionRepository).saveAndFlush(txCaptor.capture());
        FinancialTransaction capturedTx = txCaptor.getValue();
        assertThat(capturedTx.getAccountId()).isEqualTo(sourceAccountId);
        assertThat(capturedTx.getDestinationAccountId()).isEqualTo(destAccountId);
        assertThat(capturedTx.getEventType()).isEqualTo(TransactionEventType.INTERNAL_TRANSFER);
        assertThat(capturedTx.getMovementDirection()).isEqualTo(MovementDirection.DECREASE);

        // Verify two movements saved
        ArgumentCaptor<AccountMovement> movCaptor = ArgumentCaptor.forClass(AccountMovement.class);
        verify(movementRepository, times(2)).saveAndFlush(movCaptor.capture());
        List<AccountMovement> capturedMovs = movCaptor.getAllValues();
        assertThat(capturedMovs.get(0).getAccountId()).isEqualTo(sourceAccountId);
        assertThat(capturedMovs.get(0).getMovementDirection()).isEqualTo(MovementDirection.DECREASE);
        assertThat(capturedMovs.get(0).getAmount()).isEqualByComparingTo("5000.00");
        assertThat(capturedMovs.get(1).getAccountId()).isEqualTo(destAccountId);
        assertThat(capturedMovs.get(1).getMovementDirection()).isEqualTo(MovementDirection.INCREASE);
        assertThat(capturedMovs.get(1).getAmount()).isEqualByComparingTo("5000.00");

        // Verify audit event saved
        ArgumentCaptor<SecurityAuditEvent> auditCaptor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(securityAuditRepository).saveAndFlush(auditCaptor.capture());
        SecurityAuditEvent auditEvent = auditCaptor.getValue();
        assertThat(auditEvent.getEventType()).isEqualTo("FINANCIAL_TRANSFER_POSTED");
        assertThat(auditEvent.getActorUserId()).isEqualTo(userId);
        assertThat(auditEvent.getBusinessId()).isEqualTo(businessId);

        // Verify idempotency saved
        verify(idempotencyRepository).saveAndFlush(any(FinancialIdempotencyRecord.class));
    }

    @Test
    @DisplayName("Rejects transfer between identical source and destination accounts")
    void postTransfer_sameAccount_rejected() {
        TransferPostRequest request = new TransferPostRequest(sourceAccountId, sourceAccountId, "5000.00", today.toString(), null);

        assertThatThrownBy(() -> transferService.postTransfer(context, "key-1", request))
                .isInstanceOf(TransactionValidationException.class)
                .satisfies(ex -> {
                    TransactionValidationException tve = (TransactionValidationException) ex;
                    assertThat(tve.getErrors()).containsEntry("destinationAccountId", "Source and destination accounts must be distinct");
                });

        verifyNoInteractions(transactionRepository);
        verifyNoInteractions(movementRepository);
    }

    @Test
    @DisplayName("Rejects transfer if account not found or in different business")
    void postTransfer_accountNotFound_rejected() {
        when(accountRepository.findForUpdateByIdAndBusinessId(sourceAccountId, businessId)).thenReturn(Optional.empty());

        TransferPostRequest request = new TransferPostRequest(sourceAccountId, destAccountId, "5000.00", today.toString(), null);

        assertThatThrownBy(() -> transferService.postTransfer(context, "key-1", request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Financial account not found");
    }

    @Test
    @DisplayName("Rejects transfer if an account is inactive")
    void postTransfer_inactiveAccount_rejected() {
        FinancialAccount bank = createAccount(sourceAccountId, "Bank", AccountType.BANK, new BigDecimal("50000.00"), today.minusDays(10), "PKR", AccountStatus.INACTIVE);
        FinancialAccount cash = createAccount(destAccountId, "Cash", AccountType.CASH, new BigDecimal("1000.00"), today.minusDays(10), "PKR", AccountStatus.ACTIVE);

        when(accountRepository.findForUpdateByIdAndBusinessId(sourceAccountId, businessId)).thenReturn(Optional.of(bank));
        when(accountRepository.findForUpdateByIdAndBusinessId(destAccountId, businessId)).thenReturn(Optional.of(cash));

        TransferPostRequest request = new TransferPostRequest(sourceAccountId, destAccountId, "5000.00", today.toString(), null);

        assertThatThrownBy(() -> transferService.postTransfer(context, "key-1", request))
                .isInstanceOf(TransactionValidationException.class)
                .satisfies(ex -> {
                    TransactionValidationException tve = (TransactionValidationException) ex;
                    assertThat(tve.getErrors()).containsEntry("sourceAccountId", "Source account is not active");
                });
    }

    @Test
    @DisplayName("Rejects transfer if an account is non-PKR")
    void postTransfer_nonPkrAccount_rejected() {
        FinancialAccount bank = createAccount(sourceAccountId, "Bank", AccountType.BANK, new BigDecimal("50000.00"), today.minusDays(10), "USD", AccountStatus.ACTIVE);
        FinancialAccount cash = createAccount(destAccountId, "Cash", AccountType.CASH, new BigDecimal("1000.00"), today.minusDays(10), "PKR", AccountStatus.ACTIVE);

        when(accountRepository.findForUpdateByIdAndBusinessId(sourceAccountId, businessId)).thenReturn(Optional.of(bank));
        when(accountRepository.findForUpdateByIdAndBusinessId(destAccountId, businessId)).thenReturn(Optional.of(cash));

        TransferPostRequest request = new TransferPostRequest(sourceAccountId, destAccountId, "5000.00", today.toString(), null);

        assertThatThrownBy(() -> transferService.postTransfer(context, "key-1", request))
                .isInstanceOf(TransactionValidationException.class)
                .satisfies(ex -> {
                    TransactionValidationException tve = (TransactionValidationException) ex;
                    assertThat(tve.getErrors()).containsEntry("sourceAccountId", "Source account must be a PKR account");
                });
    }

    @Test
    @DisplayName("Rejects transfer with date earlier than account opening date")
    void postTransfer_dateBeforeOpening_rejected() {
        FinancialAccount bank = createAccount(sourceAccountId, "Bank", AccountType.BANK, new BigDecimal("50000.00"), today.minusDays(5), "PKR", AccountStatus.ACTIVE);
        FinancialAccount cash = createAccount(destAccountId, "Cash", AccountType.CASH, new BigDecimal("1000.00"), today.minusDays(10), "PKR", AccountStatus.ACTIVE);

        when(accountRepository.findForUpdateByIdAndBusinessId(sourceAccountId, businessId)).thenReturn(Optional.of(bank));
        when(accountRepository.findForUpdateByIdAndBusinessId(destAccountId, businessId)).thenReturn(Optional.of(cash));

        TransferPostRequest request = new TransferPostRequest(sourceAccountId, destAccountId, "5000.00", today.minusDays(6).toString(), null);

        assertThatThrownBy(() -> transferService.postTransfer(context, "key-1", request))
                .isInstanceOf(TransactionValidationException.class)
                .satisfies(ex -> {
                    TransactionValidationException tve = (TransactionValidationException) ex;
                    assertThat(tve.getErrors()).containsKey("effectiveDate");
                    assertThat(tve.getErrors().get("effectiveDate")).contains("cannot precede source account opening date");
                });
    }

    @Test
    @DisplayName("Rejects transfer with date in future")
    void postTransfer_futureDate_rejected() {
        TransferPostRequest request = new TransferPostRequest(sourceAccountId, destAccountId, "5000.00", today.plusDays(1).toString(), null);

        assertThatThrownBy(() -> transferService.postTransfer(context, "key-1", request))
                .isInstanceOf(TransactionValidationException.class)
                .satisfies(ex -> {
                    TransactionValidationException tve = (TransactionValidationException) ex;
                    assertThat(tve.getErrors()).containsEntry("effectiveDate", "Effective date cannot be in the future (Asia/Karachi)");
                });
    }

    @Test
    @DisplayName("Rejects malformed amount syntax without trimming")
    void postTransfer_malformedAmount_rejected() {
        for (String bad : List.of("5000", "5000.5", "5000.001", "-5000.00", " 5000.00", "5000.00 ", "5000.00\0", "1e5")) {
            TransferPostRequest request = new TransferPostRequest(sourceAccountId, destAccountId, bad, today.toString(), null);
            assertThatThrownBy(() -> transferService.postTransfer(context, "key-1", request))
                    .isInstanceOf(TransactionValidationException.class);
        }
    }

    @Test
    @DisplayName("Rejects transfer when source cash account balance would become negative")
    void postTransfer_cashNegative_rejected() {
        FinancialAccount cashSource = createAccount(sourceAccountId, "Cash", AccountType.CASH, new BigDecimal("1000.00"), today.minusDays(10), "PKR", AccountStatus.ACTIVE);
        FinancialAccount bankDest = createAccount(destAccountId, "Bank", AccountType.BANK, new BigDecimal("5000.00"), today.minusDays(10), "PKR", AccountStatus.ACTIVE);

        when(accountRepository.findForUpdateByIdAndBusinessId(sourceAccountId, businessId)).thenReturn(Optional.of(cashSource));
        when(accountRepository.findForUpdateByIdAndBusinessId(destAccountId, businessId)).thenReturn(Optional.of(bankDest));
        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(sourceAccountId)).thenReturn(Collections.emptyList());

        TransferPostRequest request = new TransferPostRequest(sourceAccountId, destAccountId, "5000.00", today.toString(), null);

        assertThatThrownBy(() -> transferService.postTransfer(context, "key-1", request))
                .isInstanceOf(TransactionValidationException.class)
                .satisfies(ex -> {
                    TransactionValidationException tve = (TransactionValidationException) ex;
                    assertThat(tve.getErrors()).containsKey("cashBalance");
                    assertThat(tve.getErrors().get("cashBalance")).contains("negative");
                });
    }

    @Test
    @DisplayName("Idempotent retry returns original cached response")
    void postTransfer_idempotentRetry() {
        FinancialAccount bank = createAccount(sourceAccountId, "Bank", AccountType.BANK, new BigDecimal("50000.00"), today.minusDays(10), "PKR", AccountStatus.ACTIVE);
        FinancialAccount cash = createAccount(destAccountId, "Cash", AccountType.CASH, new BigDecimal("1000.00"), today.minusDays(10), "PKR", AccountStatus.ACTIVE);

        when(accountRepository.findForUpdateByIdAndBusinessId(sourceAccountId, businessId)).thenReturn(Optional.of(bank));
        when(accountRepository.findForUpdateByIdAndBusinessId(destAccountId, businessId)).thenReturn(Optional.of(cash));

        String expectedHash = TransferService.computePayloadHash(sourceAccountId, destAccountId, new BigDecimal("5000.00"), today, "");
        FinancialIdempotencyRecord cached = new FinancialIdempotencyRecord(
                businessId,
                TransferService.OPERATION_FINANCIAL_TRANSFER,
                "key-1",
                expectedHash,
                UUID.randomUUID(),
                201,
                "{\"id\":\"" + UUID.randomUUID() + "\",\"amount\":\"5000.00\",\"resultingBalance\":\"45000.00\",\"movements\":[]}"
        );

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(businessId, TransferService.OPERATION_FINANCIAL_TRANSFER, "key-1"))
                .thenReturn(Optional.of(cached));

        TransferPostRequest request = new TransferPostRequest(sourceAccountId, destAccountId, "5000.00", today.toString(), null);
        TransactionDetailResponse response = transferService.postTransfer(context, "key-1", request);

        assertThat(response).isNotNull();
        assertThat(response.amount()).isEqualTo("5000.00");
        verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("Changed payload with same idempotency key returns 409 conflict")
    void postTransfer_changedPayload_throwsConflict() {
        FinancialAccount bank = createAccount(sourceAccountId, "Bank", AccountType.BANK, new BigDecimal("50000.00"), today.minusDays(10), "PKR", AccountStatus.ACTIVE);
        FinancialAccount cash = createAccount(destAccountId, "Cash", AccountType.CASH, new BigDecimal("1000.00"), today.minusDays(10), "PKR", AccountStatus.ACTIVE);

        when(accountRepository.findForUpdateByIdAndBusinessId(sourceAccountId, businessId)).thenReturn(Optional.of(bank));
        when(accountRepository.findForUpdateByIdAndBusinessId(destAccountId, businessId)).thenReturn(Optional.of(cash));

        FinancialIdempotencyRecord cached = new FinancialIdempotencyRecord(
                businessId,
                TransferService.OPERATION_FINANCIAL_TRANSFER,
                "key-1",
                "different-hash",
                UUID.randomUUID(),
                201,
                "{}"
        );

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(businessId, TransferService.OPERATION_FINANCIAL_TRANSFER, "key-1"))
                .thenReturn(Optional.of(cached));

        TransferPostRequest request = new TransferPostRequest(sourceAccountId, destAccountId, "5000.00", today.toString(), null);

        assertThatThrownBy(() -> transferService.postTransfer(context, "key-1", request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("has already been used with a different request payload");
    }
}
