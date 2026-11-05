package com.app.sme_health_backend.estimates.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/** Computational resource bounds, not money storage limits or accounting/settlement policy. */
final class EstimateDecimals {
    private EstimateDecimals() { }
    static final int DIVISION_SCALE = 24;
    static final RoundingMode DIVISION_ROUNDING = RoundingMode.HALF_EVEN;
    private static final Pattern INPUT = Pattern.compile("[0-9]{1,12}(?:\\.[0-9]{1,12})?");
    static BigDecimal parse(String value, String field) {
        if (value == null) throw new EstimateValidationException(field, "required");
        if (value.length() > 25 || !INPUT.matcher(value).matches())
            throw new EstimateValidationException(field, "decimal_invalid");
        return new BigDecimal(value);
    }
    static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        return numerator.divide(denominator, DIVISION_SCALE, DIVISION_ROUNDING);
    }
    static String decimal(BigDecimal value) { return value == null ? null : value.stripTrailingZeros().toPlainString(); }
    static String amount(BigDecimal value) {
        if (value == null) return null;
        BigDecimal stripped = value.stripTrailingZeros();
        // Zero padding is exact presentation only; never quantize an amount to currency minor units.
        return stripped.setScale(Math.max(2, stripped.scale())).toPlainString();
    }
}
