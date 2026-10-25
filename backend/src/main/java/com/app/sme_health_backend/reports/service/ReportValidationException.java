package com.app.sme_health_backend.reports.service;

import java.util.Map;

/** Stable machine codes only; never echoes user-supplied values. */
public class ReportValidationException extends RuntimeException {
    private final Map<String, String> errors;

    public ReportValidationException(String field, String code) {
        super("The report request is not valid.");
        this.errors = Map.of(field, code);
    }

    public Map<String, String> errors() {
        return errors;
    }
}
