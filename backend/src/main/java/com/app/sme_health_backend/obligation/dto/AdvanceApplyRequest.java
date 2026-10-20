package com.app.sme_health_backend.obligation.dto;

import java.util.UUID;

public record AdvanceApplyRequest(
        UUID obligationId,
        String amount,
        String applicationDate,
        String notes
) {
}
