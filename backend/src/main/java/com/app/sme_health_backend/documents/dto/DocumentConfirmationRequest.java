package com.app.sme_health_backend.documents.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DocumentConfirmationRequest(
        @NotBlank(message = "Target month is required")
        @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "Target month must be in YYYY-MM format")
        String targetMonth,

        @NotNull(message = "Confirmed amount is required")
        @DecimalMin(value = "0.01", message = "Confirmed amount must be greater than zero")
        BigDecimal confirmedAmount,

        LocalDate confirmedDate,

        String confirmedParty,

        @NotBlank(message = "Target classification is required (revenue, operating_expenses, cogs, none)")
        String targetClassification,

        String cashFlowImpact,

        BigDecimal initialCashBalanceEom
) {}
