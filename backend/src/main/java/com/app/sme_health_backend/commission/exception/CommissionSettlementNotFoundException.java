package com.app.sme_health_backend.commission.exception;

public class CommissionSettlementNotFoundException extends RuntimeException {
    public CommissionSettlementNotFoundException(String message) {
        super(message);
    }
}
