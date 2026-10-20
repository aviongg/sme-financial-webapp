package com.app.sme_health_backend.obligation.dto;

import java.util.List;
import java.util.UUID;

public record PaymentAllocateRequest(
        UUID counterpartyId,
        String direction,
        UUID accountId,
        String paymentDate,
        String amount,
        List<AllocationItemRequest> allocations,
        String referenceNumber,
        String description,
        Boolean retainAdvance
) {
}
