package com.app.sme_health_backend.financialquery.dto;

public record OverdueReceivablesQuery(
        String asOfDate,
        Integer limit,
        Integer offset
) {
    public OverdueReceivablesQuery(String asOfDate, Integer limit) {
        this(asOfDate, limit, 0);
    }
}
