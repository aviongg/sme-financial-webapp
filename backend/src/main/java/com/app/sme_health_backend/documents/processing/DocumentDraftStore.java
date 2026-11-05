package com.app.sme_health_backend.documents.processing;

import com.app.sme_health_backend.documents.ocr.OcrClientException;
import com.app.sme_health_backend.documents.ocr.OcrExtraction;
import com.app.sme_health_backend.documents.ocr.OcrRequest;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Atomic persistence boundary; an OCR result owns exactly one persisted processing attempt. */
public interface DocumentDraftStore {
    record Claim(OcrRequest request, LocalDateTime processingStartedAt) {
        public Claim { Objects.requireNonNull(request); Objects.requireNonNull(processingStartedAt); }
    }
    /** Returns the database-read timestamp, never the unrounded Java timestamp supplied to the claim. */
    Optional<Claim> claimPending(UUID documentId);
    boolean saveDraftIfProcessing(UUID documentId, LocalDateTime expectedStartedAt, OcrExtraction extraction, DocumentStatus target);
    boolean failIfProcessing(UUID documentId, LocalDateTime expectedStartedAt, OcrClientException.Reason reason);
}
