package com.app.sme_health_backend.documents.storage;

import com.app.sme_health_backend.documents.exception.DocumentNotFoundException;
import com.app.sme_health_backend.documents.exception.DocumentStorageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Objects;
import java.util.UUID;

@Service
public class LocalFileSystemStorageService implements DocumentStorageService {

    private final Path rootLocation;
    private final String baseUrl;
    private final DocumentFileValidator validator;

    public LocalFileSystemStorageService(
            @Value("${app.documents.storage-dir:./storage/documents}") String storageDir,
            @Value("${app.documents.base-url:http://localhost:8080}") String baseUrl,
            DocumentFileValidator validator
    ) {
        this.rootLocation = Paths.get(storageDir).toAbsolutePath().normalize();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.validator = Objects.requireNonNull(validator, "validator is required");

        try {
            Files.createDirectories(this.rootLocation);
        } catch (IOException e) {
            throw new DocumentStorageException("Could not initialize storage directory", e);
        }
    }

    @Override
    public StoredFile store(UUID userId, MultipartFile file) {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(file, "file is required");

        String detectedMime = validator.validateAndDetectMimeType(file);
        String extension = getExtensionForMime(detectedMime);
        String secureFilename = UUID.randomUUID() + extension;

        Path userDir = rootLocation.resolve(userId.toString()).normalize();
        if (!userDir.startsWith(rootLocation)) {
            throw new DocumentStorageException("Security exception: Invalid storage path");
        }

        try {
            Files.createDirectories(userDir);
            Path destinationFile = userDir.resolve(secureFilename).normalize();
            if (!destinationFile.startsWith(userDir)) {
                throw new DocumentStorageException("Security exception: Cannot store file outside current directory");
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            }

            String originalFilename = sanitizeFilename(file.getOriginalFilename());
            return new StoredFile(
                    destinationFile.toString(),
                    originalFilename,
                    detectedMime,
                    file.getSize()
            );
        } catch (IOException e) {
            throw new DocumentStorageException("Failed to store file: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] loadBytes(String storagePath) {
        Path path = validateAndResolvePath(storagePath);
        if (!Files.exists(path)) {
            throw new DocumentNotFoundException("Stored document file not found at: " + storagePath);
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new DocumentStorageException("Failed to read document bytes: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream loadStream(String storagePath) {
        Path path = validateAndResolvePath(storagePath);
        if (!Files.exists(path)) {
            throw new DocumentNotFoundException("Stored document file not found at: " + storagePath);
        }
        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            throw new DocumentStorageException("Failed to open document stream: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return;
        }
        try {
            Path path = validateAndResolvePath(storagePath);
            Files.deleteIfExists(path);
        } catch (Exception e) {
            // Log and tolerate deletion failure
        }
    }

    @Override
    public String resolveFileUrl(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId is required");
        return baseUrl + "/api/documents/" + documentId + "/file";
    }

    private Path validateAndResolvePath(String storagePath) {
        Objects.requireNonNull(storagePath, "storagePath is required");
        Path path = Paths.get(storagePath).toAbsolutePath().normalize();
        if (!path.startsWith(rootLocation)) {
            throw new DocumentStorageException("Security exception: Access outside storage directory denied");
        }
        return path;
    }

    private String getExtensionForMime(String mimeType) {
        return switch (mimeType) {
            case DocumentFileValidator.MIME_JPEG -> ".jpg";
            case DocumentFileValidator.MIME_PNG -> ".png";
            case DocumentFileValidator.MIME_WEBP -> ".webp";
            case DocumentFileValidator.MIME_PDF -> ".pdf";
            default -> ".bin";
        };
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "unnamed_document";
        }
        // Extract base name without path segments
        String clean = Paths.get(filename).getFileName().toString();
        // Remove potentially dangerous characters
        clean = clean.replaceAll("[^a-zA-Z0-9._-]", "_");
        return clean.length() > 200 ? clean.substring(0, 200) : clean;
    }
}
