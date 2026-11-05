package com.app.sme_health_backend.projects.dto;

import java.util.List;
import java.util.UUID;

public record ProjectListResponse(
        UUID businessId,
        String baseCurrency,
        String currencyBasis,
        boolean planningOnly,
        List<ProjectResponse> items
) { }
