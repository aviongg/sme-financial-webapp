package com.app.sme_health_backend.fx;

import com.app.sme_health_backend.accounting.entity.AccountMovement;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialTransaction;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.AccountMovementRepository;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialTransactionRepository;
import com.app.sme_health_backend.audit.repository.SecurityAuditRepository;
import com.app.sme_health_backend.fx.dto.*;
import com.app.sme_health_backend.fx.entity.FxObligationSettlement;
import com.app.sme_health_backend.fx.model.FxSettlementStatus;
import com.app.sme_health_backend.fx.model.SettlementDirection;
import com.app.sme_health_backend.fx.repository.FxObligationRecognitionEvidenceRepository;
import com.app.sme_health_backend.fx.repository.FxObligationSettlementRepository;
import com.app.sme_health_backend.fx.service.CurrencyMetadataService;
import com.app.sme_health_backend.fx.service.FxSettlementService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.obligation.*;
import com.app.sme_health_backend.obligation.aging.ObligationDirection;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FxSettlementServiceTest {

    private FinancialObligationRepository obligationRepository;
    private ObligationPaymentAllocationRepository allocationRepository;
    private FinancialAccountRepository accountRepository;
    private FinancialTransactionRepository transactionRepository;
    private AccountMovementRepository movementRepository;
    private FxObligationSettlementRepository fxSettlementRepository;
    private FxObligationRecognitionEvidenceRepository fxRecognitionEvidenceRepository;
    private SecurityAuditRepository auditRepository;
    private CurrencyMetadataService currencyMetadataService;
    private FxSettlementService settlementService;

    private UUID businessId;
    private UUID userId;
    private BusinessAccessContext context;

    @BeforeEach
    void setUp() {
        obligationRepository = mock(FinancialObligationRepository.class);
        allocationRepository = mock(ObligationPaymentAllocationRepository.class);
        accountRepository = mock(FinancialAccountRepository.class);
        transactionRepository = mock(FinancialTransactionRepository.class);
        movementRepository = mock(AccountMovementRepository.class);
        fxSettlementRepository = mock(FxObligationSettlementRepository.class);
        fxRecognitionEvidenceRepository = mock(FxObligationRecognitionEvidenceRepository.class);
        auditRepository = mock(SecurityAuditRepository.class);
        currencyMetadataService = new CurrencyMetadataService();

        settlementService = new FxSettlementService(
                obligationRepository,
                allocationRepository,
                accountRepository,
                transactionRepository,
                movementRepository,
                fxSettlementRepository,
                fxRecognitionEvidenceRepository,
                currencyMetadataService,
                auditRepository
        );

        businessId = UUID.randomUUID();
        userId = UUID.randomUUID();
        context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
    }

    @Test
    @DisplayName("Preview: Payable settlement calculation with fee")
    void testPreviewPayableSettlement() {
        UUID obligationId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID counterpartyId = UUID.randomUUID();

        FinancialObligation payable = new FinancialObligation(
                businessId, counterpartyId, ObligationDirection.PAYABLE, ObligationSourceType.MANUAL_DECLARATION,
                "REF-001", "PO-100", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                new BigDecimal("100.00"), "USD", "Import supplier payment", userId
        );
        payable.setId(obligationId);

        FinancialAccount pkrAccount = new FinancialAccount(
                businessId, "Operational PKR Bank", AccountType.BANK, "PKR", 2,
                new BigDecimal("1000000.00"), LocalDate.of(2026, 1, 1), AccountStatus.ACTIVE,
                "HBL", "HBL-001", "Main operating bank"
        );
        pkrAccount.setId(accountId);

        when(obligationRepository.findByIdAndBusinessId(obligationId, businessId))
                .thenReturn(Optional.of(payable));
        when(allocationRepository.sumEffectiveAllocations(businessId, obligationId))
                .thenReturn(BigDecimal.ZERO);
        when(accountRepository.findByIdAndBusinessId(accountId, businessId))
                .thenReturn(Optional.of(pkrAccount));

        FxSettlementPreviewRequest request = new FxSettlementPreviewRequest(
                obligationId, accountId, new BigDecimal("100.00"),
                new BigDecimal("278.50"), new BigDecimal("150.00"), BigDecimal.ZERO, "PKR"
        );

        FxSettlementPreviewResponse response = settlementService.previewSettlement(context, request);

        assertThat(response).isNotNull();
        assertThat(response.settleAmountInObligationCurrency()).isEqualByComparingTo("100.00");
        assertThat(response.convertedPrincipalInAccountCurrency()).isEqualByComparingTo("27850.00");
        assertThat(response.totalFeeAmount()).isEqualByComparingTo("150.00");
        assertThat(response.totalAccountMovement()).isEqualByComparingTo("28000.00");
        assertThat(response.effectiveRate()).isEqualByComparingTo("280.000000");
        assertThat(response.accountMovementType()).isEqualTo("DECREASE");
    }

    @Test
    @DisplayName("Case A: Settle USD 100 payable from PKR account @ 278.50 with 150 PKR fee -> 28,000 PKR debit")
    void testCaseAPayableSettlement() {
        UUID obligationId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID counterpartyId = UUID.randomUUID();

        FinancialObligation payable = new FinancialObligation(
                businessId, counterpartyId, ObligationDirection.PAYABLE, ObligationSourceType.MANUAL_DECLARATION,
                "REF-001", "PO-100", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                new BigDecimal("100.00"), "USD", "Import supplier payment", userId
        );
        payable.setId(obligationId);

        FinancialAccount pkrAccount = new FinancialAccount(
                businessId, "Operational PKR Bank", AccountType.BANK, "PKR", 2,
                new BigDecimal("1000000.00"), LocalDate.of(2026, 1, 1), AccountStatus.ACTIVE,
                "HBL", "HBL-001", "Main operating bank"
        );
        pkrAccount.setId(accountId);

        when(obligationRepository.findByIdAndBusinessIdForUpdate(obligationId, businessId))
                .thenReturn(Optional.of(payable));
        when(allocationRepository.sumEffectiveAllocations(businessId, obligationId))
                .thenReturn(BigDecimal.ZERO);
        when(accountRepository.findForUpdateByIdAndBusinessId(accountId, businessId))
                .thenReturn(Optional.of(pkrAccount));
        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(accountId))
                .thenReturn(Collections.emptyList());

        when(transactionRepository.saveAndFlush(any(FinancialTransaction.class)))
                .thenAnswer(inv -> {
                    FinancialTransaction tx = inv.getArgument(0);
                    tx.setId(UUID.randomUUID());
                    return tx;
                });
        when(movementRepository.saveAndFlush(any(AccountMovement.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(allocationRepository.saveAndFlush(any(ObligationPaymentAllocation.class)))
                .thenAnswer(inv -> {
                    ObligationPaymentAllocation alloc = inv.getArgument(0);
                    alloc.setId(UUID.randomUUID());
                    return alloc;
                });
        when(fxSettlementRepository.saveAndFlush(any(FxObligationSettlement.class)))
                .thenAnswer(inv -> {
                    FxObligationSettlement s = inv.getArgument(0);
                    if (s.getId() == null) {
                        s.setId(UUID.randomUUID());
                    }
                    return s;
                });

        FxSettlementPostRequest request = new FxSettlementPostRequest(
                obligationId, accountId, new BigDecimal("100.00"),
                new BigDecimal("278.50"), new BigDecimal("150.00"), BigDecimal.ZERO,
                "PKR", LocalDate.of(2026, 11, 5), "Payable USD 100", "MANUAL_USER_RATE"
        );

        FxSettlementResponse response = settlementService.postSettlement(context, "idemp-001", request);

        assertThat(response).isNotNull();
        assertThat(response.obligationAmount()).isEqualByComparingTo("100.00");
        assertThat(response.obligationCurrency()).isEqualTo("USD");
        assertThat(response.convertedPrincipal()).isEqualByComparingTo("27850.00");
        assertThat(response.feeAmount()).isEqualByComparingTo("150.00");
        assertThat(response.totalAccountMovement()).isEqualByComparingTo("28000.00");
        assertThat(response.settlementDirection()).isEqualTo(SettlementDirection.PAYABLE_SETTLEMENT);
        assertThat(response.effectiveRate()).isEqualByComparingTo("280.000000");

        // Verify allocation is recorded in USD
        ArgumentCaptor<ObligationPaymentAllocation> allocCaptor = ArgumentCaptor.forClass(ObligationPaymentAllocation.class);
        verify(allocationRepository).saveAndFlush(allocCaptor.capture());
        assertThat(allocCaptor.getValue().getAmount()).isEqualByComparingTo("100.00");

        // Verify transaction is recorded in PKR
        ArgumentCaptor<FinancialTransaction> txCaptor = ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(transactionRepository).saveAndFlush(txCaptor.capture());
        assertThat(txCaptor.getValue().getAmount()).isEqualByComparingTo("28000.00");
        assertThat(txCaptor.getValue().getCurrency()).isEqualTo("PKR");
        assertThat(txCaptor.getValue().getMovementDirection()).isEqualTo(MovementDirection.DECREASE);
    }

    @Test
    @DisplayName("Case B: Collect USD 100 receivable into PKR account @ 278.50 with 150 PKR fee -> 27,700 PKR credit")
    void testCaseBReceivableCollection() {
        UUID obligationId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID counterpartyId = UUID.randomUUID();

        FinancialObligation receivable = new FinancialObligation(
                businessId, counterpartyId, ObligationDirection.RECEIVABLE, ObligationSourceType.MANUAL_DECLARATION,
                "INV-001", "INV-100", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                new BigDecimal("100.00"), "USD", "Export customer receivable", userId
        );
        receivable.setId(obligationId);

        FinancialAccount pkrAccount = new FinancialAccount(
                businessId, "Operational PKR Bank", AccountType.BANK, "PKR", 2,
                new BigDecimal("50000.00"), LocalDate.of(2026, 1, 1), AccountStatus.ACTIVE,
                "HBL", "HBL-001", "Main operating bank"
        );
        pkrAccount.setId(accountId);

        when(obligationRepository.findByIdAndBusinessIdForUpdate(obligationId, businessId))
                .thenReturn(Optional.of(receivable));
        when(allocationRepository.sumEffectiveAllocations(businessId, obligationId))
                .thenReturn(BigDecimal.ZERO);
        when(accountRepository.findForUpdateByIdAndBusinessId(accountId, businessId))
                .thenReturn(Optional.of(pkrAccount));

        when(transactionRepository.saveAndFlush(any(FinancialTransaction.class)))
                .thenAnswer(inv -> {
                    FinancialTransaction tx = inv.getArgument(0);
                    tx.setId(UUID.randomUUID());
                    return tx;
                });
        when(movementRepository.saveAndFlush(any(AccountMovement.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(allocationRepository.saveAndFlush(any(ObligationPaymentAllocation.class)))
                .thenAnswer(inv -> {
                    ObligationPaymentAllocation alloc = inv.getArgument(0);
                    alloc.setId(UUID.randomUUID());
                    return alloc;
                });
        when(fxSettlementRepository.saveAndFlush(any(FxObligationSettlement.class)))
                .thenAnswer(inv -> {
                    FxObligationSettlement s = inv.getArgument(0);
                    if (s.getId() == null) {
                        s.setId(UUID.randomUUID());
                    }
                    return s;
                });

        FxSettlementPostRequest request = new FxSettlementPostRequest(
                obligationId, accountId, new BigDecimal("100.00"),
                new BigDecimal("278.50"), new BigDecimal("150.00"), BigDecimal.ZERO,
                "PKR", LocalDate.of(2026, 11, 5), "Receivable USD 100", "MANUAL_USER_RATE"
        );

        FxSettlementResponse response = settlementService.postSettlement(context, "idemp-002", request);

        assertThat(response).isNotNull();
        assertThat(response.obligationAmount()).isEqualByComparingTo("100.00");
        assertThat(response.obligationCurrency()).isEqualTo("USD");
        assertThat(response.convertedPrincipal()).isEqualByComparingTo("27850.00");
        assertThat(response.feeAmount()).isEqualByComparingTo("150.00");
        assertThat(response.totalAccountMovement()).isEqualByComparingTo("27700.00");
        assertThat(response.settlementDirection()).isEqualTo(SettlementDirection.RECEIVABLE_COLLECTION);
        assertThat(response.effectiveRate()).isEqualByComparingTo("277.000000");

        // Verify transaction is recorded in PKR as INCREASE
        ArgumentCaptor<FinancialTransaction> txCaptor = ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(transactionRepository).saveAndFlush(txCaptor.capture());
        assertThat(txCaptor.getValue().getAmount()).isEqualByComparingTo("27700.00");
        assertThat(txCaptor.getValue().getCurrency()).isEqualTo("PKR");
        assertThat(txCaptor.getValue().getMovementDirection()).isEqualTo(MovementDirection.INCREASE);
    }

    @Test
    @DisplayName("Case C: Partial settlements USD 40 then USD 60 with independent locked rates")
    void testCaseCPartialSettlements() {
        UUID obligationId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID counterpartyId = UUID.randomUUID();

        FinancialObligation payable = new FinancialObligation(
                businessId, counterpartyId, ObligationDirection.PAYABLE, ObligationSourceType.MANUAL_DECLARATION,
                "REF-PARTIAL", "PO-PARTIAL", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                new BigDecimal("100.00"), "USD", "Partial payable", userId
        );
        payable.setId(obligationId);

        FinancialAccount pkrAccount = new FinancialAccount(
                businessId, "Operational PKR Bank", AccountType.BANK, "PKR", 2,
                new BigDecimal("1000000.00"), LocalDate.of(2026, 1, 1), AccountStatus.ACTIVE,
                "HBL", "HBL-001", "Main operating bank"
        );
        pkrAccount.setId(accountId);

        when(obligationRepository.findByIdAndBusinessIdForUpdate(obligationId, businessId))
                .thenReturn(Optional.of(payable));
        when(accountRepository.findForUpdateByIdAndBusinessId(accountId, businessId))
                .thenReturn(Optional.of(pkrAccount));
        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(accountId))
                .thenReturn(Collections.emptyList());

        when(transactionRepository.saveAndFlush(any(FinancialTransaction.class)))
                .thenAnswer(inv -> {
                    FinancialTransaction tx = inv.getArgument(0);
                    tx.setId(UUID.randomUUID());
                    return tx;
                });
        when(movementRepository.saveAndFlush(any(AccountMovement.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(allocationRepository.saveAndFlush(any(ObligationPaymentAllocation.class)))
                .thenAnswer(inv -> {
                    ObligationPaymentAllocation alloc = inv.getArgument(0);
                    alloc.setId(UUID.randomUUID());
                    return alloc;
                });
        when(fxSettlementRepository.saveAndFlush(any(FxObligationSettlement.class)))
                .thenAnswer(inv -> {
                    FxObligationSettlement s = inv.getArgument(0);
                    if (s.getId() == null) {
                        s.setId(UUID.randomUUID());
                    }
                    return s;
                });

        // First tranche: USD 40 @ 278.50 with 0 fee -> 11,140 PKR
        when(allocationRepository.sumEffectiveAllocations(businessId, obligationId))
                .thenReturn(BigDecimal.ZERO);

        FxSettlementPostRequest req1 = new FxSettlementPostRequest(
                obligationId, accountId, new BigDecimal("40.00"),
                new BigDecimal("278.50"), BigDecimal.ZERO, BigDecimal.ZERO,
                "PKR", LocalDate.of(2026, 11, 5), "Tranche 1", "MANUAL_USER_RATE"
        );
        FxSettlementResponse res1 = settlementService.postSettlement(context, "idemp-tranche-1", req1);
        assertThat(res1.obligationAmount()).isEqualByComparingTo("40.00");
        assertThat(res1.totalAccountMovement()).isEqualByComparingTo("11140.00");
        assertThat(res1.exchangeRate()).isEqualByComparingTo("278.500000");

        // Second tranche: USD 60 @ 280.00 with 100 PKR fee -> 16,900 PKR
        when(allocationRepository.sumEffectiveAllocations(businessId, obligationId))
                .thenReturn(new BigDecimal("40.00"));

        FxSettlementPostRequest req2 = new FxSettlementPostRequest(
                obligationId, accountId, new BigDecimal("60.00"),
                new BigDecimal("280.00"), new BigDecimal("100.00"), BigDecimal.ZERO,
                "PKR", LocalDate.of(2026, 11, 6), "Tranche 2", "MANUAL_USER_RATE"
        );
        FxSettlementResponse res2 = settlementService.postSettlement(context, "idemp-tranche-2", req2);
        assertThat(res2.obligationAmount()).isEqualByComparingTo("60.00");
        assertThat(res2.totalAccountMovement()).isEqualByComparingTo("16900.00");
        assertThat(res2.exchangeRate()).isEqualByComparingTo("280.000000");
    }

    @Test
    @DisplayName("Rejection: Over-settlement exceeds outstanding balance")
    void testOverSettlementRejection() {
        UUID obligationId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        FinancialObligation payable = new FinancialObligation(
                businessId, UUID.randomUUID(), ObligationDirection.PAYABLE, ObligationSourceType.MANUAL_DECLARATION,
                "REF-001", "PO-100", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                new BigDecimal("100.00"), "USD", "Import supplier payment", userId
        );
        payable.setId(obligationId);

        when(obligationRepository.findByIdAndBusinessIdForUpdate(obligationId, businessId))
                .thenReturn(Optional.of(payable));
        when(allocationRepository.sumEffectiveAllocations(businessId, obligationId))
                .thenReturn(new BigDecimal("50.00")); // outstanding is 50.00

        FxSettlementPostRequest request = new FxSettlementPostRequest(
                obligationId, accountId, new BigDecimal("50.01"), // exceeds 50.00
                new BigDecimal("278.50"), BigDecimal.ZERO, BigDecimal.ZERO,
                "PKR", LocalDate.of(2026, 11, 5), "Exceeds balance", "MANUAL_USER_RATE"
        );

        assertThatThrownBy(() -> settlementService.postSettlement(context, null, request))
                .isInstanceOf(ObligationConflictException.class)
                .hasMessageContaining("exceeds outstanding balance");
    }

    @Test
    @DisplayName("Rejection: Idempotency conflict with different request parameters")
    void testIdempotencyKeyConflict() {
        UUID obligationId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String idempotencyKey = "key-dup-check";

        FxObligationSettlement existing = new FxObligationSettlement(
                businessId, obligationId, UUID.randomUUID(), UUID.randomUUID(),
                SettlementDirection.PAYABLE_SETTLEMENT, "USD", new BigDecimal("100.00"),
                accountId, "PKR", new BigDecimal("27850.00"), BigDecimal.ZERO, "PKR",
                new BigDecimal("27850.00"), new BigDecimal("278.50"), "BASE_TO_QUOTE",
                "USD", "PKR", "MANUAL_USER_RATE", Instant.now(), LocalDate.of(2026, 11, 5),
                new BigDecimal("278.500000"), "Note", idempotencyKey, userId
        );

        when(fxSettlementRepository.findByBusinessIdAndIdempotencyKey(businessId, idempotencyKey))
                .thenReturn(Optional.of(existing));

        // Different amount (150.00 instead of 100.00)
        FxSettlementPostRequest request = new FxSettlementPostRequest(
                obligationId, accountId, new BigDecimal("150.00"),
                new BigDecimal("278.50"), BigDecimal.ZERO, BigDecimal.ZERO,
                "PKR", LocalDate.of(2026, 11, 5), "Different payload", "MANUAL_USER_RATE"
        );

        assertThatThrownBy(() -> settlementService.postSettlement(context, idempotencyKey, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already been used with different request parameters");
    }

    @Test
    @DisplayName("Reversal: Restores obligation, cancels allocation, and posts compensating movement")
    void testSettlementReversal() {
        UUID settlementId = UUID.randomUUID();
        UUID obligationId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID allocationId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();

        FxObligationSettlement settlement = new FxObligationSettlement(
                businessId, obligationId, txId, allocationId,
                SettlementDirection.PAYABLE_SETTLEMENT, "USD", new BigDecimal("100.00"),
                accountId, "PKR", new BigDecimal("27850.00"), new BigDecimal("150.00"), "PKR",
                new BigDecimal("28000.00"), new BigDecimal("278.50"), "BASE_TO_QUOTE",
                "USD", "PKR", "MANUAL_USER_RATE", Instant.now(), LocalDate.of(2026, 11, 5),
                new BigDecimal("280.000000"), "Note", "idemp-rev", userId
        );
        settlement.setId(settlementId);

        ObligationPaymentAllocation allocation = new ObligationPaymentAllocation(
                businessId, obligationId, txId, new BigDecimal("100.00"), LocalDate.of(2026, 11, 5), userId
        );
        allocation.setId(allocationId);

        FinancialAccount account = new FinancialAccount(
                businessId, "PKR Account", AccountType.BANK, "PKR", 2,
                new BigDecimal("500000.00"), LocalDate.of(2026, 1, 1), AccountStatus.ACTIVE,
                "Bank", "ACC-01", "Notes"
        );
        account.setId(accountId);

        when(fxSettlementRepository.findForUpdateByIdAndBusinessId(settlementId, businessId))
                .thenReturn(Optional.of(settlement));
        when(allocationRepository.findByIdAndBusinessId(allocationId, businessId))
                .thenReturn(Optional.of(allocation));
        when(accountRepository.findForUpdateByIdAndBusinessId(accountId, businessId))
                .thenReturn(Optional.of(account));

        when(transactionRepository.saveAndFlush(any(FinancialTransaction.class)))
                .thenAnswer(inv -> {
                    FinancialTransaction tx = inv.getArgument(0);
                    tx.setId(UUID.randomUUID());
                    return tx;
                });
        when(movementRepository.saveAndFlush(any(AccountMovement.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(allocationRepository.saveAndFlush(any(ObligationPaymentAllocation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(fxSettlementRepository.saveAndFlush(any(FxObligationSettlement.class)))
                .thenAnswer(inv -> {
                    FxObligationSettlement s = inv.getArgument(0);
                    if (s.getId() == null) {
                        s.setId(UUID.randomUUID());
                    }
                    return s;
                });

        FxSettlementReverseRequest reverseRequest = new FxSettlementReverseRequest("Bank fee renegotiated");
        FxSettlementResponse response = settlementService.reverseSettlement(context, "idemp-rev-call", settlementId, reverseRequest);

        assertThat(response.status()).isEqualTo(FxSettlementStatus.REVERSED);
        assertThat(response.reversalReason()).isEqualTo("Bank fee renegotiated");
        assertThat(response.reversalTransactionId()).isNotNull();

        // Verify allocation cancelled
        assertThat(allocation.getStatus()).isEqualTo(AllocationStatus.CANCELLED);
        assertThat(allocation.getCancellationReason()).isEqualTo("Bank fee renegotiated");

        // Verify compensating transaction is INCREASE for payable reversal
        ArgumentCaptor<FinancialTransaction> txCaptor = ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(transactionRepository).saveAndFlush(txCaptor.capture());
        assertThat(txCaptor.getValue().getMovementDirection()).isEqualTo(MovementDirection.INCREASE);
        assertThat(txCaptor.getValue().getAmount()).isEqualByComparingTo("28000.00");
    }
}
