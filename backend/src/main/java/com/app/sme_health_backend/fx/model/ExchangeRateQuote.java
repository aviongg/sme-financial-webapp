package com.app.sme_health_backend.fx.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable domain model for foreign exchange rate quotes.
 * Adheres to standard convention: 1 BASE = [RATE] QUOTE.
 */
public record ExchangeRateQuote(
        String baseCurrency,
        String quoteCurrency,
        BigDecimal rate,
        RateConvention convention,
        RateSourceType sourceType,
        Instant effectiveAt,
        UUID businessId,
        String notes
) {
    public ExchangeRateQuote {
        if (baseCurrency == null || baseCurrency.isBlank()) {
            throw new IllegalArgumentException("Base currency is required");
        }
        baseCurrency = baseCurrency.trim().toUpperCase();
        if (quoteCurrency == null || quoteCurrency.isBlank()) {
            throw new IllegalArgumentException("Quote currency is required");
        }
        quoteCurrency = quoteCurrency.trim().toUpperCase();
        if (baseCurrency.equals(quoteCurrency)) {
            throw new IllegalArgumentException("Base and quote currencies cannot be identical");
        }
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Exchange rate must be strictly positive");
        }
        if (convention == null) {
            convention = RateConvention.BASE_TO_QUOTE;
        }
        if (sourceType == null) {
            sourceType = RateSourceType.MANUAL_USER_RATE;
        }
        if (effectiveAt == null) {
            effectiveAt = Instant.now();
        }
    }

    /**
     * Computes the high-precision reciprocal quote: 1 QUOTE = [RECIPROCAL] BASE.
     */
    public BigDecimal calculateReciprocalRate(int scale) {
        return BigDecimal.ONE.divide(rate, MathContext.DECIMAL128)
                .setScale(Math.max(scale, 6), RoundingMode.HALF_UP);
    }
}

