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
        @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
        BigDecimal confirmedAmount,

        LocalDate confirmedDate,

        @jakarta.validation.constraints.Size(max = 500)
        String confirmedParty,

        @NotBlank(message = "Target classification is required (revenue, operating_expenses, cogs, none)")
        String targetClassification,

        String cashFlowImpact,

        @jakarta.validation.constraints.Digits(integer = 12, fraction = 2)
        @DecimalMin("0")
        BigDecimal initialCashBalanceEom,

        Boolean allowPotentialDuplicate
) {
    /** Legacy and explicit-null requests never acknowledge a duplicate. */
    public DocumentConfirmationRequest {
        allowPotentialDuplicate = Boolean.TRUE.equals(allowPotentialDuplicate);
    }
    public DocumentConfirmationRequest(String targetMonth, BigDecimal confirmedAmount, LocalDate confirmedDate,
            String confirmedParty, String targetClassification, String cashFlowImpact, BigDecimal initialCashBalanceEom) {
        this(targetMonth, confirmedAmount, confirmedDate, confirmedParty, targetClassification, cashFlowImpact, initialCashBalanceEom, false);
    }
}
