package com.app.sme_health_backend.documents.exception;

public class StorageCollisionException extends DocumentStorageException {
    public StorageCollisionException(String message) {
        super(message);
    }

    public StorageCollisionException(String message, Throwable cause) {
        super(message, cause);
    }
}
