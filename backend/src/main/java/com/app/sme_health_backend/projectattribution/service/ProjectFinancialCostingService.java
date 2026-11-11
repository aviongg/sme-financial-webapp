package com.app.sme_health_backend.projectattribution.service;

import com.app.sme_health_backend.projectattribution.dto.EligibleSourceItem;
import com.app.sme_health_backend.projectattribution.dto.ProjectCostingSummaryResponse;
import com.app.sme_health_backend.projectattribution.dto.ProjectCostingSummaryResponse.CategoryCostSummary;
import com.app.sme_health_backend.projectattribution.entity.ProjectFinancialAttribution;
import com.app.sme_health_backend.projectattribution.exception.AttributionNotFoundException;
import com.app.sme_health_backend.projectattribution.model.AttributionCostCategory;
import com.app.sme_health_backend.projectattribution.model.AttributionDirection;
import com.app.sme_health_backend.projectattribution.model.AttributionStatus;
import com.app.sme_health_backend.projectattribution.model.EconomicClassification;
import com.app.sme_health_backend.projectattribution.repository.ProjectFinancialAttributionRepository;
import com.app.sme_health_backend.projects.model.ProjectBudgetStatus;
import com.app.sme_health_backend.projects.model.ProjectBudgetVersionRecord;
import com.app.sme_health_backend.projects.model.ProjectCostCategory;
import com.app.sme_health_backend.projects.model.ProjectRecord;
import com.app.sme_health_backend.projects.repository.ProjectBudgetRepository;
import com.app.sme_health_backend.projects.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class ProjectFinancialCostingService {

    private final ProjectRepository projectRepository;
    private final ProjectBudgetRepository projectBudgetRepository;
    private final ProjectFinancialAttributionRepository attributionRepository;
    private final ProjectFinancialAttributionService attributionService;

    public ProjectFinancialCostingService(
            ProjectRepository projectRepository,
            ProjectBudgetRepository projectBudgetRepository,
            ProjectFinancialAttributionRepository attributionRepository,
            ProjectFinancialAttributionService attributionService
    ) {
        this.projectRepository = projectRepository;
        this.projectBudgetRepository = projectBudgetRepository;
        this.attributionRepository = attributionRepository;
        this.attributionService = attributionService;
    }

    @Transactional(readOnly = true)
    public ProjectCostingSummaryResponse getCostingSummary(UUID businessId, UUID projectId) {
        ProjectRecord project = projectRepository.findById(businessId, projectId)
                .orElseThrow(() -> new AttributionNotFoundException("Project not found with id: " + projectId));

        // 1. Fetch Approved Budget (if any)
        List<ProjectBudgetVersionRecord> versions = projectBudgetRepository.findAllByProjectOrderByRevisionDesc(businessId, projectId);
        Optional<ProjectBudgetVersionRecord> approvedBudgetOpt = versions.stream()
                .filter(b -> b.status() == ProjectBudgetStatus.APPROVED)
                .findFirst();

        boolean hasApprovedBudget = approvedBudgetOpt.isPresent();
        Integer approvedBudgetRevision = hasApprovedBudget ? approvedBudgetOpt.get().revision() : null;
        // A missing approved budget is unknown, never zero (product-completion fix).
        BigDecimal plannedRevenue = hasApprovedBudget ? approvedBudgetOpt.get().plannedRevenue() : null;
        BigDecimal plannedCost = hasApprovedBudget ? approvedBudgetOpt.get().plannedCost() : null;
        BigDecimal plannedMargin = hasApprovedBudget ? approvedBudgetOpt.get().plannedMargin() : null;
        BigDecimal plannedMarginPct = hasApprovedBudget ? approvedBudgetOpt.get().plannedMarginPct() : null;

        // Map planned cost by category from approved budget lines
        Map<AttributionCostCategory, BigDecimal> plannedByCategory = new EnumMap<>(AttributionCostCategory.class);
        for (AttributionCostCategory cat : AttributionCostCategory.values()) {
            plannedByCategory.put(cat, BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        }

        if (hasApprovedBudget && approvedBudgetOpt.get().lines() != null) {
            for (ProjectBudgetVersionRecord.LineRecord line : approvedBudgetOpt.get().lines()) {
                AttributionCostCategory mappedCat = mapPlanningCategory(line.category());
                plannedByCategory.compute(mappedCat, (k, current) ->
                        (current == null ? BigDecimal.ZERO : current).add(line.plannedLineCost())
                );
            }
        }

        // 2. Aggregate Active Financial Attributions
        List<ProjectFinancialAttribution> activeAttributions = attributionRepository
                .findByBusinessIdAndProjectIdAndStatusOrderByAttributionDateDescCreatedAtDesc(
                        businessId, projectId, AttributionStatus.ACTIVE
                );

        BigDecimal attributedCashInflows = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal attributedCashOutflows = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal recognizedCommissionCost = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal nonCashFxImpact = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        Map<AttributionCostCategory, BigDecimal> actualCashByCategory = new EnumMap<>(AttributionCostCategory.class);
        Map<AttributionCostCategory, BigDecimal> recognizedCostByCategory = new EnumMap<>(AttributionCostCategory.class);
        Map<AttributionCostCategory, BigDecimal> nonCashByCategory = new EnumMap<>(AttributionCostCategory.class);

        for (AttributionCostCategory cat : AttributionCostCategory.values()) {
            actualCashByCategory.put(cat, BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
            recognizedCostByCategory.put(cat, BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
            nonCashByCategory.put(cat, BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        }

        for (ProjectFinancialAttribution a : activeAttributions) {
            BigDecimal amt = a.getBaseAmount();
            AttributionCostCategory cat = a.getCostCategory();

            if (a.getEconomicClassification() == EconomicClassification.POSTED_CASH_ACTUAL) {
                if (a.getSourceDirection() == AttributionDirection.INFLOW) {
                    attributedCashInflows = attributedCashInflows.add(amt);
                } else {
                    attributedCashOutflows = attributedCashOutflows.add(amt);
                    actualCashByCategory.compute(cat, (k, curr) -> (curr == null ? BigDecimal.ZERO : curr).add(amt));
                }
            } else if (a.getEconomicClassification() == EconomicClassification.RECOGNIZED_ECONOMIC_COST) {
                recognizedCommissionCost = recognizedCommissionCost.add(amt);
                recognizedCostByCategory.compute(cat, (k, curr) -> (curr == null ? BigDecimal.ZERO : curr).add(amt));
            } else if (a.getEconomicClassification() == EconomicClassification.NONCASH_FINANCIAL_EVIDENCE) {
                nonCashFxImpact = nonCashFxImpact.add(amt);
                nonCashByCategory.compute(cat, (k, curr) -> (curr == null ? BigDecimal.ZERO : curr).add(amt));
            }
        }

        // 3. Compute Headroom (Cash-to-plan comparison)
        BigDecimal cashHeadroom = null;
        BigDecimal cashHeadroomPct = null;
        if (hasApprovedBudget) {
            cashHeadroom = plannedCost.subtract(attributedCashOutflows).setScale(4, RoundingMode.HALF_UP);
            if (plannedCost.compareTo(BigDecimal.ZERO) > 0) {
                cashHeadroomPct = cashHeadroom.divide(plannedCost, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
            }
        }

        // 4. Build Category Breakdown
        List<CategoryCostSummary> categories = new ArrayList<>();
        // Focus on primary cost categories: MATERIAL, LABOUR, OVERHEAD, FREIGHT, COMMISSION, OTHER
        AttributionCostCategory[] displayCategories = {
                AttributionCostCategory.MATERIAL,
                AttributionCostCategory.LABOUR,
                AttributionCostCategory.OVERHEAD,
                AttributionCostCategory.FREIGHT,
                AttributionCostCategory.COMMISSION,
                AttributionCostCategory.OTHER
        };

        for (AttributionCostCategory cat : displayCategories) {
            BigDecimal pCost = hasApprovedBudget
                    ? plannedByCategory.getOrDefault(cat, BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP) : null;
            BigDecimal aCash = actualCashByCategory.getOrDefault(cat, BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
            BigDecimal cHeadroom = hasApprovedBudget ? pCost.subtract(aCash).setScale(4, RoundingMode.HALF_UP) : null;
            BigDecimal recCost = recognizedCostByCategory.getOrDefault(cat, BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
            BigDecimal nonCash = nonCashByCategory.getOrDefault(cat, BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);

            categories.add(new CategoryCostSummary(
                    cat,
                    pCost,
                    aCash,
                    cHeadroom,
                    recCost,
                    nonCash
            ));
        }

        // 5. Total Unattributed Eligible Amount across discoverable sources
        BigDecimal unattributedEligibleAmount = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        try {
            List<EligibleSourceItem> eligible = attributionService.getEligibleSources(businessId, projectId);
            for (EligibleSourceItem e : eligible) {
                unattributedEligibleAmount = unattributedEligibleAmount.add(e.remainingEligibleAmount());
            }
        } catch (RuntimeException discoveryFailure) {
            // Never silently report zero: the amount becomes unknown and the failure is logged.
            org.slf4j.LoggerFactory.getLogger(ProjectFinancialCostingService.class)
                    .warn("Eligible source discovery failed for project {}: {}", projectId, discoveryFailure.getClass().getSimpleName());
            unattributedEligibleAmount = null;
        }

        // 6. Data Completeness & Profitability Status
        String profitabilityStatus = "UNAVAILABLE";
        String unavailabilityReason;
        BigDecimal actualGrossProfit = null;
        BigDecimal actualGrossMarginPct = null;
        String coverageNotes;

        if (!hasApprovedBudget) {
            unavailabilityReason = "NO_APPROVED_BUDGET";
            coverageNotes = "Project has no active approved budget. Cash actuals are recorded, but plan comparison and profitability are unavailable.";
        } else {
            unavailabilityReason = "INCOMPLETE_COST_EVIDENCE";
            coverageNotes = "Project actual profitability requires complete recognized revenue and recognized cost basis. Attributed cash outflows represent liquidity disbursements and headroom under the approved plan, not finalized net accounting profit.";
        }

        return new ProjectCostingSummaryResponse(
                project.id(),
                project.code(),
                project.name(),
                project.status().name(),
                project.baseCurrency() != null ? project.baseCurrency() : "PKR",
                approvedBudgetRevision,
                hasApprovedBudget,
                plannedRevenue,
                plannedCost,
                plannedMargin,
                plannedMarginPct,
                attributedCashInflows,
                attributedCashOutflows,
                cashHeadroom,
                cashHeadroomPct,
                categories,
                recognizedCommissionCost,
                nonCashFxImpact,
                unattributedEligibleAmount,
                profitabilityStatus,
                unavailabilityReason,
                actualGrossProfit,
                actualGrossMarginPct,
                coverageNotes
        );
    }

    private AttributionCostCategory mapPlanningCategory(ProjectCostCategory category) {
        if (category == null) return AttributionCostCategory.OTHER;
        return switch (category) {
            case MATERIAL -> AttributionCostCategory.MATERIAL;
            case LABOUR -> AttributionCostCategory.LABOUR;
            case OVERHEAD -> AttributionCostCategory.OVERHEAD;
            case FREIGHT -> AttributionCostCategory.FREIGHT;
            case OTHER -> AttributionCostCategory.OTHER;
        };
    }
}
