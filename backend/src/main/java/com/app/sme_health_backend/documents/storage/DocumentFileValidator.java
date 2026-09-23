package com.app.sme_health_backend.documents.storage;

import com.app.sme_health_backend.documents.exception.DocumentValidationException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

@Component
public class DocumentFileValidator {

    public static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

    public static final String MIME_JPEG = "image/jpeg";
    public static final String MIME_PNG = "image/png";
    public static final String MIME_WEBP = "image/webp";
    public static final String MIME_PDF = "application/pdf";

    private static final Set<String> SUPPORTED_MIMES = Set.of(
            MIME_JPEG, MIME_PNG, MIME_WEBP, MIME_PDF
    );

    /**
     * Validates that the file is not empty, does not exceed 10MB,
     * and matches the magic bytes of supported formats (JPEG, PNG, WebP, PDF).
     *
     * @param file the uploaded MultipartFile
     * @return canonical detected MIME type
     */
    public String validateAndDetectMimeType(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() == 0) {
            throw new DocumentValidationException("File is empty or missing");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new DocumentValidationException("File exceeds maximum allowed size of 10MB");
        }

        byte[] header = new byte[16];
        int bytesRead;
        try (InputStream is = file.getInputStream()) {
            bytesRead = is.read(header);
        } catch (IOException e) {
            throw new DocumentValidationException("Failed to read document header: " + e.getMessage());
        }

        if (bytesRead < 4) {
            throw new DocumentValidationException("File is too small to be a valid document");
        }

        String detectedMime = detectMimeFromMagicBytes(header, bytesRead);
        if (detectedMime == null || !SUPPORTED_MIMES.contains(detectedMime)) {
            throw new DocumentValidationException(
                    "Unsupported document type. Only JPEG, PNG, WebP, and PDF documents are supported"
            );
        }

        return detectedMime;
    }

    private String detectMimeFromMagicBytes(byte[] header, int length) {
        // PDF: starts with %PDF- (0x25, 0x50, 0x44, 0x46, 0x2D)
        if (length >= 5 && header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44
                && header[3] == 0x46 && header[4] == 0x2D) {
            return MIME_PDF;
        }

        // JPEG: starts with FF D8 FF
        if (length >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF) {
            return MIME_JPEG;
        }

        // PNG: starts with 89 50 4E 47 0D 0A 1A 0A
        if (length >= 8 && (header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E
                && header[3] == 0x47 && header[4] == 0x0D && header[5] == 0x0A
                && header[6] == 0x1A && header[7] == 0x0A) {
            return MIME_PNG;
        }

        // WebP: starts with "RIFF" (bytes 0-3) and "WEBP" at bytes 8-11
        if (length >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return MIME_WEBP;
        }

        return null;
    }
}
