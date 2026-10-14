package com.app.sme_health_backend.accounting.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public final class CurrencyRegistry {

    private static final Map<String, Integer> CURRENCY_SCALES = new HashMap<>();

    static {
        CURRENCY_SCALES.put("PKR", 2);
        CURRENCY_SCALES.put("USD", 2);
        CURRENCY_SCALES.put("EUR", 2);
        CURRENCY_SCALES.put("GBP", 2);
        CURRENCY_SCALES.put("AED", 2);
        CURRENCY_SCALES.put("SAR", 2);
        CURRENCY_SCALES.put("JPY", 0);
        CURRENCY_SCALES.put("BHD", 3);
        CURRENCY_SCALES.put("KWD", 3);
        CURRENCY_SCALES.put("OMR", 3);
    }

    private static final BigDecimal MAX_CAPACITY = new BigDecimal("1000000000000"); // 10^12

    private CurrencyRegistry() {}

    public static boolean isSupported(String currencyCode) {
        if (currencyCode == null) {
            return false;
        }
        return CURRENCY_SCALES.containsKey(currencyCode.toUpperCase(Locale.ROOT));
    }

    public static boolean isSupported(String currencyCode, Integer currencyDecimals) {
        if (currencyCode == null || currencyDecimals == null) {
            return false;
        }
        Integer expectedScale = CURRENCY_SCALES.get(currencyCode.toUpperCase(Locale.ROOT));
        return expectedScale != null && expectedScale.intValue() == currencyDecimals;
    }

    public static String formatCanonicalDecimal(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        BigDecimal normalized = (amount.signum() == 0) ? amount.abs() : amount;
        return normalized.toPlainString();
    }

    public static int getScale(String currencyCode) {
        Integer scale = CURRENCY_SCALES.get(currencyCode != null ? currencyCode.toUpperCase(Locale.ROOT) : null);
        if (scale == null) {
            throw new IllegalArgumentException("Unsupported currency: " + currencyCode);
        }
        return scale;
    }

    public static Optional<Integer> findScale(String currencyCode) {
        if (currencyCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(CURRENCY_SCALES.get(currencyCode.toUpperCase(Locale.ROOT)));
    }

    public static String formatCanonicalZero(String currencyCode) {
        int scale = getScale(currencyCode);
        return BigDecimal.ZERO.setScale(scale, RoundingMode.UNNECESSARY).toPlainString();
    }

    public static String maskIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        String trimmed = identifier.trim();
        if (trimmed.startsWith("****")) {
            return trimmed;
        }
        if (trimmed.length() <= 4) {
            return "****" + trimmed;
        }
        return "****" + trimmed.substring(trimmed.length() - 4);
    }

    public record BalanceEvaluation(
            String formattedBalance,
            BalanceStatus status,
            String unavailableReason
    ) {
        public static BalanceEvaluation available(String formatted) {
            return new BalanceEvaluation(formatted, BalanceStatus.AVAILABLE, null);
        }

        public static BalanceEvaluation unavailable(String reason) {
            return new BalanceEvaluation(null, BalanceStatus.UNAVAILABLE, reason);
        }
    }

    public static BalanceEvaluation evaluateBalance(BigDecimal amount, String currencyCode) {
        Integer scale = findScale(currencyCode).orElse(null);
        return evaluateBalance(amount, currencyCode, scale);
    }

    public static BalanceEvaluation evaluateBalance(BigDecimal amount, String currencyCode, Integer currencyDecimals) {
        if (!isSupported(currencyCode, currencyDecimals)) {
            return BalanceEvaluation.unavailable("UNSUPPORTED_CURRENCY");
        }
        if (amount == null) {
            return BalanceEvaluation.unavailable("INTEGRITY_CHECK_FAILED");
        }

        int scale = getScale(currencyCode);

        // Capacity check: exactly 12 maximum integer digits (magnitude < 10^12)
        if (amount.abs().compareTo(MAX_CAPACITY) >= 0) {
            return BalanceEvaluation.unavailable("CAPACITY_OVERFLOW");
        }

        // Precision check: fractional digits cannot exceed declared currency decimals
        try {
            BigDecimal scaled = amount.setScale(scale, RoundingMode.UNNECESSARY);
            String formatted = (scaled.signum() == 0)
                    ? BigDecimal.ZERO.setScale(scale, RoundingMode.UNNECESSARY).toPlainString()
                    : scaled.toPlainString();
            return BalanceEvaluation.available(formatted);
        } catch (ArithmeticException e) {
            return BalanceEvaluation.unavailable("PRECISION_OVERFLOW");
        }
    }
}
