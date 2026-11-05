package com.app.sme_health_backend.projects.service;

import java.util.Map;

public class ProjectValidationException extends RuntimeException {
    private final Map<String, String> errors;

    public ProjectValidationException(String field, String code) {
        super("The project planning request contains an invalid value.");
        this.errors = Map.of(field, code);
    }

    public Map<String, String> errors() {
        return errors;
    }
}
