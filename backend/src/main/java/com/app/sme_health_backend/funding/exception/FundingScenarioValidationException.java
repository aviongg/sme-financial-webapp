package com.app.sme_health_backend.funding.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class FundingScenarioValidationException extends RuntimeException {
    private final Map<String, String> errors;

    public FundingScenarioValidationException(String field, String message) {
        super(message);
        this.errors = Map.of(field, message);
    }

    public FundingScenarioValidationException(Map<String, String> errors) {
        super(errors != null && !errors.isEmpty() ? errors.values().iterator().next() : "Scenario validation failed");
        this.errors = errors == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
