package com.app.sme_health_backend.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AesGcmSensitiveDataCipherTest {

    private static final String KEY_K1 = Base64.getEncoder().encodeToString(
            "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8)
    );
    private static final String KEY_K2 = Base64.getEncoder().encodeToString(
            "12345678901234567890123456789099".getBytes(StandardCharsets.UTF_8)
    );

    private static final String AAD_FULL_NAME = "FinSight|AppUser|fullName";
    private static final String AAD_PHONE = "FinSight|BusinessProfile|whatsappNumber";

    private CryptoProperties properties;
    private LocalKeyringProvider keyringProvider;
    private AesGcmSensitiveDataCipher cipher;

    @BeforeEach
    void setUp() {
        properties = new CryptoProperties();
        properties.setActiveKeyId("k1");
        properties.setAllowLegacyPlaintext(false);
        properties.setKeys(Map.of("k1", KEY_K1, "k2", KEY_K2));

        keyringProvider = new LocalKeyringProvider(properties);
        keyringProvider.initialize();

        cipher = new AesGcmSensitiveDataCipher(keyringProvider, properties);
    }

    @Test
    @DisplayName("Exact roundtrip with standard ASCII text")
    void testExactRoundtrip() {
        String plaintext = "John Doe";
        String ciphertext = cipher.encrypt(plaintext, AAD_FULL_NAME);

        assertNotNull(ciphertext);
        assertTrue(ciphertext.startsWith("enc:v1:k1:"));
        assertTrue(cipher.isEncrypted(ciphertext));

        String decrypted = cipher.decrypt(ciphertext, AAD_FULL_NAME);
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Unicode roundtrip with accented and special characters")
    void testUnicodeRoundtrip() {
        String plaintext = "Müller & François — Test 🚀 Special #!@$%^&*()";
        String ciphertext = cipher.encrypt(plaintext, AAD_FULL_NAME);

        String decrypted = cipher.decrypt(ciphertext, AAD_FULL_NAME);
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Urdu / Arabic full name roundtrip")
    void testUrduArabicFullNameRoundtrip() {
        String plaintext = "محمد سلمان خان فاروقی";
        String ciphertext = cipher.encrypt(plaintext, AAD_FULL_NAME);

        String decrypted = cipher.decrypt(ciphertext, AAD_FULL_NAME);
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Fresh random nonce ensures different ciphertexts for identical plaintext")
    void testFreshNonceUniqueness() {
        String plaintext = "Identical PII String";
        String cipher1 = cipher.encrypt(plaintext, AAD_FULL_NAME);
        String cipher2 = cipher.encrypt(plaintext, AAD_FULL_NAME);

        assertNotEquals(cipher1, cipher2, "Encrypting the same plaintext twice must produce different ciphertexts");
        assertEquals(plaintext, cipher.decrypt(cipher1, AAD_FULL_NAME));
        assertEquals(plaintext, cipher.decrypt(cipher2, AAD_FULL_NAME));
    }

    @Test
    @DisplayName("Tampered ciphertext payload fails authentication")
    void testCiphertextTamperFails() {
        String plaintext = "Confidential Data";
        String token = cipher.encrypt(plaintext, AAD_FULL_NAME);

        // Parse token and flip a bit in ciphertext portion
        String[] parts = token.split(":");
        byte[] payload = Base64.getDecoder().decode(parts[3]);
        // Ciphertext starts after 12-byte IV and ends 16 bytes before end
        payload[14] ^= 0x01; // flip bit in ciphertext
        String tamperedToken = parts[0] + ":" + parts[1] + ":" + parts[2] + ":" + Base64.getEncoder().encodeToString(payload);

        DecryptionException ex = assertThrows(DecryptionException.class,
                () -> cipher.decrypt(tamperedToken, AAD_FULL_NAME));
        assertTrue(ex.getMessage().contains("Authentication failed"));
    }

    @Test
    @DisplayName("Tampered GCM authentication tag fails authentication")
    void testGcmTagTamperFails() {
        String plaintext = "Confidential Data";
        String token = cipher.encrypt(plaintext, AAD_FULL_NAME);

        String[] parts = token.split(":");
        byte[] payload = Base64.getDecoder().decode(parts[3]);
        // Last 16 bytes are the tag; flip bit in the last byte
        payload[payload.length - 1] ^= 0x01;
        String tamperedToken = parts[0] + ":" + parts[1] + ":" + parts[2] + ":" + Base64.getEncoder().encodeToString(payload);

        DecryptionException ex = assertThrows(DecryptionException.class,
                () -> cipher.decrypt(tamperedToken, AAD_FULL_NAME));
        assertTrue(ex.getMessage().contains("Authentication failed"));
    }

    @Test
    @DisplayName("Tampered nonce fails authentication")
    void testNonceTamperFails() {
        String plaintext = "Confidential Data";
        String token = cipher.encrypt(plaintext, AAD_FULL_NAME);

        String[] parts = token.split(":");
        byte[] payload = Base64.getDecoder().decode(parts[3]);
        // First 12 bytes are the nonce; flip bit in byte 0
        payload[0] ^= 0x01;
        String tamperedToken = parts[0] + ":" + parts[1] + ":" + parts[2] + ":" + Base64.getEncoder().encodeToString(payload);

        DecryptionException ex = assertThrows(DecryptionException.class,
                () -> cipher.decrypt(tamperedToken, AAD_FULL_NAME));
        assertTrue(ex.getMessage().contains("Authentication failed"));
    }

    @Test
    @DisplayName("Invalid Base64 in ciphertext envelope fails with DecryptionException")
    void testInvalidBase64Fails() {
        String malformedToken = "enc:v1:k1:not-valid-base64-payload!!!";
        assertThrows(DecryptionException.class, () -> cipher.decrypt(malformedToken, AAD_FULL_NAME));
    }

    @Test
    @DisplayName("Malformed envelope syntax fails with DecryptionException")
    void testMalformedEnvelopeSyntaxFails() {
        assertThrows(DecryptionException.class, () -> cipher.decrypt("enc:invalid", AAD_FULL_NAME));
        assertThrows(DecryptionException.class, () -> cipher.decrypt("enc::k1:AAAA", AAD_FULL_NAME));
        assertThrows(DecryptionException.class, () -> cipher.decrypt("enc:v1:", AAD_FULL_NAME));
        assertThrows(DecryptionException.class, () -> cipher.decrypt("enc:v1:k1", AAD_FULL_NAME));
    }

    @Test
    @DisplayName("Unsupported format version fails with DecryptionException")
    void testUnsupportedFormatVersionFails() {
        String v2Token = "enc:v2:k1:" + Base64.getEncoder().encodeToString(new byte[32]);
        assertThrows(DecryptionException.class, () -> cipher.decrypt(v2Token, AAD_FULL_NAME));
    }

    @Test
    @DisplayName("Invalid key ID syntax in envelope fails with DecryptionException")
    void testInvalidKeyIdSyntaxFails() {
        String invalidKeyToken = "enc:v1:k@#invalid:" + Base64.getEncoder().encodeToString(new byte[32]);
        assertThrows(DecryptionException.class, () -> cipher.decrypt(invalidKeyToken, AAD_FULL_NAME));
    }

    @Test
    @DisplayName("Unknown key ID in envelope fails with UnknownKeyIdException")
    void testUnknownKeyIdFails() {
        String tokenWithKey99 = "enc:v1:k99:" + Base64.getEncoder().encodeToString(new byte[32]);
        assertThrows(UnknownKeyIdException.class, () -> cipher.decrypt(tokenWithKey99, AAD_FULL_NAME));
    }

    @Test
    @DisplayName("Wrong key fails authentication")
    void testWrongKeyFails() {
        String plaintext = "Sensitive Data";
        String token = cipher.encrypt(plaintext, AAD_FULL_NAME);

        // Substitute key ID in envelope from k1 to k2 (different key bytes)
        String tokenWithKey2 = token.replace("enc:v1:k1:", "enc:v1:k2:");
        assertThrows(DecryptionException.class, () -> cipher.decrypt(tokenWithKey2, AAD_FULL_NAME));
    }

    @Test
    @DisplayName("Wrong AAD fails authentication (prevents cross-field transplant)")
    void testWrongAadFails() {
        String plaintext = "+923001234567";
        String token = cipher.encrypt(plaintext, AAD_PHONE);

        // Attempt to decrypt phone ciphertext using AppUser.fullName AAD
        DecryptionException ex = assertThrows(DecryptionException.class,
                () -> cipher.decrypt(token, AAD_FULL_NAME));
        assertTrue(ex.getMessage().contains("Authentication failed"));
    }

    @Test
    @DisplayName("Null handling returns null for both encrypt and decrypt")
    void testNullHandling() {
        assertNull(cipher.encrypt(null, AAD_FULL_NAME));
        assertNull(cipher.decrypt(null, AAD_FULL_NAME));
        assertFalse(cipher.isEncrypted(null));
        assertFalse(cipher.isMalformedEncryptedToken(null));
    }

    @Test
    @DisplayName("Empty string handling roundtrips cleanly")
    void testEmptyStringHandling() {
        String empty = "";
        String token = cipher.encrypt(empty, AAD_FULL_NAME);
        assertTrue(token.startsWith("enc:v1:k1:"));
        String decrypted = cipher.decrypt(token, AAD_FULL_NAME);
        assertEquals("", decrypted);
    }

    @Test
    @DisplayName("Legacy plaintext mode returns unencrypted values as-is")
    void testLegacyPlaintextMode() {
        properties.setAllowLegacyPlaintext(true);
        String legacyValue = "Legacy Plaintext Name";
        String decrypted = cipher.decrypt(legacyValue, AAD_FULL_NAME);
        assertEquals(legacyValue, decrypted);
    }

    @Test
    @DisplayName("Strict mode rejects legacy plaintext with DecryptionException")
    void testStrictModeRejectsPlaintext() {
        properties.setAllowLegacyPlaintext(false);
        String legacyValue = "Legacy Plaintext Name";
        DecryptionException ex = assertThrows(DecryptionException.class,
                () -> cipher.decrypt(legacyValue, AAD_FULL_NAME));
        assertTrue(ex.getMessage().contains("strict mode"));
    }

    @Test
    @DisplayName("Malformed reserved token starting with enc: fails even in legacy mode")
    void testMalformedReservedTokenFailsInLegacyMode() {
        properties.setAllowLegacyPlaintext(true);
        String malformedToken = "enc:corrupted:something";
        assertTrue(cipher.isMalformedEncryptedToken(malformedToken));
        assertThrows(DecryptionException.class, () -> cipher.decrypt(malformedToken, AAD_FULL_NAME));
    }

    @Test
    @DisplayName("Key rotation: k1 ciphertext can still be read after k2 becomes active, and new writes use k2")
    void testKeyRotation() {
        String text = "Rotation Test User";
        // 1. Write with k1
        String tokenK1 = cipher.encrypt(text, AAD_FULL_NAME);
        assertTrue(tokenK1.startsWith("enc:v1:k1:"));

        // 2. Rotate active key to k2
        keyringProvider.setActiveKeyId("k2");

        // 3. Verify old k1 ciphertext still decrypts
        assertEquals(text, cipher.decrypt(tokenK1, AAD_FULL_NAME));

        // 4. Verify new writes use k2
        String tokenK2 = cipher.encrypt(text, AAD_FULL_NAME);
        assertTrue(tokenK2.startsWith("enc:v1:k2:"));
        assertEquals(text, cipher.decrypt(tokenK2, AAD_FULL_NAME));

        // 5. Re-encrypt old value with k2
        String reEncrypted = cipher.encryptWithKey(cipher.decrypt(tokenK1, AAD_FULL_NAME), AAD_FULL_NAME, "k2");
        assertTrue(reEncrypted.startsWith("enc:v1:k2:"));
        assertEquals(text, cipher.decrypt(reEncrypted, AAD_FULL_NAME));
    }
}
