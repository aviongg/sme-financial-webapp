package com.app.sme_health_backend.obligation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exception thrown when obligation input validation fails.
 */
public class ObligationValidationException extends RuntimeException {

    private final Map<String, String> errors;

    public ObligationValidationException(String field, String message) {
        super(message);
        this.errors = Collections.singletonMap(field, message);
    }

    public ObligationValidationException(Map<String, String> errors) {
        super(errors != null && !errors.isEmpty() ? errors.values().iterator().next() : "Validation failed");
        this.errors = errors != null ? Collections.unmodifiableMap(new LinkedHashMap<>(errors)) : Collections.emptyMap();
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
