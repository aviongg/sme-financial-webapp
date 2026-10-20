package com.app.sme_health_backend.obligation;

import com.app.sme_health_backend.accounting.entity.FinancialIdempotencyRecord;
import com.app.sme_health_backend.accounting.repository.FinancialIdempotencyRepository;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.obligation.dto.*;
import com.app.sme_health_backend.audit.repository.SecurityAuditRepository;
import com.app.sme_health_backend.obligation.aging.ObligationDirection;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AdvanceService {

    public static final String OPERATION_ADVANCE_APPLY = "ADVANCE_APPLY";
    public static final String OPERATION_ADVANCE_APPLICATION_REVERSE = "ADVANCE_APPLICATION_REVERSE";
    private static final DateTimeFormatter ISO_INSTANT_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final Pattern EXACT_TWO_DECIMAL_AMOUNT = Pattern.compile("^(?:0|[1-9]\\d*)\\.\\d{2}$");

    private final CounterpartyAdvanceRepository advanceRepository;
    private final ObligationPaymentAllocationRepository allocationRepository;
    private final FinancialObligationRepository obligationRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final PaymentAllocationService paymentAllocationService;
    private final FinancialIdempotencyRepository idempotencyRepository;
    private final SecurityAuditRepository securityAuditRepository;
    private final ObjectMapper objectMapper;

    public AdvanceService(
            CounterpartyAdvanceRepository advanceRepository,
            ObligationPaymentAllocationRepository allocationRepository,
            FinancialObligationRepository obligationRepository,
            CounterpartyRepository counterpartyRepository,
            PaymentAllocationService paymentAllocationService,
            FinancialIdempotencyRepository idempotencyRepository,
            SecurityAuditRepository securityAuditRepository,
            ObjectMapper objectMapper
    ) {
        this.advanceRepository = advanceRepository;
        this.allocationRepository = allocationRepository;
        this.obligationRepository = obligationRepository;
        this.counterpartyRepository = counterpartyRepository;
        this.paymentAllocationService = paymentAllocationService;
        this.idempotencyRepository = idempotencyRepository;
        this.securityAuditRepository = securityAuditRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AdvanceDetailResponse createAdvance(
            BusinessAccessContext context,
            String idempotencyKey,
            AdvanceCreateRequest request
    ) {
        if (request == null) {
            throw new ObligationValidationException("body", "Request body is required");
        }

        PaymentAllocateRequest allocateRequest = new PaymentAllocateRequest(
                request.counterpartyId(),
                request.direction(),
                request.accountId(),
                request.paymentDate(),
                request.amount(),
                Collections.emptyList(),
                request.referenceNumber(),
                request.description(),
                true // Retain entire amount as advance
        );

        PaymentAllocateResponse allocResponse = paymentAllocationService.allocatePayment(
                context,
                idempotencyKey,
                allocateRequest
        );

        if (allocResponse.advanceId() == null) {
            throw new IllegalStateException("Failed to create advance from payment allocation");
        }

        return getAdvance(context, allocResponse.advanceId());
    }

    @Transactional(readOnly = true)
    public AdvanceListResponse listAdvances(
            BusinessAccessContext context,
            String directionStr,
            UUID counterpartyId,
            String statusStr,
            Pageable pageable
    ) {
        ObligationDirection direction = null;
        if (directionStr != null && !directionStr.isBlank()) {
            try {
                direction = ObligationDirection.valueOf(directionStr.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ObligationValidationException("direction", "Invalid direction: " + directionStr);
            }
        }

        AdvanceStatus status = null;
        if (statusStr != null && !statusStr.isBlank()) {
            try {
                status = AdvanceStatus.valueOf(statusStr.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ObligationValidationException("status", "Invalid status: " + statusStr);
            }
        }

        final ObligationDirection dirFilter = direction;
        final AdvanceStatus statusFilter = status;

        Specification<CounterpartyAdvance> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("businessId"), context.businessId()));

            if (dirFilter != null) {
                predicates.add(cb.equal(root.get("direction"), dirFilter));
            }
            if (counterpartyId != null) {
                predicates.add(cb.equal(root.get("counterpartyId"), counterpartyId));
            }
            if (statusFilter != null) {
                predicates.add(cb.equal(root.get("status"), statusFilter));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<CounterpartyAdvance> page = advanceRepository.findAll(spec, pageable);

        Set<UUID> cpIds = page.getContent().stream().map(CounterpartyAdvance::getCounterpartyId).collect(Collectors.toSet());
        Map<UUID, Counterparty> cpMap = cpIds.isEmpty() ? Collections.emptyMap() :
                counterpartyRepository.findAllById(cpIds).stream()
                        .filter(c -> c.getBusinessId().equals(context.businessId()))
                        .collect(Collectors.toMap(Counterparty::getId, c -> c));

        List<AdvanceDetailResponse> content = page.getContent().stream().map(adv -> {
            Counterparty cp = cpMap.get(adv.getCounterpartyId());
            BigDecimal applied = allocationRepository.sumEffectiveAllocationsByAdvanceId(context.businessId(), adv.getId());
            BigDecimal unapplied = adv.getOriginalAmount().subtract(applied);
            if (unapplied.compareTo(BigDecimal.ZERO) < 0) unapplied = BigDecimal.ZERO;

            return new AdvanceDetailResponse(
                    adv.getId(),
                    adv.getBusinessId(),
                    adv.getCounterpartyId(),
                    cp != null ? cp.getDisplayName() : "Unknown Counterparty",
                    cp != null ? cp.getType().name() : "OTHER",
                    adv.getDirection().name(),
                    adv.getFinancialTransactionId(),
                    adv.getOriginalAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                    applied.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                    unapplied.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                    adv.getCurrency(),
                    adv.getEffectiveDate(),
                    adv.getStatus().name(),
                    adv.getReferenceNumber(),
                    adv.getDescription(),
                    adv.getCancelledAt() != null ? ISO_INSTANT_FORMATTER.format(adv.getCancelledAt().truncatedTo(ChronoUnit.SECONDS)) : null,
                    adv.getCancellationReason(),
                    adv.getReversalTransactionId(),
                    ISO_INSTANT_FORMATTER.format(adv.getCreatedAt().truncatedTo(ChronoUnit.SECONDS)),
                    Collections.emptyList()
            );
        }).toList();

        return new AdvanceListResponse(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    @Transactional(readOnly = true)
    public AdvanceDetailResponse getAdvance(BusinessAccessContext context, UUID advanceId) {
        CounterpartyAdvance adv = advanceRepository.findByIdAndBusinessId(advanceId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Advance not found"));

        Counterparty cp = counterpartyRepository.findByIdAndBusinessId(adv.getCounterpartyId(), context.businessId()).orElse(null);

        List<ObligationPaymentAllocation> allocations = allocationRepository.findByBusinessIdAndAdvanceIdOrderByEffectiveDateAscCreatedAtAsc(context.businessId(), advanceId);

        Set<UUID> obIds = allocations.stream().map(ObligationPaymentAllocation::getObligationId).collect(Collectors.toSet());
        Map<UUID, FinancialObligation> obMap = obIds.isEmpty() ? Collections.emptyMap() :
                obligationRepository.findAllById(obIds).stream()
                        .filter(o -> o.getBusinessId().equals(context.businessId()))
                        .collect(Collectors.toMap(FinancialObligation::getId, o -> o));

        BigDecimal applied = BigDecimal.ZERO;
        List<AdvanceApplicationItemResponse> applicationItems = new ArrayList<>();
        for (ObligationPaymentAllocation a : allocations) {
            if (a.getStatus() == AllocationStatus.EFFECTIVE) {
                applied = applied.add(a.getAmount());
            }
            FinancialObligation ob = obMap.get(a.getObligationId());
            applicationItems.add(new AdvanceApplicationItemResponse(
                    a.getId(),
                    a.getObligationId(),
                    ob != null ? ob.getReferenceNumber() : null,
                    a.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                    a.getEffectiveDate(),
                    a.getStatus().name(),
                    a.getCancelledAt() != null ? ISO_INSTANT_FORMATTER.format(a.getCancelledAt().truncatedTo(ChronoUnit.SECONDS)) : null,
                    a.getCancellationReason(),
                    ISO_INSTANT_FORMATTER.format(a.getCreatedAt().truncatedTo(ChronoUnit.SECONDS))
            ));
        }

        BigDecimal unapplied = adv.getOriginalAmount().subtract(applied);
        if (unapplied.compareTo(BigDecimal.ZERO) < 0) unapplied = BigDecimal.ZERO;

        return new AdvanceDetailResponse(
                adv.getId(),
                adv.getBusinessId(),
                adv.getCounterpartyId(),
                cp != null ? cp.getDisplayName() : "Unknown Counterparty",
                cp != null ? cp.getType().name() : "OTHER",
                adv.getDirection().name(),
                adv.getFinancialTransactionId(),
                adv.getOriginalAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                applied.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                unapplied.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                adv.getCurrency(),
                adv.getEffectiveDate(),
                adv.getStatus().name(),
                adv.getReferenceNumber(),
                adv.getDescription(),
                adv.getCancelledAt() != null ? ISO_INSTANT_FORMATTER.format(adv.getCancelledAt().truncatedTo(ChronoUnit.SECONDS)) : null,
                adv.getCancellationReason(),
                adv.getReversalTransactionId(),
                ISO_INSTANT_FORMATTER.format(adv.getCreatedAt().truncatedTo(ChronoUnit.SECONDS)),
                applicationItems
        );
    }

    @Transactional
    public AdvanceDetailResponse applyAdvance(
            BusinessAccessContext context,
            String idempotencyKey,
            UUID advanceId,
            AdvanceApplyRequest request
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ObligationValidationException("Idempotency-Key", "Missing required header: Idempotency-Key");
        }
        String trimmedKey = idempotencyKey.trim();
        if (trimmedKey.length() > 100) {
            throw new ObligationValidationException("Idempotency-Key", "Idempotency-Key must not exceed 100 characters");
        }

        if (request == null) {
            throw new ObligationValidationException("body", "Request body is required");
        }

        String payloadHash = computeApplyPayloadHash(advanceId, request);
        Optional<FinancialIdempotencyRecord> existingOpt = idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(
                context.businessId(),
                OPERATION_ADVANCE_APPLY,
                trimmedKey
        );
        if (existingOpt.isPresent()) {
            FinancialIdempotencyRecord existing = existingOpt.get();
            if (existing.getRequestHash().equals(payloadHash)) {
                return deserializeResponse(existing.getResponseBody());
            } else {
                throw new DuplicateResourceException(
                        "Idempotency key '" + trimmedKey + "' has already been used with a different request payload"
                );
            }
        }

        if (request.obligationId() == null) {
            throw new ObligationValidationException("obligationId", "Obligation ID is required");
        }

        if (request.amount() == null || request.amount().isBlank()) {
            throw new ObligationValidationException("amount", "Application amount is required");
        }
        String trimmedAmount = request.amount().trim();
        if (!EXACT_TWO_DECIMAL_AMOUNT.matcher(trimmedAmount).matches()) {
            throw new ObligationValidationException("amount", "Application amount must have exactly 2 decimal places (e.g. 50.00)");
        }
        BigDecimal applyAmount = new BigDecimal(trimmedAmount);
        if (applyAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ObligationValidationException("amount", "Application amount must be strictly positive");
        }

        LocalDate applicationDate = LocalDate.now(ZoneId.of("Asia/Karachi"));
        if (request.applicationDate() != null && !request.applicationDate().isBlank()) {
            try {
                applicationDate = LocalDate.parse(request.applicationDate().trim());
            } catch (DateTimeParseException e) {
                throw new ObligationValidationException("applicationDate", "Invalid applicationDate format. Expected YYYY-MM-DD");
            }
        }

        // Lock Advance first, then Obligation (deterministic lock ordering)
        CounterpartyAdvance advance = advanceRepository.findForUpdateByIdAndBusinessId(advanceId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Advance not found"));

        if (advance.getStatus() == AdvanceStatus.CANCELLED) {
            throw new ObligationConflictException("Cannot apply a cancelled advance");
        }

        BigDecimal appliedSoFar = allocationRepository.sumEffectiveAllocationsByAdvanceId(context.businessId(), advance.getId());
        BigDecimal availableBalance = advance.getOriginalAmount().subtract(appliedSoFar);
        if (availableBalance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ObligationConflictException("Advance is fully exhausted");
        }
        if (applyAmount.compareTo(availableBalance) > 0) {
            throw new ObligationConflictException(
                    "Applied amount (" + applyAmount.setScale(2, RoundingMode.UNNECESSARY).toPlainString() +
                            ") exceeds available advance balance (" + availableBalance.setScale(2, RoundingMode.UNNECESSARY).toPlainString() + ")"
            );
        }

        FinancialObligation obligation = obligationRepository.findForUpdateByIdAndBusinessId(request.obligationId(), context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Obligation not found"));

        if (obligation.getStatus() != ObligationStatus.OPEN) {
            throw new ObligationConflictException("Obligation is voided and cannot accept payment");
        }
        if (obligation.getDirection() != advance.getDirection()) {
            throw new ObligationConflictException("Obligation direction (" + obligation.getDirection() + ") does not match advance direction (" + advance.getDirection() + ")");
        }
        if (!obligation.getCounterpartyId().equals(advance.getCounterpartyId())) {
            throw new ObligationConflictException("Obligation counterparty does not match advance counterparty");
        }
        if (applicationDate.isBefore(advance.getEffectiveDate())) {
            throw new ObligationValidationException("applicationDate", "Application date (" + applicationDate + ") cannot precede advance date (" + advance.getEffectiveDate() + ")");
        }
        if (applicationDate.isBefore(obligation.getIssueDate())) {
            throw new ObligationValidationException("applicationDate", "Application date (" + applicationDate + ") cannot precede obligation issue date (" + obligation.getIssueDate() + ")");
        }

        BigDecimal obPaid = allocationRepository.sumEffectiveAllocations(context.businessId(), obligation.getId());
        BigDecimal obOutstanding = obligation.getOriginalAmount().subtract(obPaid);
        if (applyAmount.compareTo(obOutstanding) > 0) {
            throw new ObligationConflictException(
                    "Applied amount (" + applyAmount.setScale(2, RoundingMode.UNNECESSARY).toPlainString() +
                            ") exceeds obligation remaining outstanding (" + obOutstanding.setScale(2, RoundingMode.UNNECESSARY).toPlainString() + ")"
            );
        }

        // Post allocation with allocationType = ADVANCE (ZERO financial transactions or account movements)
        ObligationPaymentAllocation alloc = ObligationPaymentAllocation.fromAdvance(
                context.businessId(),
                obligation.getId(),
                advance.getId(),
                applyAmount,
                applicationDate,
                context.userId()
        );
        allocationRepository.saveAndFlush(alloc);

        // Update advance status if exhausted
        BigDecimal remainingAfter = availableBalance.subtract(applyAmount);
        if (remainingAfter.compareTo(BigDecimal.ZERO) == 0) {
            advance.setStatus(AdvanceStatus.EXHAUSTED);
        } else {
            advance.setStatus(AdvanceStatus.ACTIVE);
        }
        advance.setUpdatedAt(Instant.now());
        advanceRepository.saveAndFlush(advance);

        AdvanceDetailResponse response = getAdvance(context, advance.getId());

        String responseJson = serializeResponse(response);
        FinancialIdempotencyRecord idempotencyRecord = new FinancialIdempotencyRecord(
                context.businessId(),
                OPERATION_ADVANCE_APPLY,
                trimmedKey,
                payloadHash,
                advance.getId(),
                201,
                responseJson
        );
        idempotencyRepository.saveAndFlush(idempotencyRecord);

        return response;
    }

    @Transactional
    public AdvanceDetailResponse reverseApplication(
            BusinessAccessContext context,
            String idempotencyKey,
            UUID advanceId,
            UUID applicationId,
            AdvanceApplicationReverseRequest request
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ObligationValidationException("Idempotency-Key", "Missing required header: Idempotency-Key");
        }
        String trimmedKey = idempotencyKey.trim();
        if (trimmedKey.length() > 100) {
            throw new ObligationValidationException("Idempotency-Key", "Idempotency-Key must not exceed 100 characters");
        }

        String reason = (request != null && request.reason() != null && !request.reason().isBlank())
                ? request.reason().trim()
                : "Advance application correction";
        if (reason.length() < 3 || reason.length() > 500) {
            throw new ObligationValidationException("reason", "Reason must be between 3 and 500 characters");
        }

        String payloadHash = computeReversePayloadHash(advanceId, applicationId, reason);
        Optional<FinancialIdempotencyRecord> existingOpt = idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(
                context.businessId(),
                OPERATION_ADVANCE_APPLICATION_REVERSE,
                trimmedKey
        );
        if (existingOpt.isPresent()) {
            FinancialIdempotencyRecord existing = existingOpt.get();
            if (existing.getRequestHash().equals(payloadHash)) {
                return deserializeResponse(existing.getResponseBody());
            } else {
                throw new DuplicateResourceException(
                        "Idempotency key '" + trimmedKey + "' has already been used with a different request payload"
                );
            }
        }

        // Lock Advance, then Obligation
        CounterpartyAdvance advance = advanceRepository.findForUpdateByIdAndBusinessId(advanceId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Advance not found"));

        ObligationPaymentAllocation allocation = allocationRepository.findByIdAndBusinessId(applicationId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Allocation not found"));

        if (!advance.getId().equals(allocation.getAdvanceId())) {
            throw new ObligationConflictException("Allocation does not belong to this advance");
        }
        if (allocation.getStatus() != AllocationStatus.EFFECTIVE) {
            throw new ObligationConflictException("Application is already cancelled");
        }

        FinancialObligation obligation = obligationRepository.findForUpdateByIdAndBusinessId(allocation.getObligationId(), context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Obligation not found"));

        // Cancel allocation (restores advance available balance and obligation outstanding, ZERO cash movements)
        allocation.setStatus(AllocationStatus.CANCELLED);
        allocation.setCancelledAt(Instant.now());
        allocation.setCancellationReason(reason);
        allocationRepository.saveAndFlush(allocation);

        // Restore advance status to ACTIVE
        if (advance.getStatus() == AdvanceStatus.EXHAUSTED) {
            advance.setStatus(AdvanceStatus.ACTIVE);
            advance.setUpdatedAt(Instant.now());
            advanceRepository.saveAndFlush(advance);
        }

        AdvanceDetailResponse response = getAdvance(context, advance.getId());

        String responseJson = serializeResponse(response);
        FinancialIdempotencyRecord idempotencyRecord = new FinancialIdempotencyRecord(
                context.businessId(),
                OPERATION_ADVANCE_APPLICATION_REVERSE,
                trimmedKey,
                payloadHash,
                advance.getId(),
                200,
                responseJson
        );
        idempotencyRepository.saveAndFlush(idempotencyRecord);

        return response;
    }

    private String computeApplyPayloadHash(UUID advanceId, AdvanceApplyRequest req) {
        String canonical = (advanceId != null ? advanceId.toString() : "") + "|" +
                (req.obligationId() != null ? req.obligationId().toString() : "") + "|" +
                (req.amount() != null ? req.amount() : "") + "|" +
                (req.applicationDate() != null ? req.applicationDate() : "");
        return hashString(canonical);
    }

    private String computeReversePayloadHash(UUID advanceId, UUID applicationId, String reason) {
        String canonical = (advanceId != null ? advanceId.toString() : "") + "|" +
                (applicationId != null ? applicationId.toString() : "") + "|" +
                (reason != null ? reason : "");
        return hashString(canonical);
    }

    private String hashString(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String serializeResponse(AdvanceDetailResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize advance response", e);
        }
    }

    private AdvanceDetailResponse deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, AdvanceDetailResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize advance response", e);
        }
    }
}
