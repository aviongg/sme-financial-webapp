package com.app.sme_health_backend.projects.dto;

import java.util.UUID;

public record ProjectPlanningSummaryResponse(
        UUID businessId,
        boolean planningOnly,
        String baseCurrency,
        String currencyBasis,
        String actualFinancialsStatus,
        ProjectResponse project,
        ProjectBudgetVersionResponse activeApprovedBudget,
        ProjectBudgetVersionResponse latestDraftBudget,
        String budgetStatusReason,
        int revisionCount,
        int supersededCount,
        ProjectBudgetCalculationResponse.Calculation calculation
) { }
