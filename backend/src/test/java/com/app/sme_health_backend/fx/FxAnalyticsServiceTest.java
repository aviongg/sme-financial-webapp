package com.app.sme_health_backend.fx;

import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.counterparty.CounterpartyType;
import com.app.sme_health_backend.fx.dto.*;
import com.app.sme_health_backend.fx.entity.FxObligationRecognitionEvidence;
import com.app.sme_health_backend.fx.entity.FxObligationSettlement;
import com.app.sme_health_backend.fx.model.*;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FxAnalyticsServiceTest {

    private FxObligationSettlementRepository settlementRepository;
    private FxObligationRecognitionEvidenceRepository recognitionEvidenceRepository;
    private FinancialObligationRepository obligationRepository;
    private ObligationPaymentAllocationRepository allocationRepository;
    private CounterpartyRepository counterpartyRepository;
    private CurrencyMetadataService currencyMetadataService;
    private FxSettlementService settlementService;
    private FxAnalyticsService analyticsService;

    private UUID businessId;
    private UUID userId;
    private BusinessAccessContext context;

    @BeforeEach
    void setUp() {
        settlementRepository = mock(FxObligationSettlementRepository.class);
        recognitionEvidenceRepository = mock(FxObligationRecognitionEvidenceRepository.class);
        obligationRepository = mock(FinancialObligationRepository.class);
        allocationRepository = mock(ObligationPaymentAllocationRepository.class);
        counterpartyRepository = mock(CounterpartyRepository.class);
        currencyMetadataService = new CurrencyMetadataService();
        settlementService = mock(FxSettlementService.class);

        analyticsService = new FxAnalyticsService(
                settlementRepository,
                recognitionEvidenceRepository,
                obligationRepository,
                allocationRepository,
                counterpartyRepository,
                currencyMetadataService,
                settlementService
        );

        businessId = UUID.randomUUID();
        userId = UUID.randomUUID();
        context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
    }

    @Test
    @DisplayName("F11D: Currency Exposure separates native receivables, payables, and net exposure without cross-currency summing")
    void testCurrencyExposureGrouping() {
        UUID usdRecId = UUID.randomUUID();
        UUID usdPayId = UUID.randomUUID();
        UUID kwdPayId = UUID.randomUUID();

        FinancialObligation usdRec = new FinancialObligation(
                businessId, UUID.randomUUID(), ObligationDirection.RECEIVABLE, ObligationSourceType.MANUAL_DECLARATION,
                "REF-USD-REC", "INV-1", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                new BigDecimal("500.00"), "USD", "Export contract", userId
        );
        usdRec.setId(usdRecId);

        FinancialObligation usdPay = new FinancialObligation(
                businessId, UUID.randomUUID(), ObligationDirection.PAYABLE, ObligationSourceType.MANUAL_DECLARATION,
                "REF-USD-PAY", "PO-1", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                new BigDecimal("200.00"), "USD", "Import shipment", userId
        );
        usdPay.setId(usdPayId);

        FinancialObligation kwdPay = new FinancialObligation(
                businessId, UUID.randomUUID(), ObligationDirection.PAYABLE, ObligationSourceType.MANUAL_DECLARATION,
                "REF-KWD-PAY", "PO-KWD", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                new BigDecimal("75.125"), "KWD", "Kuwait supplier", userId
        );
        kwdPay.setId(kwdPayId);

        when(obligationRepository.findByBusinessId(businessId))
                .thenReturn(List.of(usdRec, usdPay, kwdPay));
        when(allocationRepository.sumEffectiveAllocationsByObligationIds(eq(businessId), any()))
                .thenReturn(Collections.emptyList());
        when(settlementRepository.findAllByBusinessIdOrderBySettlementDateDescCreatedAtDesc(businessId))
                .thenReturn(Collections.emptyList());

        List<CurrencyExposureSummary> exposures = analyticsService.getCurrencyExposures(context);

        assertThat(exposures).hasSize(2);

        CurrencyExposureSummary usdExposure = exposures.stream().filter(e -> e.currency().equals("USD")).findFirst().orElseThrow();
        assertThat(usdExposure.outstandingReceivables()).isEqualByComparingTo("500.00");
        assertThat(usdExposure.outstandingPayables()).isEqualByComparingTo("200.00");
        assertThat(usdExposure.netExposure()).isEqualByComparingTo("300.00");
        assertThat(usdExposure.minorUnits()).isEqualTo(2);

        CurrencyExposureSummary kwdExposure = exposures.stream().filter(e -> e.currency().equals("KWD")).findFirst().orElseThrow();
        assertThat(kwdExposure.outstandingReceivables()).isEqualByComparingTo("0.000");
        assertThat(kwdExposure.outstandingPayables()).isEqualByComparingTo("75.125");
        assertThat(kwdExposure.netExposure()).isEqualByComparingTo("-75.125");
        assertThat(kwdExposure.minorUnits()).isEqualTo(3);
    }

    @Test
    @DisplayName("F11D: Section 7 Mandatory Reporting Fixture (Receivable gain 350, Payable loss 350, Combined net FX 0, Fees 300)")
    void testMandatoryReportingFixture() {
        UUID obRecId = UUID.randomUUID();
        UUID obPayId = UUID.randomUUID();
        UUID cpId = UUID.randomUUID();

        Counterparty counterparty = new Counterparty(
                businessId, "Global Trade Corp", CounterpartyType.BOTH,
                "trade@global.test", "+923001234567", "Karachi", "TAX-01", "Notes"
        );
        counterparty.setId(cpId);

        FinancialObligation obRec = new FinancialObligation(
                businessId, cpId, ObligationDirection.RECEIVABLE, ObligationSourceType.MANUAL_DECLARATION,
                "INV-100", "REF-REC", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                new BigDecimal("100.00"), "USD", "Receivable", userId
        );
        obRec.setId(obRecId);

        FinancialObligation obPay = new FinancialObligation(
                businessId, cpId, ObligationDirection.PAYABLE, ObligationSourceType.MANUAL_DECLARATION,
                "PO-100", "REF-PAY", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                new BigDecimal("100.00"), "USD", "Payable", userId
        );
        obPay.setId(obPayId);

        // Recognition evidence @ 275.00
        FxObligationRecognitionEvidence recEvRec = new FxObligationRecognitionEvidence(
                businessId, obRecId, "USD", new BigDecimal("100.00"), "PKR",
                new BigDecimal("275.000000"), "BASE_TO_QUOTE", new BigDecimal("27500.00"),
                LocalDate.of(2026, 11, 1), "CONTRACT", "VERIFIED_AT_CREATION", "Notes", userId
        );

        FxObligationRecognitionEvidence recEvPay = new FxObligationRecognitionEvidence(
                businessId, obPayId, "USD", new BigDecimal("100.00"), "PKR",
                new BigDecimal("275.000000"), "BASE_TO_QUOTE", new BigDecimal("27500.00"),
                LocalDate.of(2026, 11, 1), "ORDER", "VERIFIED_AT_CREATION", "Notes", userId
        );

        // Settlement 1: Receivable USD 100 settled @ 278.50 with 150 PKR fee -> 350 GAIN, 27,700 credited
        FxObligationSettlement sRec = new FxObligationSettlement();
        sRec.setId(UUID.randomUUID());
        sRec.setSettlementDate(LocalDate.of(2026, 11, 5));
        sRec.setStatus(FxSettlementStatus.SETTLED);
        sRec.setSettlementDirection(SettlementDirection.RECEIVABLE_COLLECTION);
        sRec.setAccountCurrency("PKR");
        sRec.setConvertedPrincipal(new BigDecimal("27850.00"));
        sRec.setFeeAmount(new BigDecimal("150.00"));
        sRec.setTotalAccountMovement(new BigDecimal("27700.00"));
        sRec.setAllocatedBookValue(new BigDecimal("27500.00"));
        sRec.setRealizedFxDifference(new BigDecimal("350.00"));
        sRec.setRealizedFxImpact(RealizedFxImpact.GAIN);
        sRec.setRecognitionEvidenceStatus(RecognitionEvidenceStatus.AVAILABLE);

        // Settlement 2: Payable USD 100 settled @ 278.50 with 150 PKR fee -> 350 LOSS, 28,000 debited
        FxObligationSettlement sPay = new FxObligationSettlement();
        sPay.setId(UUID.randomUUID());
        sPay.setSettlementDate(LocalDate.of(2026, 11, 5));
        sPay.setStatus(FxSettlementStatus.SETTLED);
        sPay.setSettlementDirection(SettlementDirection.PAYABLE_SETTLEMENT);
        sPay.setAccountCurrency("PKR");
        sPay.setConvertedPrincipal(new BigDecimal("27850.00"));
        sPay.setFeeAmount(new BigDecimal("150.00"));
        sPay.setTotalAccountMovement(new BigDecimal("28000.00"));
        sPay.setAllocatedBookValue(new BigDecimal("27500.00"));
        sPay.setRealizedFxDifference(new BigDecimal("350.00"));
        sPay.setRealizedFxImpact(RealizedFxImpact.LOSS);
        sPay.setRecognitionEvidenceStatus(RecognitionEvidenceStatus.AVAILABLE);

        // Settlement 3: Missing evidence foreign settlement -> UNAVAILABLE, no fabricated gain/loss
        FxObligationSettlement sNoEv = new FxObligationSettlement();
        sNoEv.setId(UUID.randomUUID());
        sNoEv.setSettlementDate(LocalDate.of(2026, 11, 6));
        sNoEv.setStatus(FxSettlementStatus.SETTLED);
        sNoEv.setSettlementDirection(SettlementDirection.PAYABLE_SETTLEMENT);
        sNoEv.setAccountCurrency("PKR");
        sNoEv.setConvertedPrincipal(new BigDecimal("13925.00"));
        sNoEv.setFeeAmount(new BigDecimal("50.00"));
        sNoEv.setTotalAccountMovement(new BigDecimal("13975.00"));
        sNoEv.setRecognitionEvidenceStatus(RecognitionEvidenceStatus.UNAVAILABLE);
        sNoEv.setMissingEvidenceReason("ORIGINAL_RECOGNITION_EVIDENCE_MISSING");

        when(settlementRepository.findAllByBusinessIdOrderBySettlementDateDescCreatedAtDesc(businessId))
                .thenReturn(List.of(sRec, sPay, sNoEv));

        List<FxPeriodTrendSummary> trends = analyticsService.getPeriodicTrends(context);
        assertThat(trends).hasSize(1);
        FxPeriodTrendSummary novTrend = trends.get(0);
        assertThat(novTrend.period()).isEqualTo("2026-11");
        assertThat(novTrend.settlementCount()).isEqualTo(3);
        // Gains = 350, Losses = 350, Net FX = 0
        assertThat(novTrend.totalRealizedGain()).isEqualByComparingTo("350.00");
        assertThat(novTrend.totalRealizedLoss()).isEqualByComparingTo("350.00");
        assertThat(novTrend.netRealizedFx()).isEqualByComparingTo("0.00");
        // Total Bank fees = 150 + 150 + 50 = 350.00
        assertThat(novTrend.totalBankFees()).isEqualByComparingTo("350.00");
    }

    @Test
    @DisplayName("F11D: CSV Export protects against formula injection and escapes special characters")
    void testCsvExportFormulaInjectionProtection() {
        UUID sId = UUID.randomUUID();
        UUID obId = UUID.randomUUID();
        UUID cpId = UUID.randomUUID();

        Counterparty counterparty = new Counterparty(
                businessId, "=cmd|'/C calc'!A0", CounterpartyType.SUPPLIER, // malicious formula
                "hacker@danger.test", "+923000000000", "Unknown", "TAX", "Notes"
        );
        counterparty.setId(cpId);

        FinancialObligation ob = new FinancialObligation(
                businessId, cpId, ObligationDirection.PAYABLE, ObligationSourceType.MANUAL_DECLARATION,
                "SRC-01", "+@SUM(1,2)", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), // malicious ref
                new BigDecimal("100.00"), "USD", "Notes", userId
        );
        ob.setId(obId);

        FxObligationSettlement s = new FxObligationSettlement();
        s.setId(sId);
        s.setObligationId(ob.getId());
        s.setObligationCurrency("USD");
        s.setObligationAmount(new BigDecimal("100.00"));
        s.setAccountCurrency("PKR");
        s.setExchangeRate(new BigDecimal("278.50"));
        s.setSettlementDate(LocalDate.of(2026, 11, 5));
        s.setStatus(FxSettlementStatus.SETTLED);
        s.setSettlementDirection(SettlementDirection.PAYABLE_SETTLEMENT);
        s.setConvertedPrincipal(new BigDecimal("27850.00"));
        s.setFeeAmount(new BigDecimal("150.00"));
        s.setTotalAccountMovement(new BigDecimal("28000.00"));
        s.setAllocatedBookValue(new BigDecimal("27500.00"));
        s.setRealizedFxDifference(new BigDecimal("350.00"));
        s.setRealizedFxImpact(RealizedFxImpact.LOSS);
        s.setRecognitionEvidenceStatus(RecognitionEvidenceStatus.AVAILABLE);

        when(settlementRepository.findAllByBusinessIdOrderBySettlementDateDescCreatedAtDesc(businessId))
                .thenReturn(List.of(s));
        when(obligationRepository.findByBusinessId(businessId))
                .thenReturn(List.of(ob));
        when(counterpartyRepository.findByBusinessId(businessId))
                .thenReturn(List.of(counterparty));
        when(recognitionEvidenceRepository.findAll())
                .thenReturn(Collections.emptyList());

        String csv = analyticsService.exportSettlementsCsv(context, new FxSettlementReportFilter(
                null, null, null, null, null, null, null, 50, 0
        ));

        assertThat(csv).isNotNull();
        // Malicious name starting with '=' must be prepended with apostrophe '\''
        assertThat(csv).contains("'=cmd|'/C calc'!A0");
        // Malicious ref starting with '+' must be prepended with apostrophe '\''
        assertThat(csv).contains("'+@SUM(1,2)");
        assertThat(csv).contains("PAYABLE_SETTLEMENT");
        assertThat(csv).contains("28000.00");
    }
}
