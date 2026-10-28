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
     * Tool 4 (UNAVAILABLE): Project financial tracking (F05).
     */
    FinancialQueryEnvelope<Void> getProjectFinancials(BusinessAccessContext context, UUID projectId);

    /**
     * Tool 5 (UNAVAILABLE): Project funding gap analysis (F07).
     */
    FinancialQueryEnvelope<Void> getProjectFundingGap(BusinessAccessContext context, UUID projectId);

    /**
     * Tool 6 (UNAVAILABLE): Margin drivers & COGS (F08).
     */
    FinancialQueryEnvelope<Void> getMarginDrivers(BusinessAccessContext context, String query);

    /**
     * Tool 7 (UNAVAILABLE): Realized FX settlement impact (F11).
     */
    FinancialQueryEnvelope<Void> getFxSettlementImpact(BusinessAccessContext context, String query);
}
