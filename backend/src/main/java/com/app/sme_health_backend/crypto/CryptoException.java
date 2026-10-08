package com.app.sme_health_backend.crypto;

/**
 * Root unchecked exception for all cryptographic operations and failures in FinSight.
 */
public class CryptoException extends RuntimeException {

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
