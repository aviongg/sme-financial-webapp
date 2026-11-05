package com.app.sme_health_backend.records.service;

import java.util.Map;

public class FinancialConflictException extends RuntimeException {
    private final String code;
    private final Map<String, String> errors;
    public FinancialConflictException(String code, String message) { this(code, message, Map.of()); }
    public FinancialConflictException(String code, String message, Map<String, String> errors) {
        super(message); this.code = code; this.errors = Map.copyOf(errors);
    }
    public String code() { return code; }
    public Map<String, String> errors() { return errors; }
}