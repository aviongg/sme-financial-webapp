package com.app.sme_health_backend.counterparty;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exception thrown when counterparty input validation fails.
 */
public class CounterpartyValidationException extends RuntimeException {

    private final Map<String, String> errors;

    public CounterpartyValidationException(String field, String message) {
        super(message);
        this.errors = Collections.singletonMap(field, message);
    }

    public CounterpartyValidationException(Map<String, String> errors) {
        super(errors != null && !errors.isEmpty() ? errors.values().iterator().next() : "Validation failed");
        this.errors = errors != null ? Collections.unmodifiableMap(new LinkedHashMap<>(errors)) : Collections.emptyMap();
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
