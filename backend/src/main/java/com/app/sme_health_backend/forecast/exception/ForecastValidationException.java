package com.app.sme_health_backend.forecast.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ForecastValidationException extends RuntimeException {

    private final Map<String, String> errors;

    public ForecastValidationException(String field, String message) {
        super(message);
        Map<String, String> errMap = new LinkedHashMap<>();
        errMap.put(field, message);
        this.errors = Collections.unmodifiableMap(errMap);
    }

    public ForecastValidationException(Map<String, String> errors) {
        super("Forecast validation failed");
        this.errors = Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}

