package com.app.sme_health_backend.commission.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CommissionAdvanceCreateRequest(
    UUID salespersonId,
    UUID accountId,
    BigDecimal amount,
    LocalDate paymentDate,
    String notes
) {}
