package com.app.sme_health_backend.obligation.dto;

import java.time.LocalDate;
import java.util.UUID;

public record PaymentPlanInstallmentResponse(
        UUID id,
        Integer installmentNumber,
        LocalDate expectedDate,
        String expectedAmount,
        String status,
        String notes,
        Boolean isOverdue
) {
}
