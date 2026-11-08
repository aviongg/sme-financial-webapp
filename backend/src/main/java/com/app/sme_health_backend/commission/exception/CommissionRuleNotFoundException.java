package com.app.sme_health_backend.commission.exception;

public class CommissionRuleNotFoundException extends RuntimeException {
    public CommissionRuleNotFoundException(String message) {
        super(message);
    }
}
