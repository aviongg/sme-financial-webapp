package com.app.sme_health_backend.projectattribution.service;

import com.app.sme_health_backend.commercial.model.CommercialDocumentKind;
import com.app.sme_health_backend.commercial.model.CommercialDocumentRecord;
import com.app.sme_health_backend.commercial.model.CommercialDocumentStatus;
import com.app.sme_health_backend.commercial.repository.CommercialDocumentRepository;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.obligation.*;
import com.app.sme_health_backend.obligation.aging.ObligationDirection;
import com.app.sme_health_backend.projectattribution.dto.*;
import com.app.sme_health_backend.projectattribution.entity.ProjectCommercialDocumentLink;
import com.app.sme_health_backend.projectattribution.entity.ProjectFinancialAttribution;
import com.app.sme_health_backend.projectattribution.entity.ProjectFinancialObligationLink;
import com.app.sme_health_backend.projectattribution.exception.AttributionNotFoundException;
import com.app.sme_health_backend.projectattribution.exception.AttributionValidationException;
import com.app.sme_health_backend.projectattribution.model.AttributionDirection;
import com.app.sme_health_backend.projectattribution.model.AttributionSourceType;
import com.app.sme_health_backend.projectattribution.model.AttributionStatus;
import com.app.sme_health_backend.projectattribution.model.EconomicClassification;
import com.app.sme_health_backend.projectattribution.repository.ProjectCommercialDocumentLinkRepository;
import com.app.sme_health_backend.projectattribution.repository.ProjectFinancialAttributionRepository;
import com.app.sme_health_backend.projectattribution.repository.ProjectFinancialObligationLinkRepository;
import com.app.sme_health_backend.projects.model.ProjectBudgetStatus;
import com.app.sme_health_backend.projects.model.ProjectBudgetVersionRecord;
import com.app.sme_health_backend.projects.model.ProjectRecord;
import com.app.sme_health_backend.projects.repository.ProjectBudgetRepository;
import com.app.sme_health_backend.projects.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Service
public class ProjectCommitmentService {

    private final ProjectRepository projectRepository;
    private final ProjectBudgetRepository projectBudgetRepository;
    private final ProjectCommercialDocumentLinkRepository commercialDocumentLinkRepository;
    private final ProjectFinancialObligationLinkRepository obligationLinkRepository;
    private final FinancialObligationRepository obligationRepository;
    private final ObligationPaymentAllocationRepository paymentAllocationRepository;
    private final CommercialDocumentRepository commercialDocumentRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final ProjectFinancialAttributionRepository attributionRepository;

    public ProjectCommitmentService(
            ProjectRepository projectRepository,
            ProjectBudgetRepository projectBudgetRepository,
            ProjectCommercialDocumentLinkRepository commercialDocumentLinkRepository,
            ProjectFinancialObligationLinkRepository obligationLinkRepository,
            FinancialObligationRepository obligationRepository,
            ObligationPaymentAllocationRepository paymentAllocationRepository,
            CommercialDocumentRepository commercialDocumentRepository,
            CounterpartyRepository counterpartyRepository,
            ProjectFinancialAttributionRepository attributionRepository
    ) {
        this.projectRepository = projectRepository;
        this.projectBudgetRepository = projectBudgetRepository;
        this.commercialDocumentLinkRepository = commercialDocumentLinkRepository;
        this.obligationLinkRepository = obligationLinkRepository;
        this.obligationRepository = obligationRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.commercialDocumentRepository = commercialDocumentRepository;
        this.counterpartyRepository = counterpartyRepository;
        this.attributionRepository = attributionRepository;
    }

    // =========================================================================
    // Commercial Document Linkage
    // =========================================================================

    @Transactional
    public CommercialDocumentLinkResponse linkCommercialDocument(
            UUID businessId,
            UUID projectId,
            CommercialDocumentLinkRequest request,
            UUID userId
    ) {
        ProjectRecord project = projectRepository.findById(businessId, projectId)
                .orElseThrow(() -> new AttributionNotFoundException("Project not found with id: " + projectId));

        Optional<CommercialDocumentRecord> invoiceOpt = commercialDocumentRepository.findWithLines(businessId, CommercialDocumentKind.INVOICE, request.documentId());
        Optional<CommercialDocumentRecord> billOpt = invoiceOpt.isEmpty()
                ? commercialDocumentRepository.findWithLines(businessId, CommercialDocumentKind.BILL, request.documentId())
                : Optional.empty();

        CommercialDocumentRecord doc = invoiceOpt.or(() -> billOpt)
                .orElseThrow(() -> new AttributionNotFoundException("Commercial document not found with id: " + request.documentId() + " in business " + businessId));

        if (doc.status() == CommercialDocumentStatus.VOID) {
            throw new AttributionValidationException("Cannot link a VOID commercial document to project.");
        }

        // Check if already actively linked to this project
        Optional<ProjectCommercialDocumentLink> existingLinkOpt = commercialDocumentLinkRepository
                .findByBusinessIdAndProjectIdAndDocumentIdAndLinkStatus(businessId, projectId, doc.id(), "ACTIVE");
        if (existingLinkOpt.isPresent()) {
            return mapToCommercialDocumentResponse(existingLinkOpt.get(), doc);
        }

        BigDecimal allocatedAmount = (request.allocatedAmount() != null && request.allocatedAmount().compareTo(BigDecimal.ZERO) > 0)
                ? request.allocatedAmount().setScale(4, RoundingMode.HALF_UP)
                : doc.total().setScale(4, RoundingMode.HALF_UP);

        if (allocatedAmount.compareTo(doc.total()) > 0) {
            throw new AttributionValidationException(
                    "Allocated amount (" + allocatedAmount + ") cannot exceed commercial document total (" + doc.total() + ")"
            );
        }

        BigDecimal currentAllocated = commercialDocumentLinkRepository.sumAllocatedAmountByDocument(businessId, doc.id(), "ACTIVE");
        if (currentAllocated.add(allocatedAmount).compareTo(doc.total()) > 0) {
            throw new AttributionValidationException(
                    "Total active allocated amount across projects (" + currentAllocated.add(allocatedAmount) +
                            ") exceeds commercial document total (" + doc.total() + ")"
            );
        }

        ProjectCommercialDocumentLink link = new ProjectCommercialDocumentLink();
        link.setBusinessId(businessId);
        link.setProjectId(projectId);
        link.setDocumentId(doc.id());
        link.setDocumentKind(doc.kind().name());
        link.setDocumentNumber(doc.documentNumber());
        link.setCounterpartyName(doc.counterpartyName());
        link.setDocumentDate(doc.documentDate());
        link.setDueDate(doc.dueDate());
        link.setDocumentTotal(doc.total());
        link.setAllocatedAmount(allocatedAmount);
        link.setCurrency(doc.baseCurrency() != null ? doc.baseCurrency() : "PKR");
        link.setLinkStatus("ACTIVE");
        link.setLinkProvenance("MANUAL_COMMERCIAL_LINK");
        link.setNotes(request.notes());
        link.setCreatedByUserId(userId);
        link.setCreatedAt(Instant.now());
        link.setUpdatedAt(Instant.now());

        ProjectCommercialDocumentLink saved = commercialDocumentLinkRepository.save(link);
        return mapToCommercialDocumentResponse(saved, doc);
    }

    @Transactional
    public CommercialDocumentLinkResponse unlinkCommercialDocument(
            UUID businessId,
            UUID projectId,
            UUID linkId,
            String reason,
            UUID userId
    ) {
        ProjectCommercialDocumentLink link = commercialDocumentLinkRepository.findByIdAndBusinessId(linkId, businessId)
                .orElseThrow(() -> new AttributionNotFoundException("Commercial document link not found: " + linkId));

        if (!link.getProjectId().equals(projectId)) {
            throw new AttributionValidationException("Link does not belong to project: " + projectId);
        }

        if ("UNLINKED".equals(link.getLinkStatus())) {
            return mapToCommercialDocumentResponse(link, null);
        }

        link.setLinkStatus("UNLINKED");
        link.setUnlinkedAt(Instant.now());
        link.setUnlinkedBy(userId);
        link.setUnlinkReason(reason != null ? reason : "User unlinked commercial document");
        link.setUpdatedAt(Instant.now());

        ProjectCommercialDocumentLink saved = commercialDocumentLinkRepository.save(link);
        return mapToCommercialDocumentResponse(saved, null);
    }

    @Transactional(readOnly = true)
    public List<CommercialDocumentLinkResponse> listCommercialDocuments(UUID businessId, UUID projectId) {
        projectRepository.findById(businessId, projectId)
                .orElseThrow(() -> new AttributionNotFoundException("Project not found with id: " + projectId));

        List<ProjectCommercialDocumentLink> links = commercialDocumentLinkRepository
                .findByBusinessIdAndProjectIdOrderByCreatedAtDesc(businessId, projectId);

        return links.stream().map(link -> {
            Optional<CommercialDocumentRecord> invoiceOpt = commercialDocumentRepository.findWithLines(
                    businessId, CommercialDocumentKind.valueOf(link.getDocumentKind()), link.getDocumentId()
            );
            return mapToCommercialDocumentResponse(link, invoiceOpt.orElse(null));
        }).toList();
    }

    // =========================================================================
    // Financial Obligation Linkage
    // =========================================================================

    @Transactional
    public ProjectObligationItemResponse linkFinancialObligation(
            UUID businessId,
            UUID projectId,
            FinancialObligationLinkRequest request,
            UUID userId
    ) {
        ProjectRecord project = projectRepository.findById(businessId, projectId)
                .orElseThrow(() -> new AttributionNotFoundException("Project not found with id: " + projectId));

        FinancialObligation obligation = obligationRepository.findByIdAndBusinessId(request.obligationId(), businessId)
                .orElseThrow(() -> new AttributionNotFoundException("Financial obligation not found with id: " + request.obligationId()));

        if (obligation.getStatus() == ObligationStatus.VOIDED) {
            throw new AttributionValidationException("Cannot link a VOIDED obligation to project.");
        }

        Optional<ProjectFinancialObligationLink> existingLinkOpt = obligationLinkRepository
                .findByBusinessIdAndProjectIdAndObligationIdAndLinkStatus(businessId, projectId, obligation.getId(), "ACTIVE");
        if (existingLinkOpt.isPresent()) {
            return mapToObligationItemResponse(existingLinkOpt.get(), obligation, projectId);
        }

        BigDecimal allocatedAmount = (request.allocatedAmount() != null && request.allocatedAmount().compareTo(BigDecimal.ZERO) > 0)
                ? request.allocatedAmount().setScale(4, RoundingMode.HALF_UP)
                : obligation.getOriginalAmount().setScale(4, RoundingMode.HALF_UP);

        if (allocatedAmount.compareTo(obligation.getOriginalAmount()) > 0) {
            throw new AttributionValidationException(
                    "Allocated amount (" + allocatedAmount + ") cannot exceed obligation original amount (" + obligation.getOriginalAmount() + ")"
            );
        }

        BigDecimal currentAllocated = obligationLinkRepository.sumAllocatedAmountByObligation(businessId, obligation.getId(), "ACTIVE");
        if (currentAllocated.add(allocatedAmount).compareTo(obligation.getOriginalAmount()) > 0) {
            throw new AttributionValidationException(
                    "Total allocated amount across projects (" + currentAllocated.add(allocatedAmount) +
                            ") exceeds obligation original amount (" + obligation.getOriginalAmount() + ")"
            );
        }

        ProjectFinancialObligationLink link = new ProjectFinancialObligationLink();
        link.setBusinessId(businessId);
        link.setProjectId(projectId);
        link.setObligationId(obligation.getId());
        link.setDirection(obligation.getDirection().name());
        link.setOriginalAmount(obligation.getOriginalAmount());
        link.setAllocatedAmount(allocatedAmount);
        link.setCurrency(obligation.getCurrency() != null ? obligation.getCurrency() : "PKR");
        link.setLinkStatus("ACTIVE");
        link.setLinkProvenance("MANUAL_OBLIGATION_LINK");
        link.setNotes(request.notes());
        link.setCreatedByUserId(userId);
        link.setCreatedAt(Instant.now());
        link.setUpdatedAt(Instant.now());

        ProjectFinancialObligationLink saved = obligationLinkRepository.save(link);
        return mapToObligationItemResponse(saved, obligation, projectId);
    }

    @Transactional
    public ProjectObligationItemResponse unlinkFinancialObligation(
            UUID businessId,
            UUID projectId,
            UUID linkId,
            String reason,
            UUID userId
    ) {
        ProjectFinancialObligationLink link = obligationLinkRepository.findByIdAndBusinessId(linkId, businessId)
                .orElseThrow(() -> new AttributionNotFoundException("Financial obligation link not found: " + linkId));

        if (!link.getProjectId().equals(projectId)) {
            throw new AttributionValidationException("Link does not belong to project: " + projectId);
        }

        if ("UNLINKED".equals(link.getLinkStatus())) {
            FinancialObligation obligation = obligationRepository.findByIdAndBusinessId(link.getObligationId(), businessId).orElse(null);
            return mapToObligationItemResponse(link, obligation, projectId);
        }

        link.setLinkStatus("UNLINKED");
        link.setUnlinkedAt(Instant.now());
        link.setUnlinkedBy(userId);
        link.setUnlinkReason(reason != null ? reason : "User unlinked financial obligation");
        link.setUpdatedAt(Instant.now());

        ProjectFinancialObligationLink saved = obligationLinkRepository.save(link);
        FinancialObligation obligation = obligationRepository.findByIdAndBusinessId(saved.getObligationId(), businessId).orElse(null);
        return mapToObligationItemResponse(saved, obligation, projectId);
    }

    @Transactional(readOnly = true)
    public List<ProjectObligationItemResponse> listProjectObligations(UUID businessId, UUID projectId) {
        projectRepository.findById(businessId, projectId)
                .orElseThrow(() -> new AttributionNotFoundException("Project not found with id: " + projectId));

        List<ProjectFinancialObligationLink> links = obligationLinkRepository
                .findByBusinessIdAndProjectIdOrderByCreatedAtDesc(businessId, projectId);

        return links.stream().map(link -> {
            FinancialObligation obligation = obligationRepository.findByIdAndBusinessId(link.getObligationId(), businessId).orElse(null);
            return mapToObligationItemResponse(link, obligation, projectId);
        }).toList();
    }

    // =========================================================================
    // Project Commitments Summary & Financial Overview
    // =========================================================================

    @Transactional(readOnly = true)
    public ProjectCommitmentsSummaryResponse getCommitmentsSummary(UUID businessId, UUID projectId) {
        ProjectRecord project = projectRepository.findById(businessId, projectId)
                .orElseThrow(() -> new AttributionNotFoundException("Project not found with id: " + projectId));

        // 1. Budget figures
        List<ProjectBudgetVersionRecord> versions = projectBudgetRepository.findAllByProjectOrderByRevisionDesc(businessId, projectId);
        Optional<ProjectBudgetVersionRecord> approvedBudgetOpt = versions.stream()
                .filter(b -> b.status() == ProjectBudgetStatus.APPROVED)
                .findFirst();

        BigDecimal plannedCost = approvedBudgetOpt.map(ProjectBudgetVersionRecord::plannedCost)
                .orElse(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
        BigDecimal plannedRevenue = approvedBudgetOpt.map(ProjectBudgetVersionRecord::plannedRevenue)
                .orElse(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);

        // 2. C05A Attributed Actuals
        List<ProjectFinancialAttribution> activeAttributions = attributionRepository
                .findByBusinessIdAndProjectIdAndStatusOrderByAttributionDateDescCreatedAtDesc(
                        businessId, projectId, AttributionStatus.ACTIVE
                );

        BigDecimal attributedCashOutflows = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal attributedCashInflows = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal recognizedCommissionCost = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal nonCashFxImpact = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        for (ProjectFinancialAttribution a : activeAttributions) {
            BigDecimal amt = a.getAttributedAmount();
            if (a.getEconomicClassification() == EconomicClassification.POSTED_CASH_ACTUAL) {
                if (a.getSourceDirection() == AttributionDirection.OUTFLOW) {
                    attributedCashOutflows = attributedCashOutflows.add(amt);
                } else if (a.getSourceDirection() == AttributionDirection.INFLOW) {
                    attributedCashInflows = attributedCashInflows.add(amt);
                }
            } else if (a.getEconomicClassification() == EconomicClassification.RECOGNIZED_ECONOMIC_COST) {
                recognizedCommissionCost = recognizedCommissionCost.add(amt);
            } else if (a.getEconomicClassification() == EconomicClassification.NONCASH_FINANCIAL_EVIDENCE) {
                nonCashFxImpact = nonCashFxImpact.add(amt);
            }
        }

        // Cash Headroom = plannedCost - attributedCashOutflows
        BigDecimal cashHeadroom = plannedCost.subtract(attributedCashOutflows);

        // 3. Active Obligations
        List<ProjectFinancialObligationLink> activeObLinks = obligationLinkRepository
                .findByBusinessIdAndProjectIdAndLinkStatus(businessId, projectId, "ACTIVE");

        BigDecimal outstandingSupplierPayables = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal outstandingCustomerReceivables = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        int linkedObligationsCount = 0;

        // Keep track of obligation IDs linked to this project to detect matched commercial documents
        Set<UUID> linkedObligationIds = new HashSet<>();

        for (ProjectFinancialObligationLink link : activeObLinks) {
            Optional<FinancialObligation> obOpt = obligationRepository.findByIdAndBusinessId(link.getObligationId(), businessId);
            if (obOpt.isEmpty() || obOpt.get().getStatus() == ObligationStatus.VOIDED) {
                continue;
            }
            FinancialObligation ob = obOpt.get();
            linkedObligationIds.add(ob.getId());
            linkedObligationsCount++;

            List<ObligationPaymentAllocation> allocations = paymentAllocationRepository
                    .findByBusinessIdAndObligationIdOrderByEffectiveDateAscCreatedAtAsc(businessId, ob.getId());
            BigDecimal totalPaid = allocations.stream()
                    .filter(a -> a.getStatus() == AllocationStatus.EFFECTIVE)
                    .map(ObligationPaymentAllocation::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal orig = ob.getOriginalAmount();
            BigDecimal allocated = link.getAllocatedAmount();
            BigDecimal outstandingForLink;

            if (allocated.compareTo(orig) >= 0) {
                outstandingForLink = orig.subtract(totalPaid).max(BigDecimal.ZERO);
            } else {
                BigDecimal paidShare = orig.compareTo(BigDecimal.ZERO) > 0
                        ? allocated.multiply(totalPaid).divide(orig, 4, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                outstandingForLink = allocated.subtract(paidShare).max(BigDecimal.ZERO);
            }

            if ("PAYABLE".equalsIgnoreCase(link.getDirection())) {
                outstandingSupplierPayables = outstandingSupplierPayables.add(outstandingForLink);
            } else if ("RECEIVABLE".equalsIgnoreCase(link.getDirection())) {
                outstandingCustomerReceivables = outstandingCustomerReceivables.add(outstandingForLink);
            }
        }

        // 4. Commercial Document Links (Non-posting)
        List<ProjectCommercialDocumentLink> activeDocLinks = commercialDocumentLinkRepository
                .findByBusinessIdAndProjectIdAndLinkStatus(businessId, projectId, "ACTIVE");

        BigDecimal unpostedCommercialBills = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal unpostedCommercialInvoices = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        int linkedCommercialDocumentsCount = 0;
        int unmatchedCommercialDocumentsCount = 0;

        for (ProjectCommercialDocumentLink link : activeDocLinks) {
            linkedCommercialDocumentsCount++;

            // Deduplication: Check if this document has an associated financial obligation
            boolean matched = isCommercialDocMatchedToObligation(businessId, link);

            if (!matched) {
                unmatchedCommercialDocumentsCount++;
                if ("BILL".equalsIgnoreCase(link.getDocumentKind())) {
                    unpostedCommercialBills = unpostedCommercialBills.add(link.getAllocatedAmount());
                } else if ("INVOICE".equalsIgnoreCase(link.getDocumentKind())) {
                    unpostedCommercialInvoices = unpostedCommercialInvoices.add(link.getAllocatedAmount());
                }
            }
        }

        // Total Outstanding Commitments = supplier payables + unposted commercial bills
        BigDecimal totalOutstandingCommitments = outstandingSupplierPayables.add(unpostedCommercialBills);

        // Profitability Status
        String profitabilityStatus = "UNAVAILABLE";
        String unavailabilityReason;
        if (unpostedCommercialBills.compareTo(BigDecimal.ZERO) > 0 || outstandingSupplierPayables.compareTo(BigDecimal.ZERO) > 0) {
            unavailabilityReason = "Incomplete cost basis: " + unmatchedCommercialDocumentsCount +
                    " unposted commercial document(s) and " + linkedObligationsCount + " financial obligation(s) pending.";
        } else if (plannedCost.compareTo(BigDecimal.ZERO) == 0 && attributedCashOutflows.compareTo(BigDecimal.ZERO) == 0) {
            unavailabilityReason = "No cost activity recorded for project.";
        } else {
            unavailabilityReason = "Incomplete cost basis. Commercial non-posting documents require authoritative accounting recognition (C04).";
        }

        String evidenceCompletenessNotes = String.format(
                "Planned: %s %s; Attributed Cash Out: %s; Outstanding Payables: %s; Unposted Bills: %s; Matched Docs: %d/%d",
                project.baseCurrency(), plannedCost.toPlainString(),
                attributedCashOutflows.toPlainString(),
                outstandingSupplierPayables.toPlainString(),
                unpostedCommercialBills.toPlainString(),
                (linkedCommercialDocumentsCount - unmatchedCommercialDocumentsCount),
                linkedCommercialDocumentsCount
        );

        return new ProjectCommitmentsSummaryResponse(
                projectId,
                project.code(),
                project.name(),
                project.baseCurrency(),
                plannedCost,
                plannedRevenue,
                attributedCashOutflows,
                attributedCashInflows,
                cashHeadroom,
                outstandingSupplierPayables,
                outstandingCustomerReceivables,
                unpostedCommercialBills,
                unpostedCommercialInvoices,
                recognizedCommissionCost,
                nonCashFxImpact,
                totalOutstandingCommitments,
                linkedObligationsCount,
                linkedCommercialDocumentsCount,
                unmatchedCommercialDocumentsCount,
                profitabilityStatus,
                unavailabilityReason,
                evidenceCompletenessNotes
        );
    }

    // =========================================================================
    // Helper & Mapping Methods
    // =========================================================================

    private boolean isCommercialDocMatchedToObligation(UUID businessId, ProjectCommercialDocumentLink link) {
        String expectedSourceType = "BILL".equalsIgnoreCase(link.getDocumentKind()) ? "SUPPLIER_BILL" : "CUSTOMER_INVOICE";
        ObligationDirection expectedDirection = "BILL".equalsIgnoreCase(link.getDocumentKind()) ? ObligationDirection.PAYABLE : ObligationDirection.RECEIVABLE;

        List<FinancialObligation> byId = obligationRepository.findByBusinessIdAndSourceId(businessId, link.getDocumentId().toString());
        boolean matchedById = byId.stream().anyMatch(o ->
                o.getStatus() != ObligationStatus.VOIDED
                && o.getSourceType() != null && expectedSourceType.equalsIgnoreCase(o.getSourceType().name())
                && o.getDirection() == expectedDirection
        );
        if (matchedById) {
            return true;
        }

        if (link.getDocumentNumber() != null && !link.getDocumentNumber().isBlank()) {
            List<FinancialObligation> byNum = obligationRepository.findByBusinessIdAndSourceId(businessId, link.getDocumentNumber());
            return byNum.stream().anyMatch(o ->
                    o.getStatus() != ObligationStatus.VOIDED
                    && o.getSourceType() != null && expectedSourceType.equalsIgnoreCase(o.getSourceType().name())
                    && o.getDirection() == expectedDirection
            );
        }
        return false;
    }

    private CommercialDocumentLinkResponse mapToCommercialDocumentResponse(
            ProjectCommercialDocumentLink link,
            CommercialDocumentRecord doc
    ) {
        boolean matched = isCommercialDocMatchedToObligation(link.getBusinessId(), link);
        UUID matchedObId = null;
        if (matched) {
            String expectedSourceType = "BILL".equalsIgnoreCase(link.getDocumentKind()) ? "SUPPLIER_BILL" : "CUSTOMER_INVOICE";
            ObligationDirection expectedDirection = "BILL".equalsIgnoreCase(link.getDocumentKind()) ? ObligationDirection.PAYABLE : ObligationDirection.RECEIVABLE;

            List<FinancialObligation> byId = obligationRepository.findByBusinessIdAndSourceId(link.getBusinessId(), link.getDocumentId().toString());
            var found = byId.stream().filter(o ->
                    o.getStatus() != ObligationStatus.VOIDED
                    && o.getSourceType() != null && expectedSourceType.equalsIgnoreCase(o.getSourceType().name())
                    && o.getDirection() == expectedDirection
            ).findFirst();
            if (found.isPresent()) {
                matchedObId = found.get().getId();
            } else if (link.getDocumentNumber() != null) {
                List<FinancialObligation> byNum = obligationRepository.findByBusinessIdAndSourceId(link.getBusinessId(), link.getDocumentNumber());
                var foundNum = byNum.stream().filter(o ->
                        o.getStatus() != ObligationStatus.VOIDED
                        && o.getSourceType() != null && expectedSourceType.equalsIgnoreCase(o.getSourceType().name())
                        && o.getDirection() == expectedDirection
                ).findFirst();
                if (foundNum.isPresent()) {
                    matchedObId = foundNum.get().getId();
                }
            }
        }

        String accountingStatus = matched ? "MATCHED_TO_OBLIGATION" : "COMMERCIAL_UNPOSTED";

        return new CommercialDocumentLinkResponse(
                link.getId(),
                link.getBusinessId(),
                link.getProjectId(),
                link.getDocumentId(),
                link.getDocumentKind(),
                link.getDocumentNumber(),
                link.getCounterpartyName(),
                link.getDocumentDate(),
                link.getDueDate(),
                link.getDocumentTotal(),
                link.getAllocatedAmount(),
                link.getCurrency(),
                link.getLinkStatus(),
                accountingStatus,
                matched,
                matchedObId,
                link.getNotes(),
                link.getCreatedAt()
        );
    }

    private ProjectObligationItemResponse mapToObligationItemResponse(
            ProjectFinancialObligationLink link,
            FinancialObligation ob,
            UUID projectId
    ) {
        if (ob == null) {
            return new ProjectObligationItemResponse(
                    link.getId(),
                    link.getObligationId(),
                    link.getDirection(),
                    "Unknown Counterparty",
                    null,
                    null,
                    null,
                    link.getOriginalAmount(),
                    link.getAllocatedAmount(),
                    BigDecimal.ZERO,
                    link.getAllocatedAmount(),
                    link.getCurrency(),
                    "UNKNOWN",
                    link.getLinkStatus(),
                    List.of(),
                    false,
                    BigDecimal.ZERO,
                    link.getNotes()
            );
        }

        String counterpartyName = "Unknown";
        if (ob.getCounterpartyId() != null) {
            counterpartyName = counterpartyRepository.findByIdAndBusinessId(ob.getCounterpartyId(), link.getBusinessId())
                    .map(Counterparty::getDisplayName)
                    .orElse("Counterparty #" + ob.getCounterpartyId().toString().substring(0, 8));
        }

        List<ObligationPaymentAllocation> rawAllocations = paymentAllocationRepository
                .findByBusinessIdAndObligationIdOrderByEffectiveDateAscCreatedAtAsc(link.getBusinessId(), ob.getId());

        List<ObligationPaymentAllocation> effectiveAllocations = rawAllocations.stream()
                .filter(a -> a.getStatus() == AllocationStatus.EFFECTIVE)
                .toList();

        BigDecimal totalPaid = effectiveAllocations.stream()
                .map(ObligationPaymentAllocation::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal orig = ob.getOriginalAmount();
        BigDecimal allocated = link.getAllocatedAmount();
        BigDecimal outstanding;

        if (allocated.compareTo(orig) >= 0) {
            outstanding = orig.subtract(totalPaid).max(BigDecimal.ZERO);
        } else {
            BigDecimal paidShare = orig.compareTo(BigDecimal.ZERO) > 0
                    ? allocated.multiply(totalPaid).divide(orig, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            outstanding = allocated.subtract(paidShare).max(BigDecimal.ZERO);
        }

        boolean hasAttributedPayment = false;
        BigDecimal attributedCashAmount = BigDecimal.ZERO;
        List<ObligationSettlementEvidenceItem> paymentHistory = new ArrayList<>();

        for (ObligationPaymentAllocation alloc : effectiveAllocations) {
            boolean isAttributed = false;
            if (alloc.getFinancialTransactionId() != null) {
                List<ProjectFinancialAttribution> txAttributions = attributionRepository
                        .findByBusinessIdAndSourceTypeAndSourceIdAndStatus(
                                link.getBusinessId(), AttributionSourceType.TRANSACTION,
                                alloc.getFinancialTransactionId(), AttributionStatus.ACTIVE
                        );
                if (txAttributions.stream().anyMatch(a -> a.getProjectId().equals(projectId))) {
                    isAttributed = true;
                    hasAttributedPayment = true;
                    attributedCashAmount = attributedCashAmount.add(alloc.getAmount());
                }
            }

            paymentHistory.add(new ObligationSettlementEvidenceItem(
                    alloc.getId(),
                    alloc.getFinancialTransactionId(),
                    alloc.getAmount(),
                    alloc.getEffectiveDate(),
                    isAttributed
            ));
        }

        return new ProjectObligationItemResponse(
                link.getId(),
                ob.getId(),
                ob.getDirection().name(),
                counterpartyName,
                ob.getReferenceNumber(),
                ob.getIssueDate(),
                ob.getDueDate(),
                ob.getOriginalAmount(),
                link.getAllocatedAmount(),
                totalPaid,
                outstanding,
                ob.getCurrency(),
                ob.getStatus().name(),
                link.getLinkStatus(),
                paymentHistory,
                hasAttributedPayment,
                attributedCashAmount,
                link.getNotes()
        );
    }
}
