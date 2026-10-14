package com.app.sme_health_backend.accounting.dto;

import java.util.List;

public record AccountListResponse(
        List<AccountListItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
