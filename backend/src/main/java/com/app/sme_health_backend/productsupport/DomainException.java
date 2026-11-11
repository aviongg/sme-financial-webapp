package com.app.sme_health_backend.productsupport;

import java.util.Map;

/**
 * Typed domain failure for the product-completion modules (F04 lifecycle, F08 ledger, F05/F09 actuals,
 * budgets/goals, intelligence, copilot tools). The code is a stable machine identifier; the HTTP status is
 * explicit; field errors are optional.
 */
public class DomainException extends RuntimeException {
    private final String code;
    private final int status;
    private final Map<String, String> errors;

    public DomainException(String code, int status, String message) {
        this(code, status, message, Map.of());
    }

    public DomainException(String code, int status, String message, Map<String, String> errors) {
        super(message);
        this.code = code;
        this.status = status;
        this.errors = errors == null ? Map.of() : Map.copyOf(errors);
    }

    public static DomainException invalid(String field, String reason) {
        return new DomainException("validation_failed", 400, "The request is invalid.", Map.of(field, reason));
    }

    public static DomainException notFound(String what) {
        return new DomainException("not_found", 404, what + " was not found.");
    }

    public static DomainException conflict(String code, String message) {
        return new DomainException(code, 409, message);
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }

    public Map<String, String> errors() {
        return errors;
    }
}
