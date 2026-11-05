package com.app.sme_health_backend.estimates.dto;

import java.util.List;

/** Estimate identity, every revision (newest first) and the immutable conversion lineage. */
public record EstimateDetailResponse(EstimateResponse estimate, List<EstimateRevisionResponse> revisions,
                                     List<EstimateConversionResponse> conversions) { }
