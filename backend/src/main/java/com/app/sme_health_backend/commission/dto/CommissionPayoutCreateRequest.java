package com.app.sme_health_backend.commission.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CommissionPayoutCreateRequest(
    UUID accountId,
    BigDecimal amount,
    LocalDate paymentDate,
    String notes
) {}
