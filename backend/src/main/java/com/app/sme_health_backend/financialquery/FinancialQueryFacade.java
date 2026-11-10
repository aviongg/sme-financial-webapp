package com.app.sme_health_backend.financialquery;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.financialquery.dto.*;

import java.util.UUID;

/**
 * Authoritative, read-only financial query facade for intelligence and AI orchestration.
 * Exposes deterministic financial queries backed by canonical FinSight financial services.
 */
public interface FinancialQueryFacade {

    /**
     * Lists all supported financial query tools and their current availability status.
     */
    CapabilitiesResponse getCapabilities(BusinessAccessContext context);

    /**
     * Tool A: Returns authoritative cash and bank position across business accounts.
     */
    FinancialQueryEnvelope<CashPositionResult> getCashPosition(BusinessAccessContext context, CashPositionQuery query);

    /**
     * Tool B: Returns overdue receivables using canonical obligation aging.
     */
    FinancialQueryEnvelope<OverdueReceivablesResult> getOverdueReceivables(BusinessAccessContext context, OverdueReceivablesQuery query);

    /**
     * Tool C: Returns upcoming obligations and payment schedules without double counting.
     */
    FinancialQueryEnvelope<UpcomingObligationsResult> getUpcomingObligations(BusinessAccessContext context, UpcomingObligationsQuery query);

    /**
     * Tool 4: Project financial tracking (F05/C05).
     */
    FinancialQueryEnvelope<ProjectFinancialsResult> getProjectFinancials(BusinessAccessContext context, UUID projectId);

    /**
     * Tool 5: Project funding gap analysis (F07/C05).
     */
    FinancialQueryEnvelope<ProjectFundingGapResult> getProjectFundingGap(BusinessAccessContext context, UUID projectId);

    /**
     * Tool 6: Margin drivers & COGS (F08 inventory authority + accrual P&L).
     */
    FinancialQueryEnvelope<java.util.Map<String, Object>> getMarginDrivers(BusinessAccessContext context, String query);

    /** Tool 9: Accrual profit and loss for one month (pnl-accrual-v1). */
    FinancialQueryEnvelope<java.util.Map<String, Object>> getProfitAndLoss(BusinessAccessContext context, String yearMonth);

    /** Tool 10: Authoritative perpetual inventory position (f08-wac-v1). */
    FinancialQueryEnvelope<java.util.Map<String, Object>> getInventoryPosition(BusinessAccessContext context);

    /** Tool 11: Active budgets variance and goal progress. */
    FinancialQueryEnvelope<java.util.Map<String, Object>> getBudgetAndGoalStatus(BusinessAccessContext context);

    /** Tool 12: Deterministic break-even for one month (break-even-v1). */
    FinancialQueryEnvelope<java.util.Map<String, Object>> getBreakEven(BusinessAccessContext context, String yearMonth);

    /** Tool 13: Actual project profitability (f05-profitability-v1). */
    FinancialQueryEnvelope<java.util.Map<String, Object>> getProjectProfitability(BusinessAccessContext context, UUID projectId);

    /**
     * Tool 7: Realized FX settlement impact (F11/C03).
     */
    FinancialQueryEnvelope<FxSettlementImpactResult> getFxSettlementImpact(BusinessAccessContext context, String query);

    /**
     * Tool 8: Financial Health Evidence (C02/Bridge).
     */
    FinancialQueryEnvelope<FinancialHealthEvidenceResult> getAvailableFinancialHealthEvidence(BusinessAccessContext context, String yearMonth);
}
