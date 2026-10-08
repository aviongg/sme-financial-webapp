package com.app.sme_health_backend.crypto;

/**
 * Service interface for encrypting and decrypting sensitive application fields.
 */
public interface SensitiveDataCipher {

    /**
     * Encrypts plaintext using the currently active key and supplied AAD.
     *
     * @param plaintext sensitive plaintext string to encrypt (may be empty string)
     * @param aad additional authenticated data to bind to the ciphertext
     * @return versioned ciphertext envelope, or null if plaintext is null
     */
    String encrypt(String plaintext, String aad);

    /**
     * Encrypts plaintext using a specific key ID and supplied AAD (used during key rotation).
     *
     * @param plaintext sensitive plaintext string to encrypt
     * @param aad additional authenticated data
     * @param keyId key ID to encrypt with
     * @return versioned ciphertext envelope
     */
    String encryptWithKey(String plaintext, String aad, String keyId);

    /**
     * Decrypts ciphertext envelope using the embedded key ID and supplied AAD.
     * In legacy-plaintext mode (allowLegacyPlaintext=true), unencrypted values are returned as-is.
     * In strict mode (allowLegacyPlaintext=false), unencrypted values throw DecryptionException.
     *
     * @param ciphertext ciphertext envelope or legacy plaintext
     * @param aad additional authenticated data expected
     * @return decrypted plaintext string, or null if input is null
     * @throws DecryptionException if ciphertext is tampered, corrupted, strict mode rejects plaintext,
     *                             or AAD does not match
     * @throws UnknownKeyIdException if key ID in ciphertext is not recognized
     */
    String decrypt(String ciphertext, String aad);

    /**
     * Returns true if the string matches the canonical versioned ciphertext envelope format.
     */
    boolean isEncrypted(String value);

    /**
     * Returns true if the string starts with the reserved "enc:" prefix but does NOT conform
     * to a valid ciphertext envelope format.
     */
    boolean isMalformedEncryptedToken(String value);
}
