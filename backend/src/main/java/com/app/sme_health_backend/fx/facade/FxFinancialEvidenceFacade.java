package com.app.sme_health_backend.fx.facade;

import com.app.sme_health_backend.fx.dto.*;
import com.app.sme_health_backend.fx.entity.FxObligationSettlement;
import com.app.sme_health_backend.fx.model.FxSettlementStatus;
import com.app.sme_health_backend.fx.model.RealizedFxImpact;
import com.app.sme_health_backend.fx.repository.FxObligationRecognitionEvidenceRepository;
import com.app.sme_health_backend.fx.repository.FxObligationSettlementRepository;
import com.app.sme_health_backend.fx.service.FxAnalyticsService;
import com.app.sme_health_backend.fx.service.FxSettlementService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only facade providing authoritative FX and settlement accounting evidence
 * for integration with Developer B commercial costing feeds and C11 financial queries.
 */
@Component
public class FxFinancialEvidenceFacade {

    private final FxObligationRecognitionEvidenceRepository recognitionEvidenceRepository;
    private final FxObligationSettlementRepository settlementRepository;
    private final FxSettlementService settlementService;
    private final FxAnalyticsService analyticsService;

    public FxFinancialEvidenceFacade(
            FxObligationRecognitionEvidenceRepository recognitionEvidenceRepository,
            FxObligationSettlementRepository settlementRepository,
            FxSettlementService settlementService,
            FxAnalyticsService analyticsService
    ) {
        this.recognitionEvidenceRepository = recognitionEvidenceRepository;
        this.settlementRepository = settlementRepository;
        this.settlementService = settlementService;
        this.analyticsService = analyticsService;
    }

    @Transactional(readOnly = true)
    public Optional<FxRecognitionEvidenceResponse> getObligationRecognitionEvidence(UUID businessId, UUID obligationId) {
        return recognitionEvidenceRepository.findByBusinessIdAndObligationId(businessId, obligationId)
                .map(FxRecognitionEvidenceResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public Optional<FxSettlementReconciliationResponse> getSettlementReconciliation(UUID businessId, UUID settlementId) {
        BusinessAccessContext context = createSystemReadContext(businessId);
        try {
            return Optional.of(settlementService.getSettlementReconciliation(context, settlementId));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public List<FxSettlementReconciliationResponse> getSettlementReconciliationsForObligation(UUID businessId, UUID obligationId) {
        BusinessAccessContext context = createSystemReadContext(businessId);
        return settlementService.listSettlementReconciliations(context, obligationId);
    }

    @Transactional(readOnly = true)
    public FxPeriodSummaryResponse getPeriodSummary(UUID businessId, LocalDate startDate, LocalDate endDate) {
        BusinessAccessContext context = createSystemReadContext(businessId);
        return settlementService.getPeriodSummary(context, startDate, endDate);
    }

    @Transactional(readOnly = true)
    public List<CurrencyExposureSummary> getCurrencyExposureSummary(UUID businessId) {
        BusinessAccessContext context = createSystemReadContext(businessId);
        return analyticsService.getCurrencyExposures(context);
    }

    @Transactional(readOnly = true)
    public FxAnalyticsOverviewResponse getFxAnalyticsOverview(UUID businessId) {
        BusinessAccessContext context = createSystemReadContext(businessId);
        return analyticsService.getAnalyticsOverview(context);
    }

    @Transactional(readOnly = true)
    public List<FxPeriodTrendSummary> getPeriodicTrends(UUID businessId) {
        BusinessAccessContext context = createSystemReadContext(businessId);
        return analyticsService.getPeriodicTrends(context);
    }

    @Transactional(readOnly = true)
    public List<FxSettlementReportItem> getSettlementsReport(UUID businessId, FxSettlementReportFilter filter) {
        BusinessAccessContext context = createSystemReadContext(businessId);
        return analyticsService.getSettlementsReport(context, filter);
    }

    @Transactional(readOnly = true)
    public BigDecimal getCumulativeRealizedFxForObligation(UUID businessId, UUID obligationId) {
        List<FxObligationSettlement> settlements = settlementRepository
                .findAllByBusinessIdAndObligationIdOrderBySettlementDateDescCreatedAtDesc(businessId, obligationId);

        BigDecimal netRealized = BigDecimal.ZERO;
        for (FxObligationSettlement s : settlements) {
            if (s.getStatus() == FxSettlementStatus.SETTLED && s.getRealizedFxDifference() != null) {
                if (s.getRealizedFxImpact() == RealizedFxImpact.GAIN) {
                    netRealized = netRealized.add(s.getRealizedFxDifference());
                } else if (s.getRealizedFxImpact() == RealizedFxImpact.LOSS) {
                    netRealized = netRealized.subtract(s.getRealizedFxDifference());
                }
            }
        }
        return netRealized;
    }

    private BusinessAccessContext createSystemReadContext(UUID businessId) {
        return new BusinessAccessContext(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                businessId,
                MembershipRole.OWNER
        );
    }
}
