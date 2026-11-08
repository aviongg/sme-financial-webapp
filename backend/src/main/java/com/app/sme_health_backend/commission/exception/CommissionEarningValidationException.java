package com.app.sme_health_backend.commission.exception;

public class CommissionEarningValidationException extends RuntimeException {
    private final String field;

    public CommissionEarningValidationException(String message) {
        super(message);
        this.field = null;
    }

    public CommissionEarningValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
