package com.app.sme_health_backend.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Versioned AES-256-GCM application encryption implementation.
 * Envelope format: enc:v1:{keyId}:{base64(12-byte IV || ciphertext || 16-byte GCM tag)}
 */
@Service
public class AesGcmSensitiveDataCipher implements SensitiveDataCipher {

    private static final Logger log = LoggerFactory.getLogger(AesGcmSensitiveDataCipher.class);

    public static final String ALGORITHM = "AES";
    public static final String TRANSFORMATION = "AES/GCM/NoPadding";
    public static final String ENVELOPE_PREFIX = "enc:";
    public static final String FORMAT_VERSION = "v1";

    public static final int NONCE_LENGTH_BYTES = 12; // 96-bit nonce
    public static final int TAG_LENGTH_BITS = 128;   // 128-bit authentication tag
    public static final int TAG_LENGTH_BYTES = 16;
    public static final int MIN_PAYLOAD_LENGTH_BYTES = NONCE_LENGTH_BYTES + TAG_LENGTH_BYTES; // 28 bytes

    public static final Pattern ENVELOPE_PATTERN = Pattern.compile("^enc:v1:([A-Za-z0-9_-]{1,32}):([A-Za-z0-9+/=]+)$");

    private final EncryptionKeyProvider keyProvider;
    private final CryptoProperties cryptoProperties;
    private final SecureRandom secureRandom;

    public AesGcmSensitiveDataCipher(EncryptionKeyProvider keyProvider, CryptoProperties cryptoProperties) {
        this.keyProvider = keyProvider;
        this.cryptoProperties = cryptoProperties;
        this.secureRandom = new SecureRandom();
    }

    @Override
    public String encrypt(String plaintext, String aad) {
        return encryptWithKey(plaintext, aad, keyProvider.getActiveKeyId());
    }

    @Override
    public String encryptWithKey(String plaintext, String aad, String keyId) {
        if (plaintext == null) {
            return null;
        }

        byte[] keyBytes = keyProvider.getKey(keyId);
        byte[] iv = new byte[NONCE_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            if (aad != null && !aad.isEmpty()) {
                cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            }

            byte[] cipherAndTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combinedPayload = new byte[NONCE_LENGTH_BYTES + cipherAndTag.length];
            System.arraycopy(iv, 0, combinedPayload, 0, NONCE_LENGTH_BYTES);
            System.arraycopy(cipherAndTag, 0, combinedPayload, NONCE_LENGTH_BYTES, cipherAndTag.length);

            String base64Payload = Base64.getEncoder().encodeToString(combinedPayload);
            return ENVELOPE_PREFIX + FORMAT_VERSION + ":" + keyId + ":" + base64Payload;
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Failed to encrypt sensitive data", e);
        }
    }

    @Override
    public String decrypt(String ciphertext, String aad) {
        if (ciphertext == null) {
            return null;
        }

        if (ciphertext.startsWith(ENVELOPE_PREFIX)) {
            Matcher matcher = ENVELOPE_PATTERN.matcher(ciphertext);
            if (!matcher.matches()) {
                throw new DecryptionException("Malformed or unsupported encrypted token: envelope syntax or version invalid");
            }

            String keyId = matcher.group(1);
            String base64Payload = matcher.group(2);

            if (!keyProvider.hasKey(keyId)) {
                throw new UnknownKeyIdException("Unknown key ID in ciphertext envelope: " + keyId);
            }

            byte[] keyBytes = keyProvider.getKey(keyId);

            byte[] payloadBytes;
            try {
                payloadBytes = Base64.getDecoder().decode(base64Payload);
            } catch (IllegalArgumentException e) {
                throw new DecryptionException("Invalid Base64 payload in ciphertext envelope");
            }

            if (payloadBytes.length < MIN_PAYLOAD_LENGTH_BYTES) {
                throw new DecryptionException("Ciphertext payload is shorter than minimum required length ("
                        + payloadBytes.length + " < " + MIN_PAYLOAD_LENGTH_BYTES + ")");
            }

            byte[] iv = Arrays.copyOfRange(payloadBytes, 0, NONCE_LENGTH_BYTES);
            byte[] cipherAndTag = Arrays.copyOfRange(payloadBytes, NONCE_LENGTH_BYTES, payloadBytes.length);

            try {
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
                GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
                cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

                if (aad != null && !aad.isEmpty()) {
                    cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
                }

                byte[] decryptedBytes = cipher.doFinal(cipherAndTag);
                return new String(decryptedBytes, StandardCharsets.UTF_8);
            } catch (AEADBadTagException e) {
                throw new DecryptionException("Authentication failed (tampered ciphertext, invalid tag, corrupted nonce, or mismatched AAD)");
            } catch (GeneralSecurityException e) {
                throw new DecryptionException("Decryption operation failed", e);
            }
        } else {
            // Unencrypted legacy value
            if (cryptoProperties.isAllowLegacyPlaintext()) {
                return ciphertext;
            } else {
                throw new DecryptionException("Legacy plaintext value rejected while allowLegacyPlaintext is disabled (strict mode)");
            }
        }
    }

    @Override
    public boolean isEncrypted(String value) {
        if (value == null) {
            return false;
        }
        return ENVELOPE_PATTERN.matcher(value).matches();
    }

    @Override
    public boolean isMalformedEncryptedToken(String value) {
        if (value == null) {
            return false;
        }
        return value.startsWith(ENVELOPE_PREFIX) && !isEncrypted(value);
    }
}
