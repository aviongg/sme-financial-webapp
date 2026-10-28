package com.app.sme_health_backend.financialquery.dto;

import java.time.LocalDate;
import java.util.UUID;

public record AccountCashPosition(
        UUID accountId,
        String accountName,
        String accountType,
        String currency,
        int currencyDecimals,
        String openingBalance,
        LocalDate openingDate,
        String netMovements,
        String currentBalance,
        String status,
        String institutionName,
        String maskedIdentifier
) {}
