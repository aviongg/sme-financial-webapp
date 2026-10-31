package com.app.sme_health_backend.samples.service;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Deterministic BigDecimal parsing and non-quantizing string serialization
 * for F09 Phase A sample and production-cost planning.
 * These exact products and sums apply no accounting or settlement rounding policy.
 */
public final class SampleDecimals {
    private SampleDecimals() { }

    // Deliberate bounded storage inputs for persisted F09 Phase A:
    // - plannedRecovery: 1..12 integer digits, 0..4 fractional digits -> NUMERIC(16, 4)
    // - quantity:       1..9 integer digits,  0..6 fractional digits -> NUMERIC(15, 6)
    // - unitCost:       1..12 integer digits, 0..6 fractional digits -> NUMERIC(18, 6)
    // Exact product plannedLineCost = quantity * unitCost has <= 21 integer and <= 12 fractional digits -> NUMERIC(33, 12).
    // Exact sum plannedCost across <= 100 lines and plannedContribution have <= 23 integer and <= 12 fractional digits -> NUMERIC(35, 12).
    private static final Pattern RECOVERY_INPUT = Pattern.compile("[0-9]{1,12}(?:\\.[0-9]{1,4})?");
    private static final Pattern QUANTITY_INPUT = Pattern.compile("[0-9]{1,9}(?:\\.[0-9]{1,6})?");
    private static final Pattern UNIT_COST_INPUT = Pattern.compile("[0-9]{1,12}(?:\\.[0-9]{1,6})?");

    public static BigDecimal parseRecovery(String value, String field) {
        return parseWithPattern(value, field, RECOVERY_INPUT, 17);
    }

    public static BigDecimal parseQuantity(String value, String field) {
        return parseWithPattern(value, field, QUANTITY_INPUT, 16);
    }

    public static BigDecimal parseUnitCost(String value, String field) {
        return parseWithPattern(value, field, UNIT_COST_INPUT, 19);
    }

    private static BigDecimal parseWithPattern(String value, String field, Pattern pattern, int maxLength) {
        if (value == null) {
            throw new SampleValidationException(field, "required");
        }
        if (value.length() > maxLength || !pattern.matcher(value).matches()) {
            throw new SampleValidationException(field, "decimal_invalid");
        }
        return new BigDecimal(value);
    }

    public static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    public static String amount(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        // Zero padding is exact presentation only; never quantize an amount to currency minor units.
        return stripped.setScale(Math.max(2, stripped.scale())).toPlainString();
    }
}
