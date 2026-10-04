package com.app.sme_health_backend.documents.service;

import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.audit.service.SecurityAuditService;
import com.app.sme_health_backend.documents.dto.*;
import com.app.sme_health_backend.documents.entity.*;
import com.app.sme_health_backend.documents.exception.*;
import com.app.sme_health_backend.documents.processing.DocumentStatus;
import com.app.sme_health_backend.documents.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.util.*;

@Service
public class DocumentReviewService {
    private static final List<String> FIELDS = List.of("date", "amount", "vendor_or_party", "category", "document_type_detected");
    private final UploadedDocumentRepository documents;
    private final DocumentCorrectionRepository corrections;
    private final SecurityAuditService audit;
    private final ObjectMapper json;
    public DocumentReviewService(UploadedDocumentRepository documents, DocumentCorrectionRepository corrections,
                                 SecurityAuditService audit, ObjectMapper json) {
        this.documents = documents; this.corrections = corrections; this.audit = audit; this.json = json;
    }

    @Transactional
    public UploadedDocument updateDraft(UUID businessId, UUID documentId, UUID actorId, DocumentDraftCorrectionRequest request) {
        Objects.requireNonNull(actorId, "Authenticated actor is required");
        UploadedDocument doc = documents.findByIdAndUserIdForUpdate(documentId, businessId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found"));
        if (doc.getProcessingStatus() != DocumentStatus.extracted && doc.getProcessingStatus() != DocumentStatus.needs_review) {
            throw new IllegalStateException("Only extracted or needs_review documents can be corrected");
        }
        if (request == null) throw new DocumentValidationException("Correction is required");
        if (request.amount() != null && (request.amount().signum() < 0 || request.amount().precision() > 18 || request.amount().scale() > 2)) {
            throw new DocumentValidationException("Amount must be non-negative with at most two decimal places");
        }
        if (request.vendorOrParty() != null && request.vendorOrParty().length() > 500) {
            throw new DocumentValidationException("Party must not exceed 500 characters");
        }
        String category = request.category() == null ? "unknown" : request.category();
        String type = request.documentType() == null ? "unknown" : request.documentType();
        if (!Set.of("sales", "expense", "purchase", "unknown").contains(category)
                || !Set.of("receipt", "invoice", "bank_statement", "unknown").contains(type)) {
            throw new DocumentValidationException("Invalid document category or type");
        }
        String previous = doc.getReviewedData() != null ? doc.getReviewedData() : doc.getExtractedData();
        ObjectNode before = previous == null ? json.createObjectNode() : (ObjectNode) json.readTree(previous);
        ObjectNode next = before.deepCopy();
        if (request.supplies("date")) next.put("date", request.date() == null ? null : request.date().toString());
        if (request.supplies("amount")) next.put("amount", request.amount());
        if (request.supplies("vendorOrParty")) next.put("vendor_or_party", request.vendorOrParty());
        if (request.supplies("category")) next.put("category", category);
        if (request.supplies("documentType")) next.put("document_type_detected", type);
        // Confidence describes machine evidence. A human edit never upgrades it.
        if (!next.has("confidence")) next.putNull("confidence");
        List<String> changed = FIELDS.stream().filter(field -> changed(before.get(field), next.get(field))).toList();
        if (changed.isEmpty()) return doc;
        String updated = json.writeValueAsString(next);
        corrections.save(new DocumentCorrection(documentId, businessId, actorId, previous, updated, changed));
        doc.setReviewedData(updated);
        // Review is a draft only. Explicit confirmation remains the sole financial posting operation.
        boolean complete = next.hasNonNull("date") && next.hasNonNull("amount")
                && next.get("amount").isNumber() && next.get("amount").decimalValue().signum() > 0;
        doc.setProcessingStatus(complete ? DocumentStatus.extracted : DocumentStatus.needs_review);
        UploadedDocument result = documents.save(doc);
        audit.logSuccess(AuditEventType.DOCUMENT_CORRECTED, actorId, null, businessId, "document", documentId.toString(),
                Map.of("changedFields", changed));
        return result;
    }

    @Transactional(readOnly = true)
    public List<DocumentCorrectionResponse> history(UUID businessId, UUID documentId, UUID actorId) {
        documents.findByIdAndUserId(documentId, businessId).orElseThrow(() -> new DocumentNotFoundException("Document not found"));
        return corrections.findByBusinessIdAndDocumentIdOrderByCorrectedAtAsc(businessId, documentId).stream()
                .map(c -> new DocumentCorrectionResponse(c.getId(), c.getPreviousData(), c.getNewData(), c.getChangedFields(),
                        c.getCorrectedAt(), Objects.equals(actorId, c.getActorUserId()))).toList();
    }

    private boolean changed(tools.jackson.databind.JsonNode before, tools.jackson.databind.JsonNode after) {
        // JSONB and Jackson may represent 20 and 20.00 differently; formatting alone is not a correction.
        if (before != null && after != null && before.isNumber() && after.isNumber()) {
            return before.decimalValue().compareTo(after.decimalValue()) != 0;
        }
        return !Objects.equals(before, after);
    }
}
