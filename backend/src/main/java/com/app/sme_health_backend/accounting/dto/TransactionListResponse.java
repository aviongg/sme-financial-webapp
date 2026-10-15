package com.app.sme_health_backend.accounting.dto;

import java.time.LocalDate;
import java.util.List;

public record TransactionListResponse(
        List<TransactionDetailResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        LocalDate effectiveCutoffDate,
        String observationAt
) {}
