package com.app.sme_health_backend.documents.service;

import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.audit.service.SecurityAuditService;
import com.app.sme_health_backend.documents.entity.UploadedDocument;
import com.app.sme_health_backend.documents.exception.DocumentNotFoundException;
import com.app.sme_health_backend.documents.exception.ManualReviewNotEligibleException;
import com.app.sme_health_backend.documents.processing.DocumentStatus;
import com.app.sme_health_backend.documents.repository.UploadedDocumentRepository;
import com.app.sme_health_backend.documents.storage.DocumentStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * DEF-01 manual-review fallback. When OCR extraction failed for a provider-side/transient reason, the owner may move the
 * uploaded file into the existing {@code needs_review} workflow and enter the evidence fields by hand. From there the
 * normal correction (append-only history) and explicit confirmation contract applies unchanged.
 *
 * <p>Provenance is preserved, never fabricated: {@code extracted_data} stays NULL (no OCR values exist), the original
 * {@code failure_reason} is kept, {@code extraction_provenance} is unchanged (immutable by V17), the manual draft is
 * marked {@code entry_mode = "manual"} with {@code ocr_failure_reason}, and the transition is audited.
 *
 * <p>Only an allowlist of failure reasons qualifies. {@code invalid_request} (the OCR service rejected the file as
 * unsupported/invalid), {@code file_storage_error}, unknown reasons, and files no longer present in storage never gain
 * a manual-confirm bypass. Upload security validation failures never create a document at all.
 */
@Service
public class DocumentManualReviewService {
    /** Provider unavailable/timeout, provider returned an unusable response, or our worker was interrupted. */
    public static final Set<String> ELIGIBLE_FAILURE_REASONS = Set.of("unavailable", "invalid_response", "processing_interrupted");
    public static final String ENTRY_MODE_MANUAL = "manual";

    private final UploadedDocumentRepository documents;
    private final DocumentStorageService storage;
    private final SecurityAuditService audit;
    private final ObjectMapper json;

    public DocumentManualReviewService(UploadedDocumentRepository documents, DocumentStorageService storage,
                                       SecurityAuditService audit, ObjectMapper json) {
        this.documents = documents; this.storage = storage; this.audit = audit; this.json = json;
    }

    /** Eligibility from persisted state alone (exposed on the document response; storage is re-checked on transition). */
    public static boolean isEligible(UploadedDocument doc) {
        return doc != null && doc.getProcessingStatus() == DocumentStatus.failed
                && doc.getFailureReason() != null && ELIGIBLE_FAILURE_REASONS.contains(doc.getFailureReason())
                && doc.getExtractedData() == null && doc.getReviewedData() == null
                && doc.getStoragePath() != null && !doc.getStoragePath().isBlank();
    }

    @Transactional
    public UploadedDocument startManualReview(UUID businessId, UUID documentId, UUID actorId) {
        Objects.requireNonNull(actorId, "Authenticated actor is required");
        UploadedDocument doc = documents.findByIdAndUserIdForUpdate(documentId, businessId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found"));
        if (!isEligible(doc)) {
            throw new ManualReviewNotEligibleException(
                    "Manual review is only available for a stored upload whose OCR failed because the provider was unavailable.");
        }
        boolean stored;
        try {
            stored = storage.exists(doc.getStoragePath());
        } catch (RuntimeException invalidKey) {
            stored = false;
        }
        if (!stored) throw new ManualReviewNotEligibleException("The uploaded file is no longer available for manual review.");

        String ocrFailure = doc.getFailureReason();
        ObjectNode draft = json.createObjectNode();
        draft.putNull("date");
        draft.putNull("amount");
        draft.putNull("vendor_or_party");
        draft.put("category", "unknown");
        String hint = doc.getDocumentTypeHint();
        draft.put("document_type_detected", hint != null && Set.of("receipt", "invoice", "bank_statement").contains(hint) ? hint : "unknown");
        // Confidence describes machine evidence; there is none.
        draft.putNull("confidence");
        draft.put("entry_mode", ENTRY_MODE_MANUAL);
        draft.put("ocr_failure_reason", ocrFailure);
        doc.setReviewedData(json.writeValueAsString(draft));
        doc.setProcessingStatus(DocumentStatus.needs_review);
        // failure_reason intentionally retained as OCR provenance; extracted_data stays NULL.
        UploadedDocument saved = documents.save(doc);
        audit.logSuccess(AuditEventType.DOCUMENT_MANUAL_REVIEW_STARTED, actorId, null, businessId, "document",
                documentId.toString(), Map.of("ocrFailureReason", ocrFailure, "previousStatus", DocumentStatus.failed.name(),
                        "entryMode", ENTRY_MODE_MANUAL));
        return saved;
    }
}
