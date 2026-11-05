package com.app.sme_health_backend.inventory.service;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Exact decimal parsing for F08 Phase A. Inputs that cannot be represented exactly in the accepted
 * contract are rejected, never silently rounded. These scales are separate from MonthlyRecord's
 * NUMERIC(14,2) and from any shared accounting rounding policy.
 */
public final class InventoryDecimals {
    private InventoryDecimals() { }

    // Quantity: 1..9 integer digits, 0..6 fractional digits -> NUMERIC(15, 6) where persisted (reorder level).
    public static final int QUANTITY_SCALE = 6;
    private static final Pattern QUANTITY_INPUT = Pattern.compile("[0-9]{1,9}(?:\\.[0-9]{1,6})?");
    // Value: 1..12 integer digits; fractional digits bounded by the active WAC policy's value scale.
    private static final int VALUE_INTEGER_DIGITS = 12;

    public static BigDecimal parseQuantity(String value, String field) {
        return parse(value, field, QUANTITY_INPUT, 16);
    }

    public static BigDecimal parseValue(String value, String field, int valueScale) {
        Pattern pattern = Pattern.compile("[0-9]{1," + VALUE_INTEGER_DIGITS + "}"
                + (valueScale > 0 ? "(?:\\.[0-9]{1," + valueScale + "})?" : ""));
        return parse(value, field, pattern, VALUE_INTEGER_DIGITS + 1 + valueScale);
    }

    private static BigDecimal parse(String value, String field, Pattern pattern, int maxLength) {
        if (value == null) {
            throw new InventoryValidationException(field, "required");
        }
        if (value.length() > maxLength || !pattern.matcher(value).matches()) {
            throw new InventoryValidationException(field, "decimal_invalid");
        }
        return new BigDecimal(value);
    }

    public static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    /** Fixed-scale presentation; the value is already exact at or below this scale. */
    public static String fixed(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale).toPlainString();
    }
}
