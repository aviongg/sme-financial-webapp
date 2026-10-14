package com.app.sme_health_backend.accounting.dto;

public record AccountCreateRequest(
        String accountName,
        String accountType,
        String currency,
        String openingBalance,
        String openingDate,
        String institutionName,
        String notes
) {}
