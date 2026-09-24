package com.app.sme_health_backend.crypto;

import com.app.sme_health_backend.crypto.converter.EncryptedDestinationNumberConverter;
import com.app.sme_health_backend.crypto.converter.EncryptedFilenameConverter;
import com.app.sme_health_backend.crypto.converter.EncryptedFullNameConverter;
import com.app.sme_health_backend.crypto.converter.EncryptedWhatsAppNumberConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CryptoConverterTest {

    private static final String KEY_K1 = Base64.getEncoder().encodeToString(
            "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8)
    );

    private EncryptedFullNameConverter fullNameConverter;
    private EncryptedWhatsAppNumberConverter whatsAppConverter;
    private EncryptedDestinationNumberConverter destinationConverter;
    private EncryptedFilenameConverter filenameConverter;

    @BeforeEach
    void setUp() {
        CryptoProperties props = new CryptoProperties();
        props.setActiveKeyId("k1");
        props.setAllowLegacyPlaintext(true);
        props.setKeys(Map.of("k1", KEY_K1));

        LocalKeyringProvider keyring = new LocalKeyringProvider(props);
        keyring.initialize();

        AesGcmSensitiveDataCipher cipher = new AesGcmSensitiveDataCipher(keyring, props);

        fullNameConverter = new EncryptedFullNameConverter(cipher);
        whatsAppConverter = new EncryptedWhatsAppNumberConverter(cipher);
        destinationConverter = new EncryptedDestinationNumberConverter(cipher);
        filenameConverter = new EncryptedFilenameConverter(cipher);
    }

    @Test
    @DisplayName("FullNameConverter roundtrips and handles null")
    void testFullNameConverter() {
        assertNull(fullNameConverter.convertToDatabaseColumn(null));
        assertNull(fullNameConverter.convertToEntityAttribute(null));

        String raw = "Muhammad Ali";
        String db = fullNameConverter.convertToDatabaseColumn(raw);
        assertTrue(db.startsWith("enc:v1:k1:"));
        assertEquals(raw, fullNameConverter.convertToEntityAttribute(db));
    }

    @Test
    @DisplayName("WhatsAppNumberConverter roundtrips and handles null")
    void testWhatsAppNumberConverter() {
        assertNull(whatsAppConverter.convertToDatabaseColumn(null));
        assertNull(whatsAppConverter.convertToEntityAttribute(null));

        String raw = "+923001234567";
        String db = whatsAppConverter.convertToDatabaseColumn(raw);
        assertTrue(db.startsWith("enc:v1:k1:"));
        assertEquals(raw, whatsAppConverter.convertToEntityAttribute(db));
    }

    @Test
    @DisplayName("DestinationNumberConverter roundtrips and handles null")
    void testDestinationNumberConverter() {
        assertNull(destinationConverter.convertToDatabaseColumn(null));
        assertNull(destinationConverter.convertToEntityAttribute(null));

        String raw = "+923009876543";
        String db = destinationConverter.convertToDatabaseColumn(raw);
        assertTrue(db.startsWith("enc:v1:k1:"));
        assertEquals(raw, destinationConverter.convertToEntityAttribute(db));
    }

    @Test
    @DisplayName("FilenameConverter roundtrips and handles null")
    void testFilenameConverter() {
        assertNull(filenameConverter.convertToDatabaseColumn(null));
        assertNull(filenameConverter.convertToEntityAttribute(null));

        String raw = "bank_statement_2026.pdf";
        String db = filenameConverter.convertToDatabaseColumn(raw);
        assertTrue(db.startsWith("enc:v1:k1:"));
        assertEquals(raw, filenameConverter.convertToEntityAttribute(db));
    }

    @Test
    @DisplayName("Cross-converter decryption fails authentication due to unique AAD bindings")
    void testCrossConverterDecryptionFails() {
        String dbFromFullName = fullNameConverter.convertToDatabaseColumn("Secret User");
        assertThrows(DecryptionException.class, () -> whatsAppConverter.convertToEntityAttribute(dbFromFullName));
        assertThrows(DecryptionException.class, () -> destinationConverter.convertToEntityAttribute(dbFromFullName));
        assertThrows(DecryptionException.class, () -> filenameConverter.convertToEntityAttribute(dbFromFullName));

        String dbFromWhatsApp = whatsAppConverter.convertToDatabaseColumn("+923001234567");
        assertThrows(DecryptionException.class, () -> fullNameConverter.convertToEntityAttribute(dbFromWhatsApp));

        String dbFromFilename = filenameConverter.convertToDatabaseColumn("invoice.pdf");
        assertThrows(DecryptionException.class, () -> fullNameConverter.convertToEntityAttribute(dbFromFilename));
    }
}
