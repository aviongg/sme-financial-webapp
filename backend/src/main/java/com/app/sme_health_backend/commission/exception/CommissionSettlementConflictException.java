package com.app.sme_health_backend.commission.exception;

public class CommissionSettlementConflictException extends RuntimeException {
    public CommissionSettlementConflictException(String message) {
        super(message);
    }
}
