package com.app.sme_health_backend.commission.exception;

public class SalespersonValidationException extends RuntimeException {
    private final String field;

    public SalespersonValidationException(String message) {
        super(message);
        this.field = null;
    }

    public SalespersonValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
