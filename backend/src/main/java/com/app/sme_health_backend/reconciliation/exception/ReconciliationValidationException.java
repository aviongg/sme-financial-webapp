package com.app.sme_health_backend.reconciliation.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ReconciliationValidationException extends RuntimeException {

    private final Map<String, String> errors;

    public ReconciliationValidationException(String field, String message) {
        super(message);
        this.errors = Collections.singletonMap(field, message);
    }

    public ReconciliationValidationException(Map<String, String> errors) {
        super(errors.isEmpty() ? "Validation failed" : errors.values().iterator().next());
        this.errors = new LinkedHashMap<>(errors);
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
