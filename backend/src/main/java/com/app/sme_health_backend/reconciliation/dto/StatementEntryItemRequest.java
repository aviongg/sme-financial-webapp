package com.app.sme_health_backend.reconciliation.dto;

import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StatementEntryItemRequest(
        @JsonAlias({"sourceRowId", "rowNumber"})
        int sourceRowNumber,
        @NotNull(message = "postedDate is required")
        LocalDate postedDate,
        LocalDate valueDate,
        @NotNull(message = "direction is required (INCREASE or DECREASE)")
        MovementDirection direction,
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be greater than zero")
        @Digits(integer = 14, fraction = 2, message = "amount cannot have more than 2 decimal places")
        BigDecimal amount,
        @Size(max = 500, message = "description cannot exceed 500 characters")
        String description,
        @Size(max = 255, message = "bankReference cannot exceed 255 characters")
        String bankReference,
        @Size(max = 255, message = "externalTransactionId cannot exceed 255 characters")
        String externalTransactionId,
        BigDecimal runningBalance
) {}
