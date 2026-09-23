package com.app.sme_health_backend.documents.processing;

import com.app.sme_health_backend.documents.entity.UploadedDocument;
import com.app.sme_health_backend.documents.ocr.OcrClientException;
import com.app.sme_health_backend.documents.ocr.OcrExtraction;
import com.app.sme_health_backend.documents.ocr.OcrRequest;
import com.app.sme_health_backend.documents.repository.UploadedDocumentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaDocumentDraftStore implements DocumentDraftStore {

    private final UploadedDocumentRepository repository;

    public JpaDocumentDraftStore(UploadedDocumentRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
    }

    @Override
    @Transactional
    public Optional<OcrRequest> claimPending(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId is required");
        int updated = repository.claimStatus(documentId, DocumentStatus.pending, DocumentStatus.processing, java.time.LocalDateTime.now());
        if (updated == 0) {
            return Optional.empty();
        }

        UploadedDocument doc = repository.findById(documentId).orElse(null);
        if (doc == null) {
            return Optional.empty();
        }

        OcrExtraction.DocumentType hint = parseHint(doc.getDocumentTypeHint());
        return Optional.of(new OcrRequest(doc.getFileUrl(), hint));
    }

    @Override
    @Transactional
    public boolean saveDraftIfProcessing(UUID documentId, OcrExtraction extraction, DocumentStatus target) {
        Objects.requireNonNull(documentId, "documentId is required");
        Objects.requireNonNull(extraction, "extraction is required");
        Objects.requireNonNull(target, "target status is required");

        if (target == DocumentStatus.confirmed) {
            throw new IllegalArgumentException("Draft store cannot transition to confirmed directly");
        }

        String json = toJson(extraction);
        int updated = repository.saveDraftIfStatus(documentId, DocumentStatus.processing, target, json);
        return updated == 1;
    }

    @Override
    @Transactional
    public boolean failIfProcessing(UUID documentId, OcrClientException.Reason reason) {
        Objects.requireNonNull(documentId, "documentId is required");
        String reasonStr = reason != null ? reason.name() : "unknown_failure";
        int updated = repository.failIfStatus(documentId, DocumentStatus.processing, DocumentStatus.failed, reasonStr);
        return updated == 1;
    }

    private OcrExtraction.DocumentType parseHint(String hint) {
        if (hint == null || hint.isBlank()) {
            return OcrExtraction.DocumentType.unknown;
        }
        try {
            return OcrExtraction.DocumentType.valueOf(hint.trim().toLowerCase());
        } catch (IllegalArgumentException e) {
            return OcrExtraction.DocumentType.unknown;
        }
    }

    private String toJson(OcrExtraction e) {
        return String.format(
                "{\"date\":%s,\"amount\":%s,\"vendor_or_party\":%s,\"category\":\"%s\",\"confidence\":\"%s\",\"document_type_detected\":\"%s\"}",
                e.date() != null ? "\"" + e.date() + "\"" : "null",
                e.amount() != null ? e.amount().toPlainString() : "null",
                e.vendorOrParty() != null ? "\"" + escapeJson(e.vendorOrParty()) + "\"" : "null",
                e.category(),
                e.confidence(),
                e.documentTypeDetected()
        );
    }

    private String escapeJson(String raw) {
        return raw.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
