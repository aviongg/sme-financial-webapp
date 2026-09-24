package com.app.sme_health_backend.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LocalKeyringProviderTest {

    private static final String VALID_KEY_32 = Base64.getEncoder().encodeToString(
            "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8)
    );
    private static final String KEY_16 = Base64.getEncoder().encodeToString(
            "16-byte-secret!!".getBytes(StandardCharsets.UTF_8)
    );
    private static final String KEY_24 = Base64.getEncoder().encodeToString(
            "24-byte-secret-is-here!!".getBytes(StandardCharsets.UTF_8)
    );

    @Test
    @DisplayName("Valid 32-byte key is accepted and active key is retrievable")
    void testValid32ByteKeyAccepted() {
        CryptoProperties props = new CryptoProperties();
        props.setActiveKeyId("k1");
        props.setKeys(Map.of("k1", VALID_KEY_32));

        LocalKeyringProvider provider = new LocalKeyringProvider(props);
        provider.initialize();

        assertEquals("k1", provider.getActiveKeyId());
        assertTrue(provider.hasKey("k1"));
        assertNotNull(provider.getActiveKey());
        assertEquals(32, provider.getActiveKey().length);
        assertArrayEquals("12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8), provider.getKey("k1"));
    }

    @Test
    @DisplayName("16-byte key is rejected with CryptoConfigurationException")
    void test16ByteKeyRejected() {
        CryptoProperties props = new CryptoProperties();
        props.setActiveKeyId("k1");
        props.setKeys(Map.of("k1", KEY_16));

        LocalKeyringProvider provider = new LocalKeyringProvider(props);
        CryptoConfigurationException ex = assertThrows(CryptoConfigurationException.class, provider::initialize);
        assertTrue(ex.getMessage().contains("must decode to exactly 32 bytes"));
    }

    @Test
    @DisplayName("24-byte key is rejected with CryptoConfigurationException")
    void test24ByteKeyRejected() {
        CryptoProperties props = new CryptoProperties();
        props.setActiveKeyId("k1");
        props.setKeys(Map.of("k1", KEY_24));

        LocalKeyringProvider provider = new LocalKeyringProvider(props);
        CryptoConfigurationException ex = assertThrows(CryptoConfigurationException.class, provider::initialize);
        assertTrue(ex.getMessage().contains("must decode to exactly 32 bytes"));
    }

    @Test
    @DisplayName("Malformed Base64 secret is rejected with CryptoConfigurationException")
    void testMalformedBase64SecretRejected() {
        CryptoProperties props = new CryptoProperties();
        props.setActiveKeyId("k1");
        props.setKeys(Map.of("k1", "not-a-valid-base64-string!!@@##"));

        LocalKeyringProvider provider = new LocalKeyringProvider(props);
        CryptoConfigurationException ex = assertThrows(CryptoConfigurationException.class, provider::initialize);
        assertTrue(ex.getMessage().contains("is not valid Base64"));
    }

    @Test
    @DisplayName("Missing active key ID causes startup failure")
    void testActiveKeyMissingRejected() {
        CryptoProperties props = new CryptoProperties();
        props.setActiveKeyId(null);
        props.setKeys(Map.of("k1", VALID_KEY_32));

        LocalKeyringProvider provider = new LocalKeyringProvider(props);
        CryptoConfigurationException ex = assertThrows(CryptoConfigurationException.class, provider::initialize);
        assertTrue(ex.getMessage().contains("Active encryption key ID is not configured"));
    }

    @Test
    @DisplayName("Active key ID absent from keyring causes startup failure")
    void testActiveKeyAbsentFromKeyringRejected() {
        CryptoProperties props = new CryptoProperties();
        props.setActiveKeyId("k2");
        props.setKeys(Map.of("k1", VALID_KEY_32));

        LocalKeyringProvider provider = new LocalKeyringProvider(props);
        CryptoConfigurationException ex = assertThrows(CryptoConfigurationException.class, provider::initialize);
        assertTrue(ex.getMessage().contains("not present in configured keyring"));
    }

    @Test
    @DisplayName("Invalid key ID syntax is rejected")
    void testInvalidKeyIdSyntaxRejected() {
        CryptoProperties props = new CryptoProperties();
        props.setActiveKeyId("invalid key ID with spaces");
        props.setKeys(Map.of("invalid key ID with spaces", VALID_KEY_32));

        LocalKeyringProvider provider = new LocalKeyringProvider(props);
        CryptoConfigurationException ex = assertThrows(CryptoConfigurationException.class, provider::initialize);
        assertTrue(ex.getMessage().contains("is invalid"));
    }

    @Test
    @DisplayName("Empty keyring causes startup failure")
    void testEmptyKeyringRejected() {
        CryptoProperties props = new CryptoProperties();
        props.setActiveKeyId("k1");
        props.setKeys(Map.of());

        LocalKeyringProvider provider = new LocalKeyringProvider(props);
        CryptoConfigurationException ex = assertThrows(CryptoConfigurationException.class, provider::initialize);
        assertTrue(ex.getMessage().contains("keyring is empty"));
    }

    @Test
    @DisplayName("Blank key secret causes startup failure")
    void testBlankKeySecretRejected() {
        CryptoProperties props = new CryptoProperties();
        props.setActiveKeyId("k1");
        props.setKeys(Map.of("k1", "   "));

        LocalKeyringProvider provider = new LocalKeyringProvider(props);
        CryptoConfigurationException ex = assertThrows(CryptoConfigurationException.class, provider::initialize);
        assertTrue(ex.getMessage().contains("must not be blank"));
    }

    @Test
    @DisplayName("getKey creates a defensive copy of the key bytes")
    void testDefensiveCopy() {
        CryptoProperties props = new CryptoProperties();
        props.setActiveKeyId("k1");
        props.setKeys(Map.of("k1", VALID_KEY_32));

        LocalKeyringProvider provider = new LocalKeyringProvider(props);
        provider.initialize();

        byte[] key1 = provider.getKey("k1");
        byte[] original = key1.clone();
        key1[0] = (byte) ~key1[0]; // mutate retrieved array

        byte[] key2 = provider.getKey("k1");
        assertArrayEquals(original, key2, "Mutating returned key array must not alter stored key");
    }

    @Test
    @DisplayName("Unknown key ID lookup throws UnknownKeyIdException")
    void testUnknownKeyLookupThrows() {
        CryptoProperties props = new CryptoProperties();
        props.setActiveKeyId("k1");
        props.setKeys(Map.of("k1", VALID_KEY_32));

        LocalKeyringProvider provider = new LocalKeyringProvider(props);
        provider.initialize();

        assertThrows(UnknownKeyIdException.class, () -> provider.getKey("unknownKey"));
        assertThrows(UnknownKeyIdException.class, () -> provider.getKey(null));
    }
}
