package com.app.sme_health_backend.counterparty.dto;

import java.util.List;

public record CounterpartyListResponse(
        List<CounterpartySummaryItem> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
