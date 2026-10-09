package com.app.sme_health_backend.documents.exception;

public class DocumentAlreadyConfirmedException extends RuntimeException {
    public DocumentAlreadyConfirmedException(String message) {
        super(message);
    }
}
