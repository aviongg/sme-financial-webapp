package com.app.sme_health_backend.samples.dto;

import java.util.UUID;

public record SamplePlanningSummaryResponse(
        UUID businessId,
        boolean planningOnly,
        String financialBasis,
        String baseCurrency,
        String currencyBasis,
        String actualFinancialsStatus,
        SampleResponse activity,
        SamplePlanVersionResponse activeApprovedPlan,
        SamplePlanVersionResponse latestDraftPlan,
        String planStatusReason,
        int revisionCount,
        int supersededCount,
        SamplePlanCalculationResponse.Calculation calculation
) { }
