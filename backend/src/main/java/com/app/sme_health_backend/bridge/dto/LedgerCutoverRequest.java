package com.app.sme_health_backend.bridge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record LedgerCutoverRequest(
        @NotBlank(message = "yearMonth is required")
        @Pattern(regexp = "^[0-9]{4}-(0[1-9]|1[0-2])$", message = "yearMonth must follow YYYY-MM format")
        String yearMonth,

        BigDecimal manualCogs,
        BigDecimal manualInventoryValue,
        BigDecimal manualLoanOutstanding,
        BigDecimal manualInterestExpense,
        String financingType,
        Long expectedVersion,
        String confirmationNote
) {
}

