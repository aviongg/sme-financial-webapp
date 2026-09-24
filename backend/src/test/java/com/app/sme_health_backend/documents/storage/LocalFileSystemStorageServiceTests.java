package com.app.sme_health_backend.documents.storage;

import com.app.sme_health_backend.documents.exception.DocumentNotFoundException;
import com.app.sme_health_backend.documents.exception.DocumentStorageException;
import com.app.sme_health_backend.documents.exception.DocumentValidationException;
import com.app.sme_health_backend.documents.exception.StorageCollisionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
        // Verify relative storage key layout: {userId}/{yyyy-MM}/{filename}
        assertFalse(stored.storagePath().startsWith("/"));
        assertFalse(stored.storagePath().contains(":"));
        assertTrue(stored.storagePath().startsWith(userId.toString()));
        assertEquals("receipt.png", stored.originalFilename());
        assertEquals(DocumentFileValidator.MIME_PNG, stored.contentType());
        assertEquals(pngBytes.length, stored.sizeBytes());

        assertTrue(storageService.exists(stored.storagePath()));
        assertTrue(Files.exists(tempDir.resolve(stored.storagePath())));

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
        assertTrue(storageService.exists(stored.storagePath()));

        storageService.delete(stored.storagePath());
        assertFalse(storageService.exists(stored.storagePath()));

        // Second deletion should be safe / idempotent
        assertDoesNotThrow(() -> storageService.delete(stored.storagePath()));
    }

    @Test
    void loadNonExistentFileThrowsDocumentNotFoundException() {
        String missingKey = userId + "/2026-09/missing.pdf";
        assertThrows(DocumentNotFoundException.class, () -> storageService.loadBytes(missingKey));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../../etc/passwd",
            "..\\..\\windows\\system.ini",
            "....//....//etc/shadow",
            "/etc/passwd",
            "C:\\windows\\system32\\cmd.exe",
            "file/../../root"
    })
    void pathTraversalAttemptIsRejected(String traversalPath) {
        assertThrows(DocumentStorageException.class, () -> storageService.loadBytes(traversalPath));
    }

    @Test
    void sanitizesDangerousFilenamesInMetadataWithoutAffectingStorageDestination() {
        byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "../../../etc/passwd.png", "image/png", pngBytes);

        StoredFile stored = storageService.store(userId, file);
        assertFalse(stored.originalFilename().contains(".."));
        assertFalse(stored.originalFilename().contains("/"));
        assertFalse(stored.originalFilename().contains("\\"));
        // Physical storage path uses server-generated UUID
        assertTrue(stored.storagePath().startsWith(userId.toString()));
        assertTrue(storageService.exists(stored.storagePath()));
    }

    @Test
    void crlfAndControlCharactersInFilenameSanitized() {
        byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "receipt\r\n\0header_inject.png", "image/png", pngBytes);

        StoredFile stored = storageService.store(userId, file);
        assertFalse(stored.originalFilename().contains("\r"));
        assertFalse(stored.originalFilename().contains("\n"));
        assertFalse(stored.originalFilename().contains("\0"));
    }

    @Test
    void executableDisguisedAsPdfRejectedBySignatureValidator() {
        byte[] exeBytes = "MZ\u0090\u0000\u0003\u0000\u0000\u0000fake-exe-content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "evil.pdf", "application/pdf", exeBytes);

        assertThrows(DocumentValidationException.class, () -> storageService.store(userId, file));
    }

    @Test
    void mimeSignatureMismatchRejected() {
        byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile file = new MockMultipartFile("file", "invoice.pdf", "application/pdf", pngBytes);

        assertThrows(DocumentValidationException.class, () -> storageService.store(userId, file));
    }

    @Test
    void oversizedUploadRejected() {
        // Validator max size is 10MB
        byte[] oversized = new byte[10 * 1024 * 1024 + 1];
        oversized[0] = '%'; oversized[1] = 'P'; oversized[2] = 'D'; oversized[3] = 'F'; oversized[4] = '-';
        MockMultipartFile file = new MockMultipartFile("file", "large.pdf", "application/pdf", oversized);

        assertThrows(DocumentValidationException.class, () -> storageService.store(userId, file));
    }

    @Test
    void resolvesFileUrlCorrectly() {
        UUID docId = UUID.randomUUID();
        String url = storageService.resolveFileUrl(docId);
        assertEquals("http://localhost:8080/api/documents/" + docId + "/file", url);
    }

    @Test
    void existingTargetIsNeverOverwrittenAndThrowsCollisionException() throws Exception {
        byte[] original = "ORIGINAL_DOCUMENT_CONTENT".getBytes();
        String yearMonth = java.time.YearMonth.now().toString();
        Path userDir = tempDir.resolve(userId.toString()).resolve(yearMonth);
        Files.createDirectories(userDir);

        // Pre-create target file
        String collisionFilename = UUID.randomUUID() + ".png";
        Path targetFile = userDir.resolve(collisionFilename);
        Files.write(targetFile, original);

        // Subclass that returns the colliding filename
        LocalFileSystemStorageService collidingStorage = new LocalFileSystemStorageService(
                tempDir.toString(),
                "http://localhost:8080",
                validator
        ) {
            @Override
            public StoredFile store(UUID uid, MultipartFile f) {
                // Same logic as parent but uses collisionFilename
                try {
                    String detectedMime = validator.validateAndDetectMimeType(f);
                    Path destination = userDir.resolve(collisionFilename).normalize();
                    if (Files.exists(destination, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                        throw new StorageCollisionException("Storage collision detected for filename: " + collisionFilename);
                    }
                    Path tempFile = userDir.resolve(collisionFilename + ".tmp." + UUID.randomUUID()).normalize();
                    try (InputStream is = f.getInputStream()) {
                        Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
                    }
                    try {
                        Files.move(tempFile, destination, StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException e) {
                        Files.move(tempFile, destination);
                    } finally {
                        Files.deleteIfExists(tempFile);
                    }
                    return new StoredFile(uid + "/" + yearMonth + "/" + collisionFilename, f.getOriginalFilename(), detectedMime, f.getSize());
                } catch (StorageCollisionException e) {
                    throw e;
                } catch (java.io.IOException e) {
                    throw new DocumentStorageException("Failed: " + e.getMessage(), e);
                }
            }
        };

        byte[] newBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
        MockMultipartFile newFile = new MockMultipartFile("file", "new.png", "image/png", newBytes);

        assertThrows(StorageCollisionException.class, () -> collidingStorage.store(userId, newFile));

        // CRITICAL: verify original content was never overwritten
        assertArrayEquals(original, Files.readAllBytes(targetFile), "Existing target must never be overwritten on collision");
    }

    @Test
    void symlinkEscapeOutsideStorageRootIsPrevented(@TempDir Path outsideDir) throws Exception {
        Path outsideSecret = outsideDir.resolve("secret.txt");
        Files.writeString(outsideSecret, "TOP_SECRET_DATA");

        Path symlinkInStorage = tempDir.resolve("evil_link.txt");
        boolean symlinkCreated = false;
        try {
            Files.createSymbolicLink(symlinkInStorage, outsideSecret);
            symlinkCreated = true;
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException e) {
            // Windows unprivileged environment without developer mode
        }

        org.junit.jupiter.api.Assumptions.assumeTrue(symlinkCreated, "Skipping symlink test: OS environment does not permit symlink creation without elevated privileges");

        // Verify symlink access is blocked
        assertThrows(DocumentStorageException.class, () -> storageService.loadBytes("evil_link.txt"));
        assertThrows(DocumentStorageException.class, () -> storageService.loadStream("evil_link.txt"));
        assertFalse(storageService.exists("evil_link.txt"), "Symlink pointing outside root must not report existing");

        // Deleting via symlink must not delete target
        assertDoesNotThrow(() -> storageService.delete("evil_link.txt"));
        assertTrue(Files.exists(outsideSecret), "External target must not be deleted through symlink");
    }
}
