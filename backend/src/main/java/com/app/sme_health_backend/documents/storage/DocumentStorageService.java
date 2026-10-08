package com.app.sme_health_backend.documents.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

public interface DocumentStorageService {

    StoredFile store(UUID userId, MultipartFile file);

    byte[] loadBytes(String storagePath);

    InputStream loadStream(String storagePath);

    void delete(String storagePath);

    boolean exists(String storagePath);

    String resolveFileUrl(UUID documentId);
}
