package com.app.sme_health_backend.fx;

import com.app.sme_health_backend.accounting.entity.AccountMovement;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialTransaction;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.accounting.repository.AccountMovementRepository;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialTransactionRepository;
import com.app.sme_health_backend.audit.repository.SecurityAuditRepository;
import com.app.sme_health_backend.fx.dto.*;
import com.app.sme_health_backend.fx.entity.FxObligationRecognitionEvidence;
import com.app.sme_health_backend.fx.entity.FxObligationSettlement;
import com.app.sme_health_backend.fx.facade.FxFinancialEvidenceFacade;
import com.app.sme_health_backend.fx.model.FxSettlementStatus;
import com.app.sme_health_backend.fx.model.RealizedFxImpact;
import com.app.sme_health_backend.fx.model.RecognitionEvidenceStatus;
import com.app.sme_health_backend.fx.model.SettlementDirection;
import com.app.sme_health_backend.fx.repository.FxObligationRecognitionEvidenceRepository;
import com.app.sme_health_backend.fx.repository.FxObligationSettlementRepository;
import com.app.sme_health_backend.fx.service.CurrencyMetadataService;
import com.app.sme_health_backend.fx.service.FxAnalyticsService;
import com.app.sme_health_backend.fx.service.FxSettlementService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.obligation.*;
import com.app.sme_health_backend.obligation.aging.ObligationDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FxReconciliationServiceTest {

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
    private FxFinancialEvidenceFacade facade;

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

        facade = new FxFinancialEvidenceFacade(
                fxRecognitionEvidenceRepository,
                fxSettlementRepository,
                settlementService,
                mock(FxAnalyticsService.class)
        );

        businessId = UUID.randomUUID();
        userId = UUID.randomUUID();
        context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);

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
                    ObligationPaymentAllocation a = inv.getArgument(0);
                    a.setId(UUID.randomUUID());
                    return a;
                });
        when(fxSettlementRepository.saveAndFlush(any(FxObligationSettlement.class)))
                .thenAnswer(inv -> {
                    FxObligationSettlement s = inv.getArgument(0);
                    if (s.getId() == null) {
                        s.setId(UUID.randomUUID());
                    }
                    return s;
                });
        when(fxRecognitionEvidenceRepository.saveAndFlush(any(FxObligationRecognitionEvidence.class)))
                .thenAnswer(inv -> {
                    FxObligationRecognitionEvidence e = inv.getArgument(0);
                    if (e.getId() == null) {
                        e.setId(UUID.randomUUID());
                    }
                    return e;
                });
    }

    @Test
    @DisplayName("F11C: Record recognition evidence for USD 100 obligation @ 275.00 -> PKR 27,500.00 book value")
    void testRecordRecognitionEvidence() {
        UUID obligationId = UUID.randomUUID();
        FinancialObligation obligation = new FinancialObligation(
                businessId, UUID.randomUUID(), ObligationDirection.RECEIVABLE, ObligationSourceType.MANUAL_DECLARATION,
                "INV-100", "INV-REF", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                new BigDecimal("100.00"), "USD", "Export sale", userId
        );
        obligation.setId(obligationId);

        when(obligationRepository.findByIdAndBusinessId(obligationId, businessId)).thenReturn(Optional.of(obligation));
        when(fxRecognitionEvidenceRepository.existsByBusinessIdAndObligationId(businessId, obligationId)).thenReturn(false);

        FxRecognitionEvidenceRequest req = new FxRecognitionEvidenceRequest(
                new BigDecimal("275.00"), "PKR", LocalDate.of(2026, 11, 1),
                "SALES_INVOICE", "ORIGINAL_CONTRACT", "Initial invoice booking"
        );

        FxRecognitionEvidenceResponse res = settlementService.recordRecognitionEvidence(context, obligationId, req);

        assertThat(res).isNotNull();
        assertThat(res.nativeCurrency()).isEqualTo("USD");
        assertThat(res.nativeAmount()).isEqualByComparingTo("100.00");
        assertThat(res.recognitionRate()).isEqualByComparingTo("275.000000");
        assertThat(res.bookValue()).isEqualByComparingTo("27500.00");
        assertThat(res.functionalCurrency()).isEqualTo("PKR");
    }

    @Test
    @DisplayName("F11C: Receivable collection with Realized FX GAIN (USD 100 @ 275 book settled @ 278.50 with 150 PKR fee)")
    void testReceivableRealizedFxGain() {
        UUID obligationId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        FinancialObligation receivable = new FinancialObligation(
                businessId, UUID.randomUUID(), ObligationDirection.RECEIVABLE, ObligationSourceType.MANUAL_DECLARATION,
                "INV-100", "REF", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                new BigDecimal("100.00"), "USD", "Export sale", userId
        );
        receivable.setId(obligationId);

        FinancialAccount account = new FinancialAccount(
                businessId, "PKR Account", AccountType.BANK, "PKR", 2,
                new BigDecimal("50000.00"), LocalDate.of(2026, 1, 1), AccountStatus.ACTIVE,
                "Bank", "ACC", "Notes"
        );
        account.setId(accountId);

        FxObligationRecognitionEvidence evidence = new FxObligationRecognitionEvidence(
                businessId, obligationId, "USD", new BigDecimal("100.00"), "PKR",
                new BigDecimal("275.000000"), "BASE_TO_QUOTE", new BigDecimal("27500.00"),
                LocalDate.of(2026, 11, 1), "MANUAL_DECLARATION", "VERIFIED_AT_CREATION", "Notes", userId
        );

        when(obligationRepository.findByIdAndBusinessIdForUpdate(obligationId, businessId)).thenReturn(Optional.of(receivable));
        when(allocationRepository.sumEffectiveAllocations(businessId, obligationId)).thenReturn(BigDecimal.ZERO);
        when(accountRepository.findForUpdateByIdAndBusinessId(accountId, businessId)).thenReturn(Optional.of(account));
        when(fxRecognitionEvidenceRepository.findByBusinessIdAndObligationId(businessId, obligationId)).thenReturn(Optional.of(evidence));
        when(fxSettlementRepository.findAllByBusinessIdAndObligationIdOrderBySettlementDateDescCreatedAtDesc(businessId, obligationId))
                .thenReturn(Collections.emptyList());

        FxSettlementPostRequest request = new FxSettlementPostRequest(
                obligationId, accountId, new BigDecimal("100.00"),
                new BigDecimal("278.50"), new BigDecimal("150.00"), BigDecimal.ZERO,
                "PKR", LocalDate.of(2026, 11, 5), "Receivable settled", "MANUAL_USER_RATE"
        );

        FxSettlementResponse response = settlementService.postSettlement(context, "idemp-rec-gain", request);

        assertThat(response).isNotNull();
        assertThat(response.convertedPrincipal()).isEqualByComparingTo("27850.00");
        assertThat(response.feeAmount()).isEqualByComparingTo("150.00");
        assertThat(response.totalAccountMovement()).isEqualByComparingTo("27700.00");
        assertThat(response.allocatedBookValue()).isEqualByComparingTo("27500.00");
        assertThat(response.realizedFxDifference()).isEqualByComparingTo("350.00");
        assertThat(response.realizedFxImpact()).isEqualTo(RealizedFxImpact.GAIN);
        assertThat(response.recognitionEvidenceStatus()).isEqualTo(RecognitionEvidenceStatus.AVAILABLE);

        // Test reconciliation response
        when(fxSettlementRepository.findByIdAndBusinessId(response.id(), businessId))
                .thenAnswer(inv -> {
                    FxObligationSettlement s = new FxObligationSettlement(
                            businessId, obligationId, response.financialTransactionId(), response.allocationId(),
                            SettlementDirection.RECEIVABLE_COLLECTION, "USD", new BigDecimal("100.00"),
                            accountId, "PKR", new BigDecimal("27850.00"), new BigDecimal("150.00"), "PKR",
                            new BigDecimal("27700.00"), new BigDecimal("278.50"), "BASE_TO_QUOTE",
                            "USD", "PKR", "MANUAL_USER_RATE", Instant.now(), LocalDate.of(2026, 11, 5),
                            new BigDecimal("277.000000"), "Notes", "idemp-rec-gain", userId
                    );
                    s.setId(response.id());
                    s.setAllocatedBookValue(new BigDecimal("27500.00"));
                    s.setRealizedFxDifference(new BigDecimal("350.00"));
                    s.setRealizedFxImpact(RealizedFxImpact.GAIN);
                    s.setRecognitionEvidenceStatus(RecognitionEvidenceStatus.AVAILABLE);
                    return Optional.of(s);
                });
        when(obligationRepository.findByIdAndBusinessId(obligationId, businessId)).thenReturn(Optional.of(receivable));

        FxSettlementReconciliationResponse recon = settlementService.getSettlementReconciliation(context, response.id());
        assertThat(recon.cashReconciliationVerified()).isTrue();
        assertThat(recon.feeTreatment()).isEqualTo("FEE_DEDUCTED_FROM_CASH_CREDIT");
        assertThat(recon.realizedFxDifference()).isEqualByComparingTo("350.00");
        assertThat(recon.realizedFxImpact()).isEqualTo(RealizedFxImpact.GAIN);
        // Net economic variance: 27,700 - 27,500 = +200.00
        assertThat(recon.netEconomicVariance()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("F11C: Payable settlement with Realized FX LOSS (USD 100 @ 275 book settled @ 278.50 with 150 PKR fee)")
    void testPayableRealizedFxLoss() {
        UUID obligationId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        FinancialObligation payable = new FinancialObligation(
                businessId, UUID.randomUUID(), ObligationDirection.PAYABLE, ObligationSourceType.MANUAL_DECLARATION,
                "PO-100", "REF", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                new BigDecimal("100.00"), "USD", "Import payable", userId
        );
        payable.setId(obligationId);

        FinancialAccount account = new FinancialAccount(
                businessId, "PKR Account", AccountType.BANK, "PKR", 2,
                new BigDecimal("500000.00"), LocalDate.of(2026, 1, 1), AccountStatus.ACTIVE,
                "Bank", "ACC", "Notes"
        );
        account.setId(accountId);

        FxObligationRecognitionEvidence evidence = new FxObligationRecognitionEvidence(
                businessId, obligationId, "USD", new BigDecimal("100.00"), "PKR",
                new BigDecimal("275.000000"), "BASE_TO_QUOTE", new BigDecimal("27500.00"),
                LocalDate.of(2026, 11, 1), "MANUAL_DECLARATION", "VERIFIED_AT_CREATION", "Notes", userId
        );

        when(obligationRepository.findByIdAndBusinessIdForUpdate(obligationId, businessId)).thenReturn(Optional.of(payable));
        when(allocationRepository.sumEffectiveAllocations(businessId, obligationId)).thenReturn(BigDecimal.ZERO);
        when(accountRepository.findForUpdateByIdAndBusinessId(accountId, businessId)).thenReturn(Optional.of(account));
        when(fxRecognitionEvidenceRepository.findByBusinessIdAndObligationId(businessId, obligationId)).thenReturn(Optional.of(evidence));
        when(fxSettlementRepository.findAllByBusinessIdAndObligationIdOrderBySettlementDateDescCreatedAtDesc(businessId, obligationId))
                .thenReturn(Collections.emptyList());

        FxSettlementPostRequest request = new FxSettlementPostRequest(
                obligationId, accountId, new BigDecimal("100.00"),
                new BigDecimal("278.50"), new BigDecimal("150.00"), BigDecimal.ZERO,
                "PKR", LocalDate.of(2026, 11, 5), "Payable settled", "MANUAL_USER_RATE"
        );

        FxSettlementResponse response = settlementService.postSettlement(context, "idemp-pay-loss", request);

        assertThat(response).isNotNull();
        assertThat(response.convertedPrincipal()).isEqualByComparingTo("27850.00");
        assertThat(response.feeAmount()).isEqualByComparingTo("150.00");
        assertThat(response.totalAccountMovement()).isEqualByComparingTo("28000.00");
        assertThat(response.allocatedBookValue()).isEqualByComparingTo("27500.00");
        assertThat(response.realizedFxDifference()).isEqualByComparingTo("350.00");
        assertThat(response.realizedFxImpact()).isEqualTo(RealizedFxImpact.LOSS);
        assertThat(response.recognitionEvidenceStatus()).isEqualTo(RecognitionEvidenceStatus.AVAILABLE);

        when(fxSettlementRepository.findByIdAndBusinessId(response.id(), businessId))
                .thenAnswer(inv -> {
                    FxObligationSettlement s = new FxObligationSettlement(
                            businessId, obligationId, response.financialTransactionId(), response.allocationId(),
                            SettlementDirection.PAYABLE_SETTLEMENT, "USD", new BigDecimal("100.00"),
                            accountId, "PKR", new BigDecimal("27850.00"), new BigDecimal("150.00"), "PKR",
                            new BigDecimal("28000.00"), new BigDecimal("278.50"), "BASE_TO_QUOTE",
                            "USD", "PKR", "MANUAL_USER_RATE", Instant.now(), LocalDate.of(2026, 11, 5),
                            new BigDecimal("280.000000"), "Notes", "idemp-pay-loss", userId
                    );
                    s.setId(response.id());
                    s.setAllocatedBookValue(new BigDecimal("27500.00"));
                    s.setRealizedFxDifference(new BigDecimal("350.00"));
                    s.setRealizedFxImpact(RealizedFxImpact.LOSS);
                    s.setRecognitionEvidenceStatus(RecognitionEvidenceStatus.AVAILABLE);
                    return Optional.of(s);
                });
        when(obligationRepository.findByIdAndBusinessId(obligationId, businessId)).thenReturn(Optional.of(payable));

        FxSettlementReconciliationResponse recon = settlementService.getSettlementReconciliation(context, response.id());
        assertThat(recon.cashReconciliationVerified()).isTrue();
        assertThat(recon.feeTreatment()).isEqualTo("FEE_ADDED_TO_CASH_DEBIT");
        assertThat(recon.realizedFxDifference()).isEqualByComparingTo("350.00");
        assertThat(recon.realizedFxImpact()).isEqualTo(RealizedFxImpact.LOSS);
        // Net economic variance: 28,000 - 27,500 = +500.00
        assertThat(recon.netEconomicVariance()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("F11C: Partial settlements across 2 rates with residual allocation (USD 40 @ 278.50, USD 60 @ 280.00)")
    void testPartialSettlementsTwoRatesResidual() {
        UUID obligationId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        FinancialObligation payable = new FinancialObligation(
                businessId, UUID.randomUUID(), ObligationDirection.PAYABLE, ObligationSourceType.MANUAL_DECLARATION,
                "PO-PARTIAL", "REF", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                new BigDecimal("100.00"), "USD", "Import payable", userId
        );
        payable.setId(obligationId);

        FinancialAccount account = new FinancialAccount(
                businessId, "PKR Account", AccountType.BANK, "PKR", 2,
                new BigDecimal("500000.00"), LocalDate.of(2026, 1, 1), AccountStatus.ACTIVE,
                "Bank", "ACC", "Notes"
        );
        account.setId(accountId);

        // Recognized at 275.00 -> total book value 27,500.00
        FxObligationRecognitionEvidence evidence = new FxObligationRecognitionEvidence(
                businessId, obligationId, "USD", new BigDecimal("100.00"), "PKR",
                new BigDecimal("275.000000"), "BASE_TO_QUOTE", new BigDecimal("27500.00"),
                LocalDate.of(2026, 11, 1), "MANUAL_DECLARATION", "VERIFIED_AT_CREATION", "Notes", userId
        );

        when(obligationRepository.findByIdAndBusinessIdForUpdate(obligationId, businessId)).thenReturn(Optional.of(payable));
        when(accountRepository.findForUpdateByIdAndBusinessId(accountId, businessId)).thenReturn(Optional.of(account));
        when(fxRecognitionEvidenceRepository.findByBusinessIdAndObligationId(businessId, obligationId)).thenReturn(Optional.of(evidence));

        // First tranche: USD 40 @ 278.50 with 50 PKR fee
        when(allocationRepository.sumEffectiveAllocations(businessId, obligationId)).thenReturn(BigDecimal.ZERO);
        when(fxSettlementRepository.findAllByBusinessIdAndObligationIdOrderBySettlementDateDescCreatedAtDesc(businessId, obligationId))
                .thenReturn(Collections.emptyList());

        FxSettlementPostRequest req1 = new FxSettlementPostRequest(
                obligationId, accountId, new BigDecimal("40.00"),
                new BigDecimal("278.50"), new BigDecimal("50.00"), BigDecimal.ZERO,
                "PKR", LocalDate.of(2026, 11, 5), "Tranche 1", "MANUAL_USER_RATE"
        );
        FxSettlementResponse res1 = settlementService.postSettlement(context, "t1", req1);

        // 40 @ 275 = 11,000 book. 40 @ 278.50 = 11,140 principal. Realized loss = 140. Total movement = 11,140 + 50 = 11,190.
        assertThat(res1.allocatedBookValue()).isEqualByComparingTo("11000.00");
        assertThat(res1.convertedPrincipal()).isEqualByComparingTo("11140.00");
        assertThat(res1.realizedFxDifference()).isEqualByComparingTo("140.00");
        assertThat(res1.realizedFxImpact()).isEqualTo(RealizedFxImpact.LOSS);
        assertThat(res1.totalAccountMovement()).isEqualByComparingTo("11190.00");

        // Second tranche: USD 60 @ 280.00 with 100 PKR fee (final settlement)
        when(allocationRepository.sumEffectiveAllocations(businessId, obligationId)).thenReturn(new BigDecimal("40.00"));
        FxObligationSettlement priorSettle = new FxObligationSettlement();
        priorSettle.setStatus(FxSettlementStatus.SETTLED);
        priorSettle.setAllocatedBookValue(new BigDecimal("11000.00"));
        when(fxSettlementRepository.findAllByBusinessIdAndObligationIdOrderBySettlementDateDescCreatedAtDesc(businessId, obligationId))
                .thenReturn(List.of(priorSettle));

        FxSettlementPostRequest req2 = new FxSettlementPostRequest(
                obligationId, accountId, new BigDecimal("60.00"),
                new BigDecimal("280.00"), new BigDecimal("100.00"), BigDecimal.ZERO,
                "PKR", LocalDate.of(2026, 11, 6), "Tranche 2", "MANUAL_USER_RATE"
        );
        FxSettlementResponse res2 = settlementService.postSettlement(context, "t2", req2);

        // 27,500 - 11,000 = 16,500 residual book allocated. 60 @ 280 = 16,800 principal. Realized loss = 300. Total movement = 16,800 + 100 = 16,900.
        assertThat(res2.allocatedBookValue()).isEqualByComparingTo("16500.00");
        assertThat(res2.convertedPrincipal()).isEqualByComparingTo("16800.00");
        assertThat(res2.realizedFxDifference()).isEqualByComparingTo("300.00");
        assertThat(res2.realizedFxImpact()).isEqualTo(RealizedFxImpact.LOSS);
        assertThat(res2.totalAccountMovement()).isEqualByComparingTo("16900.00");

        // Sum of allocated book = 11,000 + 16,500 = 27,500 exactly (zero residual drift!)
        assertThat(res1.allocatedBookValue().add(res2.allocatedBookValue())).isEqualByComparingTo("27500.00");
        // Total cash movement = 11,190 + 16,900 = 28,090.00
        assertThat(res1.totalAccountMovement().add(res2.totalAccountMovement())).isEqualByComparingTo("28090.00");
        // Total realized loss = 140 + 300 = 440.00
        assertThat(res1.realizedFxDifference().add(res2.realizedFxDifference())).isEqualByComparingTo("440.00");
    }

    @Test
    @DisplayName("F11C: Missing recognition evidence fails closed with UNAVAILABLE (never zero)")
    void testMissingRecognitionEvidenceFailsClosed() {
        UUID obligationId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        FinancialObligation obligation = new FinancialObligation(
                businessId, UUID.randomUUID(), ObligationDirection.RECEIVABLE, ObligationSourceType.MANUAL_DECLARATION,
                "INV-NO-REC", "REF", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                new BigDecimal("100.00"), "USD", "Export sale", userId
        );
        obligation.setId(obligationId);

        FinancialAccount account = new FinancialAccount(
                businessId, "PKR Account", AccountType.BANK, "PKR", 2,
                new BigDecimal("50000.00"), LocalDate.of(2026, 1, 1), AccountStatus.ACTIVE,
                "Bank", "ACC", "Notes"
        );
        account.setId(accountId);

        when(obligationRepository.findByIdAndBusinessIdForUpdate(obligationId, businessId)).thenReturn(Optional.of(obligation));
        when(allocationRepository.sumEffectiveAllocations(businessId, obligationId)).thenReturn(BigDecimal.ZERO);
        when(accountRepository.findForUpdateByIdAndBusinessId(accountId, businessId)).thenReturn(Optional.of(account));
        // No recognition evidence found
        when(fxRecognitionEvidenceRepository.findByBusinessIdAndObligationId(businessId, obligationId)).thenReturn(Optional.empty());

        FxSettlementPostRequest request = new FxSettlementPostRequest(
                obligationId, accountId, new BigDecimal("100.00"),
                new BigDecimal("278.50"), BigDecimal.ZERO, BigDecimal.ZERO,
                "PKR", LocalDate.of(2026, 11, 5), "No evidence", "MANUAL_USER_RATE"
        );

        FxSettlementResponse response = settlementService.postSettlement(context, "idemp-no-evidence", request);

        assertThat(response.recognitionEvidenceStatus()).isEqualTo(RecognitionEvidenceStatus.UNAVAILABLE);
        assertThat(response.missingEvidenceReason()).isEqualTo("ORIGINAL_RECOGNITION_EVIDENCE_MISSING");
        assertThat(response.allocatedBookValue()).isNull();
        assertThat(response.realizedFxDifference()).isNull();
        assertThat(response.realizedFxImpact()).isNull();
    }

    @Test
    @DisplayName("F11C: Period summary aggregates gains, losses, fees, and excludes reversed settlements")
    void testPeriodSummaryAndReversal() {
        FxObligationSettlement activeGain = new FxObligationSettlement();
        activeGain.setStatus(FxSettlementStatus.SETTLED);
        activeGain.setSettlementDate(LocalDate.of(2026, 11, 5));
        activeGain.setAccountCurrency("PKR");
        activeGain.setConvertedPrincipal(new BigDecimal("27850.00"));
        activeGain.setRealizedFxDifference(new BigDecimal("350.00"));
        activeGain.setRealizedFxImpact(RealizedFxImpact.GAIN);
        activeGain.setFeeAmount(new BigDecimal("150.00"));
        activeGain.setTotalAccountMovement(new BigDecimal("27700.00"));
        activeGain.setSettlementDirection(SettlementDirection.RECEIVABLE_COLLECTION);
        activeGain.setRecognitionEvidenceStatus(RecognitionEvidenceStatus.AVAILABLE);

        FxObligationSettlement activeLoss = new FxObligationSettlement();
        activeLoss.setStatus(FxSettlementStatus.SETTLED);
        activeLoss.setSettlementDate(LocalDate.of(2026, 11, 6));
        activeLoss.setAccountCurrency("PKR");
        activeLoss.setConvertedPrincipal(new BigDecimal("13925.00"));
        activeLoss.setRealizedFxDifference(new BigDecimal("100.00"));
        activeLoss.setRealizedFxImpact(RealizedFxImpact.LOSS);
        activeLoss.setFeeAmount(new BigDecimal("50.00"));
        activeLoss.setTotalAccountMovement(new BigDecimal("13975.00"));
        activeLoss.setSettlementDirection(SettlementDirection.PAYABLE_SETTLEMENT);
        activeLoss.setRecognitionEvidenceStatus(RecognitionEvidenceStatus.AVAILABLE);

        FxObligationSettlement reversedSettlement = new FxObligationSettlement();
        reversedSettlement.setStatus(FxSettlementStatus.REVERSED);
        reversedSettlement.setSettlementDate(LocalDate.of(2026, 11, 4));
        reversedSettlement.setAccountCurrency("PKR");
        reversedSettlement.setConvertedPrincipal(new BigDecimal("50000.00"));
        reversedSettlement.setRealizedFxDifference(new BigDecimal("1000.00"));
        reversedSettlement.setRealizedFxImpact(RealizedFxImpact.GAIN);
        reversedSettlement.setFeeAmount(new BigDecimal("200.00"));
        reversedSettlement.setTotalAccountMovement(new BigDecimal("50200.00"));
        reversedSettlement.setSettlementDirection(SettlementDirection.PAYABLE_SETTLEMENT);

        when(fxSettlementRepository.findAllByBusinessIdOrderBySettlementDateDescCreatedAtDesc(businessId))
                .thenReturn(List.of(activeGain, activeLoss, reversedSettlement));

        FxPeriodSummaryResponse summary = settlementService.getPeriodSummary(context, null, null);

        assertThat(summary.totalSettlementsCount()).isEqualTo(3);
        assertThat(summary.activeSettlementsCount()).isEqualTo(2);
        assertThat(summary.reversedSettlementsCount()).isEqualTo(1);
        assertThat(summary.totalRealizedGain()).isEqualByComparingTo("350.00");
        assertThat(summary.totalRealizedLoss()).isEqualByComparingTo("100.00");
        assertThat(summary.netRealizedFx()).isEqualByComparingTo("250.00");
        assertThat(summary.totalBankFees()).isEqualByComparingTo("200.00");
    }
}
