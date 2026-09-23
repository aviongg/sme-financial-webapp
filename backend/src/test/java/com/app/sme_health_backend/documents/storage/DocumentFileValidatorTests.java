package com.app.sme_health_backend.documents.storage;

import com.app.sme_health_backend.documents.exception.DocumentValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

class DocumentFileValidatorTests {

    private DocumentFileValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DocumentFileValidator();
    }

    @Test
    void acceptsValidJpeg() {
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
        MockMultipartFile file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", jpegBytes);

        String mime = validator.validateAndDetectMimeType(file);
        assertEquals(DocumentFileValidator.MIME_JPEG, mime);
    }

    @Test
    void acceptsValidPng() {
        byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "invoice.png", "image/png", pngBytes);

        String mime = validator.validateAndDetectMimeType(file);
        assertEquals(DocumentFileValidator.MIME_PNG, mime);
    }

    @Test
    void acceptsValidWebP() {
        byte[] webpBytes = new byte[]{
                'R', 'I', 'F', 'F', 0x20, 0x00, 0x00, 0x00,
                'W', 'E', 'B', 'P', 'V', 'P', '8', ' '
        };
        MockMultipartFile file = new MockMultipartFile("file", "doc.webp", "image/webp", webpBytes);

        String mime = validator.validateAndDetectMimeType(file);
        assertEquals(DocumentFileValidator.MIME_WEBP, mime);
    }

    @Test
    void acceptsValidPdf() {
        byte[] pdfBytes = "%PDF-1.4\n%test pdf content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "statement.pdf", "application/pdf", pdfBytes);

        String mime = validator.validateAndDetectMimeType(file);
        assertEquals(DocumentFileValidator.MIME_PDF, mime);
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);
        DocumentValidationException ex = assertThrows(DocumentValidationException.class,
                () -> validator.validateAndDetectMimeType(file));
        assertTrue(ex.getMessage().contains("empty"));
    }

    @Test
    void rejectsNullFile() {
        assertThrows(DocumentValidationException.class, () -> validator.validateAndDetectMimeType(null));
    }

    @Test
    void rejectsOversizedFile() {
        MockMultipartFile file = new MockMultipartFile("file", "large.pdf", "application/pdf", new byte[10 * 1024 * 1024 + 1]) {
            @Override
            public long getSize() {
                return 10 * 1024 * 1024 + 1;
            }
        };
        DocumentValidationException ex = assertThrows(DocumentValidationException.class,
                () -> validator.validateAndDetectMimeType(file));
        assertTrue(ex.getMessage().contains("10MB"));
    }

    @Test
    void rejectsUnsupportedMimeType() {
        byte[] textBytes = "This is a plain text file, not an image or pdf".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", textBytes);

        DocumentValidationException ex = assertThrows(DocumentValidationException.class,
                () -> validator.validateAndDetectMimeType(file));
        assertTrue(ex.getMessage().contains("Unsupported document type"));
    }

    @Test
    void rejectsCorruptHeaderOrTooSmallFile() {
        byte[] smallBytes = new byte[]{0x01, 0x02};
        MockMultipartFile file = new MockMultipartFile("file", "corrupt.jpg", "image/jpeg", smallBytes);

        DocumentValidationException ex = assertThrows(DocumentValidationException.class,
                () -> validator.validateAndDetectMimeType(file));
        assertTrue(ex.getMessage().contains("too small"));
    }
}
