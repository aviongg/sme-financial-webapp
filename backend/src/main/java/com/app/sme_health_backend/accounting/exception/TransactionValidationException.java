package com.app.sme_health_backend.accounting.exception;

import java.util.Collections;
import java.util.Map;

public class TransactionValidationException extends RuntimeException {
    private final Map<String, String> errors;

    public TransactionValidationException(Map<String, String> errors) {
        super("One or more request parameters are invalid");
        this.errors = Collections.unmodifiableMap(errors);
    }

    public TransactionValidationException(String field, String error) {
        super("One or more request parameters are invalid");
        this.errors = Collections.singletonMap(field, error);
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
