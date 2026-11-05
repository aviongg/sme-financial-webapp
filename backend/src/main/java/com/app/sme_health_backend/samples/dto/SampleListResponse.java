package com.app.sme_health_backend.samples.dto;

import java.util.List;
import java.util.UUID;

public record SampleListResponse(
        UUID businessId,
        String baseCurrency,
        String currencyBasis,
        boolean planningOnly,
        String financialBasis,
        List<SampleResponse> items
) { }
