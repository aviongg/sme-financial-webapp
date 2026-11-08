package com.app.sme_health_backend.fx.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Deterministic ISO 4217 currency metadata definition.
 * Governs precision, display formatting, and rounding rules across FinSight.
 */
public record CurrencyMetadata(
        String code,
        String displayName,
        String symbol,
        int minorUnit,
        RoundingMode roundingMode,
        boolean active
) {
    public CurrencyMetadata {
        if (code == null || code.isBlank() || code.trim().length() != 3) {
            throw new IllegalArgumentException("Currency code must be a 3-letter ISO 4217 code");
        }
        code = code.trim().toUpperCase();
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Display name is required");
        }
        if (symbol == null || symbol.isBlank()) {
            symbol = code;
        }
        if (minorUnit < 0 || minorUnit > 6) {
            throw new IllegalArgumentException("Minor unit must be between 0 and 6");
        }
        if (roundingMode == null) {
            roundingMode = RoundingMode.HALF_UP;
        }
    }

    public BigDecimal round(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(minorUnit, roundingMode);
        }
        return amount.setScale(minorUnit, roundingMode);
    }
}

