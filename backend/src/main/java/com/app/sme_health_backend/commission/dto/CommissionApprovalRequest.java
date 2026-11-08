package com.app.sme_health_backend.commission.dto;

import java.math.BigDecimal;

public record CommissionApprovalRequest(
    BigDecimal approvedAmount,
    String notes
) {}
