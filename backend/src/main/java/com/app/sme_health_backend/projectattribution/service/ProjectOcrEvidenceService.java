package com.app.sme_health_backend.projectattribution.service;

import com.app.sme_health_backend.accounting.entity.FinancialTransaction;
import com.app.sme_health_backend.accounting.repository.FinancialTransactionRepository;
import com.app.sme_health_backend.documents.entity.UploadedDocument;
import com.app.sme_health_backend.documents.processing.DocumentStatus;
import com.app.sme_health_backend.documents.repository.UploadedDocumentRepository;
import com.app.sme_health_backend.projectattribution.dto.*;
import com.app.sme_health_backend.projectattribution.entity.ProjectFinancialAttribution;
import com.app.sme_health_backend.projectattribution.entity.ProjectOcrEvidenceLink;
import com.app.sme_health_backend.projectattribution.exception.AttributionNotFoundException;
import com.app.sme_health_backend.projectattribution.exception.AttributionValidationException;
import com.app.sme_health_backend.projectattribution.model.AttributionSourceType;
import com.app.sme_health_backend.projectattribution.model.AttributionStatus;
import com.app.sme_health_backend.projectattribution.model.ProjectOcrEvidenceStatus;
import com.app.sme_health_backend.projectattribution.model.ProjectOcrMatchMethod;
import com.app.sme_health_backend.projectattribution.repository.ProjectFinancialAttributionRepository;
import com.app.sme_health_backend.projectattribution.repository.ProjectOcrEvidenceLinkRepository;
import com.app.sme_health_backend.projects.model.ProjectRecord;
import com.app.sme_health_backend.projects.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class ProjectOcrEvidenceService {

    private final ProjectOcrEvidenceLinkRepository ocrEvidenceLinkRepository;
    private final ProjectRepository projectRepository;
    private final ProjectFinancialAttributionRepository attributionRepository;
    private final UploadedDocumentRepository uploadedDocumentRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    public ProjectOcrEvidenceService(
            ProjectOcrEvidenceLinkRepository ocrEvidenceLinkRepository,
            ProjectRepository projectRepository,
            ProjectFinancialAttributionRepository attributionRepository,
            UploadedDocumentRepository uploadedDocumentRepository,
            FinancialTransactionRepository transactionRepository,
            ObjectMapper objectMapper
    ) {
        this.ocrEvidenceLinkRepository = ocrEvidenceLinkRepository;
        this.projectRepository = projectRepository;
        this.attributionRepository = attributionRepository;
        this.uploadedDocumentRepository = uploadedDocumentRepository;
        this.transactionRepository = transactionRepository;
        this.objectMapper = objectMapper;
    }

    // =========================================================================
    // Candidate Listing
    // =========================================================================

    @Transactional(readOnly = true)
    public List<OcrEvidenceCandidateResponse> listEligibleOcrCandidates(UUID businessId, UUID projectId) {
        ProjectRecord project = projectRepository.findById(businessId, projectId)
                .orElseThrow(() -> new AttributionNotFoundException("Project not found: " + projectId));

        List<UploadedDocument> documents = uploadedDocumentRepository.findByUserIdOrderByUploadTimestampDesc(businessId);
        List<OcrEvidenceCandidateResponse> candidates = new ArrayList<>();

        for (UploadedDocument doc : documents) {
            if (doc.getProcessingStatus() == DocumentStatus.failed || doc.getProcessingStatus() == DocumentStatus.pending) {
                continue;
            }

            ParsedOcrData ocrData = parseOcrData(doc);
            List<ProjectOcrEvidenceLink> existingLinks = ocrEvidenceLinkRepository.findByBusinessIdAndDocumentId(businessId, doc.getId());

            boolean isAlreadyLinkedToThisProject = existingLinks.stream()
                    .anyMatch(l -> l.getProjectId().equals(projectId) && l.getEvidenceStatus() != ProjectOcrEvidenceStatus.INVALIDATED);

            long alreadyLinkedProjectCount = existingLinks.stream()
                    .filter(l -> l.getEvidenceStatus() != ProjectOcrEvidenceStatus.INVALIDATED)
                    .map(ProjectOcrEvidenceLink::getProjectId)
                    .distinct()
                    .count();

            candidates.add(new OcrEvidenceCandidateResponse(
                    doc.getId(),
                    doc.getOriginalFilename(),
                    doc.getProcessingStatus() != null ? doc.getProcessingStatus().name() : "unknown",
                    ocrData.amount(),
                    ocrData.currency(),
                    ocrData.date(),
                    ocrData.counterpartyName(),
                    ocrData.category(),
                    ocrData.documentType(),
                    ocrData.confidence(),
                    doc.getUploadTimestamp(),
                    isAlreadyLinkedToThisProject,
                    alreadyLinkedProjectCount
            ));
        }

        return candidates;
    }

    // =========================================================================
    // Evidence Listing
    // =========================================================================

    @Transactional(readOnly = true)
    public List<ProjectOcrEvidenceItemResponse> listProjectOcrEvidence(UUID businessId, UUID projectId) {
        ProjectRecord project = projectRepository.findById(businessId, projectId)
                .orElseThrow(() -> new AttributionNotFoundException("Project not found: " + projectId));

        List<ProjectOcrEvidenceLink> links = ocrEvidenceLinkRepository.findByBusinessIdAndProjectIdOrderByCreatedAtDesc(businessId, projectId);
        List<ProjectOcrEvidenceItemResponse> responses = new ArrayList<>();

        for (ProjectOcrEvidenceLink link : links) {
            responses.add(mapToResponse(businessId, project.name(), link));
        }

        return responses;
    }

    @Transactional(readOnly = true)
    public ProjectOcrEvidenceItemResponse getOcrEvidenceDetail(UUID businessId, UUID projectId, UUID linkId) {
        ProjectRecord project = projectRepository.findById(businessId, projectId)
                .orElseThrow(() -> new AttributionNotFoundException("Project not found: " + projectId));

        ProjectOcrEvidenceLink link = ocrEvidenceLinkRepository.findByBusinessIdAndProjectIdAndId(businessId, projectId, linkId)
                .orElseThrow(() -> new AttributionNotFoundException("OCR evidence link not found: " + linkId));

        return mapToResponse(businessId, project.name(), link);
    }

    // =========================================================================
    // Link Evidence to Project
    // =========================================================================

    @Transactional
    public ProjectOcrEvidenceItemResponse linkOcrEvidenceToProject(
            UUID businessId,
            UUID projectId,
            OcrEvidenceLinkRequest request,
            UUID actorUserId
    ) {
        ProjectRecord project = projectRepository.findById(businessId, projectId)
                .orElseThrow(() -> new AttributionNotFoundException("Project not found: " + projectId));

        UploadedDocument doc = uploadedDocumentRepository.findByIdAndUserId(request.documentId(), businessId)
                .orElseThrow(() -> new AttributionNotFoundException("Document not found: " + request.documentId()));

        ParsedOcrData ocrData = parseOcrData(doc);

        ProjectOcrEvidenceLink link = new ProjectOcrEvidenceLink();
        link.setBusinessId(businessId);
        link.setProjectId(projectId);
        link.setDocumentId(doc.getId());
        link.setOcrAmount(ocrData.amount());
        link.setOcrCurrency(ocrData.currency());
        link.setOcrDate(ocrData.date());
        link.setCounterpartyName(ocrData.counterpartyName());
        link.setCategory(ocrData.category());
        link.setDocumentType(ocrData.documentType());
        link.setConfidence(ocrData.confidence());
        link.setNotes(request.notes());
        link.setIdempotencyKey(request.idempotencyKey());
        link.setLinkProvenance("OCR_UPLOAD_ATTACHMENT");
        link.setCreatedByUserId(actorUserId);
        link.setCreatedAt(Instant.now());
        link.setUpdatedAt(Instant.now());

        boolean isConfirmed = doc.getProcessingStatus() == DocumentStatus.confirmed
                || (doc.getReviewedData() != null && !doc.getReviewedData().isBlank());

        if (!isConfirmed) {
            if (request.attributionId() != null || request.financialSourceId() != null) {
                throw new AttributionValidationException("Document must be human-reviewed and confirmed before matching or attributing to financial sources");
            }
            link.setEvidenceStatus(ProjectOcrEvidenceStatus.EXTRACTED_UNREVIEWED);
            link.setMatchMethod(ProjectOcrMatchMethod.UNMATCHED);
        } else {
            link.setEvidenceStatus(ProjectOcrEvidenceStatus.REVIEWED_UNMATCHED);
            link.setMatchMethod(ProjectOcrMatchMethod.UNMATCHED);
        }

        // Check if an attribution is directly matched
        if (request.attributionId() != null) {
            ProjectFinancialAttribution attribution = attributionRepository.findByBusinessIdAndId(businessId, request.attributionId())
                    .orElseThrow(() -> new AttributionNotFoundException("Attribution not found: " + request.attributionId()));

            if (!attribution.getProjectId().equals(projectId)) {
                throw new AttributionValidationException("Attribution does not belong to project: " + projectId);
            }
            if (attribution.getStatus() != AttributionStatus.ACTIVE) {
                throw new AttributionValidationException("Cannot link evidence to inactive/reversed attribution");
            }

            link.setAttributionId(attribution.getId());
            link.setFinancialSourceType(attribution.getSourceType());
            link.setFinancialSourceId(attribution.getSourceId());
            link.setMatchMethod(request.matchMethod() != null ? request.matchMethod() : ProjectOcrMatchMethod.MANUAL_ASSISTED);
            link.setEvidenceStatus(ProjectOcrEvidenceStatus.ATTRIBUTED);
            link.setConfirmedByUserId(actorUserId);
            link.setConfirmedAt(Instant.now());

            computeDiscrepancy(link, ocrData.amount(), attribution.getAttributedAmount(), "attribution");
        } else if (request.financialSourceId() != null) {
            FinancialTransaction tx = transactionRepository.findByIdAndBusinessId(request.financialSourceId(), businessId)
                    .orElseThrow(() -> new AttributionNotFoundException("Financial transaction not found: " + request.financialSourceId()));

            link.setFinancialSourceType(AttributionSourceType.TRANSACTION);
            link.setFinancialSourceId(tx.getId());
            link.setMatchMethod(request.matchMethod() != null ? request.matchMethod() : ProjectOcrMatchMethod.MANUAL_ASSISTED);
            link.setEvidenceStatus(ProjectOcrEvidenceStatus.MATCHED_TO_POSTED_SOURCE);
            link.setConfirmedByUserId(actorUserId);
            link.setConfirmedAt(Instant.now());

            computeDiscrepancy(link, ocrData.amount(), tx.getAmount(), "transaction");
        }

        ProjectOcrEvidenceLink saved = ocrEvidenceLinkRepository.saveAndFlush(link);
        return mapToResponse(businessId, project.name(), saved);
    }

    // =========================================================================
    // Confirm Match & Attribute
    // =========================================================================

    @Transactional
    public ProjectOcrEvidenceItemResponse confirmMatchAndAttribute(
            UUID businessId,
            UUID projectId,
            UUID linkId,
            OcrMatchConfirmationRequest request,
            UUID actorUserId
    ) {
        ProjectRecord project = projectRepository.findById(businessId, projectId)
                .orElseThrow(() -> new AttributionNotFoundException("Project not found: " + projectId));

        ProjectOcrEvidenceLink link = ocrEvidenceLinkRepository.findByBusinessIdAndProjectIdAndId(businessId, projectId, linkId)
                .orElseThrow(() -> new AttributionNotFoundException("OCR evidence link not found: " + linkId));

        if (link.getEvidenceStatus() == ProjectOcrEvidenceStatus.INVALIDATED) {
            throw new AttributionValidationException("Cannot match an invalidated evidence link");
        }
        if (link.getEvidenceStatus() == ProjectOcrEvidenceStatus.REVERSED) {
            throw new AttributionValidationException("Cannot match a reversed evidence link");
        }

        UploadedDocument doc = uploadedDocumentRepository.findByIdAndUserId(link.getDocumentId(), businessId)
                .orElseThrow(() -> new AttributionNotFoundException("Document not found: " + link.getDocumentId()));
        boolean isConfirmed = doc.getProcessingStatus() == DocumentStatus.confirmed
                || (doc.getReviewedData() != null && !doc.getReviewedData().isBlank());
        if (!isConfirmed) {
            throw new AttributionValidationException("Document must be human-reviewed and confirmed before matching to financial sources");
        }

        if (request.attributionId() != null) {
            ProjectFinancialAttribution attribution = attributionRepository.findByBusinessIdAndId(businessId, request.attributionId())
                    .orElseThrow(() -> new AttributionNotFoundException("Attribution not found: " + request.attributionId()));

            if (!attribution.getProjectId().equals(projectId)) {
                throw new AttributionValidationException("Attribution does not belong to project: " + projectId);
            }
            if (attribution.getStatus() != AttributionStatus.ACTIVE) {
                throw new AttributionValidationException("Cannot link evidence to inactive/reversed attribution");
            }

            link.setAttributionId(attribution.getId());
            link.setFinancialSourceType(attribution.getSourceType());
            link.setFinancialSourceId(attribution.getSourceId());
            link.setEvidenceStatus(ProjectOcrEvidenceStatus.ATTRIBUTED);
            computeDiscrepancy(link, link.getOcrAmount(), attribution.getAttributedAmount(), "attribution");
        } else if (request.financialSourceId() != null) {
            FinancialTransaction tx = transactionRepository.findByIdAndBusinessId(request.financialSourceId(), businessId)
                    .orElseThrow(() -> new AttributionNotFoundException("Financial transaction not found: " + request.financialSourceId()));

            link.setFinancialSourceType(AttributionSourceType.TRANSACTION);
            link.setFinancialSourceId(tx.getId());
            link.setEvidenceStatus(ProjectOcrEvidenceStatus.MATCHED_TO_POSTED_SOURCE);
            computeDiscrepancy(link, link.getOcrAmount(), tx.getAmount(), "transaction");
        }

        if (link.getMatchMethod() == ProjectOcrMatchMethod.UNMATCHED) {
            link.setMatchMethod(ProjectOcrMatchMethod.MANUAL_ASSISTED);
        }

        if (request.notes() != null && !request.notes().isBlank()) {
            link.setNotes(request.notes());
        }

        link.setConfirmedByUserId(actorUserId);
        link.setConfirmedAt(Instant.now());
        link.setUpdatedAt(Instant.now());

        ProjectOcrEvidenceLink saved = ocrEvidenceLinkRepository.saveAndFlush(link);
        return mapToResponse(businessId, project.name(), saved);
    }

    // =========================================================================
    // Invalidate Evidence Link
    // =========================================================================

    @Transactional
    public ProjectOcrEvidenceItemResponse invalidateOcrEvidenceLink(
            UUID businessId,
            UUID projectId,
            UUID linkId,
            String reason,
            UUID actorUserId
    ) {
        ProjectRecord project = projectRepository.findById(businessId, projectId)
                .orElseThrow(() -> new AttributionNotFoundException("Project not found: " + projectId));

        ProjectOcrEvidenceLink link = ocrEvidenceLinkRepository.findByBusinessIdAndProjectIdAndId(businessId, projectId, linkId)
                .orElseThrow(() -> new AttributionNotFoundException("OCR evidence link not found: " + linkId));

        link.setEvidenceStatus(ProjectOcrEvidenceStatus.INVALIDATED);
        link.setInvalidatedAt(Instant.now());
        link.setInvalidatedBy(actorUserId);
        link.setInvalidationReason(reason);
        link.setUpdatedAt(Instant.now());

        ProjectOcrEvidenceLink saved = ocrEvidenceLinkRepository.saveAndFlush(link);
        return mapToResponse(businessId, project.name(), saved);
    }

    // =========================================================================
    // Reversal Notification
    // =========================================================================

    @Transactional
    public void handleTransactionReversed(UUID businessId, UUID transactionId, UUID reversalTxId, UUID actorUserId) {
        List<ProjectOcrEvidenceLink> links = ocrEvidenceLinkRepository
                .findByBusinessIdAndFinancialSourceTypeAndFinancialSourceId(
                        businessId, AttributionSourceType.TRANSACTION, transactionId
                );
        for (var link : links) {
            if (link.getEvidenceStatus() != ProjectOcrEvidenceStatus.INVALIDATED) {
                link.setEvidenceStatus(ProjectOcrEvidenceStatus.REVERSED);
                link.setDiscrepancyNotes("Source transaction reversed by " + reversalTxId);
                link.setUpdatedAt(Instant.now());
                ocrEvidenceLinkRepository.saveAndFlush(link);
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void computeDiscrepancy(
            ProjectOcrEvidenceLink link,
            BigDecimal ocrAmount,
            BigDecimal matchedAmount,
            String targetType
    ) {
        if (ocrAmount == null || matchedAmount == null) {
            link.setDiscrepancyAmount(null);
            link.setDiscrepancyNotes(null);
            return;
        }

        BigDecimal diff = ocrAmount.subtract(matchedAmount).abs();
        if (diff.compareTo(new BigDecimal("0.01")) > 0) {
            link.setDiscrepancyAmount(diff);
            link.setDiscrepancyNotes(String.format(
                    "OCR receipt amount (%s) differs from posted %s amount (%s) by %s %s",
                    ocrAmount.setScale(2, RoundingMode.HALF_UP),
                    targetType,
                    matchedAmount.setScale(2, RoundingMode.HALF_UP),
                    diff.setScale(2, RoundingMode.HALF_UP),
                    link.getOcrCurrency()
            ));
        } else {
            link.setDiscrepancyAmount(BigDecimal.ZERO);
            link.setDiscrepancyNotes(null);
        }
    }

    private ProjectOcrEvidenceItemResponse mapToResponse(
            UUID businessId,
            String projectName,
            ProjectOcrEvidenceLink link
    ) {
        String filename = null;
        var docOpt = uploadedDocumentRepository.findByIdAndUserId(link.getDocumentId(), businessId);
        if (docOpt.isPresent()) {
            filename = docOpt.get().getOriginalFilename();
        }

        BigDecimal matchedTxAmount = null;
        LocalDate matchedTxDate = null;
        String matchedTxDescription = null;

        if (link.getFinancialSourceId() != null && link.getFinancialSourceType() == AttributionSourceType.TRANSACTION) {
            var txOpt = transactionRepository.findByIdAndBusinessId(link.getFinancialSourceId(), businessId);
            if (txOpt.isPresent()) {
                FinancialTransaction tx = txOpt.get();
                matchedTxAmount = tx.getAmount();
                matchedTxDate = tx.getEffectiveDate();
                matchedTxDescription = tx.getDescription();
            }
        }

        long duplicateCount = ocrEvidenceLinkRepository.countByBusinessIdAndDocumentIdAndEvidenceStatusNot(
                businessId, link.getDocumentId(), ProjectOcrEvidenceStatus.INVALIDATED);
        boolean isDuplicate = duplicateCount > 1;

        boolean hasDiscrepancy = link.getDiscrepancyAmount() != null
                && link.getDiscrepancyAmount().compareTo(new BigDecimal("0.01")) > 0;

        return new ProjectOcrEvidenceItemResponse(
                link.getId(),
                link.getProjectId(),
                projectName,
                link.getDocumentId(),
                filename,
                link.getEvidenceStatus(),
                link.getFinancialSourceType(),
                link.getFinancialSourceId(),
                link.getAttributionId(),
                link.getMatchMethod(),
                link.getOcrAmount(),
                link.getOcrCurrency(),
                link.getOcrDate(),
                link.getCounterpartyName(),
                link.getCategory(),
                link.getDocumentType(),
                link.getConfidence(),
                link.getDiscrepancyAmount(),
                link.getDiscrepancyNotes(),
                hasDiscrepancy,
                isDuplicate,
                duplicateCount,
                link.getLinkProvenance(),
                link.getNotes(),
                link.getConfirmedByUserId(),
                link.getConfirmedAt(),
                link.getInvalidatedAt(),
                link.getInvalidatedBy(),
                link.getInvalidationReason(),
                link.getCreatedAt(),
                matchedTxAmount,
                matchedTxDate,
                matchedTxDescription
        );
    }

    private record ParsedOcrData(
            BigDecimal amount,
            String currency,
            LocalDate date,
            String counterpartyName,
            String category,
            String documentType,
            BigDecimal confidence
    ) {}

    private ParsedOcrData parseOcrData(UploadedDocument doc) {
        String jsonStr = doc.getConfirmedData();
        if (jsonStr == null || jsonStr.isBlank()) {
            jsonStr = doc.getReviewedData();
        }
        if (jsonStr == null || jsonStr.isBlank()) {
            jsonStr = doc.getExtractedData();
        }

        BigDecimal amount = BigDecimal.ZERO;
        String currency = "PKR";
        LocalDate date = null;
        String vendor = null;
        String category = null;
        String docType = doc.getDocumentTypeHint();
        BigDecimal confidence = null;

        if (jsonStr != null && !jsonStr.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(jsonStr);
                if (node.hasNonNull("amount")) {
                    try {
                        amount = new BigDecimal(node.get("amount").asText().replace(",", "").trim());
                    } catch (Exception ignored) {}
                }
                if (node.hasNonNull("currency")) {
                    currency = node.get("currency").asText().trim().toUpperCase();
                }
                if (node.hasNonNull("date")) {
                    try {
                        date = LocalDate.parse(node.get("date").asText().trim());
                    } catch (Exception ignored) {}
                }
                if (node.hasNonNull("vendor_or_party")) {
                    vendor = node.get("vendor_or_party").asText().trim();
                } else if (node.hasNonNull("vendor")) {
                    vendor = node.get("vendor").asText().trim();
                } else if (node.hasNonNull("merchant")) {
                    vendor = node.get("merchant").asText().trim();
                }
                if (node.hasNonNull("category")) {
                    category = node.get("category").asText().trim();
                }
                if (node.hasNonNull("document_type_detected")) {
                    docType = node.get("document_type_detected").asText().trim();
                }
                if (node.hasNonNull("confidence")) {
                    try {
                        confidence = new BigDecimal(node.get("confidence").asText().trim());
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }

        if (docType == null || docType.isBlank()) {
            docType = "receipt";
        }

        return new ParsedOcrData(amount, currency, date, vendor, category, docType, confidence);
    }
}
