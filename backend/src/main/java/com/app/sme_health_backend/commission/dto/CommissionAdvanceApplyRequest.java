package com.app.sme_health_backend.commission.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CommissionAdvanceApplyRequest(
    UUID advanceId,
    BigDecimal amount,
    String notes
) {}
