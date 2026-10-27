package com.app.sme_health_backend.commercial.service;

import java.util.Map;

public class CommercialValidationException extends RuntimeException {
    private final Map<String, String> errors;

    public CommercialValidationException(String field, String code) {
        super("The commercial document request contains an invalid value.");
        this.errors = Map.of(field, code);
    }

    public Map<String, String> errors() {
        return errors;
    }
}
