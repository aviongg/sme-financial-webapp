package com.app.sme_health_backend.whatsapp.validation;

import java.util.regex.Pattern;

public final class PhoneNumberValidator {

    // Standard ITU-T E.164 format: + followed by 7 to 15 digits, starting with non-zero
    private static final Pattern E164_PATTERN = Pattern.compile("^\\+[1-9]\\d{6,14}$");
    private static final Pattern PAKISTAN_LOCAL_PATTERN = Pattern.compile("^03\\d{9}$");

    private PhoneNumberValidator() {}

    /**
     * Normalizes and validates phone number to standard E.164 international format.
     * Supports:
     * - Standard E.164: +923001234567, +14155552671
     * - Common international prefix: 00923001234567 -> +923001234567
     * - Pakistan national format: 03001234567 -> +923001234567
     * - Tolerates standard spacing and dashes: +92 300 123-4567
     *
     * @throws IllegalArgumentException if the number is null, blank, or invalid.
     */
    public static String normalizeAndValidate(String rawNumber) {
        if (rawNumber == null || rawNumber.isBlank()) {
            throw new IllegalArgumentException("Phone number is required");
        }

        // Check for forbidden characters (letters or invalid symbols)
        if (rawNumber.matches(".*[a-zA-Z].*")) {
            throw new IllegalArgumentException("Invalid phone number: contains alphabetical characters");
        }

        // Strip common whitespace, dashes, dots, and parentheses
        String clean = rawNumber.replaceAll("[\\s\\-\\(\\)\\.]", "");

        // Convert leading '00' international access code to '+'
        if (clean.startsWith("00")) {
            clean = "+" + clean.substring(2);
        }

        // Convert Pakistani local mobile format (03XXXXXXXXX) to +923XXXXXXXXX
        if (PAKISTAN_LOCAL_PATTERN.matcher(clean).matches()) {
            clean = "+92" + clean.substring(1);
        }

        if (!clean.startsWith("+")) {
            throw new IllegalArgumentException(
                    "Invalid phone number format: '" + rawNumber + "'. International numbers must include country code starting with '+'"
            );
        }

        if (!E164_PATTERN.matcher(clean).matches()) {
            throw new IllegalArgumentException(
                    "Invalid E.164 phone number: '" + rawNumber + "'. Must contain 7 to 15 digits following country code."
            );
        }

        return clean;
    }

    /**
     * Masks phone number for privacy in application logs.
     * Example: +923001234567 -> +92300***4567
     */
    public static String mask(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return "[empty]";
        }
        int len = phoneNumber.length();
        if (len <= 6) {
            return "***";
        }
        return phoneNumber.substring(0, Math.min(6, len - 4)) + "***" + phoneNumber.substring(len - 4);
    }
}
