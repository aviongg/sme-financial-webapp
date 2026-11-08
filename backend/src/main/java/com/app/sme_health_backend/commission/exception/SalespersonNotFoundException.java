package com.app.sme_health_backend.commission.exception;

public class SalespersonNotFoundException extends RuntimeException {
    public SalespersonNotFoundException(String message) {
        super(message);
    }
}
