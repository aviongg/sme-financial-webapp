package com.app.sme_health_backend.estimates.dto;

import java.util.List;
import java.util.UUID;

public record EstimateListResponse(UUID businessId, String currency, String currencyBasis, boolean planningOnly,
                                   List<EstimateResponse> items) { }
