package com.app.sme_health_backend.documents.service;

import com.app.sme_health_backend.documents.dto.DocumentConfirmationRequest;
import com.app.sme_health_backend.documents.entity.UploadedDocument;
import com.app.sme_health_backend.documents.exception.DocumentAlreadyConfirmedException;
import com.app.sme_health_backend.documents.exception.DocumentNotFoundException;
import com.app.sme_health_backend.documents.exception.DocumentValidationException;
import com.app.sme_health_backend.documents.processing.DocumentStatus;
import com.app.sme_health_backend.documents.repository.UploadedDocumentRepository;
import com.app.sme_health_backend.records.service.MonthlyRecordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentConfirmationService {

    private static final Set<String> VALID_CLASSIFICATIONS = Set.of(
            "revenue", "operating_expenses", "cogs", "none"
    );

    private static final Set<String> VALID_CASH_FLOW_IMPACTS = Set.of(
            "cash_inflow", "cash_outflow", "none"
    );

    private final UploadedDocumentRepository repository;
    private final MonthlyRecordService monthlyRecordService;
    private final com.app.sme_health_backend.audit.service.SecurityAuditService auditService;

    public DocumentConfirmationService(UploadedDocumentRepository repository, MonthlyRecordService monthlyRecordService,
            com.app.sme_health_backend.audit.service.SecurityAuditService auditService) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.monthlyRecordService = Objects.requireNonNull(monthlyRecordService, "monthlyRecordService is required");
        this.auditService = Objects.requireNonNull(auditService, "auditService is required");
    }
    @Transactional
    public UploadedDocument confirmDocument(UUID userId, UUID documentId, UUID actorUserId, DocumentConfirmationRequest request) {
        if (userId == null) throw new DocumentValidationException("User ID is required");
        if (documentId == null) throw new DocumentValidationException("Document ID is required");
        if (request == null) throw new DocumentValidationException("Confirmation request is required");

        Objects.requireNonNull(actorUserId, "Authenticated actor is required");
        if (request.confirmedParty() != null && (request.confirmedParty().length() > 500 || request.confirmedParty().indexOf(0) >= 0))
            throw new DocumentValidationException("Confirmed party must be at most 500 characters and contain no NUL character");
        monthlyRecordService.lockBusiness(userId);

        UploadedDocument doc = repository.findByIdAndUserIdForUpdate(documentId, userId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + documentId));

        if (doc.getProcessingStatus() == DocumentStatus.confirmed) {
            throw new DocumentAlreadyConfirmedException("Document " + documentId + " has already been confirmed");
        }

        if (doc.getProcessingStatus() != DocumentStatus.extracted && doc.getProcessingStatus() != DocumentStatus.needs_review) {
            throw new IllegalStateException(
                    "Only extracted or needs_review documents can be confirmed. Current status: " + doc.getProcessingStatus()
            );
        }

        String classification = request.targetClassification() != null
                ? request.targetClassification().trim().toLowerCase(java.util.Locale.ROOT) : "none";
        if (!VALID_CLASSIFICATIONS.contains(classification)) {
            throw new DocumentValidationException(
                    "targetClassification must be one of: revenue, operating_expenses, cogs, none"
            );
        }

        String flowImpact = request.cashFlowImpact() != null
                ? request.cashFlowImpact().trim().toLowerCase(java.util.Locale.ROOT) : "none";
        if (!VALID_CASH_FLOW_IMPACTS.contains(flowImpact)) {
            throw new DocumentValidationException(
                    "cashFlowImpact must be one of: cash_inflow, cash_outflow, none"
            );
        }

        // Apply incremental financial contribution to MonthlyRecord
        try {
            monthlyRecordService.applyDocumentContribution(userId, actorUserId, documentId, request);
        } catch (IllegalArgumentException e) {
            throw new DocumentValidationException(e.getMessage());
        }

        doc.setConfirmedData(formatConfirmedJson(request, classification, flowImpact));
        doc.setLinkedMonth(request.targetMonth());
        doc.setConfirmedAt(LocalDateTime.now());
        doc.setProcessingStatus(DocumentStatus.confirmed);

        UploadedDocument saved = repository.save(doc);

        {
            auditService.logSuccess(
                    com.app.sme_health_backend.audit.model.AuditEventType.DOCUMENT_CONFIRMED,
                    actorUserId,
                    null,
                    userId,
                    "document",
                    saved.getId().toString(),
                    java.util.Map.of(
                            "targetMonth", request.targetMonth() != null ? request.targetMonth() : "unknown",
                            "classification", classification,
                            "cashFlowImpact", flowImpact
                    )
            );
        }

        return saved;
    }

    private String formatConfirmedJson(DocumentConfirmationRequest req, String classification, String flowImpact) {
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("target_month", req.targetMonth());
        data.put("confirmed_amount", req.confirmedAmount());
        data.put("confirmed_date", req.confirmedDate() == null ? null : req.confirmedDate().toString());
        data.put("confirmed_party", req.confirmedParty());
        data.put("target_classification", classification);
        data.put("cash_flow_impact", flowImpact);
        return new tools.jackson.databind.ObjectMapper().writeValueAsString(data);
    }
}