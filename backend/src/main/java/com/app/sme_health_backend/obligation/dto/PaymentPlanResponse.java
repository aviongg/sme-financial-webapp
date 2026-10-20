package com.app.sme_health_backend.obligation.dto;

import java.util.List;
import java.util.UUID;

public record PaymentPlanResponse(
        UUID obligationId,
        String obligationReferenceNumber,
        String obligationOriginalAmount,
        String obligationOutstandingAmount,
        String totalPlannedAmount,
        String coverageStatus,
        List<PaymentPlanInstallmentResponse> installments
) {
}
