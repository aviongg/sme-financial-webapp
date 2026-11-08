package com.app.sme_health_backend.commission.dto;

import com.app.sme_health_backend.commission.model.CalculationType;
import com.app.sme_health_backend.commission.model.EarningBasis;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CommissionRuleCreateRequest(
    UUID salespersonId,

    @NotBlank(message = "Rule name is required")
    @Size(max = 255, message = "Rule name must not exceed 255 characters")
    String ruleName,

    @NotNull(message = "Calculation type is required")
    CalculationType calculationType,

    EarningBasis earningBasis,

    BigDecimal ratePercentage,
    BigDecimal fixedAmount,
    String currency,

    @NotNull(message = "Effective start date is required")
    LocalDate effectiveStartDate,

    LocalDate effectiveEndDate
) {}
