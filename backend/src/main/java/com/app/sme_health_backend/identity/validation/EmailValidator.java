package com.app.sme_health_backend.identity.validation;

import java.util.Locale;
import java.util.regex.Pattern;

public final class EmailValidator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    private EmailValidator() {
    }

    public static String normalizeAndValidate(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email address is required");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 320) {
            throw new IllegalArgumentException("Email address must not exceed 320 characters");
        }
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid email address format: " + email);
        }
        return normalized;
    }
}
