package com.app.sme_health_backend.crypto;

/**
 * Thrown when attempting to decrypt ciphertext referencing a key ID not present in the keyring.
 */
public class UnknownKeyIdException extends DecryptionException {

    public UnknownKeyIdException(String message) {
        super(message);
    }

    public UnknownKeyIdException(String message, Throwable cause) {
        super(message, cause);
    }
}
