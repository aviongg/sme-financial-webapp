package com.app.sme_health_backend.identity.validation;

public final class PasswordValidator {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private PasswordValidator() {
    }

    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_LENGTH + " characters long");
        }
        if (password.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Password must not exceed " + MAX_LENGTH + " characters");
        }
    }
}
