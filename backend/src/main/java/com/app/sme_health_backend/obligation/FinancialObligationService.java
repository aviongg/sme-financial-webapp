package com.app.sme_health_backend.obligation;

import com.app.sme_health_backend.accounting.dto.MovementDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionDetailResponse;
import com.app.sme_health_backend.accounting.entity.AccountMovement;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialIdempotencyRecord;
import com.app.sme_health_backend.accounting.entity.FinancialTransaction;
import com.app.sme_health_backend.accounting.exception.TransactionValidationException;
import com.app.sme_health_backend.accounting.model.*;
import com.app.sme_health_backend.accounting.repository.AccountMovementRepository;
import com.app.sme_health_backend.accounting.repository.AccountMovementSum;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialIdempotencyRepository;
import com.app.sme_health_backend.accounting.repository.FinancialTransactionRepository;
import com.app.sme_health_backend.audit.entity.SecurityAuditEvent;
import com.app.sme_health_backend.audit.model.AuditOutcome;
import com.app.sme_health_backend.audit.repository.SecurityAuditRepository;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.counterparty.CounterpartyStatus;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.obligation.aging.*;
import com.app.sme_health_backend.obligation.dto.*;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
public class FinancialObligationService {

    public static final String OPERATION_SETTLEMENT = "OBLIGATION_SETTLEMENT";
    private static final ZoneId KARACHI_ZONE = ZoneId.of("Asia/Karachi");
    private static final DateTimeFormatter ISO_INSTANT_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final BigDecimal MAX_CAPACITY = new BigDecimal("999999999999.99");
    private static final Pattern EXACT_TWO_DECIMAL_AMOUNT = Pattern.compile("^(?:0|[1-9]\\d*)\\.\\d{2}$");

    private final FinancialObligationRepository obligationRepository;
    private final ObligationPaymentAllocationRepository allocationRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final FinancialAccountRepository accountRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final AccountMovementRepository movementRepository;
    private final FinancialIdempotencyRepository idempotencyRepository;
    private final SecurityAuditRepository securityAuditRepository;
    private final ObjectMapper objectMapper;

    public FinancialObligationService(
            FinancialObligationRepository obligationRepository,
            ObligationPaymentAllocationRepository allocationRepository,
            CounterpartyRepository counterpartyRepository,
            FinancialAccountRepository accountRepository,
            FinancialTransactionRepository transactionRepository,
            AccountMovementRepository movementRepository,
            FinancialIdempotencyRepository idempotencyRepository,
            SecurityAuditRepository securityAuditRepository,
            ObjectMapper objectMapper
    ) {
        this.obligationRepository = obligationRepository;
        this.allocationRepository = allocationRepository;
        this.counterpartyRepository = counterpartyRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.movementRepository = movementRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.securityAuditRepository = securityAuditRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ObligationDetailResponse createObligation(BusinessAccessContext context, ObligationCreateRequest request) {
        if (request == null) {
            throw new ObligationValidationException("body", "Request body is required");
        }

        Map<String, String> errors = new LinkedHashMap<>();

        // 1. Counterparty
        Counterparty counterparty = null;
        if (request.counterpartyId() == null) {
            errors.put("counterpartyId", "Counterparty is required");
        } else {
            counterparty = counterpartyRepository.findByIdAndBusinessId(request.counterpartyId(), context.businessId()).orElse(null);
            if (counterparty == null) {
                errors.put("counterpartyId", "Counterparty not found");
            } else if (counterparty.getStatus() != CounterpartyStatus.ACTIVE) {
                errors.put("counterpartyId", "Counterparty is archived");
            }
        }

        // 2. Direction
        ObligationDirection direction = null;
        if (request.direction() == null || request.direction().isBlank()) {
            errors.put("direction", "Direction is required");
        } else {
            try {
                direction = ObligationDirection.valueOf(request.direction().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errors.put("direction", "Direction must be RECEIVABLE or PAYABLE");
            }
        }

        // Validate direction compatibility with counterparty
        if (counterparty != null && direction != null) {
            if (direction == ObligationDirection.RECEIVABLE && !counterparty.getType().isCustomerCompatible()) {
                errors.put("counterpartyId", "Counterparty must be a customer for receivables");
            } else if (direction == ObligationDirection.PAYABLE && !counterparty.getType().isSupplierCompatible()) {
                errors.put("counterpartyId", "Counterparty must be a supplier for payables");
            }
        }

        // 3. Amount
        BigDecimal amount = null;
        if (request.amount() == null || request.amount().isBlank()) {
            errors.put("amount", "Amount is required");
        } else {
            String rawAmount = request.amount().trim();
            if (!EXACT_TWO_DECIMAL_AMOUNT.matcher(rawAmount).matches()) {
                errors.put("amount", "Amount must be an exact two-decimal positive amount (e.g. 100.00)");
            } else {
                try {
                    amount = new BigDecimal(rawAmount);
                    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                        errors.put("amount", "Amount must be greater than zero");
                    } else if (amount.compareTo(MAX_CAPACITY) > 0) {
                        errors.put("amount", "Amount exceeds maximum capacity of 999,999,999,999.99");
                    }
                } catch (NumberFormatException e) {
                    errors.put("amount", "Invalid amount format");
                }
            }
        }

        // 4. Dates
        LocalDate today = LocalDate.now(KARACHI_ZONE);
        LocalDate issueDate = null;
        if (request.issueDate() == null || request.issueDate().isBlank()) {
            errors.put("issueDate", "Issue date is required");
        } else {
            try {
                issueDate = LocalDate.parse(request.issueDate().trim());
                if (issueDate.isAfter(today)) {
                    errors.put("issueDate", "Issue date cannot be in the future beyond today (" + today + ")");
                }
            } catch (DateTimeParseException e) {
                errors.put("issueDate", "Invalid issue date format. Expected YYYY-MM-DD");
            }
        }

        LocalDate dueDate = null;
        if (request.dueDate() != null && !request.dueDate().isBlank()) {
            try {
                dueDate = LocalDate.parse(request.dueDate().trim());
                if (issueDate != null && dueDate.isBefore(issueDate)) {
                    errors.put("dueDate", "Due date cannot precede issue date (" + issueDate + ")");
                }
            } catch (DateTimeParseException e) {
                errors.put("dueDate", "Invalid due date format. Expected YYYY-MM-DD");
            }
        }

        // 5. Source Type & Id
        ObligationSourceType sourceType = ObligationSourceType.MANUAL_DECLARATION;
        if (request.sourceType() != null && !request.sourceType().isBlank()) {
            try {
                sourceType = ObligationSourceType.valueOf(request.sourceType().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errors.put("sourceType", "Invalid sourceType: " + request.sourceType());
            }
        }

        String sourceId = (request.sourceId() != null && !request.sourceId().isBlank()) ? request.sourceId().trim() : null;
        if (sourceId != null && obligationRepository.existsByBusinessIdAndSourceTypeAndSourceId(context.businessId(), sourceType, sourceId)) {
            errors.put("sourceId", "An obligation already exists for this source document");
        }

        // 6. Optional string fields
        String referenceNumber = (request.referenceNumber() != null && !request.referenceNumber().isBlank())
                ? request.referenceNumber().trim() : null;
        if (referenceNumber != null && referenceNumber.length() > 100) {
            errors.put("referenceNumber", "Reference number must not exceed 100 characters");
        }

        String description = (request.description() != null && !request.description().isBlank())
                ? request.description().trim() : null;
        if (description != null && description.length() > 1000) {
            errors.put("description", "Description must not exceed 1000 characters");
        }

        if (!errors.isEmpty()) {
            throw new ObligationValidationException(errors);
        }

        FinancialObligation obligation = new FinancialObligation(
                context.businessId(),
                counterparty.getId(),
                direction,
                sourceType,
                sourceId,
                referenceNumber,
                issueDate,
                dueDate,
                amount,
                "PKR",
                description,
                context.userId()
        );

        FinancialObligation saved = obligationRepository.saveAndFlush(obligation);

        SecurityAuditEvent auditEvent = new SecurityAuditEvent();
        auditEvent.setEventType("OBLIGATION_CREATED");
        auditEvent.setActorUserId(context.userId());
        auditEvent.setBusinessId(context.businessId());
        auditEvent.setTargetType("FINANCIAL_OBLIGATION");
        auditEvent.setTargetId(saved.getId().toString());
        auditEvent.setOutcome(AuditOutcome.SUCCESS);
        auditEvent.setMetadata("{\"direction\":\"" + direction + "\",\"amount\":\"" + amount.toPlainString() + "\"}");
        securityAuditRepository.saveAndFlush(auditEvent);

        return toDetailResponse(saved, counterparty, List.of(), BigDecimal.ZERO);
    }

    @Transactional
    public ObligationDetailResponse updateObligation(BusinessAccessContext context, UUID id, ObligationUpdateRequest request) {
        if (request == null) {
            throw new ObligationValidationException("body", "Request body is required");
        }

        FinancialObligation obligation = obligationRepository.findByIdAndBusinessId(id, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Obligation not found"));

        if (obligation.getStatus() == ObligationStatus.VOIDED) {
            throw new ObligationConflictException("Cannot modify a voided obligation");
        }

        Map<String, String> errors = new LinkedHashMap<>();

        if (request.dueDate() != null && !request.dueDate().isBlank()) {
            try {
                LocalDate dueDate = LocalDate.parse(request.dueDate().trim());
                if (dueDate.isBefore(obligation.getIssueDate())) {
                    errors.put("dueDate", "Due date cannot precede issue date (" + obligation.getIssueDate() + ")");
                } else {
                    obligation.setDueDate(dueDate);
                }
            } catch (DateTimeParseException e) {
                errors.put("dueDate", "Invalid due date format. Expected YYYY-MM-DD");
            }
        }

        if (request.referenceNumber() != null) {
            String trimmed = request.referenceNumber().trim();
            if (trimmed.length() > 100) {
                errors.put("referenceNumber", "Reference number must not exceed 100 characters");
            } else {
                obligation.setReferenceNumber(trimmed.isEmpty() ? null : trimmed);
            }
        }

        if (request.description() != null) {
            String trimmed = request.description().trim();
            if (trimmed.length() > 1000) {
                errors.put("description", "Description must not exceed 1000 characters");
            } else {
                obligation.setDescription(trimmed.isEmpty() ? null : trimmed);
            }
        }

        if (!errors.isEmpty()) {
            throw new ObligationValidationException(errors);
        }

        FinancialObligation saved = obligationRepository.saveAndFlush(obligation);
        Counterparty counterparty = counterpartyRepository.findByIdAndBusinessId(saved.getCounterpartyId(), context.businessId()).orElse(null);
        List<ObligationPaymentAllocation> allocations = allocationRepository.findByBusinessIdAndObligationIdOrderByEffectiveDateAscCreatedAtAsc(context.businessId(), id);
        BigDecimal effectivePaid = allocationRepository.sumEffectiveAllocations(context.businessId(), id);

        return toDetailResponse(saved, counterparty, allocations, effectivePaid);
    }

    @Transactional
    public ObligationDetailResponse voidObligation(BusinessAccessContext context, UUID id, ObligationVoidRequest request) {
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new ObligationValidationException("reason", "Void reason is required");
        }
        String trimmedReason = request.reason().trim();
        if (trimmedReason.length() > 500) {
            throw new ObligationValidationException("reason", "Void reason must not exceed 500 characters");
        }

        FinancialObligation obligation = obligationRepository.findByIdAndBusinessId(id, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Obligation not found"));

        if (obligation.getStatus() == ObligationStatus.VOIDED) {
            throw new ObligationConflictException("Obligation is already voided");
        }

        BigDecimal effectiveAllocations = allocationRepository.sumEffectiveAllocations(context.businessId(), id);
        if (effectiveAllocations.compareTo(BigDecimal.ZERO) > 0) {
            throw new ObligationConflictException("Cannot void obligation with effective settlements. Reverse settlements first.");
        }

        LocalDate today = LocalDate.now(KARACHI_ZONE);
        obligation.setStatus(ObligationStatus.VOIDED);
        obligation.setVoidDate(today);
        obligation.setVoidReason(trimmedReason);

        FinancialObligation saved = obligationRepository.saveAndFlush(obligation);

        SecurityAuditEvent auditEvent = new SecurityAuditEvent();
        auditEvent.setEventType("OBLIGATION_VOIDED");
        auditEvent.setActorUserId(context.userId());
        auditEvent.setBusinessId(context.businessId());
        auditEvent.setTargetType("FINANCIAL_OBLIGATION");
        auditEvent.setTargetId(saved.getId().toString());
        auditEvent.setOutcome(AuditOutcome.SUCCESS);
        auditEvent.setMetadata("{\"reason\":\"" + trimmedReason + "\"}");
        securityAuditRepository.saveAndFlush(auditEvent);

        Counterparty counterparty = counterpartyRepository.findByIdAndBusinessId(saved.getCounterpartyId(), context.businessId()).orElse(null);
        List<ObligationPaymentAllocation> allocations = allocationRepository.findByBusinessIdAndObligationIdOrderByEffectiveDateAscCreatedAtAsc(context.businessId(), id);

        return toDetailResponse(saved, counterparty, allocations, BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public ObligationDetailResponse getObligation(BusinessAccessContext context, UUID id) {
        FinancialObligation obligation = obligationRepository.findByIdAndBusinessId(id, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Obligation not found"));

        Counterparty counterparty = counterpartyRepository.findByIdAndBusinessId(obligation.getCounterpartyId(), context.businessId()).orElse(null);
        List<ObligationPaymentAllocation> allocations = allocationRepository.findByBusinessIdAndObligationIdOrderByEffectiveDateAscCreatedAtAsc(context.businessId(), id);
        BigDecimal effectivePaid = allocationRepository.sumEffectiveAllocations(context.businessId(), id);

        return toDetailResponse(obligation, counterparty, allocations, effectivePaid);
    }

    @Transactional(readOnly = true)
    public ObligationListResponse listObligations(
            BusinessAccessContext context,
            String directionStr,
            UUID counterpartyId,
            String statusStr,
            String settlementStatusStr,
            String search,
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

        ObligationStatus status = null;
        if (statusStr != null && !statusStr.isBlank()) {
            try {
                status = ObligationStatus.valueOf(statusStr.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ObligationValidationException("status", "Invalid status: " + statusStr);
            }
        }

        final ObligationDirection dirFilter = direction;
        final ObligationStatus statusFilter = status;

        Specification<FinancialObligation> spec = (root, query, cb) -> {
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
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(root.get("referenceNumber")), pattern));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<FinancialObligation> page = obligationRepository.findAll(spec, pageable);

        List<UUID> obligationIds = page.getContent().stream().map(FinancialObligation::getId).toList();
        Map<UUID, BigDecimal> paidSums = new HashMap<>();
        if (!obligationIds.isEmpty()) {
            List<Object[]> sumRows = allocationRepository.sumEffectiveAllocationsByObligationIds(context.businessId(), obligationIds);
            for (Object[] row : sumRows) {
                paidSums.put((UUID) row[0], (BigDecimal) row[1]);
            }
        }

        Set<UUID> counterpartyIds = page.getContent().stream().map(FinancialObligation::getCounterpartyId).collect(Collectors.toSet());
        Map<UUID, Counterparty> counterparties = counterpartyIds.isEmpty() ? Collections.emptyMap() :
                counterpartyRepository.findAllById(counterpartyIds).stream()
                        .filter(c -> c.getBusinessId().equals(context.businessId()))
                        .collect(Collectors.toMap(Counterparty::getId, c -> c));

        LocalDate today = LocalDate.now(KARACHI_ZONE);

        List<ObligationSummaryResponse> items = page.getContent().stream()
                .map(o -> {
                    Counterparty cp = counterparties.get(o.getCounterpartyId());
                    BigDecimal paid = paidSums.getOrDefault(o.getId(), BigDecimal.ZERO);
                    return toSummaryResponse(o, cp, paid, today);
                })
                .filter(item -> {
                    if (settlementStatusStr != null && !settlementStatusStr.isBlank()) {
                        return item.settlementStatus().equalsIgnoreCase(settlementStatusStr.trim());
                    }
                    return true;
                })
                .toList();

        return new ObligationListResponse(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    @Transactional
    public ObligationSettlementResponse recordSettlement(
            BusinessAccessContext context,
            UUID obligationId,
            String idempotencyKey,
            ObligationSettlementRequest request
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

        Map<String, String> errors = new LinkedHashMap<>();

        // 1. Account ID
        if (request.accountId() == null) {
            errors.put("accountId", "Money account is required");
        }

        // 2. Amount
        BigDecimal amount = null;
        if (request.amount() == null || request.amount().isBlank()) {
            errors.put("amount", "Amount is required");
        } else {
            String rawAmount = request.amount().trim();
            if (!EXACT_TWO_DECIMAL_AMOUNT.matcher(rawAmount).matches()) {
                errors.put("amount", "Amount must be an exact two-decimal positive amount (e.g. 50.00)");
            } else {
                try {
                    amount = new BigDecimal(rawAmount);
                    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                        errors.put("amount", "Amount must be greater than zero");
                    } else if (amount.compareTo(MAX_CAPACITY) > 0) {
                        errors.put("amount", "Amount exceeds maximum capacity of 999,999,999,999.99");
                    }
                } catch (NumberFormatException e) {
                    errors.put("amount", "Invalid amount format");
                }
            }
        }

        // 3. Settlement date
        LocalDate today = LocalDate.now(KARACHI_ZONE);
        LocalDate settlementDate = null;
        if (request.settlementDate() == null || request.settlementDate().isBlank()) {
            errors.put("settlementDate", "Settlement date is required");
        } else {
            try {
                settlementDate = LocalDate.parse(request.settlementDate().trim());
                if (settlementDate.isAfter(today)) {
                    errors.put("settlementDate", "Settlement date cannot be in the future beyond today (" + today + ")");
                }
            } catch (DateTimeParseException e) {
                errors.put("settlementDate", "Invalid settlement date format. Expected YYYY-MM-DD");
            }
        }

        String reference = (request.reference() != null && !request.reference().isBlank())
                ? request.reference().trim() : null;
        if (reference != null && reference.length() > 255) {
            errors.put("reference", "Reference must not exceed 255 characters");
        }

        String description = (request.description() != null && !request.description().isBlank())
                ? request.description().trim() : null;
        if (description != null && description.length() > 255) {
            errors.put("description", "Description must not exceed 255 characters");
        }

        if (!errors.isEmpty()) {
            throw new ObligationValidationException(errors);
        }

        // Lock obligation first (deterministic lock hierarchy: Obligation -> Account)
        FinancialObligation obligation = obligationRepository.findByIdAndBusinessIdForUpdate(obligationId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Obligation not found"));

        if (obligation.getStatus() == ObligationStatus.VOIDED) {
            throw new ObligationConflictException("Cannot record settlement on a voided obligation");
        }

        if (settlementDate.isBefore(obligation.getIssueDate())) {
            throw new ObligationValidationException("settlementDate", "Settlement date cannot precede obligation issue date (" + obligation.getIssueDate() + ")");
        }

        // Compute payload hash for durable idempotency
        String payloadHash = computeSettlementPayloadHash(
                obligationId,
                request.accountId(),
                amount,
                settlementDate,
                reference,
                description
        );

        Optional<FinancialIdempotencyRecord> existingOpt = idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(
                context.businessId(),
                OPERATION_SETTLEMENT,
                trimmedKey
        );

        if (existingOpt.isPresent()) {
            FinancialIdempotencyRecord existing = existingOpt.get();
            if (existing.getRequestHash().equals(payloadHash)) {
                return deserializeSettlementResponse(existing.getResponseBody());
            } else {
                throw new DuplicateResourceException("Idempotency key '" + trimmedKey + "' has already been used with a different request payload");
            }
        }

        BigDecimal currentPaid = allocationRepository.sumEffectiveAllocations(context.businessId(), obligationId);
        BigDecimal outstanding = obligation.getOriginalAmount().subtract(currentPaid);

        if (amount.compareTo(outstanding) > 0) {
            throw new ObligationConflictException("Settlement amount (" + amount.setScale(2) + ") exceeds outstanding balance (" + outstanding.setScale(2) + ")");
        }

        // Lock account second
        FinancialAccount account = accountRepository.findForUpdateByIdAndBusinessId(request.accountId(), context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Financial account not found"));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ObligationValidationException("accountId", "Financial account is not active");
        }
        if (!"PKR".equals(account.getCurrency())) {
            throw new ObligationValidationException("accountId", "Settlement account must be a PKR account");
        }
        if (settlementDate.isBefore(account.getOpeningDate())) {
            throw new ObligationValidationException("settlementDate", "Settlement date cannot precede account opening date (" + account.getOpeningDate() + ")");
        }

        TransactionEventType eventType;
        MovementDirection direction;
        if (obligation.getDirection() == ObligationDirection.RECEIVABLE) {
            eventType = TransactionEventType.RECEIVABLE_COLLECTION;
            direction = MovementDirection.INCREASE;
        } else {
            eventType = TransactionEventType.PAYABLE_SETTLEMENT;
            direction = MovementDirection.DECREASE;
            // Validate chronological sequence for account decrease
            validateChronologicalSequence(account, MovementDirection.DECREASE, amount, settlementDate, context.businessId());
        }

        // 1. Create and persist FinancialTransaction
        String txDescription = description != null ? description :
                (obligation.getDirection() == ObligationDirection.RECEIVABLE ? "Customer payment collection" : "Supplier payable settlement");
        if (reference != null) {
            txDescription = txDescription + " (" + reference + ")";
        }

        FinancialTransaction tx = new FinancialTransaction(
                context.businessId(),
                account.getId(),
                eventType,
                direction,
                amount,
                "PKR",
                amount,
                settlementDate,
                txDescription,
                context.userId(),
                obligation.getCounterpartyId()
        );
        tx = transactionRepository.saveAndFlush(tx);

        // 2. Create and persist AccountMovement
        AccountMovement movement = new AccountMovement(
                context.businessId(),
                tx.getId(),
                account.getId(),
                direction,
                amount,
                "PKR",
                settlementDate
        );
        movementRepository.saveAndFlush(movement);

        // 3. Create and persist ObligationPaymentAllocation
        ObligationPaymentAllocation allocation = new ObligationPaymentAllocation(
                context.businessId(),
                obligation.getId(),
                tx.getId(),
                amount,
                settlementDate,
                context.userId()
        );
        allocation = allocationRepository.saveAndFlush(allocation);

        // 4. Compute resulting account balance
        List<AccountMovementSum> sums = movementRepository.findMovementSumsByAccountIdAndCutoffDate(account.getId(), today);
        BigDecimal totalIncrease = BigDecimal.ZERO;
        BigDecimal totalDecrease = BigDecimal.ZERO;
        for (AccountMovementSum sum : sums) {
            if (sum.getMovementDirection() == MovementDirection.INCREASE) {
                totalIncrease = totalIncrease.add(sum.getTotalAmount());
            } else if (sum.getMovementDirection() == MovementDirection.DECREASE) {
                totalDecrease = totalDecrease.add(sum.getTotalAmount());
            }
        }
        BigDecimal resultingBalanceDec = account.getOpeningBalance().add(totalIncrease).subtract(totalDecrease);
        String resultingBalance = resultingBalanceDec.setScale(2, RoundingMode.UNNECESSARY).toPlainString();

        BigDecimal remainingOutstanding = outstanding.subtract(amount);
        String settlementStatus = remainingOutstanding.signum() == 0 ? "FULLY_PAID" : "PARTIALLY_PAID";

        ObligationSettlementResponse response = new ObligationSettlementResponse(
                allocation.getId(),
                obligation.getId(),
                tx.getId(),
                amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                settlementDate,
                resultingBalance,
                remainingOutstanding.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                settlementStatus,
                ISO_INSTANT_FORMATTER.format(allocation.getCreatedAt().truncatedTo(ChronoUnit.SECONDS))
        );

        // 5. Security audit
        SecurityAuditEvent auditEvent = new SecurityAuditEvent();
        auditEvent.setEventType("OBLIGATION_SETTLED");
        auditEvent.setActorUserId(context.userId());
        auditEvent.setBusinessId(context.businessId());
        auditEvent.setTargetType("FINANCIAL_OBLIGATION");
        auditEvent.setTargetId(obligation.getId().toString());
        auditEvent.setOutcome(AuditOutcome.SUCCESS);
        auditEvent.setMetadata("{\"transactionId\":\"" + tx.getId() + "\",\"amount\":\"" + amount.toPlainString() + "\"}");
        securityAuditRepository.saveAndFlush(auditEvent);

        // 6. Durable idempotency
        String responseJson = serializeSettlementResponse(response);
        FinancialIdempotencyRecord idempotencyRecord = new FinancialIdempotencyRecord(
                context.businessId(),
                OPERATION_SETTLEMENT,
                trimmedKey,
                payloadHash,
                tx.getId(),
                201,
                responseJson
        );
        idempotencyRepository.saveAndFlush(idempotencyRecord);

        return response;
    }

    @Transactional(readOnly = true)
    public ObligationSummaryTotalsResponse getTotalsSummary(BusinessAccessContext context, String asOfDateStr) {
        LocalDate asOfDate = LocalDate.now(KARACHI_ZONE);
        if (asOfDateStr != null && !asOfDateStr.isBlank()) {
            try {
                asOfDate = LocalDate.parse(asOfDateStr.trim());
            } catch (DateTimeParseException e) {
                throw new ObligationValidationException("asOfDate", "Invalid asOfDate format. Expected YYYY-MM-DD");
            }
        }

        List<FinancialObligation> allObligations = obligationRepository.findByBusinessId(context.businessId());
        List<ObligationPaymentAllocation> allAllocations = allocationRepository.findByBusinessId(context.businessId());

        // Group allocations by obligation
        Map<UUID, List<ObligationPaymentAllocation>> allocationsByObligation = allAllocations.stream()
                .collect(Collectors.groupingBy(ObligationPaymentAllocation::getObligationId));

        BigDecimal totalRec = BigDecimal.ZERO;
        BigDecimal totalPay = BigDecimal.ZERO;
        BigDecimal outRec = BigDecimal.ZERO;
        BigDecimal outPay = BigDecimal.ZERO;
        BigDecimal overdueRec = BigDecimal.ZERO;
        BigDecimal overduePay = BigDecimal.ZERO;
        BigDecimal dueSoonRec = BigDecimal.ZERO;
        BigDecimal dueSoonPay = BigDecimal.ZERO;

        Set<UUID> customersWithBalance = new HashSet<>();
        Set<UUID> suppliersWithBalance = new HashSet<>();

        LocalDate soonCutoff = asOfDate.plusDays(14);

        for (FinancialObligation o : allObligations) {
            if (o.getStatus() == ObligationStatus.VOIDED && o.getVoidDate() != null && !o.getVoidDate().isAfter(asOfDate)) {
                continue; // Exclude voided as of date
            }
            if (o.getIssueDate().isAfter(asOfDate)) {
                continue; // Not yet issued
            }

            // Sum effective allocations on or before asOfDate
            List<ObligationPaymentAllocation> allocs = allocationsByObligation.getOrDefault(o.getId(), Collections.emptyList());
            BigDecimal paid = BigDecimal.ZERO;
            for (ObligationPaymentAllocation a : allocs) {
                if (a.getStatus() == AllocationStatus.EFFECTIVE && !a.getEffectiveDate().isAfter(asOfDate)) {
                    paid = paid.add(a.getAmount());
                }
            }

            BigDecimal outstanding = o.getOriginalAmount().subtract(paid);
            if (outstanding.compareTo(BigDecimal.ZERO) < 0) outstanding = BigDecimal.ZERO;

            if (o.getDirection() == ObligationDirection.RECEIVABLE) {
                totalRec = totalRec.add(o.getOriginalAmount());
                outRec = outRec.add(outstanding);
                if (outstanding.compareTo(BigDecimal.ZERO) > 0) {
                    customersWithBalance.add(o.getCounterpartyId());
                    if (o.getDueDate() != null) {
                        if (o.getDueDate().isBefore(asOfDate)) {
                            overdueRec = overdueRec.add(outstanding);
                        } else if (!o.getDueDate().isAfter(soonCutoff)) {
                            dueSoonRec = dueSoonRec.add(outstanding);
                        }
                    }
                }
            } else {
                totalPay = totalPay.add(o.getOriginalAmount());
                outPay = outPay.add(outstanding);
                if (outstanding.compareTo(BigDecimal.ZERO) > 0) {
                    suppliersWithBalance.add(o.getCounterpartyId());
                    if (o.getDueDate() != null) {
                        if (o.getDueDate().isBefore(asOfDate)) {
                            overduePay = overduePay.add(outstanding);
                        } else if (!o.getDueDate().isAfter(soonCutoff)) {
                            dueSoonPay = dueSoonPay.add(outstanding);
                        }
                    }
                }
            }
        }

        return new ObligationSummaryTotalsResponse(
                "PKR",
                asOfDate,
                totalRec.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                totalPay.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                outRec.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                outPay.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                overdueRec.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                overduePay.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                customersWithBalance.size(),
                suppliersWithBalance.size(),
                dueSoonRec.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                dueSoonPay.setScale(2, RoundingMode.UNNECESSARY).toPlainString()
        );
    }

    @Transactional(readOnly = true)
    public ObligationAgingResult getAging(BusinessAccessContext context, String asOfDateStr) {
        LocalDate asOfDate = LocalDate.now(KARACHI_ZONE);
        if (asOfDateStr != null && !asOfDateStr.isBlank()) {
            try {
                asOfDate = LocalDate.parse(asOfDateStr.trim());
            } catch (DateTimeParseException e) {
                throw new ObligationValidationException("asOfDate", "Invalid asOfDate format. Expected YYYY-MM-DD");
            }
        }

        List<FinancialObligation> obligations = obligationRepository.findByBusinessId(context.businessId());
        List<ObligationPaymentAllocation> allocations = allocationRepository.findByBusinessId(context.businessId());

        // Batch fetch reversal transactions to get their effective dates
        Set<UUID> reversalTxIds = allocations.stream()
                .map(ObligationPaymentAllocation::getReversalTransactionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, FinancialTransaction> reversalTxMap = reversalTxIds.isEmpty() ? Collections.emptyMap() :
                transactionRepository.findAllById(reversalTxIds).stream()
                        .filter(tx -> tx.getBusinessId().equals(context.businessId()))
                        .collect(Collectors.toMap(FinancialTransaction::getId, tx -> tx));

        Map<UUID, List<ObligationPaymentAllocation>> allocationsByObligation = allocations.stream()
                .collect(Collectors.groupingBy(ObligationPaymentAllocation::getObligationId));

        List<ObligationHistory> histories = new ArrayList<>();
        for (FinancialObligation o : obligations) {
            List<ObligationPaymentAllocation> oAllocs = allocationsByObligation.getOrDefault(o.getId(), Collections.emptyList());
            List<SettlementAdjustment> adjustments = new ArrayList<>();

            for (ObligationPaymentAllocation a : oAllocs) {
                if (a.getStatus() == AllocationStatus.EFFECTIVE) {
                    adjustments.add(new SettlementAdjustment(
                            a.getId().toString(),
                            a.getEffectiveDate(),
                            SettlementAdjustmentType.REDUCE_OUTSTANDING,
                            a.getAmount().setScale(2, RoundingMode.UNNECESSARY)
                    ));
                } else if (a.getStatus() == AllocationStatus.CANCELLED) {
                    adjustments.add(new SettlementAdjustment(
                            a.getId().toString(),
                            a.getEffectiveDate(),
                            SettlementAdjustmentType.REDUCE_OUTSTANDING,
                            a.getAmount().setScale(2, RoundingMode.UNNECESSARY)
                    ));
                    LocalDate revDate = a.getEffectiveDate();
                    if (a.getReversalTransactionId() != null && reversalTxMap.containsKey(a.getReversalTransactionId())) {
                        revDate = reversalTxMap.get(a.getReversalTransactionId()).getEffectiveDate();
                    }
                    adjustments.add(new SettlementAdjustment(
                            a.getId().toString() + "-rev",
                            revDate,
                            SettlementAdjustmentType.RESTORE_OUTSTANDING,
                            a.getAmount().setScale(2, RoundingMode.UNNECESSARY)
                    ));
                }
            }

            histories.add(new ObligationHistory(
                    o.getId().toString(),
                    o.getDirection(),
                    o.getIssueDate(),
                    o.getDueDate(),
                    o.getOriginalAmount().setScale(2, RoundingMode.UNNECESSARY),
                    o.getVoidDate(),
                    adjustments
            ));
        }

        return ObligationAgingCalculator.calculate(asOfDate, "PKR", histories);
    }

    private void validateChronologicalSequence(
            FinancialAccount account,
            MovementDirection direction,
            BigDecimal amount,
            LocalDate effectiveDate,
            UUID businessId
    ) {
        List<AccountMovement> existingMovements = movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(account.getId());
        List<AccountMovement> simulated = new ArrayList<>(existingMovements);
        AccountMovement proposed = new AccountMovement(businessId, null, account.getId(), direction, amount, "PKR", effectiveDate);
        simulated.add(proposed);

        simulated.sort((m1, m2) -> {
            int cmp = m1.getEffectiveDate().compareTo(m2.getEffectiveDate());
            if (cmp != 0) return cmp;
            if (m1.getId() == null && m2.getId() != null) return 1;
            if (m1.getId() != null && m2.getId() == null) return -1;
            if (m1.getCreatedAt() != null && m2.getCreatedAt() != null) {
                return m1.getCreatedAt().compareTo(m2.getCreatedAt());
            }
            return 0;
        });

        BigDecimal running = account.getOpeningBalance();
        for (AccountMovement m : simulated) {
            if (m.getMovementDirection() == MovementDirection.INCREASE) {
                running = running.add(m.getAmount());
            } else if (m.getMovementDirection() == MovementDirection.DECREASE) {
                running = running.subtract(m.getAmount());
            }
            if (account.getAccountType() == AccountType.CASH && running.compareTo(BigDecimal.ZERO) < 0) {
                throw new TransactionValidationException(
                        "cashBalance",
                        "Settlement would cause cash account balance to become negative on " + m.getEffectiveDate()
                );
            }
            if (running.abs().compareTo(MAX_CAPACITY) >= 0) {
                throw new TransactionValidationException(
                        "amount",
                        "Settlement would cause account balance to exceed maximum capacity of 999,999,999,999.99"
                );
            }
        }
    }

    private ObligationSummaryResponse toSummaryResponse(FinancialObligation o, Counterparty cp, BigDecimal paid, LocalDate today) {
        BigDecimal outstanding = o.getOriginalAmount().subtract(paid);
        if (outstanding.compareTo(BigDecimal.ZERO) < 0) outstanding = BigDecimal.ZERO;

        String settlementStatus;
        if (o.getStatus() == ObligationStatus.VOIDED) {
            settlementStatus = "VOIDED";
        } else if (paid.compareTo(BigDecimal.ZERO) == 0) {
            settlementStatus = "UNPAID";
        } else if (paid.compareTo(o.getOriginalAmount()) >= 0) {
            settlementStatus = "FULLY_PAID";
        } else {
            settlementStatus = "PARTIALLY_PAID";
        }

        String agingBucket = "NOT_YET_DUE";
        Integer daysOverdue = null;
        if (o.getDueDate() == null) {
            agingBucket = "UNKNOWN_DUE_DATE";
        } else if (today.isEqual(o.getDueDate())) {
            agingBucket = "DUE_TODAY";
            daysOverdue = 0;
        } else if (today.isAfter(o.getDueDate())) {
            daysOverdue = (int) ChronoUnit.DAYS.between(o.getDueDate(), today);
            if (daysOverdue <= 30) agingBucket = "OVERDUE_1_30";
            else if (daysOverdue <= 60) agingBucket = "OVERDUE_31_60";
            else if (daysOverdue <= 90) agingBucket = "OVERDUE_61_90";
            else agingBucket = "OVERDUE_91_PLUS";
        }

        return new ObligationSummaryResponse(
                o.getId(),
                o.getBusinessId(),
                o.getCounterpartyId(),
                cp != null ? cp.getDisplayName() : "Unknown",
                cp != null ? cp.getType().name() : "CUSTOMER",
                o.getDirection().name(),
                o.getSourceType().name(),
                o.getSourceId(),
                o.getReferenceNumber(),
                o.getIssueDate(),
                o.getDueDate(),
                o.getOriginalAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                paid.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                outstanding.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                o.getCurrency(),
                o.getStatus().name(),
                settlementStatus,
                agingBucket,
                daysOverdue,
                o.getDescription(),
                ISO_INSTANT_FORMATTER.format(o.getCreatedAt().truncatedTo(ChronoUnit.SECONDS))
        );
    }

    private ObligationDetailResponse toDetailResponse(
            FinancialObligation o,
            Counterparty cp,
            List<ObligationPaymentAllocation> allocations,
            BigDecimal paid
    ) {
        LocalDate today = LocalDate.now(KARACHI_ZONE);
        ObligationSummaryResponse summary = toSummaryResponse(o, cp, paid, today);

        // Fetch transaction and account names for allocations
        Set<UUID> txIds = allocations.stream().map(ObligationPaymentAllocation::getFinancialTransactionId).collect(Collectors.toSet());
        Map<UUID, FinancialTransaction> txMap = txIds.isEmpty() ? Collections.emptyMap() :
                transactionRepository.findAllById(txIds).stream()
                        .filter(tx -> tx.getBusinessId().equals(o.getBusinessId()))
                        .collect(Collectors.toMap(FinancialTransaction::getId, tx -> tx));

        Set<UUID> accIds = txMap.values().stream().map(FinancialTransaction::getAccountId).collect(Collectors.toSet());
        Map<UUID, FinancialAccount> accMap = accIds.isEmpty() ? Collections.emptyMap() :
                accountRepository.findAllById(accIds).stream()
                        .filter(acc -> acc.getBusinessId().equals(o.getBusinessId()))
                        .collect(Collectors.toMap(FinancialAccount::getId, acc -> acc));

        List<ObligationAllocationResponse> allocDtos = allocations.stream().map(a -> {
            FinancialTransaction tx = txMap.get(a.getFinancialTransactionId());
            FinancialAccount acc = (tx != null) ? accMap.get(tx.getAccountId()) : null;
            return new ObligationAllocationResponse(
                    a.getId(),
                    a.getFinancialTransactionId(),
                    a.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                    a.getEffectiveDate(),
                    a.getStatus().name(),
                    a.getCancelledAt() != null ? ISO_INSTANT_FORMATTER.format(a.getCancelledAt().truncatedTo(ChronoUnit.SECONDS)) : null,
                    a.getCancellationReason(),
                    a.getReversalTransactionId(),
                    ISO_INSTANT_FORMATTER.format(a.getCreatedAt().truncatedTo(ChronoUnit.SECONDS)),
                    tx != null ? tx.getDescription() : null,
                    acc != null ? acc.getAccountName() : "Unknown Account",
                    acc != null ? acc.getId() : null
            );
        }).toList();

        return new ObligationDetailResponse(
                o.getId(),
                o.getBusinessId(),
                o.getCounterpartyId(),
                summary.counterpartyName(),
                summary.counterpartyType(),
                summary.direction(),
                summary.sourceType(),
                summary.sourceId(),
                summary.referenceNumber(),
                summary.issueDate(),
                summary.dueDate(),
                summary.originalAmount(),
                summary.paidAmount(),
                summary.outstandingAmount(),
                summary.currency(),
                summary.status(),
                summary.settlementStatus(),
                summary.agingBucket(),
                summary.daysOverdue(),
                summary.description(),
                o.getVoidDate(),
                o.getVoidReason(),
                allocDtos,
                summary.createdAt()
        );
    }

    public static String computeSettlementPayloadHash(
            UUID obligationId,
            UUID accountId,
            BigDecimal amount,
            LocalDate settlementDate,
            String reference,
            String description
    ) {
        String canonical = (obligationId != null ? obligationId.toString() : "") + "|" +
                (accountId != null ? accountId.toString() : "") + "|" +
                (amount != null ? amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString() : "") + "|" +
                (settlementDate != null ? settlementDate.toString() : "") + "|" +
                (reference != null ? reference : "") + "|" +
                (description != null ? description : "");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String serializeSettlementResponse(ObligationSettlementResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize settlement response", e);
        }
    }

    private ObligationSettlementResponse deserializeSettlementResponse(String json) {
        try {
            return objectMapper.readValue(json, ObligationSettlementResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize settlement response", e);
        }
    }
}
