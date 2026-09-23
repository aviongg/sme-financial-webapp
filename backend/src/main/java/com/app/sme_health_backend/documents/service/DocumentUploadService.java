package com.app.sme_health_backend.documents.service;

import com.app.sme_health_backend.documents.entity.UploadedDocument;
import com.app.sme_health_backend.documents.exception.DocumentNotFoundException;
import com.app.sme_health_backend.documents.exception.DocumentValidationException;
import com.app.sme_health_backend.documents.processing.AsyncDocumentProcessingService;
import com.app.sme_health_backend.documents.processing.DocumentStatus;
import com.app.sme_health_backend.documents.repository.UploadedDocumentRepository;
import com.app.sme_health_backend.documents.storage.DocumentStorageService;
import com.app.sme_health_backend.documents.storage.StoredFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class DocumentUploadService {

    public static final int MAX_BULK_DOCUMENTS = 10;

    private final UploadedDocumentRepository repository;
    private final DocumentStorageService storageService;
    private final AsyncDocumentProcessingService asyncProcessingService;

    public DocumentUploadService(
            UploadedDocumentRepository repository,
            DocumentStorageService storageService,
            AsyncDocumentProcessingService asyncProcessingService
    ) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.storageService = Objects.requireNonNull(storageService, "storageService is required");
        this.asyncProcessingService = Objects.requireNonNull(asyncProcessingService, "asyncProcessingService is required");
    }

    @Transactional
    public UploadedDocument uploadSingle(UUID userId, MultipartFile file, String documentTypeHint) {
        if (userId == null) {
            throw new DocumentValidationException("User ID is required");
        }
        if (file == null || file.isEmpty()) {
            throw new DocumentValidationException("File is required");
        }

        StoredFile stored = storageService.store(userId, file);

        UploadedDocument doc = new UploadedDocument();
        UUID docId = UUID.randomUUID();
        doc.setId(docId);
        doc.setUserId(userId);
        doc.setFileUrl(storageService.resolveFileUrl(docId));
        doc.setDocumentTypeHint(sanitizeHint(documentTypeHint));
        doc.setProcessingStatus(DocumentStatus.pending);
        doc.setOriginalFilename(stored.originalFilename());
        doc.setContentType(stored.contentType());
        doc.setFileSizeBytes(stored.sizeBytes());
        doc.setStoragePath(stored.storagePath());

        UploadedDocument saved = repository.save(doc);

        asyncProcessingService.processAfterCommit(saved.getId());

        return saved;
    }

    @Transactional
    public List<UploadedDocument> uploadBulk(UUID userId, List<MultipartFile> files, String documentTypeHint) {
        if (userId == null) {
            throw new DocumentValidationException("User ID is required");
        }
        if (files == null || files.isEmpty()) {
            throw new DocumentValidationException("At least one document file is required");
        }
        if (files.size() > MAX_BULK_DOCUMENTS) {
            throw new DocumentValidationException(
                    "Bulk upload limit exceeded. Maximum " + MAX_BULK_DOCUMENTS + " files per upload"
            );
        }

        List<UploadedDocument> results = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                StoredFile stored = storageService.store(userId, file);

                UploadedDocument doc = new UploadedDocument();
                UUID docId = UUID.randomUUID();
                doc.setId(docId);
                doc.setUserId(userId);
                doc.setFileUrl(storageService.resolveFileUrl(docId));
                doc.setDocumentTypeHint(sanitizeHint(documentTypeHint));
                doc.setProcessingStatus(DocumentStatus.pending);
                doc.setOriginalFilename(stored.originalFilename());
                doc.setContentType(stored.contentType());
                doc.setFileSizeBytes(stored.sizeBytes());
                doc.setStoragePath(stored.storagePath());

                UploadedDocument saved = repository.save(doc);
                results.add(saved);

                asyncProcessingService.processAfterCommit(saved.getId());
            }
        }

        return results;
    }

    @Transactional(readOnly = true)
    public UploadedDocument getDocument(UUID userId, UUID documentId) {
        if (userId == null) throw new DocumentValidationException("User ID is required");
        if (documentId == null) throw new DocumentValidationException("Document ID is required");

        return repository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + documentId));
    }

    @Transactional(readOnly = true)
    public List<UploadedDocument> listDocuments(UUID userId, DocumentStatus status, String month) {
        if (userId == null) throw new DocumentValidationException("User ID is required");

        if (status != null && month != null && !month.isBlank()) {
            return repository.findByUserIdAndProcessingStatusAndLinkedMonthOrderByUploadTimestampDesc(userId, status, month);
        } else if (status != null) {
            return repository.findByUserIdAndProcessingStatusOrderByUploadTimestampDesc(userId, status);
        } else if (month != null && !month.isBlank()) {
            return repository.findByUserIdAndLinkedMonthOrderByUploadTimestampDesc(userId, month);
        } else {
            return repository.findByUserIdOrderByUploadTimestampDesc(userId);
        }
    }

    @Transactional
    public UploadedDocument retryProcessing(UUID userId, UUID documentId) {
        UploadedDocument doc = getDocument(userId, documentId);

        if (doc.getProcessingStatus() == DocumentStatus.confirmed) {
            throw new IllegalStateException("Cannot retry a confirmed document");
        }

        doc.setProcessingStatus(DocumentStatus.pending);
        doc.setFailureReason(null);
        UploadedDocument saved = repository.save(doc);

        asyncProcessingService.processAfterCommit(saved.getId());

        return saved;
    }

    @Transactional
    public void deleteDraft(UUID userId, UUID documentId) {
        UploadedDocument doc = getDocument(userId, documentId);

        if (doc.getProcessingStatus() == DocumentStatus.confirmed) {
            throw new IllegalStateException("Confirmed financial source documents cannot be deleted");
        }

        storageService.delete(doc.getStoragePath());
        repository.delete(doc);
    }

    @Transactional(readOnly = true)
    public byte[] getDocumentBytes(UUID userId, UUID documentId) {
        UploadedDocument doc = getDocument(userId, documentId);
        return storageService.loadBytes(doc.getStoragePath());
    }

    private String sanitizeHint(String hint) {
        if (hint == null || hint.isBlank()) {
            return "unknown";
        }
        String clean = hint.trim().toLowerCase();
        return switch (clean) {
            case "receipt", "invoice", "bank_statement" -> clean;
            default -> "unknown";
        };
    }
}
