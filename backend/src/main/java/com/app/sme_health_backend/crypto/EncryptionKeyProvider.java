package com.app.sme_health_backend.crypto;

/**
 * Abstraction for cryptographic key resolution.
 * Allows transparent future migration from local keyring to external KMS/Vault
 * without modifying domain encryption code.
 */
public interface EncryptionKeyProvider {

    /**
     * Returns the 32-byte secret key for the currently active key ID.
     */
    byte[] getActiveKey();

    /**
     * Returns the currently active key ID.
     */
    String getActiveKeyId();

    /**
     * Returns the 32-byte secret key associated with the specified key ID.
     *
     * @throws UnknownKeyIdException if the key ID is not found
     */
    byte[] getKey(String keyId);

    /**
     * Checks if a key ID exists in the keyring.
     */
    boolean hasKey(String keyId);
}
