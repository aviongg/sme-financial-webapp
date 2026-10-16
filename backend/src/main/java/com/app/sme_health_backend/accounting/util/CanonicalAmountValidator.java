package com.app.sme_health_backend.accounting.util;

import com.app.sme_health_backend.accounting.model.CurrencyRegistry;
import com.app.sme_health_backend.accounting.exception.TransactionValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * Shared canonical amount validator enforcing the single authoritative syntax rule:
 * - exact positive two-decimal string (\A(?:0|[1-9][0-9]*)\.[0-9]{2}\z)
 * - no surrounding whitespace or control characters (including NUL)
 * - strictly positive (> 0.00)
 * - within monetary capacity (<= 999,999,999,999.99)
 * - validated before trimming or numeric parsing
 */
public final class CanonicalAmountValidator {

    public static final Pattern CANONICAL_AMOUNT_PATTERN = Pattern.compile("\\A(?:0|[1-9][0-9]*)\\.[0-9]{2}\\z");
    public static final BigDecimal MAX_CAPACITY = new BigDecimal("999999999999.99");

    private CanonicalAmountValidator() {}

    public static BigDecimal validateCanonicalAmount(String rawAmount, String fieldName) {
        if (rawAmount == null || rawAmount.isEmpty()) {
            String msg = "confirmedAmount".equals(fieldName) ? "Confirmed amount is required" : "Amount is required";
            throw new TransactionValidationException(fieldName, msg);
        }
        if (!CANONICAL_AMOUNT_PATTERN.matcher(rawAmount).matches()) {
            throw new TransactionValidationException(
                    fieldName,
                    "Amount must be a positive decimal string with exactly two decimal places (e.g. '1500.56')"
            );
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(rawAmount);
        } catch (NumberFormatException e) {
            throw new TransactionValidationException(fieldName, "Invalid decimal format for amount");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransactionValidationException(fieldName, "Amount must be greater than zero");
        }
        if (amount.compareTo(MAX_CAPACITY) > 0) {
            throw new TransactionValidationException(fieldName, "Amount exceeds maximum capacity of 999,999,999,999.99");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }
}
