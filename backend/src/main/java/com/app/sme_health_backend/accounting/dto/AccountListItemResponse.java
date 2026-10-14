package com.app.sme_health_backend.accounting.dto;

import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.BalanceStatus;

import java.time.LocalDate;
import java.util.UUID;

public record AccountListItemResponse(
        UUID id,
        String accountName,
        AccountType accountType,
        String currency,
        Integer currencyDecimals,
        String openingBalance,
        LocalDate openingDate,
        String currentBalance,
        BalanceStatus balanceStatus,
        String balanceUnavailableReason,
        AccountStatus status,
        String observationAt,
        LocalDate effectiveCutoffDate,
        String institutionName,
        String accountIdentifier,
        String createdAt,
        String updatedAt
) {}
