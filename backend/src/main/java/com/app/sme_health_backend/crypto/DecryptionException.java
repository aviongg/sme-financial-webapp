package com.app.sme_health_backend.crypto;

/**
 * Thrown when decryption fails due to corrupted ciphertext, authentication tag failure,
 * mismatched AAD, invalid encoding, or strict mode rejection.
 */
public class DecryptionException extends CryptoException {

    public DecryptionException(String message) {
        super(message);
    }

    public DecryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
