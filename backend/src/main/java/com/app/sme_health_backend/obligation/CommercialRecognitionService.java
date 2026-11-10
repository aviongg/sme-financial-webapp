package com.app.sme_health_backend.obligation;

import com.app.sme_health_backend.accounting.entity.FinancialIdempotencyRecord;
import com.app.sme_health_backend.accounting.repository.FinancialIdempotencyRepository;
import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.audit.model.AuditOutcome;
import com.app.sme_health_backend.audit.service.SecurityAuditService;
import com.app.sme_health_backend.commercial.model.CommercialDocumentKind;
import com.app.sme_health_backend.commercial.model.CommercialDocumentRecord;
import com.app.sme_health_backend.commercial.model.CommercialDocumentStatus;
import com.app.sme_health_backend.commercial.repository.CommercialDocumentRepository;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.counterparty.CounterpartyStatus;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.obligation.aging.ObligationDirection;
import com.app.sme_health_backend.obligation.dto.*;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import com.app.sme_health_backend.projectattribution.service.ProjectCommitmentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class CommercialRecognitionService {

    public static final String OPERATION_COMMERCIAL_RECOGNITION = "COMMERCIAL_DOCUMENT_RECOGNITION";
    private static final ZoneId KARACHI_ZONE = ZoneId.of("Asia/Karachi");
    private static final String RECOGNITION_PROVENANCE = "F04_COMMERCIAL_RECOGNITION_V1";

    private final FinancialObligationRepository obligationRepository;
    private final ObligationPaymentAllocationRepository allocationRepository;
    private final FinancialObligationService obligationService;
    private final CounterpartyRepository counterpartyRepository;
    private final CommercialDocumentRepository commercialDocumentRepository;
    private final FinancialIdempotencyRepository idempotencyRepository;
    private final SecurityAuditService securityAuditService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ProjectCommitmentService projectCommitmentService;

    @Autowired
    public CommercialRecognitionService(
            FinancialObligationRepository obligationRepository,
            ObligationPaymentAllocationRepository allocationRepository,
            FinancialObligationService obligationService,
            CounterpartyRepository counterpartyRepository,
            CommercialDocumentRepository commercialDocumentRepository,
            FinancialIdempotencyRepository idempotencyRepository,
            SecurityAuditService securityAuditService,
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            @Autowired(required = false) ProjectCommitmentService projectCommitmentService
    ) {
        this.obligationRepository = obligationRepository;
        this.allocationRepository = allocationRepository;
        this.obligationService = obligationService;
        this.counterpartyRepository = counterpartyRepository;
        this.commercialDocumentRepository = commercialDocumentRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.securityAuditService = securityAuditService;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.projectCommitmentService = projectCommitmentService;
    }

    public CommercialRecognitionService(
            FinancialObligationRepository obligationRepository,
            ObligationPaymentAllocationRepository allocationRepository,
            FinancialObligationService obligationService,
            CounterpartyRepository counterpartyRepository,
            CommercialDocumentRepository commercialDocumentRepository,
            FinancialIdempotencyRepository idempotencyRepository,
            SecurityAuditService securityAuditService,
            JdbcTemplate jdbc,
            ObjectMapper objectMapper
    ) {
        this(obligationRepository, allocationRepository, obligationService, counterpartyRepository,
                commercialDocumentRepository, idempotencyRepository, securityAuditService, jdbc, objectMapper, null);
    }

    /**
     * Authoritative recognition of an F04 commercial document into an AR/AP financial obligation.
     * Enforces explicit counterparty validation, immutable document totals, idempotent replay,
     * and transactional row-locking. Creates 0 cash movements.
     */
    @Transactional
    public CommercialRecognitionResponse recognizeCommercialDocument(
            BusinessAccessContext context,
            String idempotencyKeyHeader,
            CommercialRecognitionRequest request,
            HttpServletRequest httpRequest
    ) {
        // 1. Validate request and idempotency key
        if (request == null) {
            throw new ObligationValidationException("body", "Request body is required");
        }
        if (request.documentId() == null) {
            throw new ObligationValidationException("documentId", "Document ID is required");
        }
        if (request.counterpartyId() == null) {
            throw new ObligationValidationException("counterpartyId", "Explicit counterparty selection is required for financial recognition");
        }

        String rawKey = (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank())
                ? idempotencyKeyHeader.trim()
                : (request.idempotencyKey() != null ? request.idempotencyKey().trim() : null);

        if (rawKey == null || rawKey.isEmpty()) {
            throw new ObligationValidationException("idempotencyKey", "Idempotency key is required");
        }
        if (rawKey.length() > 100) {
            throw new ObligationValidationException("idempotencyKey", "Idempotency key must not exceed 100 characters");
        }

        // 2. Check existing idempotency record before taking locks
        String payloadHash = computePayloadHash(context.businessId(), request.documentId(), request.counterpartyId());
        Optional<FinancialIdempotencyRecord> existingIdemOpt = idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(
                context.businessId(), OPERATION_COMMERCIAL_RECOGNITION, rawKey
        );
        if (existingIdemOpt.isPresent()) {
            FinancialIdempotencyRecord existing = existingIdemOpt.get();
            if (existing.getRequestHash().equals(payloadHash)) {
                return deserializeResponse(existing.getResponseBody());
            } else {
                throw new DuplicateResourceException(
                        "Idempotency key '" + rawKey + "' has already been used with a different request payload"
                );
            }
        }

        // 3. Concurrency Lock: Lock commercial document row for update
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, kind, status FROM commercial_documents WHERE id = ? AND business_id = ? FOR UPDATE",
                request.documentId(), context.businessId()
        );
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Commercial document not found: " + request.documentId());
        }

        Map<String, Object> row = rows.get(0);
        CommercialDocumentKind kind = CommercialDocumentKind.valueOf((String) row.get("kind"));
        CommercialDocumentStatus status = CommercialDocumentStatus.valueOf((String) row.get("status"));

        // 4. Validate kind consistency if provided in request
        if (request.documentKind() != null && request.documentKind() != kind) {
            throw new ObligationValidationException("documentKind",
                    "Provided document kind " + request.documentKind() + " does not match actual document kind: " + kind);
        }

        // 5. Validate document status
        if (status == CommercialDocumentStatus.DRAFT) {
            throw new ObligationValidationException("status",
                    "Cannot recognize draft commercial document. Document must be FINALIZED before financial recognition.");
        }
        if (status == CommercialDocumentStatus.VOID) {
            throw new ObligationValidationException("status",
                    "Cannot recognize voided commercial document.");
        }
        if (status != CommercialDocumentStatus.FINALIZED) {
            throw new ObligationValidationException("status",
                    "Commercial document must be FINALIZED to be recognized (current status: " + status + ").");
        }

        // 6. Load authoritative finalized document
        CommercialDocumentRecord doc = commercialDocumentRepository.findWithLines(context.businessId(), kind, request.documentId())
                .orElseThrow(() -> new ResourceNotFoundException("Commercial document not found: " + request.documentId()));

        if (doc.documentNumber() == null || doc.documentNumber().isBlank()) {
            throw new ObligationValidationException("documentNumber", "Finalized document number is missing.");
        }
        if (doc.total() == null || doc.total().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ObligationValidationException("total", "Commercial document total must be greater than zero to create a financial obligation.");
        }

        // 7. Check if document has already been recognized as a financial obligation
        ObligationSourceType sourceType = (kind == CommercialDocumentKind.INVOICE)
                ? ObligationSourceType.CUSTOMER_INVOICE
                : ObligationSourceType.SUPPLIER_BILL;

        Optional<FinancialObligation> existingObligationOpt = obligationRepository.findByBusinessIdAndSourceTypeAndSourceId(
                context.businessId(), sourceType, doc.id().toString()
        );
        if (existingObligationOpt.isPresent()) {
            FinancialObligation existingObligation = existingObligationOpt.get();
            throw new ObligationConflictException(
                    "Commercial document " + doc.documentNumber() + " is already recognized as a financial obligation (ID: " + existingObligation.getId() + ")."
            );
        }

        // 8. Explicit Counterparty Validation
        Counterparty counterparty = counterpartyRepository.findByIdAndBusinessId(request.counterpartyId(), context.businessId())
                .orElseThrow(() -> new ObligationValidationException("counterpartyId", "Counterparty not found in active business"));

        if (counterparty.getStatus() != CounterpartyStatus.ACTIVE) {
            throw new ObligationValidationException("counterpartyId", "Selected counterparty is archived or inactive");
        }

        ObligationDirection direction = (kind == CommercialDocumentKind.INVOICE)
                ? ObligationDirection.RECEIVABLE
                : ObligationDirection.PAYABLE;

        if (direction == ObligationDirection.RECEIVABLE && !counterparty.getType().isCustomerCompatible()) {
            throw new ObligationValidationException("counterpartyId",
                    "Counterparty must be a customer (CUSTOMER or BOTH) for customer invoice recognition.");
        }
        if (direction == ObligationDirection.PAYABLE && !counterparty.getType().isSupplierCompatible()) {
            throw new ObligationValidationException("counterpartyId",
                    "Counterparty must be a supplier (SUPPLIER or BOTH) for supplier bill recognition.");
        }

        // 9. Authoritative Amounts & Dates
        LocalDate issueDate = doc.documentDate() != null ? doc.documentDate() : LocalDate.now(KARACHI_ZONE);
        LocalDate dueDate = doc.dueDate();
        BigDecimal originalAmount = doc.total().setScale(4, RoundingMode.UNNECESSARY);
        String currency = (doc.baseCurrency() != null && !doc.baseCurrency().isBlank())
                ? doc.baseCurrency().trim()
                : "PKR";

        String notes = (request.notes() != null && !request.notes().isBlank())
                ? request.notes().trim()
                : ("Recognized from " + kind.name() + " " + doc.documentNumber());

        // 10. Persist FinancialObligation
        FinancialObligation obligation = new FinancialObligation(
                context.businessId(),
                counterparty.getId(),
                direction,
                sourceType,
                doc.id().toString(),
                doc.documentNumber(),
                issueDate,
                dueDate,
                originalAmount,
                currency,
                notes,
                context.userId()
        );

        try {
            obligation = obligationRepository.saveAndFlush(obligation);
        } catch (DataIntegrityViolationException ex) {
            // Catch concurrent duplicate attempt
            throw new ObligationConflictException(
                    "Commercial document " + doc.documentNumber() + " is already recognized as a financial obligation."
            );
        }

        // 10b. Synchronize with project commitments if commercial document was linked to projects
        if (projectCommitmentService != null) {
            try {
                projectCommitmentService.syncCommercialDocumentRecognition(
                        context.businessId(),
                        doc.id(),
                        obligation.getId(),
                        context.userId()
                );
            } catch (Exception e) {
                // Non-fatal synchronization warning to preserve core obligation creation
            }
        }

        // 11. Build Response DTO
        ObligationDetailResponse detail = obligationService.getObligation(context, obligation.getId());
        CommercialRecognitionResponse response = new CommercialRecognitionResponse(
                obligation.getId(),
                context.businessId(),
                direction,
                sourceType,
                doc.id().toString(),
                doc.documentNumber(),
                counterparty.getId(),
                counterparty.getDisplayName(),
                currency,
                originalAmount,
                originalAmount, // outstanding amount initially equals original amount
                BigDecimal.ZERO.setScale(4, RoundingMode.UNNECESSARY), // initial allocated amount is 0.0000
                obligation.getStatus(),
                issueDate,
                dueDate,
                obligation.getCreatedAt(),
                RECOGNITION_PROVENANCE,
                notes,
                detail
        );

        // 12. Persist Idempotency Record
        try {
            String responseJson = objectMapper.writeValueAsString(response);
            FinancialIdempotencyRecord idemRecord = new FinancialIdempotencyRecord(
                    context.businessId(),
                    OPERATION_COMMERCIAL_RECOGNITION,
                    rawKey,
                    payloadHash,
                    obligation.getId(),
                    201,
                    responseJson
            );
            idempotencyRepository.saveAndFlush(idemRecord);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize and persist idempotency record", e);
        }

        // 13. Durable Security Audit Event
        Map<String, Object> auditMetadata = new LinkedHashMap<>();
        auditMetadata.put("documentId", doc.id().toString());
        auditMetadata.put("documentKind", kind.name());
        auditMetadata.put("documentNumber", doc.documentNumber());
        auditMetadata.put("obligationId", obligation.getId().toString());
        auditMetadata.put("direction", direction.name());
        auditMetadata.put("amount", originalAmount.toPlainString());
        auditMetadata.put("currency", currency);
        auditMetadata.put("counterpartyId", counterparty.getId().toString());

        securityAuditService.recordEvent(
                AuditEventType.COMMERCIAL_DOCUMENT_RECOGNIZED,
                context.userId(),
                context.role().name(),
                context.businessId(),
                "FINANCIAL_OBLIGATION",
                obligation.getId().toString(),
                AuditOutcome.SUCCESS,
                httpRequest,
                auditMetadata
        );

        return response;
    }

    /**
     * Read-only recognition status for a commercial document.
     * Enforces active business context and returns existing recognized obligation and payment allocations if present.
     */
    @Transactional(readOnly = true)
    public CommercialRecognitionStatusResponse getRecognitionStatus(BusinessAccessContext context, UUID documentId) {
        if (documentId == null) {
            throw new ObligationValidationException("documentId", "Document ID is required");
        }

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, kind, status, document_number, total, base_currency FROM commercial_documents WHERE id = ? AND business_id = ?",
                documentId, context.businessId()
        );
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Commercial document not found: " + documentId);
        }

        Map<String, Object> row = rows.get(0);
        CommercialDocumentKind kind = CommercialDocumentKind.valueOf((String) row.get("kind"));
        String commercialStatus = (String) row.get("status");
        String documentNumber = (String) row.get("document_number");
        BigDecimal total = (BigDecimal) row.get("total");
        String baseCurrency = (String) row.get("base_currency");

        List<FinancialObligation> obligations = obligationRepository.findByBusinessIdAndSourceId(context.businessId(), documentId.toString());
        ObligationSourceType targetSourceType = (kind == CommercialDocumentKind.INVOICE)
                ? ObligationSourceType.CUSTOMER_INVOICE
                : ObligationSourceType.SUPPLIER_BILL;

        Optional<FinancialObligation> matchingObligation = obligations.stream()
                .filter(o -> o.getSourceType() == targetSourceType)
                .findFirst();

        if (matchingObligation.isEmpty()) {
            return new CommercialRecognitionStatusResponse(
                    documentId,
                    kind,
                    documentNumber,
                    commercialStatus,
                    false,
                    null,
                    null,
                    null,
                    total != null ? total.setScale(4, RoundingMode.UNNECESSARY) : null,
                    null,
                    null,
                    baseCurrency != null ? baseCurrency.trim() : "PKR",
                    null,
                    null,
                    Collections.emptyList(),
                    "Commercial document has not been recognized as a financial obligation."
            );
        }

        FinancialObligation obligation = matchingObligation.get();
        BigDecimal allocatedAmount = allocationRepository.sumEffectiveAllocations(context.businessId(), obligation.getId());
        if (allocatedAmount == null) {
            allocatedAmount = BigDecimal.ZERO.setScale(4, RoundingMode.UNNECESSARY);
        }
        BigDecimal outstandingAmount = obligation.getOriginalAmount().subtract(allocatedAmount);

        ObligationDetailResponse detail = obligationService.getObligation(context, obligation.getId());

        return new CommercialRecognitionStatusResponse(
                documentId,
                kind,
                documentNumber,
                commercialStatus,
                true,
                obligation.getId(),
                obligation.getDirection(),
                obligation.getSourceType(),
                obligation.getOriginalAmount(),
                outstandingAmount,
                allocatedAmount,
                obligation.getCurrency(),
                obligation.getStatus(),
                obligation.getCreatedAt(),
                detail.allocations(),
                obligation.getDescription()
        );
    }

    /**
     * Lists finalized commercial documents for the business annotated with financial recognition status.
     */
    @Transactional(readOnly = true)
    public List<CommercialEligibleDocumentResponse> listEligibleDocuments(
            BusinessAccessContext context,
            CommercialDocumentKind kind
    ) {
        UUID businessId = context.businessId();
        List<CommercialDocumentRecord> documents = new ArrayList<>();

        if (kind != null) {
            documents.addAll(commercialDocumentRepository.list(businessId, kind, CommercialDocumentStatus.FINALIZED));
        } else {
            documents.addAll(commercialDocumentRepository.list(businessId, CommercialDocumentKind.INVOICE, CommercialDocumentStatus.FINALIZED));
            documents.addAll(commercialDocumentRepository.list(businessId, CommercialDocumentKind.BILL, CommercialDocumentStatus.FINALIZED));
        }

        List<FinancialObligation> allObligations = obligationRepository.findByBusinessId(businessId);
        Map<String, FinancialObligation> obligationBySourceId = new HashMap<>();
        for (FinancialObligation ob : allObligations) {
            if (ob.getSourceId() != null && ob.getStatus() != ObligationStatus.VOIDED) {
                obligationBySourceId.put(ob.getSourceId(), ob);
            }
        }

        List<CommercialEligibleDocumentResponse> result = new ArrayList<>();
        for (CommercialDocumentRecord doc : documents) {
            String docIdStr = doc.id().toString();
            FinancialObligation matching = obligationBySourceId.get(docIdStr);
            if (matching == null && doc.documentNumber() != null) {
                matching = obligationBySourceId.get(doc.documentNumber());
            }

            if (matching != null) {
                BigDecimal allocated = allocationRepository.sumEffectiveAllocations(businessId, matching.getId());
                if (allocated == null) {
                    allocated = BigDecimal.ZERO.setScale(4, RoundingMode.UNNECESSARY);
                }
                BigDecimal outstanding = matching.getOriginalAmount().subtract(allocated);

                result.add(new CommercialEligibleDocumentResponse(
                        doc.id(),
                        doc.documentNumber(),
                        doc.kind(),
                        doc.status(),
                        doc.counterpartyName(),
                        doc.documentDate(),
                        doc.dueDate(),
                        doc.total(),
                        doc.baseCurrency(),
                        true,
                        matching.getId(),
                        matching.getReferenceNumber(),
                        matching.getOriginalAmount(),
                        outstanding,
                        allocated,
                        matching.getStatus(),
                        matching.getCreatedAt(),
                        matching.getDescription()
                ));
            } else {
                result.add(new CommercialEligibleDocumentResponse(
                        doc.id(),
                        doc.documentNumber(),
                        doc.kind(),
                        doc.status(),
                        doc.counterpartyName(),
                        doc.documentDate(),
                        doc.dueDate(),
                        doc.total(),
                        doc.baseCurrency(),
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        doc.notes()
                ));
            }
        }

        result.sort((a, b) -> {
            if (a.documentDate() != null && b.documentDate() != null) {
                int cmp = b.documentDate().compareTo(a.documentDate());
                if (cmp != 0) return cmp;
            }
            if (a.documentNumber() != null && b.documentNumber() != null) {
                return a.documentNumber().compareTo(b.documentNumber());
            }
            return a.documentId().compareTo(b.documentId());
        });

        return result;
    }

    private String computePayloadHash(UUID businessId, UUID documentId, UUID counterpartyId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String raw = businessId.toString() + "|" + documentId.toString() + "|" + counterpartyId.toString();
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private CommercialRecognitionResponse deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, CommercialRecognitionResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize idempotency response", e);
        }
    }
}
