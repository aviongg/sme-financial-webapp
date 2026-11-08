package com.app.sme_health_backend.fx.dto;

import com.app.sme_health_backend.fx.model.CurrencyMetadata;

public record CurrencyInfoResponse(
        String code,
        String displayName,
        String symbol,
        int minorUnit,
        boolean active
) {
    public static CurrencyInfoResponse from(CurrencyMetadata meta) {
        return new CurrencyInfoResponse(
                meta.code(),
                meta.displayName(),
                meta.symbol(),
                meta.minorUnit(),
                meta.active()
        );
    }
}

