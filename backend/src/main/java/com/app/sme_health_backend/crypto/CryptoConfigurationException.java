package com.app.sme_health_backend.crypto;

/**
 * Thrown when cryptographic configuration is missing, malformed, or invalid at startup.
 */
public class CryptoConfigurationException extends CryptoException {

    public CryptoConfigurationException(String message) {
        super(message);
    }

    public CryptoConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
