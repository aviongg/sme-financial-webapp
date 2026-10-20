package com.app.sme_health_backend.obligation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AdvanceListResponse(
        @JsonProperty("content")
        List<AdvanceDetailResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    @JsonProperty("items")
    public List<AdvanceDetailResponse> items() {
        return content != null ? content : List.of();
    }
}

