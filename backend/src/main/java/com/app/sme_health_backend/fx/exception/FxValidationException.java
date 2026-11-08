package com.app.sme_health_backend.fx.exception;

import java.util.Collections;
import java.util.List;

public class FxValidationException extends RuntimeException {

    private final List<String> errors;

    public FxValidationException(String message) {
        super(message);
        this.errors = List.of(message);
    }

    public FxValidationException(String field, String message) {
        super(field + ": " + message);
        this.errors = List.of(field + ": " + message);
    }

    public FxValidationException(String message, List<String> errors) {
        super(message);
        this.errors = errors != null ? Collections.unmodifiableList(errors) : List.of(message);
    }

    public List<String> getErrors() {
        return errors;
    }
}

