package com.app.sme_health_backend.documents.processing;

import com.app.sme_health_backend.documents.ocr.OcrClient;
import com.app.sme_health_backend.documents.ocr.OcrClientException;
import com.app.sme_health_backend.documents.ocr.OcrExtraction;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * OCR orchestration; remote extraction runs outside the persistence transactions.
 * Every successful extraction remains an unapproved draft, including high-confidence extractions.
 */
public final class DocumentDraftProcessor {
    private final OcrClient client;
    private final DocumentDraftStore store;

    public DocumentDraftProcessor(OcrClient client, DocumentDraftStore store) {
        this.client = Objects.requireNonNull(client);
        this.store = Objects.requireNonNull(store);
    }

    public Optional<DocumentStatus> process(UUID documentId) {
        Objects.requireNonNull(documentId, "document ID is required");
        Optional<DocumentDraftStore.Claim> claimed = store.claimPending(documentId);
        if (claimed.isEmpty()) return Optional.empty();

        OcrExtraction extraction;
        try {
            extraction = client.extract(claimed.get().request());
            if (extraction == null) throw new OcrClientException(OcrClientException.Reason.invalid_response);
        } catch (OcrClientException exception) {
            return store.failIfProcessing(documentId, claimed.get().processingStartedAt(), exception.reason())
                    ? Optional.of(DocumentStatus.failed) : Optional.empty();
        } catch (RuntimeException exception) {
            return store.failIfProcessing(documentId, claimed.get().processingStartedAt(), OcrClientException.Reason.unavailable)
                    ? Optional.of(DocumentStatus.failed) : Optional.empty();
        }
        DocumentStatus target = extraction.isCompleteAndHighConfidence()
                ? DocumentStatus.extracted : DocumentStatus.needs_review;
        return store.saveDraftIfProcessing(documentId, claimed.get().processingStartedAt(), extraction, target) ? Optional.of(target) : Optional.empty();
    }
}
