package com.app.sme_health_backend.commercial.service;

import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.audit.service.SecurityAuditService;
import com.app.sme_health_backend.commercial.dto.CommercialDocumentDraftRequest;
import com.app.sme_health_backend.commercial.dto.CommercialDocumentListResponse;
import com.app.sme_health_backend.commercial.dto.CommercialDocumentResponse;
import com.app.sme_health_backend.commercial.dto.CommercialPreviewRequest;
import com.app.sme_health_backend.commercial.dto.CommercialPreviewResponse;
import com.app.sme_health_backend.commercial.dto.CommercialTransitionRequest;
import com.app.sme_health_backend.commercial.model.CommercialDocumentKind;
import com.app.sme_health_backend.commercial.model.CommercialDocumentRecord;
import com.app.sme_health_backend.commercial.model.CommercialDocumentStatus;
import com.app.sme_health_backend.commercial.repository.CommercialDocumentRepository;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * F04 Phase A lifecycle: record, edit, finalize (numbered) and void commercial documents.
 * This service touches only the commercial_* tables; it never posts money, creates a receivable/payable,
 * records payment, or changes MonthlyRecord, document contributions, scores, projects or estimates.
 * Business and actor identity always come from the authenticated {@link BusinessAccessContext}.
 */
@Service
public class CommercialDocumentService {
    private final CommercialDocumentRepository repository;
    private final CommercialDocumentCalculator calculator;
    private final SecurityAuditService audit;

    public CommercialDocumentService(
            CommercialDocumentRepository repository,
            CommercialDocumentCalculator calculator,
            SecurityAuditService audit
    ) {
        this.repository = repository;
        this.calculator = calculator;
        this.audit = audit;
    }

    public CommercialPreviewResponse preview(CommercialDocumentKind kind, CommercialPreviewRequest request) {
        if (request == null) {
            throw new CommercialValidationException("request", "required");
        }
        return CommercialPreviewResponse.from(kind, calculator.calculateLines(request.lines()));
    }

    @Transactional(readOnly = true)
    public CommercialDocumentListResponse list(
            BusinessAccessContext context,
            CommercialDocumentKind kind,
            CommercialDocumentStatus status
    ) {
        List<CommercialDocumentResponse> items = repository.list(context.businessId(), kind, status).stream()
                .map(this::respond)
                .toList();
        return new CommercialDocumentListResponse(
                context.businessId(),
                kind,
                CommercialDocumentCalculator.BASE_CURRENCY,
                CommercialDocumentCalculator.FINANCIAL_EFFECT,
                CommercialDocumentCalculator.SETTLEMENT_AVAILABILITY,
                items
        );
    }

    @Transactional(readOnly = true)
    public CommercialDocumentResponse get(BusinessAccessContext context, CommercialDocumentKind kind, UUID id) {
        return respond(load(context, kind, id));
    }

    @Transactional
    public CommercialDocumentResponse createDraft(
            BusinessAccessContext context,
            CommercialDocumentKind kind,
            CommercialDocumentDraftRequest request
    ) {
        var content = calculator.validateDraft(request);
        UUID id = UUID.randomUUID();
        repository.insertDraft(id, context.businessId(), kind, content, context.userId(), OffsetDateTime.now());
        CommercialDocumentRecord created = load(context, kind, id);
        auditEvent(AuditEventType.COMMERCIAL_DOCUMENT_CREATED, context, created, null);
        return respond(created);
    }

    @Transactional
    public CommercialDocumentResponse updateDraft(
            BusinessAccessContext context,
            CommercialDocumentKind kind,
            UUID id,
            CommercialDocumentDraftRequest request
    ) {
        long expectedVersion = requireVersion(request == null ? null : request.expectedVersion());
        var content = calculator.validateDraft(request);
        CommercialDocumentRecord current = lockAndLoad(context, kind, id);
        if (!current.status().isEditable()) {
            throw notDraft();
        }
        requireCurrentVersion(current, expectedVersion);
        if (!repository.updateDraft(context.businessId(), kind, id, expectedVersion, content, context.userId(), OffsetDateTime.now())) {
            throw stale();
        }
        CommercialDocumentRecord updated = load(context, kind, id);
        auditEvent(AuditEventType.COMMERCIAL_DOCUMENT_UPDATED, context, updated, null);
        return respond(updated);
    }

    @Transactional
    public void deleteDraft(BusinessAccessContext context, CommercialDocumentKind kind, UUID id, Long expectedVersion) {
        long version = requireVersion(expectedVersion);
        CommercialDocumentRecord current = lockAndLoad(context, kind, id);
        if (!current.status().canDelete()) {
            throw new CommercialException(
                    "document_delete_forbidden",
                    409,
                    "Finalized and voided documents are permanent records and cannot be deleted."
            );
        }
        requireCurrentVersion(current, version);
        if (!repository.deleteDraft(context.businessId(), kind, id, version)) {
            throw stale();
        }
        auditEvent(AuditEventType.COMMERCIAL_DOCUMENT_DELETED, context, current, null);
    }

    /**
     * Assigns the next number for business + kind and freezes the document in one transaction.
     * A repeated finalize of the same document is rejected (409) without consuming another number.
     */
    @Transactional
    public CommercialDocumentResponse finalizeDraft(
            BusinessAccessContext context,
            CommercialDocumentKind kind,
            UUID id,
            CommercialTransitionRequest request
    ) {
        long expectedVersion = requireVersion(request == null ? null : request.expectedVersion());
        CommercialDocumentRecord current = lockAndLoad(context, kind, id);
        if (current.status() == CommercialDocumentStatus.FINALIZED) {
            throw new CommercialException("document_already_finalized", 409, "This document is already finalized.");
        }
        if (!current.status().canFinalize()) {
            throw invalidTransition(current.status(), CommercialDocumentStatus.FINALIZED);
        }
        requireCurrentVersion(current, expectedVersion);
        List<String> blockers = calculator.finalizeBlockers(current);
        if (!blockers.isEmpty()) {
            throw new CommercialException(
                    blockers.getFirst(),
                    422,
                    "This draft is not ready to finalize: " + String.join(", ", blockers) + "."
            );
        }

        // Lock order is always document row, then sequence row; a rollback releases the number.
        String number = kind.formatNumber(repository.allocateNumber(context.businessId(), kind));
        if (!repository.finalizeDraft(context.businessId(), kind, id, expectedVersion, number, context.userId(), OffsetDateTime.now())) {
            throw stale();
        }
        CommercialDocumentRecord finalized = load(context, kind, id);
        auditEvent(AuditEventType.COMMERCIAL_DOCUMENT_FINALIZED, context, finalized, null);
        return respond(finalized);
    }

    @Transactional
    public CommercialDocumentResponse voidFinalized(
            BusinessAccessContext context,
            CommercialDocumentKind kind,
            UUID id,
            CommercialTransitionRequest request
    ) {
        long expectedVersion = requireVersion(request == null ? null : request.expectedVersion());
        String reason = calculator.validateVoidReason(request.reason());
        CommercialDocumentRecord current = lockAndLoad(context, kind, id);
        if (current.status() == CommercialDocumentStatus.VOID) {
            throw new CommercialException("document_already_void", 409, "This document is already void.");
        }
        if (!current.status().canVoid()) {
            throw invalidTransition(current.status(), CommercialDocumentStatus.VOID);
        }
        requireCurrentVersion(current, expectedVersion);
        if (!repository.voidFinalized(context.businessId(), kind, id, expectedVersion, reason, context.userId(), OffsetDateTime.now())) {
            throw stale();
        }
        CommercialDocumentRecord voided = load(context, kind, id);
        auditEvent(AuditEventType.COMMERCIAL_DOCUMENT_VOIDED, context, voided, current.status());
        return respond(voided);
    }

    private CommercialDocumentRecord lockAndLoad(BusinessAccessContext context, CommercialDocumentKind kind, UUID id) {
        if (!repository.lockForUpdate(context.businessId(), kind, id)) {
            throw notFound();
        }
        return load(context, kind, id);
    }

    private CommercialDocumentRecord load(BusinessAccessContext context, CommercialDocumentKind kind, UUID id) {
        return repository.findWithLines(context.businessId(), kind, id).orElseThrow(CommercialDocumentService::notFound);
    }

    private CommercialDocumentResponse respond(CommercialDocumentRecord record) {
        return CommercialDocumentResponse.from(record, calculator.finalizeBlockers(record));
    }

    private void auditEvent(
            AuditEventType type,
            BusinessAccessContext context,
            CommercialDocumentRecord document,
            CommercialDocumentStatus fromStatus
    ) {
        // Counterparty names and notes are deliberately excluded from audit metadata.
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("kind", document.kind().name());
        details.put("status", document.status().name());
        details.put("version", document.version());
        details.put("lineCount", document.lineCount());
        details.put("financialEffect", CommercialDocumentCalculator.FINANCIAL_EFFECT);
        if (document.documentNumber() != null) {
            details.put("documentNumber", document.documentNumber());
        }
        if (document.total() != null) {
            details.put("total", CommercialDocumentCalculator.amount(document.total()));
        }
        if (fromStatus != null) {
            details.put("fromStatus", fromStatus.name());
        }
        audit.logSuccess(type, context.userId(), null, context.businessId(), "commercial_document",
                document.id().toString(), details);
    }

    private static long requireVersion(Long expectedVersion) {
        if (expectedVersion == null || expectedVersion < 0) {
            throw new CommercialValidationException("expectedVersion", "version_invalid");
        }
        return expectedVersion;
    }

    private static void requireCurrentVersion(CommercialDocumentRecord current, long expectedVersion) {
        if (current.version() != expectedVersion) {
            throw stale();
        }
    }

    private static CommercialException notFound() {
        return new CommercialException("document_not_found", 404, "Commercial document not found.");
    }

    private static CommercialException notDraft() {
        return new CommercialException("document_not_draft", 409, "Only draft documents can be edited.");
    }

    private static CommercialException stale() {
        return new CommercialException(
                "stale_document_version",
                409,
                "This document changed since you loaded it. Reload before trying again."
        );
    }

    private static CommercialException invalidTransition(CommercialDocumentStatus from, CommercialDocumentStatus to) {
        return new CommercialException(
                "invalid_document_transition",
                409,
                "Cannot change a " + from + " document to " + to + "."
        );
    }
}
