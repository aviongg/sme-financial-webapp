package com.app.sme_health_backend.documents.storage;

import com.app.sme_health_backend.documents.exception.DocumentNotFoundException;
import com.app.sme_health_backend.documents.exception.DocumentStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LocalFileSystemStorageServiceTests {

    @TempDir
    Path tempDir;

    private LocalFileSystemStorageService storageService;
    private final DocumentFileValidator validator = new DocumentFileValidator();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        storageService = new LocalFileSystemStorageService(
                tempDir.toString(),
                "http://localhost:8080",
                validator
        );
    }

    @Test
    void storesAndLoadsFileSuccessfully() throws Exception {
        byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "receipt.png", "image/png", pngBytes);

        StoredFile stored = storageService.store(userId, file);

        assertNotNull(stored);
        assertNotNull(stored.storagePath());
        assertEquals("receipt.png", stored.originalFilename());
        assertEquals(DocumentFileValidator.MIME_PNG, stored.contentType());
        assertEquals(pngBytes.length, stored.sizeBytes());
        assertTrue(Files.exists(Path.of(stored.storagePath())));

        byte[] loadedBytes = storageService.loadBytes(stored.storagePath());
        assertArrayEquals(pngBytes, loadedBytes);

        try (InputStream is = storageService.loadStream(stored.storagePath())) {
            assertNotNull(is);
            byte[] streamBytes = is.readAllBytes();
            assertArrayEquals(pngBytes, streamBytes);
        }
    }

    @Test
    void deletesStoredFileCleanly() {
        byte[] pdfBytes = "%PDF-1.4\n%sample pdf".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "invoice.pdf", "application/pdf", pdfBytes);

        StoredFile stored = storageService.store(userId, file);
        assertTrue(Files.exists(Path.of(stored.storagePath())));

        storageService.delete(stored.storagePath());
        assertFalse(Files.exists(Path.of(stored.storagePath())));

        // Second deletion should be safe / idempotent
        assertDoesNotThrow(() -> storageService.delete(stored.storagePath()));
    }

    @Test
    void loadNonExistentFileThrowsDocumentNotFoundException() {
        Path missingPath = tempDir.resolve(userId.toString()).resolve("missing.pdf");
        assertThrows(DocumentNotFoundException.class, () -> storageService.loadBytes(missingPath.toString()));
    }

    @Test
    void pathTraversalAttemptIsRejected() {
        assertThrows(DocumentStorageException.class,
                () -> storageService.loadBytes(tempDir.getParent().resolve("secret.txt").toString()));
    }

    @Test
    void sanitizesDangerousFilenames() {
        byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "../../../etc/passwd.png", "image/png", pngBytes);

        StoredFile stored = storageService.store(userId, file);
        assertFalse(stored.originalFilename().contains(".."));
        assertFalse(stored.originalFilename().contains("/"));
    }

    @Test
    void resolvesFileUrlCorrectly() {
        UUID docId = UUID.randomUUID();
        String url = storageService.resolveFileUrl(docId);
        assertEquals("http://localhost:8080/api/documents/" + docId + "/file", url);
    }
}
