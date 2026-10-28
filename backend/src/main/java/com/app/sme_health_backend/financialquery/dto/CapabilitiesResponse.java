package com.app.sme_health_backend.financialquery.dto;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record CapabilitiesResponse(
        String contractVersion,
        UUID businessId,
        Instant evaluatedAt,
        List<CapabilityItem> capabilities
) {
    public CapabilitiesResponse {
        capabilities = capabilities == null ? Collections.emptyList() : List.copyOf(capabilities);
    }
}
