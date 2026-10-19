package com.app.sme_health_backend.obligation.dto;

public record ObligationUpdateRequest(
        String dueDate,
        String referenceNumber,
        String description
) {
}
