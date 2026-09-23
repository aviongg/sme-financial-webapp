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
    public static final java.time.Duration STALE_PROCESSING_TIMEOUT = java.time.Duration.ofMinutes(5);

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

        try {
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
        } catch (Exception e) {
            try {
                storageService.delete(stored.storagePath());
            } catch (Exception suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
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
        List<StoredFile> storedFiles = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    StoredFile stored = storageService.store(userId, file);
                    storedFiles.add(stored);

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
        } catch (Exception e) {
            for (StoredFile sf : storedFiles) {
                try {
                    storageService.delete(sf.storagePath());
                } catch (Exception suppressed) {
                    e.addSuppressed(suppressed);
                }
            }
            throw e;
        }
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

        if (doc.getProcessingStatus() == DocumentStatus.processing) {
            if (doc.getProcessingStartedAt() != null &&
                    doc.getProcessingStartedAt().isAfter(java.time.LocalDateTime.now().minus(STALE_PROCESSING_TIMEOUT))) {
                throw new IllegalStateException("Document is currently being processed. Please wait before retrying.");
            }
        }

        doc.setProcessingStatus(DocumentStatus.pending);
        doc.setFailureReason(null);
        doc.setProcessingStartedAt(null);
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

    @Transactional
    public UploadedDocument updateDraft(
            UUID userId,
            UUID documentId,
            com.app.sme_health_backend.documents.dto.DocumentDraftCorrectionRequest request
    ) {
        UploadedDocument doc = getDocument(userId, documentId);

        if (doc.getProcessingStatus() == DocumentStatus.confirmed) {
            throw new IllegalStateException("Cannot edit a confirmed document");
        }

        if (request != null) {
            String updatedJson = String.format(
                    "{\"date\":%s,\"amount\":%s,\"vendor_or_party\":%s,\"category\":\"%s\",\"confidence\":\"high\",\"document_type_detected\":\"%s\"}",
                    request.date() != null ? "\"" + request.date() + "\"" : "null",
                    request.amount() != null ? request.amount().toPlainString() : "null",
                    request.vendorOrParty() != null ? "\"" + escapeJson(request.vendorOrParty()) + "\"" : "null",
                    request.category() != null ? request.category() : "unknown",
                    request.documentType() != null ? request.documentType() : "unknown"
            );
            doc.setExtractedData(updatedJson);

            if (request.date() != null && request.amount() != null && request.amount().compareTo(java.math.BigDecimal.ZERO) > 0) {
                doc.setProcessingStatus(DocumentStatus.extracted);
            }
        }

        return repository.save(doc);
    }

    @Transactional(readOnly = true)
    public UploadedDocument getDocument(UUID documentId) {
        if (documentId == null) throw new DocumentValidationException("Document ID is required");
        return repository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + documentId));
    }

    @Transactional(readOnly = true)
    public byte[] getDocumentBytes(UUID userId, UUID documentId) {
        UploadedDocument doc = getDocument(userId, documentId);
        return storageService.loadBytes(doc.getStoragePath());
    }

    @Transactional(readOnly = true)
    public byte[] getDocumentBytes(UUID documentId) {
        UploadedDocument doc = getDocument(documentId);
        return storageService.loadBytes(doc.getStoragePath());
    }

    private String escapeJson(String raw) {
        return raw.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
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
