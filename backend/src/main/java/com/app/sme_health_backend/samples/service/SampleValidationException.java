package com.app.sme_health_backend.samples.service;

import java.util.Map;

public class SampleValidationException extends RuntimeException {
    private final Map<String, String> errors;

    public SampleValidationException(String field, String code) {
        super("The activity planning request contains an invalid value.");
        this.errors = Map.of(field, code);
    }

    public Map<String, String> errors() {
        return errors;
    }
}
