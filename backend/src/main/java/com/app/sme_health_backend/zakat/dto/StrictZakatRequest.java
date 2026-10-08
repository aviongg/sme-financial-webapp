package com.app.sme_health_backend.zakat.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

public interface StrictZakatRequest {
    @JsonAnySetter
    default void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("Unsupported Zakat request field: " + field);
    }
}
