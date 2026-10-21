package com.app.sme_health_backend.recurring.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class RecurringValidationException extends RuntimeException {

    private final Map<String, String> errors;

    public RecurringValidationException(String field, String message) {
        super(message);
        this.errors = Collections.singletonMap(field, message);
    }

    public RecurringValidationException(Map<String, String> errors) {
        super(errors.isEmpty() ? "Validation failed" : errors.values().iterator().next());
        this.errors = new LinkedHashMap<>(errors);
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
