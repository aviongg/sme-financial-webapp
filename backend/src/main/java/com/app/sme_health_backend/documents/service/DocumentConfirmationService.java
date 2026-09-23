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

    public DocumentConfirmationService(
            UploadedDocumentRepository repository,
            MonthlyRecordService monthlyRecordService
    ) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.monthlyRecordService = Objects.requireNonNull(monthlyRecordService, "monthlyRecordService is required");
    }

    @Transactional
    public UploadedDocument confirmDocument(UUID userId, UUID documentId, DocumentConfirmationRequest request) {
        if (userId == null) throw new DocumentValidationException("User ID is required");
        if (documentId == null) throw new DocumentValidationException("Document ID is required");
        if (request == null) throw new DocumentValidationException("Confirmation request is required");

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
                ? request.targetClassification().trim().toLowerCase() : "none";
        if (!VALID_CLASSIFICATIONS.contains(classification)) {
            throw new DocumentValidationException(
                    "targetClassification must be one of: revenue, operating_expenses, cogs, none"
            );
        }

        String flowImpact = request.cashFlowImpact() != null
                ? request.cashFlowImpact().trim().toLowerCase() : "none";
        if (!VALID_CASH_FLOW_IMPACTS.contains(flowImpact)) {
            throw new DocumentValidationException(
                    "cashFlowImpact must be one of: cash_inflow, cash_outflow, none"
            );
        }

        // Apply incremental financial contribution to MonthlyRecord
        try {
            monthlyRecordService.applyDocumentContribution(
                    userId,
                    request.targetMonth(),
                    classification,
                    flowImpact,
                    request.confirmedAmount(),
                    request.initialCashBalanceEom()
            );
        } catch (IllegalArgumentException e) {
            throw new DocumentValidationException(e.getMessage());
        }

        // Record confirmed snapshot and transition status
        doc.setConfirmedData(formatConfirmedJson(request, classification, flowImpact));
        doc.setLinkedMonth(request.targetMonth());
        doc.setConfirmedAt(LocalDateTime.now());
        doc.setProcessingStatus(DocumentStatus.confirmed);

        return repository.save(doc);
    }

    private String formatConfirmedJson(DocumentConfirmationRequest req, String classification, String flowImpact) {
        return String.format(
                "{\"target_month\":\"%s\",\"confirmed_amount\":%s,\"confirmed_date\":%s,\"confirmed_party\":%s,\"target_classification\":\"%s\",\"cash_flow_impact\":\"%s\"}",
                req.targetMonth(),
                req.confirmedAmount().toPlainString(),
                req.confirmedDate() != null ? "\"" + req.confirmedDate() + "\"" : "null",
                req.confirmedParty() != null ? "\"" + escapeJson(req.confirmedParty()) + "\"" : "null",
                classification,
                flowImpact
        );
    }

    private String escapeJson(String raw) {
        return raw.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
