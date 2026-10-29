package com.app.sme_health_backend.risk.exception;

import java.util.Collections;
import java.util.Map;

public class RiskValidationException extends RuntimeException {

    private final Map<String, String> errors;

    public RiskValidationException(String field, String message) {
        super(message);
        this.errors = Collections.singletonMap(field, message);
    }

    public RiskValidationException(Map<String, String> errors) {
        super(errors != null && !errors.isEmpty() ? errors.values().iterator().next() : "Validation failed");
        this.errors = errors != null ? Collections.unmodifiableMap(errors) : Collections.emptyMap();
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
