package com.app.sme_health_backend.obligation.dto;

import java.util.List;

public record ObligationListResponse(
        List<ObligationSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
