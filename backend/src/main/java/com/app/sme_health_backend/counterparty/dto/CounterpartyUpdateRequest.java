package com.app.sme_health_backend.counterparty.dto;

public record CounterpartyUpdateRequest(
        Long version,
        String displayName,
        String type,
        String email,
        String phone,
        String address,
        String referenceCode,
        String notes
) {
}
