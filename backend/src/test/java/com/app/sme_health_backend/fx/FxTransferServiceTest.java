package com.app.sme_health_backend.fx;

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
import com.app.sme_health_backend.audit.repository.SecurityAuditRepository;
import com.app.sme_health_backend.fx.dto.*;
import com.app.sme_health_backend.fx.entity.FxInternalTransfer;
import com.app.sme_health_backend.fx.exception.FxValidationException;
import com.app.sme_health_backend.fx.model.FxSettlementStatus;
import com.app.sme_health_backend.fx.model.FxTransferFeePlacement;
import com.app.sme_health_backend.fx.repository.FxInternalTransferRepository;
import com.app.sme_health_backend.fx.service.CurrencyMetadataService;
import com.app.sme_health_backend.fx.service.FxTransferService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FxTransferServiceTest {

    @Mock
    private FinancialAccountRepository accountRepository;
    @Mock
    private FinancialTransactionRepository transactionRepository;
    @Mock
    private AccountMovementRepository movementRepository;
    @Mock
    private FxInternalTransferRepository fxTransferRepository;
    @Mock
    private FinancialIdempotencyRepository idempotencyRepository;
    @Mock
    private SecurityAuditRepository securityAuditRepository;

    private CurrencyMetadataService currencyMetadataService;
    private ObjectMapper objectMapper;
    private FxTransferService fxTransferService;

    private UUID businessId;
    private UUID userId;
    private BusinessAccessContext context;

    private FinancialAccount usdAccount;
    private FinancialAccount pkrAccount;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        userId = UUID.randomUUID();
        context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);

        currencyMetadataService = new CurrencyMetadataService();
        objectMapper = new ObjectMapper();

        fxTransferService = new FxTransferService(
                accountRepository,
                transactionRepository,
                movementRepository,
                fxTransferRepository,
                idempotencyRepository,
                securityAuditRepository,
                currencyMetadataService,
                objectMapper
        );

        usdAccount = new FinancialAccount();
        usdAccount.setId(UUID.randomUUID());
        usdAccount.setBusinessId(businessId);
        usdAccount.setAccountName("USD Operating Account");
        usdAccount.setAccountType(AccountType.BANK);
        usdAccount.setCurrency("USD");
        usdAccount.setCurrencyDecimals(2);
        usdAccount.setOpeningBalance(new BigDecimal("1000.00"));
        usdAccount.setOpeningDate(LocalDate.of(2026, 1, 1));
        usdAccount.setStatus(AccountStatus.ACTIVE);

        pkrAccount = new FinancialAccount();
        pkrAccount.setId(UUID.randomUUID());
        pkrAccount.setBusinessId(businessId);
        pkrAccount.setAccountName("PKR Operating Account");
        pkrAccount.setAccountType(AccountType.BANK);
        pkrAccount.setCurrency("PKR");
        pkrAccount.setCurrencyDecimals(2);
        pkrAccount.setOpeningBalance(new BigDecimal("50000.00"));
        pkrAccount.setOpeningDate(LocalDate.of(2026, 1, 1));
        pkrAccount.setStatus(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("F11E Preview: Case A - USD 100 @ 278.50 with PKR 150 destination-deducted fee")
    void testPreviewDestinationDeductedFee() {
        when(accountRepository.findByIdAndBusinessId(usdAccount.getId(), businessId)).thenReturn(Optional.of(usdAccount));
        when(accountRepository.findByIdAndBusinessId(pkrAccount.getId(), businessId)).thenReturn(Optional.of(pkrAccount));

        FxTransferPreviewRequest request = new FxTransferPreviewRequest(
                usdAccount.getId(),
                pkrAccount.getId(),
                new BigDecimal("100.00"),
                new BigDecimal("278.50"),
                new BigDecimal("150.00"),
                "DESTINATION_DEDUCTED"
        );

        FxTransferPreviewResponse response = fxTransferService.previewTransfer(context, request);

        assertThat(response.sourceCurrency()).isEqualTo("USD");
        assertThat(response.destinationCurrency()).isEqualTo("PKR");
        assertThat(response.sourceAmount()).isEqualByComparingTo("100.00");
        assertThat(response.convertedDestinationPrincipal()).isEqualByComparingTo("27850.00");
        assertThat(response.feeAmount()).isEqualByComparingTo("150.00");
        assertThat(response.feeCurrency()).isEqualTo("PKR");
        assertThat(response.feePlacement()).isEqualTo("DESTINATION_DEDUCTED");
        assertThat(response.netDestinationAmount()).isEqualByComparingTo("27700.00");
        assertThat(response.totalSourceDebit()).isEqualByComparingTo("100.00");
        assertThat(response.effectiveRate()).isEqualByComparingTo("277.000000");
    }

    @Test
    @DisplayName("F11E Preview: Case B - USD 100 @ 278.50 with USD 2 source-charged fee")
    void testPreviewSourceChargedFee() {
        when(accountRepository.findByIdAndBusinessId(usdAccount.getId(), businessId)).thenReturn(Optional.of(usdAccount));
        when(accountRepository.findByIdAndBusinessId(pkrAccount.getId(), businessId)).thenReturn(Optional.of(pkrAccount));

        FxTransferPreviewRequest request = new FxTransferPreviewRequest(
                usdAccount.getId(),
                pkrAccount.getId(),
                new BigDecimal("100.00"),
                new BigDecimal("278.50"),
                new BigDecimal("2.00"),
                "SOURCE_CHARGED"
        );

        FxTransferPreviewResponse response = fxTransferService.previewTransfer(context, request);

        assertThat(response.sourceAmount()).isEqualByComparingTo("100.00");
        assertThat(response.convertedDestinationPrincipal()).isEqualByComparingTo("27850.00");
        assertThat(response.feeAmount()).isEqualByComparingTo("2.00");
        assertThat(response.feeCurrency()).isEqualTo("USD");
        assertThat(response.feePlacement()).isEqualTo("SOURCE_CHARGED");
        assertThat(response.netDestinationAmount()).isEqualByComparingTo("27850.00");
        assertThat(response.totalSourceDebit()).isEqualByComparingTo("102.00");
        // Effective rate = 27850.00 / 102.00 = 273.039216
        assertThat(response.effectiveRate()).isEqualByComparingTo("273.039216");
    }

    @Test
    @DisplayName("F11E Preview Validation: Rejects same-currency accounts")
    void testPreviewRejectsSameCurrency() {
        FinancialAccount anotherPkr = new FinancialAccount();
        anotherPkr.setId(UUID.randomUUID());
        anotherPkr.setBusinessId(businessId);
        anotherPkr.setCurrency("PKR");
        anotherPkr.setStatus(AccountStatus.ACTIVE);

        when(accountRepository.findByIdAndBusinessId(pkrAccount.getId(), businessId)).thenReturn(Optional.of(pkrAccount));
        when(accountRepository.findByIdAndBusinessId(anotherPkr.getId(), businessId)).thenReturn(Optional.of(anotherPkr));

        FxTransferPreviewRequest request = new FxTransferPreviewRequest(
                pkrAccount.getId(),
                anotherPkr.getId(),
                new BigDecimal("1000.00"),
                new BigDecimal("1.00"),
                BigDecimal.ZERO,
                "DESTINATION_DEDUCTED"
        );

        assertThatThrownBy(() -> fxTransferService.previewTransfer(context, request))
                .isInstanceOf(FxValidationException.class)
                .hasMessageContaining("must have different currencies");
    }

    @Test
    @DisplayName("F11E Post Transfer: Atomic dual-movement ledger posting (Case A)")
    void testPostTransferCaseA() {
        when(accountRepository.findForUpdateByIdAndBusinessId(any(), eq(businessId)))
                .thenAnswer(invocation -> {
                    UUID id = invocation.getArgument(0);
                    if (id.equals(usdAccount.getId())) return Optional.of(usdAccount);
                    if (id.equals(pkrAccount.getId())) return Optional.of(pkrAccount);
                    return Optional.empty();
                });

        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(any())).thenReturn(Collections.emptyList());

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

        when(fxTransferRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            FxInternalTransfer t = invocation.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        FxTransferPostRequest request = new FxTransferPostRequest(
                usdAccount.getId(),
                pkrAccount.getId(),
                "100.00",
                "278.50",
                "150.00",
                "DESTINATION_DEDUCTED",
                "2026-11-08",
                "Inter-account foreign currency transfer"
        );

        FxTransferResponse response = fxTransferService.postTransfer(context, "idem-test-1", request);

        assertThat(response.sourceCurrency()).isEqualTo("USD");
        assertThat(response.destinationCurrency()).isEqualTo("PKR");
        assertThat(response.sourceAmount()).isEqualByComparingTo("100.00");
        assertThat(response.convertedDestinationPrincipal()).isEqualByComparingTo("27850.00");
        assertThat(response.feeAmount()).isEqualByComparingTo("150.00");
        assertThat(response.netDestinationAmount()).isEqualByComparingTo("27700.00");
        assertThat(response.totalSourceAmount()).isEqualByComparingTo("100.00");
        assertThat(response.status()).isEqualTo("SETTLED");

        // Verify Movements
        ArgumentCaptor<AccountMovement> movementCaptor = ArgumentCaptor.forClass(AccountMovement.class);
        verify(movementRepository, times(2)).saveAndFlush(movementCaptor.capture());
        List<AccountMovement> movements = movementCaptor.getAllValues();

        AccountMovement sourceMov = movements.get(0);
        assertThat(sourceMov.getAccountId()).isEqualTo(usdAccount.getId());
        assertThat(sourceMov.getMovementDirection()).isEqualTo(MovementDirection.DECREASE);
        assertThat(sourceMov.getAmount()).isEqualByComparingTo("100.00");
        assertThat(sourceMov.getCurrency()).isEqualTo("USD");

        AccountMovement destMov = movements.get(1);
        assertThat(destMov.getAccountId()).isEqualTo(pkrAccount.getId());
        assertThat(destMov.getMovementDirection()).isEqualTo(MovementDirection.INCREASE);
        assertThat(destMov.getAmount()).isEqualByComparingTo("27700.00");
        assertThat(destMov.getCurrency()).isEqualTo("PKR");

        // Verify FxInternalTransfer entity
        ArgumentCaptor<FxInternalTransfer> transferCaptor = ArgumentCaptor.forClass(FxInternalTransfer.class);
        verify(fxTransferRepository).saveAndFlush(transferCaptor.capture());
        FxInternalTransfer saved = transferCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(FxSettlementStatus.SETTLED);
        assertThat(saved.getExchangeRate()).isEqualByComparingTo("278.50");
        assertThat(saved.getFeePlacement()).isEqualTo(FxTransferFeePlacement.DESTINATION_DEDUCTED);
    }

    @Test
    @DisplayName("F11E Idempotency: Duplicate key with altered payload throws 409 Conflict")
    void testIdempotencyConflict() {
        when(accountRepository.findForUpdateByIdAndBusinessId(any(), eq(businessId)))
                .thenAnswer(invocation -> {
                    UUID id = invocation.getArgument(0);
                    if (id.equals(usdAccount.getId())) return Optional.of(usdAccount);
                    if (id.equals(pkrAccount.getId())) return Optional.of(pkrAccount);
                    return Optional.empty();
                });

        FinancialIdempotencyRecord existingRecord = new FinancialIdempotencyRecord(
                businessId,
                FxTransferService.OPERATION_FX_TRANSFER,
                "idem-conflict-key",
                "original-hash-differs",
                UUID.randomUUID(),
                201,
                "{}"
        );
        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(
                businessId, FxTransferService.OPERATION_FX_TRANSFER, "idem-conflict-key"
        )).thenReturn(Optional.of(existingRecord));

        FxTransferPostRequest request = new FxTransferPostRequest(
                usdAccount.getId(),
                pkrAccount.getId(),
                "100.00",
                "278.50",
                "150.00",
                "DESTINATION_DEDUCTED",
                "2026-11-08",
                "Test notes"
        );

        assertThatThrownBy(() -> fxTransferService.postTransfer(context, "idem-conflict-key", request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already been used with a different request payload");
    }

    @Test
    @DisplayName("F11E Reversal: Reverses posted transfer, restores balances, creates compensating movements")
    void testReverseTransfer() {
        UUID transferId = UUID.randomUUID();
        UUID origTxId = UUID.randomUUID();
        FxInternalTransfer transfer = new FxInternalTransfer();
        transfer.setId(transferId);
        transfer.setBusinessId(businessId);
        transfer.setSourceAccountId(usdAccount.getId());
        transfer.setDestinationAccountId(pkrAccount.getId());
        transfer.setSourceCurrency("USD");
        transfer.setDestinationCurrency("PKR");
        transfer.setSourceAmount(new BigDecimal("100.00"));
        transfer.setExchangeRate(new BigDecimal("278.50"));
        transfer.setConvertedDestinationPrincipal(new BigDecimal("27850.00"));
        transfer.setFeeAmount(new BigDecimal("150.00"));
        transfer.setFeeCurrency("PKR");
        transfer.setFeePlacement(FxTransferFeePlacement.DESTINATION_DEDUCTED);
        transfer.setNetDestinationAmount(new BigDecimal("27700.00"));
        transfer.setTotalSourceAmount(new BigDecimal("100.00"));
        transfer.setFinancialTransactionId(origTxId);
        transfer.setTransferDate(LocalDate.of(2026, 11, 8));
        transfer.setStatus(FxSettlementStatus.SETTLED);

        when(fxTransferRepository.findByIdAndBusinessId(transferId, businessId)).thenReturn(Optional.of(transfer));
        when(accountRepository.findForUpdateByIdAndBusinessId(any(), eq(businessId)))
                .thenAnswer(invocation -> {
                    UUID id = invocation.getArgument(0);
                    if (id.equals(usdAccount.getId())) return Optional.of(usdAccount);
                    if (id.equals(pkrAccount.getId())) return Optional.of(pkrAccount);
                    return Optional.empty();
                });
        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(any())).thenReturn(Collections.emptyList());

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

        when(fxTransferRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        FxTransferReverseRequest revReq = new FxTransferReverseRequest("Customer requested reversal");
        FxTransferResponse response = fxTransferService.reverseTransfer(context, transferId, revReq);

        assertThat(response.status()).isEqualTo("REVERSED");
        assertThat(response.reversalReason()).isEqualTo("Customer requested reversal");

        // Verify Compensating Movements
        ArgumentCaptor<AccountMovement> movementCaptor = ArgumentCaptor.forClass(AccountMovement.class);
        verify(movementRepository, times(2)).saveAndFlush(movementCaptor.capture());
        List<AccountMovement> movements = movementCaptor.getAllValues();

        // Source restored: INCREASE 100.00 USD
        AccountMovement revSourceMov = movements.get(0);
        assertThat(revSourceMov.getAccountId()).isEqualTo(usdAccount.getId());
        assertThat(revSourceMov.getMovementDirection()).isEqualTo(MovementDirection.INCREASE);
        assertThat(revSourceMov.getAmount()).isEqualByComparingTo("100.00");
        assertThat(revSourceMov.getCurrency()).isEqualTo("USD");

        // Destination debited back: DECREASE 27,700.00 PKR
        AccountMovement revDestMov = movements.get(1);
        assertThat(revDestMov.getAccountId()).isEqualTo(pkrAccount.getId());
        assertThat(revDestMov.getMovementDirection()).isEqualTo(MovementDirection.DECREASE);
        assertThat(revDestMov.getAmount()).isEqualByComparingTo("27700.00");
        assertThat(revDestMov.getCurrency()).isEqualTo("PKR");

        // Verify second reversal is rejected
        assertThatThrownBy(() -> fxTransferService.reverseTransfer(context, transferId, revReq))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already been reversed");
    }
}
