package com.app.sme_health_backend.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "finsight.crypto")
public class CryptoProperties {

    /**
     * Active key ID used for all new encryption operations (e.g. "k1", "k2").
     */
    private String activeKeyId = "k1";

    /**
     * If true, legacy plaintext values in encrypted columns are returned as-is during migration.
     * If false (strict mode), encountering legacy plaintext throws a DecryptionException.
     * Secure default is false (strict mode).
     */
    private boolean allowLegacyPlaintext = false;

    /**
     * Keyring mapping key IDs to Base64-encoded 256-bit (32-byte) secret keys.
     */
    private Map<String, String> keys = new HashMap<>();

    /**
     * Controls whether background migration runs automatically on application startup.
     * Default is false to prevent uncontrolled automatic data migrations.
     */
    private boolean autoRunMigration = false;

    public String getActiveKeyId() {
        return activeKeyId;
    }

    public void setActiveKeyId(String activeKeyId) {
        this.activeKeyId = activeKeyId;
    }

    public boolean isAllowLegacyPlaintext() {
        return allowLegacyPlaintext;
    }

    public void setAllowLegacyPlaintext(boolean allowLegacyPlaintext) {
        this.allowLegacyPlaintext = allowLegacyPlaintext;
    }

    public Map<String, String> getKeys() {
        return keys;
    }

    public void setKeys(Map<String, String> keys) {
        this.keys = keys != null ? keys : new HashMap<>();
    }

    public boolean isAutoRunMigration() {
        return autoRunMigration;
    }

    public void setAutoRunMigration(boolean autoRunMigration) {
        this.autoRunMigration = autoRunMigration;
    }
}
