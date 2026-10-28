package com.app.sme_health_backend.financialquery.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class FinancialQueryValidationException extends RuntimeException {

    private final Map<String, String> errors;

    public FinancialQueryValidationException(String field, String message) {
        super(message);
        this.errors = Map.of(field, message);
    }

    public FinancialQueryValidationException(Map<String, String> errors) {
        super(errors != null && !errors.isEmpty() ? errors.values().iterator().next() : "Validation error");
        this.errors = errors != null ? Collections.unmodifiableMap(new LinkedHashMap<>(errors)) : Collections.emptyMap();
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
