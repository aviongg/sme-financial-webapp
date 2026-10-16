package com.app.sme_health_backend.accounting.draft;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thrown when draft creation, update, or recording parameters fail validation.
 */
public class DraftValidationException extends RuntimeException {

    private final Map<String, String> errors;

    public DraftValidationException(String field, String message) {
        super(message);
        this.errors = Collections.singletonMap(field, message);
    }

    public DraftValidationException(Map<String, String> errors) {
        super("Validation Failed");
        this.errors = new LinkedHashMap<>(errors);
    }

    public DraftValidationException(String message, Map<String, String> errors) {
        super(message);
        this.errors = new LinkedHashMap<>(errors);
    }

    public Map<String, String> getErrors() {
        return Collections.unmodifiableMap(errors);
    }
}
