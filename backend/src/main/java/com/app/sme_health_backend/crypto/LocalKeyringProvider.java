package com.app.sme_health_backend.crypto;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Local in-memory keyring provider that loads and validates versioned AES-256 keys
 * from application configuration.
 */
@Component
public class LocalKeyringProvider implements EncryptionKeyProvider {

    public static final Pattern KEY_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");
    public static final int REQUIRED_KEY_LENGTH_BYTES = 32;

    private final CryptoProperties properties;
    private final Map<String, byte[]> keyring = new ConcurrentHashMap<>();
    private volatile String activeKeyId;

    public LocalKeyringProvider(CryptoProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initialize() {
        String activeId = properties.getActiveKeyId();
        if (activeId == null || activeId.isBlank()) {
            throw new CryptoConfigurationException("Active encryption key ID is not configured (finsight.crypto.active-key-id)");
        }
        if (!KEY_ID_PATTERN.matcher(activeId).matches()) {
            throw new CryptoConfigurationException("Active key ID '" + activeId + "' is invalid. Must match pattern [A-Za-z0-9_-]{1,32}");
        }

        Map<String, String> configuredKeys = properties.getKeys();
        if (configuredKeys == null || configuredKeys.isEmpty()) {
            throw new CryptoConfigurationException("Crypto keyring is empty. At least active key '" + activeId + "' must be configured");
        }

        for (Map.Entry<String, String> entry : configuredKeys.entrySet()) {
            String keyId = entry.getKey();
            String rawSecret = entry.getValue();

            if (keyId == null || !KEY_ID_PATTERN.matcher(keyId).matches()) {
                throw new CryptoConfigurationException("Key ID '" + keyId + "' is invalid. Must match pattern [A-Za-z0-9_-]{1,32}");
            }
            if (rawSecret == null || rawSecret.isBlank()) {
                if (keyId.equals(activeId)) {
                    throw new CryptoConfigurationException("Key secret for active key ID '" + keyId + "' must not be blank");
                }
                continue;
            }

            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(rawSecret.trim());
            } catch (IllegalArgumentException e) {
                throw new CryptoConfigurationException("Key secret for key ID '" + keyId + "' is not valid Base64: " + e.getMessage());
            }

            if (decoded.length != REQUIRED_KEY_LENGTH_BYTES) {
                throw new CryptoConfigurationException("Key '" + keyId + "' must decode to exactly "
                        + REQUIRED_KEY_LENGTH_BYTES + " bytes (256-bit AES). Provided: " + decoded.length + " bytes");
            }

            keyring.put(keyId, decoded);
        }

        if (!keyring.containsKey(activeId)) {
            throw new CryptoConfigurationException("Active key ID '" + activeId + "' is not present in configured keyring");
        }

        this.activeKeyId = activeId;
    }

    @Override
    public byte[] getActiveKey() {
        return getKey(activeKeyId);
    }

    @Override
    public String getActiveKeyId() {
        return activeKeyId;
    }

    @Override
    public byte[] getKey(String keyId) {
        if (keyId == null) {
            throw new UnknownKeyIdException("Key ID cannot be null");
        }
        byte[] key = keyring.get(keyId);
        if (key == null) {
            throw new UnknownKeyIdException("Unknown key ID: " + keyId);
        }
        return Arrays.copyOf(key, key.length);
    }

    @Override
    public boolean hasKey(String keyId) {
        return keyId != null && keyring.containsKey(keyId);
    }

    /**
     * Test / programmatic helper to dynamically register a key in the keyring.
     */
    public void registerKey(String keyId, byte[] secretKeyBytes) {
        if (keyId == null || !KEY_ID_PATTERN.matcher(keyId).matches()) {
            throw new CryptoConfigurationException("Key ID '" + keyId + "' is invalid. Must match [A-Za-z0-9_-]{1,32}");
        }
        if (secretKeyBytes == null || secretKeyBytes.length != REQUIRED_KEY_LENGTH_BYTES) {
            throw new CryptoConfigurationException("Secret key must be exactly 32 bytes");
        }
        keyring.put(keyId, Arrays.copyOf(secretKeyBytes, secretKeyBytes.length));
    }

    /**
     * Test / programmatic helper to set the active key ID.
     */
    public void setActiveKeyId(String keyId) {
        if (!keyring.containsKey(keyId)) {
            throw new CryptoConfigurationException("Cannot set active key ID to '" + keyId + "': not in keyring");
        }
        this.activeKeyId = keyId;
    }
}
