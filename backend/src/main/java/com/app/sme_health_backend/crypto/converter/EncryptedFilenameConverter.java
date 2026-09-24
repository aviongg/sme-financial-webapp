package com.app.sme_health_backend.crypto.converter;

import com.app.sme_health_backend.crypto.SensitiveDataCipher;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * JPA AttributeConverter for UploadedDocument.originalFilename bound to AAD "FinSight|UploadedDocument|originalFilename".
 */
@Component
@Converter
public class EncryptedFilenameConverter implements AttributeConverter<String, String> {

    public static final String AAD = "FinSight|UploadedDocument|originalFilename";

    private final SensitiveDataCipher cipher;

    public EncryptedFilenameConverter(SensitiveDataCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        return cipher.encrypt(attribute, AAD);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return cipher.decrypt(dbData, AAD);
    }
}
