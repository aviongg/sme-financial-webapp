package com.app.sme_health_backend.obligation.dto;

import java.util.List;

public record PaymentPlanRequest(
        List<PaymentPlanInstallmentRequest> installments
) {
}
