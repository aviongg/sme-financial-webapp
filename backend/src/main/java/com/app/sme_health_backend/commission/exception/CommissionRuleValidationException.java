package com.app.sme_health_backend.commission.exception;

public class CommissionRuleValidationException extends RuntimeException {
    private final String field;

    public CommissionRuleValidationException(String message) {
        super(message);
        this.field = null;
    }

    public CommissionRuleValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
