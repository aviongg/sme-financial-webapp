package com.app.sme_health_backend.estimates.service;

import java.util.Map;

public class EstimateValidationException extends RuntimeException {
    private final Map<String, String> errors;
    public EstimateValidationException(String field, String code) {
        super("The planning estimate contains an invalid value.");
        errors = Map.of(field, code);
    }
    public Map<String, String> errors() { return errors; }
}
