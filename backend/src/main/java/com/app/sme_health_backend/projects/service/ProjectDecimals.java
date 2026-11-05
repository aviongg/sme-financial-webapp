package com.app.sme_health_backend.projects.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * Deterministic BigDecimal parsing, division precision, and non-quantizing string serialization
 * for F05 Phase A project planning and budgeting.
 * Calculation precision is strictly separate from any accounting or settlement rounding policy.
 */
public final class ProjectDecimals {
    private ProjectDecimals() { }

    public static final int DIVISION_SCALE = 12;
    public static final RoundingMode DIVISION_ROUNDING = RoundingMode.HALF_EVEN;

    // Deliberate bounded storage inputs for persisted F05 Phase A:
    // - plannedRevenue: 1..12 integer digits, 0..4 fractional digits -> NUMERIC(16, 4)
    // - quantity:       1..9 integer digits,  0..6 fractional digits -> NUMERIC(15, 6)
    // - unitCost:       1..12 integer digits, 0..6 fractional digits -> NUMERIC(18, 6)
    // Exact product plannedLineCost = quantity * unitCost has <= 21 integer and <= 12 fractional digits -> NUMERIC(33, 12).
    // Exact sum plannedCost across <= 100 lines and plannedMargin have <= 23 integer and <= 12 fractional digits -> NUMERIC(35, 12).
    // Ratio plannedMarginPct = plannedMargin / plannedRevenue (when plannedRevenue >= 0.0001) has <= 27 integer and 12 fractional digits -> NUMERIC(39, 12).
    private static final Pattern REVENUE_INPUT = Pattern.compile("[0-9]{1,12}(?:\\.[0-9]{1,4})?");
    private static final Pattern QUANTITY_INPUT = Pattern.compile("[0-9]{1,9}(?:\\.[0-9]{1,6})?");
    private static final Pattern UNIT_COST_INPUT = Pattern.compile("[0-9]{1,12}(?:\\.[0-9]{1,6})?");

    public static BigDecimal parseRevenue(String value, String field) {
        return parseWithPattern(value, field, REVENUE_INPUT, 17);
    }

    public static BigDecimal parseQuantity(String value, String field) {
        return parseWithPattern(value, field, QUANTITY_INPUT, 16);
    }

    public static BigDecimal parseUnitCost(String value, String field) {
        return parseWithPattern(value, field, UNIT_COST_INPUT, 19);
    }

    private static BigDecimal parseWithPattern(String value, String field, Pattern pattern, int maxLength) {
        if (value == null) {
            throw new ProjectValidationException(field, "required");
        }
        if (value.length() > maxLength || !pattern.matcher(value).matches()) {
            throw new ProjectValidationException(field, "decimal_invalid");
        }
        return new BigDecimal(value);
    }

    public static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        return numerator.divide(denominator, DIVISION_SCALE, DIVISION_ROUNDING);
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
