package com.app.sme_health_backend.financialquery.dto;

public record CashPositionQuery(
        String asOfDate,
        String accountType
) {}
