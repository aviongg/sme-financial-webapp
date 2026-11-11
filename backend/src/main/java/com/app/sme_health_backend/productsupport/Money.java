package com.app.sme_health_backend.productsupport;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * Exact money and quantity helpers. Amounts are stored at scale 4 (ledger scale) and accepted from clients
 * as plain decimal strings with at most 2 fraction digits (PKR minor unit). Nothing is silently rounded on input.
 */
public final class Money {
    private Money() { }

    public static final int SCALE = 4;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);
    public static final BigDecimal MAX = new BigDecimal("999999999999.99");
    private static final Pattern AMOUNT = Pattern.compile("(?:0|[1-9][0-9]{0,11})(?:[.][0-9]{1,2})?");
    private static final Pattern QUANTITY = Pattern.compile("(?:0|[1-9][0-9]{0,8})(?:[.][0-9]{1,6})?");

    public static BigDecimal amount(String raw, String field, boolean zeroAllowed) {
        if (raw == null || raw.isBlank()) {
            throw DomainException.invalid(field, "required");
        }
        String value = raw.trim();
        if (!AMOUNT.matcher(value).matches()) {
            throw DomainException.invalid(field, "decimal_invalid");
        }
        BigDecimal parsed = new BigDecimal(value).setScale(SCALE, RoundingMode.UNNECESSARY);
        if (parsed.signum() < 0 || (!zeroAllowed && parsed.signum() == 0) || parsed.compareTo(MAX) > 0) {
            throw DomainException.invalid(field, "amount_out_of_range");
        }
        return parsed;
    }

    public static BigDecimal quantity(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw DomainException.invalid(field, "required");
        }
        String value = raw.trim();
        if (!QUANTITY.matcher(value).matches()) {
            throw DomainException.invalid(field, "decimal_invalid");
        }
        BigDecimal parsed = new BigDecimal(value).setScale(6, RoundingMode.UNNECESSARY);
        if (parsed.signum() <= 0) {
            throw DomainException.invalid(field, "quantity_invalid");
        }
        return parsed;
    }

    public static BigDecimal nz(BigDecimal value) {
        return value == null ? ZERO : value.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static String str(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() < 2) {
            stripped = stripped.setScale(2, RoundingMode.UNNECESSARY);
        }
        return stripped.toPlainString();
    }

    /** Ratio as a percentage with 2 decimals, or null when the denominator is zero/unknown. */
    public static BigDecimal pct(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() == 0) {
            return null;
        }
        return numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 2, RoundingMode.HALF_UP);
    }
}
