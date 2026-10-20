package com.app.sme_health_backend.obligation.dto;

public record PaymentPlanInstallmentRequest(
        Integer installmentNumber,
        String expectedDate,
        String expectedAmount,
        String notes
) {
}
