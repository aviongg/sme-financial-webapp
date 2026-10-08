package com.app.sme_health_backend.documents.processing;

import com.app.sme_health_backend.documents.ocr.OcrClientException;
import com.app.sme_health_backend.documents.ocr.OcrExtraction;
import com.app.sme_health_backend.documents.ocr.OcrRequest;

import java.util.Optional;
import java.util.UUID;

/**
 * Future persistence boundary; intentionally has no implementation or Spring bean yet.
 * Each operation must be atomic. A production adapter must also enforce document ownership.
 * Store OCR drafts separately from confirmed accounting records and preserve all null fields.
 */
public interface DocumentDraftStore {
    /** Atomically claims pending -> processing; empty for all other statuses or a missing document. */
    Optional<OcrRequest> claimPending(UUID documentId);

    /** Save extracted fields and transition only if this document remains processing. Never confirm. */
    boolean saveDraftIfProcessing(UUID documentId, OcrExtraction extraction, DocumentStatus target);

    /** Transition processing -> failed with a safe reason; leave newer human edits/status intact. */
    boolean failIfProcessing(UUID documentId, OcrClientException.Reason reason);
}
