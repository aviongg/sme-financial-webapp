package com.app.sme_health_backend.reconciliation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StatementImportRequest(
        @NotNull(message = "accountId is required")
        UUID accountId,
        @Size(max = 255, message = "sourceFilename cannot exceed 255 characters")
        String sourceFilename,
        String fileFingerprint,
        String mappingConfig,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        @NotEmpty(message = "Statement must contain at least one entry")
        @Valid
        List<StatementEntryItemRequest> entries
) {}
