package com.app.sme_health_backend.obligation.dto;

import java.util.UUID;

public record AllocationItemRequest(
        UUID obligationId,
        String amount
) {
}
