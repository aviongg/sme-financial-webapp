package com.app.sme_health_backend.financialquery.dto;

public record UpcomingObligationsQuery(
        String direction,
        String fromDate,
        String toDate
) {}
