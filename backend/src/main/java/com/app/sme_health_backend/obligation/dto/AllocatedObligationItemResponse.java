package com.app.sme_health_backend.obligation.dto;

import java.util.UUID;

public record AllocatedObligationItemResponse(
        UUID allocationId,
        UUID obligationId,
        String referenceNumber,
        String allocatedAmount,
        String remainingObligationOutstanding,
        String obligationSettlementStatus
) {
}
