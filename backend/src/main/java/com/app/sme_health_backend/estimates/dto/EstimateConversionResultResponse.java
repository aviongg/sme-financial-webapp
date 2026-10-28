package com.app.sme_health_backend.estimates.dto;

import com.app.sme_health_backend.projects.dto.ProjectBudgetVersionResponse;

/**
 * Outcome of converting an APPROVED revision. {@code budget} is the F05 DRAFT planning budget as F05 reports it;
 * it is null on a replay when F05 has since discarded that draft. Nothing is posted: financialEffect is NONE.
 */
public record EstimateConversionResultResponse(boolean replayed, EstimateConversionResponse conversion,
                                               ProjectBudgetVersionResponse budget, String financialEffect,
                                               String planningStatus) { }
