package com.app.sme_health_backend.counterparty.dto;

public record CounterpartyCreateRequest(
        String displayName,
        String type,
        String email,
        String phone,
        String address,
        String referenceCode,
        String notes
) {
    public CounterpartyCreateRequest(String displayName, String type) {
        this(displayName, type, null, null, null, null, null);
    }
}
