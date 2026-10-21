package com.app.sme_health_backend.accounting.draft;

import com.app.sme_health_backend.accounting.dto.TransactionDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionPostRequest;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialIdempotencyRecord;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialIdempotencyRepository;
import com.app.sme_health_backend.accounting.service.TransactionPostService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service managing the lifecycle, optimistic concurrency, and safe recording
 * of persistent record drafts.
 */
@Service
@Transactional(readOnly = true)
public class RecordDraftService {

    private static final String OPERATION_DRAFT_CREATE = "RECORD_DRAFT_CREATE";
    private static final String OPERATION_TRANSACTION_POST = "FINANCIAL_TRANSACTION";
    private static final ZoneId KARACHI_ZONE = ZoneId.of("Asia/Karachi");
    private static final BigDecimal MAX_CAPACITY = new BigDecimal("999999999999.99");
    private static final Pattern CANONICAL_CONFIRMED_AMOUNT = Pattern.compile("\\A(?:0|[1-9][0-9]*)\\.[0-9]{2}\\z");
    private static final Pattern EXACT_TWO_DECIMAL_AMOUNT = Pattern.compile("^(?:0|[1-9]\\d*)\\.\\d{2}$");

    private final RecordDraftRepository draftRepository;
    private final FinancialAccountRepository accountRepository;
    private final TransactionPostService transactionPostService;
    private final FinancialIdempotencyRepository idempotencyRepository;
    private final com.app.sme_health_backend.counterparty.CounterpartyRepository counterpartyRepository;
    private final com.app.sme_health_backend.recurring.repository.RecurringRuleOccurrenceRepository recurringOccurrenceRepository;
    private final ObjectMapper objectMapper;

    public RecordDraftService(
            RecordDraftRepository draftRepository,
            FinancialAccountRepository accountRepository,
            TransactionPostService transactionPostService,
            FinancialIdempotencyRepository idempotencyRepository,
            ObjectMapper objectMapper
    ) {
        this(draftRepository, accountRepository, transactionPostService, idempotencyRepository, null, null, objectMapper);
    }

    public RecordDraftService(
            RecordDraftRepository draftRepository,
            FinancialAccountRepository accountRepository,
            TransactionPostService transactionPostService,
            FinancialIdempotencyRepository idempotencyRepository,
            com.app.sme_health_backend.counterparty.CounterpartyRepository counterpartyRepository,
            ObjectMapper objectMapper
    ) {
        this(draftRepository, accountRepository, transactionPostService, idempotencyRepository, counterpartyRepository, null, objectMapper);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RecordDraftService(
            RecordDraftRepository draftRepository,
            FinancialAccountRepository accountRepository,
            TransactionPostService transactionPostService,
            FinancialIdempotencyRepository idempotencyRepository,
            com.app.sme_health_backend.counterparty.CounterpartyRepository counterpartyRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            com.app.sme_health_backend.recurring.repository.RecurringRuleOccurrenceRepository recurringOccurrenceRepository,
            ObjectMapper objectMapper
    ) {
        this.draftRepository = draftRepository;
        this.accountRepository = accountRepository;
        this.transactionPostService = transactionPostService;
        this.idempotencyRepository = idempotencyRepository;
        this.counterpartyRepository = counterpartyRepository;
        this.recurringOccurrenceRepository = recurringOccurrenceRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a new persistent record draft with optional idempotency protection.
     */
    @Transactional
    public DraftDetailResponse createDraft(
            BusinessAccessContext context,
            String idempotencyKey,
            DraftCreateRequest request
    ) {
        String trimmedKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey.trim()
                : null;

        if (trimmedKey != null && trimmedKey.length() > 100) {
            throw new DraftValidationException("Idempotency-Key", "Idempotency-Key must not exceed 100 characters");
        }

        if (request == null) {
            throw new DraftValidationException("body", "Request body is required");
        }

        Map<String, String> errors = new LinkedHashMap<>();

        // Validate account reference if supplied
        if (request.accountId() != null) {
            Optional<FinancialAccount> accOpt = accountRepository.findByIdAndBusinessId(request.accountId(), context.businessId());
            if (accOpt.isEmpty()) {
                errors.put("accountId", "Financial account not found");
            }
        }

        // Validate eventType format if supplied
        TransactionEventType eventType = null;
        if (request.eventType() != null && !request.eventType().trim().isEmpty()) {
            try {
                eventType = TransactionEventType.valueOf(request.eventType().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errors.put("eventType", "Invalid eventType: " + request.eventType() + ". Supported types: DIRECT_SALE_RECEIPT, OPERATING_EXPENSE_PAYMENT");
            }
        }

        // Validate effectiveDate format if supplied
        LocalDate effectiveDate = null;
        if (request.effectiveDate() != null && !request.effectiveDate().trim().isEmpty()) {
            try {
                effectiveDate = LocalDate.parse(request.effectiveDate().trim());
            } catch (DateTimeParseException e) {
                errors.put("effectiveDate", "Invalid date format. Expected YYYY-MM-DD");
            }
        }

        // Validate amountInput length: preserve raw text verbatim, enforce 100 char limit on raw string
        String amountInput = (request != null) ? request.amountInput() : null;
        if (amountInput != null && amountInput.length() > 100) {
            errors.put("amountInput", "Amount input must not exceed 100 characters");
        }

        // Validate description length
        String description = (request.description() != null && !request.description().trim().isEmpty())
                ? request.description().trim()
                : null;
        if (description != null && description.length() > 255) {
            errors.put("description", "Description must not exceed 255 characters");
        }

        // Validate counterparty if supplied
        if (request.counterpartyId() != null) {
            Optional<com.app.sme_health_backend.counterparty.Counterparty> cpOpt = counterpartyRepository != null
                    ? counterpartyRepository.findByIdAndBusinessId(request.counterpartyId(), context.businessId())
                    : Optional.empty();
            if (cpOpt.isEmpty()) {
                errors.put("counterpartyId", "Counterparty not found");
            } else if (cpOpt.get().getStatus() != com.app.sme_health_backend.counterparty.CounterpartyStatus.ACTIVE) {
                errors.put("counterpartyId", "Selected counterparty is archived and cannot be newly assigned");
            }
        }

        if (!errors.isEmpty()) {
            throw new DraftValidationException(errors);
        }

        // Idempotency check if key supplied
        String payloadHash = null;
        if (trimmedKey != null) {
            payloadHash = computeDraftPayloadHash(
                    request.accountId(),
                    eventType != null ? eventType.name() : null,
                    effectiveDate != null ? effectiveDate.toString() : null,
                    amountInput,
                    description,
                    request.counterpartyId()
            );

            Optional<FinancialIdempotencyRecord> existingOpt = idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(
                    context.businessId(),
                    OPERATION_DRAFT_CREATE,
                    trimmedKey
            );

            if (existingOpt.isPresent()) {
                FinancialIdempotencyRecord existing = existingOpt.get();
                if (existing.getRequestHash().equals(payloadHash)) {
                    return deserializeDraftDetail(existing.getResponseBody());
                } else {
                    throw new DuplicateResourceException(
                            "Idempotency key '" + trimmedKey + "' has already been used with a different request payload"
                    );
                }
            }
        }

        RecordDraft draft = new RecordDraft(
                context.businessId(),
                request.accountId(),
                eventType,
                effectiveDate,
                amountInput,
                description,
                context.userId(),
                request.counterpartyId()
        );

        draft = draftRepository.saveAndFlush(draft);
        DraftDetailResponse response = toDetailResponse(draft);

        if (trimmedKey != null) {
            saveIdempotencyRecord(
                    context.businessId(),
                    OPERATION_DRAFT_CREATE,
                    trimmedKey,
                    payloadHash,
                    draft.getId(),
                    HttpStatus.CREATED.value(),
                    serializeDraftDetail(response)
            );
        }

        return response;
    }

    /**
     * Reads a single draft by ID within active business.
     */
    public DraftDetailResponse getDraft(BusinessAccessContext context, UUID id) {
        RecordDraft draft = draftRepository.findByIdAndBusinessId(id, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Record draft not found"));
        return toDetailResponse(draft);
    }

    /**
     * Lists and filters drafts for the active business with deterministic ordering.
     */
    public DraftListResponse listDrafts(
            BusinessAccessContext context,
            String statusFilter,
            UUID accountId,
            String eventTypeStr,
            Pageable pageable
    ) {
        DraftStatus status = null;
        if (statusFilter != null && !statusFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(statusFilter.trim())) {
            try {
                status = DraftStatus.valueOf(statusFilter.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new DraftValidationException("status", "Invalid status filter: " + statusFilter);
            }
        } else if (statusFilter == null || statusFilter.trim().isEmpty()) {
            status = DraftStatus.DRAFT; // Default to active drafts
        }

        TransactionEventType eventType = null;
        if (eventTypeStr != null && !eventTypeStr.trim().isEmpty()) {
            try {
                eventType = TransactionEventType.valueOf(eventTypeStr.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new DraftValidationException("eventType", "Invalid eventType filter: " + eventTypeStr);
            }
        }

        final DraftStatus finalStatus = status;
        final TransactionEventType finalEventType = eventType;

        Specification<RecordDraft> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("businessId"), context.businessId()));
            if (finalStatus != null) {
                predicates.add(cb.equal(root.get("status"), finalStatus));
            }
            if (accountId != null) {
                predicates.add(cb.equal(root.get("accountId"), accountId));
            }
            if (finalEventType != null) {
                predicates.add(cb.equal(root.get("eventType"), finalEventType));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // Deterministic ordering: updatedAt DESC, createdAt DESC, id DESC
        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "updatedAt", "createdAt", "id")
        );

        Page<RecordDraft> page = draftRepository.findAll(spec, sortedPageable);

        Set<UUID> cpIds = new HashSet<>();
        for (RecordDraft d : page.getContent()) {
            if (d.getCounterpartyId() != null) {
                cpIds.add(d.getCounterpartyId());
            }
        }
        Map<UUID, com.app.sme_health_backend.counterparty.Counterparty> cpMap = (cpIds.isEmpty() || counterpartyRepository == null) ? Collections.emptyMap() :
                counterpartyRepository.findAllById(cpIds).stream()
                        .filter(c -> c.getBusinessId().equals(context.businessId()))
                        .collect(Collectors.toMap(com.app.sme_health_backend.counterparty.Counterparty::getId, c -> c));

        List<DraftSummaryItem> items = page.getContent().stream()
                .map(d -> toSummaryItem(d, d.getCounterpartyId() != null ? cpMap.get(d.getCounterpartyId()) : null))
                .toList();

        return new DraftListResponse(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    /**
     * Updates an existing draft with optimistic version concurrency check.
     */
    @Transactional
    public DraftDetailResponse updateDraft(
            BusinessAccessContext context,
            UUID id,
            DraftUpdateRequest request
    ) {
        if (request == null) {
            throw new DraftValidationException("body", "Request body is required");
        }
        if (request.version() == null) {
            throw new DraftValidationException("version", "Version is required for updating a draft");
        }

        RecordDraft draft = draftRepository.findByIdAndBusinessId(id, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Record draft not found"));

        if (draft.getStatus() != DraftStatus.DRAFT) {
            throw new DraftConflictException("Cannot update draft in " + draft.getStatus() + " status");
        }

        if (!Objects.equals(request.version(), draft.getVersion())) {
            throw new DraftConflictException(
                    "Draft version mismatch: expected " + draft.getVersion() + " but received " + request.version()
            );
        }

        Map<String, String> errors = new LinkedHashMap<>();

        // Validate account reference if supplied
        if (request.accountId() != null) {
            Optional<FinancialAccount> accOpt = accountRepository.findByIdAndBusinessId(request.accountId(), context.businessId());
            if (accOpt.isEmpty()) {
                errors.put("accountId", "Financial account not found");
            }
        }

        // Validate eventType format if supplied
        TransactionEventType eventType = null;
        if (request.eventType() != null && !request.eventType().trim().isEmpty()) {
            try {
                eventType = TransactionEventType.valueOf(request.eventType().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errors.put("eventType", "Invalid eventType: " + request.eventType() + ". Supported types: DIRECT_SALE_RECEIPT, OPERATING_EXPENSE_PAYMENT");
            }
        }

        // Validate effectiveDate format if supplied
        LocalDate effectiveDate = null;
        if (request.effectiveDate() != null && !request.effectiveDate().trim().isEmpty()) {
            try {
                effectiveDate = LocalDate.parse(request.effectiveDate().trim());
            } catch (DateTimeParseException e) {
                errors.put("effectiveDate", "Invalid date format. Expected YYYY-MM-DD");
            }
        }

        // Validate amountInput length: preserve raw text verbatim, enforce 100 char limit on raw string
        String amountInput = (request != null) ? request.amountInput() : null;
        if (amountInput != null && amountInput.length() > 100) {
            errors.put("amountInput", "Amount input must not exceed 100 characters");
        }

        // Validate description length
        String description = (request.description() != null && !request.description().trim().isEmpty())
                ? request.description().trim()
                : null;
        if (description != null && description.length() > 255) {
            errors.put("description", "Description must not exceed 255 characters");
        }

        // Validate counterparty if supplied
        if (request.counterpartyId() != null) {
            Optional<com.app.sme_health_backend.counterparty.Counterparty> cpOpt = counterpartyRepository != null
                    ? counterpartyRepository.findByIdAndBusinessId(request.counterpartyId(), context.businessId())
                    : Optional.empty();
            if (cpOpt.isEmpty()) {
                errors.put("counterpartyId", "Counterparty not found");
            } else if (cpOpt.get().getStatus() != com.app.sme_health_backend.counterparty.CounterpartyStatus.ACTIVE) {
                errors.put("counterpartyId", "Selected counterparty is archived and cannot be newly assigned");
            }
        }

        if (!errors.isEmpty()) {
            throw new DraftValidationException(errors);
        }

        draft.setAccountId(request.accountId());
        draft.setEventType(eventType);
        draft.setEffectiveDate(effectiveDate);
        draft.setAmountInput(amountInput);
        draft.setDescription(description);
        draft.setCounterpartyId(request.counterpartyId());
        draft.setUpdatedById(context.userId());
        draft.setUpdatedAt(Instant.now());

        draft = draftRepository.saveAndFlush(draft);
        return toDetailResponse(draft);
    }

    /**
     * Discards an existing draft with optimistic version concurrency check.
     */
    @Transactional
    public DraftDetailResponse discardDraft(
            BusinessAccessContext context,
            UUID id,
            DraftDiscardRequest request
    ) {
        if (request == null || request.version() == null) {
            throw new DraftValidationException("version", "Version is required for discarding a draft");
        }

        RecordDraft draft = draftRepository.findByIdAndBusinessId(id, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Record draft not found"));

        if (draft.getStatus() != DraftStatus.DRAFT) {
            throw new DraftConflictException("Cannot discard draft in " + draft.getStatus() + " status");
        }

        if (!Objects.equals(request.version(), draft.getVersion())) {
            throw new DraftConflictException(
                    "Draft version mismatch: expected " + draft.getVersion() + " but received " + request.version()
            );
        }

        draft.setStatus(DraftStatus.DISCARDED);
        draft.setUpdatedById(context.userId());
        draft.setUpdatedAt(Instant.now());

        draft = draftRepository.saveAndFlush(draft);
        return toDetailResponse(draft);
    }

    /**
     * Atomically records a draft as an authoritative financial transaction.
     */
    @Transactional
    public DraftRecordResponse recordDraft(
            BusinessAccessContext context,
            UUID id,
            String idempotencyKey,
            DraftRecordRequest request
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new DraftValidationException("Idempotency-Key", "Missing required header: Idempotency-Key");
        }
        String trimmedKey = idempotencyKey.trim();
        if (trimmedKey.length() > 100) {
            throw new DraftValidationException("Idempotency-Key", "Idempotency-Key must not exceed 100 characters");
        }

        if (request == null) {
            throw new DraftValidationException("body", "Request body is required");
        }
        if (request.version() == null) {
            throw new DraftValidationException("version", "Version is required for recording a draft");
        }

        // Validate confirmedAmount syntax before any trimming, BigDecimal parsing, payload hashing, locking or cached-success return
        BigDecimal validatedAmount = validateConfirmedAmount(request.confirmedAmount());
        String canonicalAmount = validatedAmount.toPlainString();

        // Acquire pessimistic lock on the draft to prevent concurrent double-recording
        RecordDraft draft = draftRepository.findForUpdateByIdAndBusinessId(id, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Record draft not found"));

        // If draft is already RECORDED, verify idempotency replay and payload identity
        if (draft.getStatus() == DraftStatus.RECORDED) {
            Optional<FinancialIdempotencyRecord> existingOpt = idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(
                    context.businessId(),
                    OPERATION_TRANSACTION_POST,
                    trimmedKey
            );

            if (existingOpt.isPresent()) {
                FinancialIdempotencyRecord existing = existingOpt.get();
                TransactionDetailResponse cachedTx = deserializeTransactionDetail(existing.getResponseBody());
                // Confirm the cached transaction matches the draft's recorded transaction
                if (!cachedTx.id().equals(draft.getRecordedTransactionId())) {
                    throw new DuplicateResourceException(
                            "Idempotency key '" + trimmedKey + "' has already been used with a different request payload"
                    );
                }

                // Verify request payload identity: version must match the recorded draft version (draft.getVersion() - 1)
                if (!Objects.equals(request.version(), draft.getVersion() - 1)) {
                    throw new DuplicateResourceException(
                            "Idempotency key '" + trimmedKey + "' has already been used with a different request payload"
                    );
                }

                // Resolve effective fields for the incoming request
                UUID effectiveAccountId = request.accountId() != null ? request.accountId() : draft.getAccountId();
                String effectiveEventTypeStr = request.eventType() != null ? request.eventType()
                        : (draft.getEventType() != null ? draft.getEventType().name() : null);
                String effectiveDateStr = request.effectiveDate() != null ? request.effectiveDate()
                        : (draft.getEffectiveDate() != null ? draft.getEffectiveDate().toString() : null);
                String effectiveDescription = request.description() != null ? request.description() : draft.getDescription();

                TransactionEventType eventType = null;
                if (effectiveEventTypeStr != null) {
                    try {
                        eventType = TransactionEventType.valueOf(effectiveEventTypeStr.trim().toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ignored) {}
                }

                LocalDate effectiveDate = null;
                if (effectiveDateStr != null) {
                    try {
                        effectiveDate = LocalDate.parse(effectiveDateStr.trim());
                    } catch (DateTimeParseException ignored) {}
                }

                BigDecimal amount = validatedAmount;

                String trimmedDescription = (effectiveDescription != null && !effectiveDescription.trim().isEmpty())
                        ? effectiveDescription.trim()
                        : null;

                if (effectiveAccountId == null || eventType == null || effectiveDate == null || amount == null) {
                    throw new DuplicateResourceException(
                            "Idempotency key '" + trimmedKey + "' has already been used with a different request payload"
                    );
                }

                String currentPayloadHash = TransactionPostService.computePayloadHash(
                        effectiveAccountId,
                        eventType,
                        amount,
                        effectiveDate,
                        trimmedDescription,
                        request.counterpartyId() != null ? request.counterpartyId() : draft.getCounterpartyId()
                );

                if (!existing.getRequestHash().equals(currentPayloadHash)) {
                    throw new DuplicateResourceException(
                            "Idempotency key '" + trimmedKey + "' has already been used with a different request payload"
                    );
                }

                return new DraftRecordResponse(toDetailResponse(draft), cachedTx);
            } else {
                throw new DraftConflictException(
                        "Draft " + id + " has already been recorded with transaction " + draft.getRecordedTransactionId()
                );
            }
        }

        if (draft.getStatus() == DraftStatus.DISCARDED) {
            throw new DraftConflictException("Cannot record discarded draft " + id);
        }

        // Verify recurring occurrence guard if draft is linked to a recurring rule occurrence
        if (recurringOccurrenceRepository != null) {
            recurringOccurrenceRepository.findByBusinessIdAndDraftId(context.businessId(), draft.getId())
                    .ifPresent(occ -> {
                        if (occ.getStatus() == com.app.sme_health_backend.recurring.model.RecurringOccurrenceStatus.REVERSED) {
                            throw new DraftConflictException("Cannot record draft: linked recurring occurrence " + occ.getId() + " is reversed");
                        }
                        if (occ.getStatus() == com.app.sme_health_backend.recurring.model.RecurringOccurrenceStatus.SKIPPED) {
                            throw new DraftConflictException("Cannot record draft: linked recurring occurrence " + occ.getId() + " was skipped");
                        }
                        if (occ.getStatus() == com.app.sme_health_backend.recurring.model.RecurringOccurrenceStatus.POSTED) {
                            throw new DraftConflictException("Cannot record draft: linked recurring occurrence " + occ.getId() + " has already been posted");
                        }
                    });
        }

        // Optimistic concurrency version check
        if (!Objects.equals(request.version(), draft.getVersion())) {
            throw new DraftConflictException(
                    "Draft version mismatch: expected " + draft.getVersion() + " but received " + request.version()
            );
        }

        // Resolve effective values for posting
        UUID effectiveAccountId = request.accountId() != null ? request.accountId() : draft.getAccountId();
        String effectiveEventTypeStr = request.eventType() != null ? request.eventType()
                : (draft.getEventType() != null ? draft.getEventType().name() : null);
        String effectiveDateStr = request.effectiveDate() != null ? request.effectiveDate()
                : (draft.getEffectiveDate() != null ? draft.getEffectiveDate().toString() : null);
        String effectiveDescription = request.description() != null ? request.description() : draft.getDescription();
        UUID effectiveCounterpartyId = request.counterpartyId() != null ? request.counterpartyId() : draft.getCounterpartyId();

        Map<String, String> errors = new LinkedHashMap<>();
        if (effectiveAccountId == null) {
            errors.put("accountId", "Account ID is required to record draft");
        }
        if (effectiveEventTypeStr == null || effectiveEventTypeStr.trim().isEmpty()) {
            errors.put("eventType", "Event type is required to record draft");
        }
        if (effectiveDateStr == null || effectiveDateStr.trim().isEmpty()) {
            errors.put("effectiveDate", "Effective date is required to record draft");
        }

        if (effectiveCounterpartyId != null) {
            Optional<com.app.sme_health_backend.counterparty.Counterparty> cpOpt = counterpartyRepository != null
                    ? counterpartyRepository.findByIdAndBusinessId(effectiveCounterpartyId, context.businessId())
                    : Optional.empty();
            if (cpOpt.isEmpty()) {
                errors.put("counterpartyId", "Counterparty not found");
            } else {
                com.app.sme_health_backend.counterparty.Counterparty cp = cpOpt.get();
                if (cp.getStatus() != com.app.sme_health_backend.counterparty.CounterpartyStatus.ACTIVE) {
                    errors.put("counterpartyId", "Selected counterparty is archived and cannot be newly assigned");
                } else if ("DIRECT_SALE_RECEIPT".equals(effectiveEventTypeStr) && !cp.getType().isCustomerCompatible()) {
                    errors.put("counterpartyId", "Counterparty must be a CUSTOMER or BOTH for a direct sale receipt");
                } else if ("OPERATING_EXPENSE_PAYMENT".equals(effectiveEventTypeStr) && !cp.getType().isSupplierCompatible()) {
                    errors.put("counterpartyId", "Counterparty must be a SUPPLIER or BOTH for an operating expense payment");
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new DraftValidationException(errors);
        }

        // Check ordinary idempotency lookup: an unrecorded draft cannot reuse an existing transaction key
        Optional<FinancialIdempotencyRecord> existingKeyOpt = idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(
                context.businessId(),
                OPERATION_TRANSACTION_POST,
                trimmedKey
        );
        if (existingKeyOpt.isPresent()) {
            throw new DuplicateResourceException(
                    "Idempotency key '" + trimmedKey + "' has already been used with a different request payload"
                    );
        }

        // Delegate to canonical TransactionPostService
        TransactionPostRequest postRequest = new TransactionPostRequest(
                effectiveAccountId,
                effectiveEventTypeStr,
                canonicalAmount,
                effectiveDateStr,
                effectiveDescription,
                effectiveCounterpartyId
        );

        TransactionDetailResponse txDetail = transactionPostService.postTransaction(
                context,
                trimmedKey,
                postRequest
        );

        // Mark draft RECORDED and link to resulting transaction
        draft.setStatus(DraftStatus.RECORDED);
        draft.setRecordedTransactionId(txDetail.id());
        draft.setCounterpartyId(effectiveCounterpartyId);
        draft.setUpdatedById(context.userId());
        draft.setUpdatedAt(Instant.now());

        draft = draftRepository.saveAndFlush(draft);

        if (recurringOccurrenceRepository != null) {
            recurringOccurrenceRepository.findByBusinessIdAndDraftId(context.businessId(), draft.getId())
                    .ifPresent(occ -> {
                        if (occ.getStatus() == com.app.sme_health_backend.recurring.model.RecurringOccurrenceStatus.DRAFT_READY
                                || occ.getStatus() == com.app.sme_health_backend.recurring.model.RecurringOccurrenceStatus.SCHEDULED) {
                            occ.setStatus(com.app.sme_health_backend.recurring.model.RecurringOccurrenceStatus.POSTED);
                            occ.setPostedTransactionId(txDetail.id());
                            occ.setPostedAt(Instant.now());
                            recurringOccurrenceRepository.saveAndFlush(occ);
                        }
                    });
        }

        return new DraftRecordResponse(toDetailResponse(draft), txDetail);
    }

    private BigDecimal validateConfirmedAmount(String rawAmount) {
        if (rawAmount == null || rawAmount.isEmpty()) {
            throw new DraftValidationException("confirmedAmount", "Confirmed amount is required");
        }
        if (!CANONICAL_CONFIRMED_AMOUNT.matcher(rawAmount).matches()) {
            throw new DraftValidationException(
                    "confirmedAmount",
                    "Amount must be a positive decimal string with exactly two decimal places (e.g. '1500.56')"
            );
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(rawAmount);
        } catch (NumberFormatException e) {
            throw new DraftValidationException("confirmedAmount", "Invalid decimal format for amount");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DraftValidationException("confirmedAmount", "Amount must be greater than zero");
        }
        if (amount.compareTo(MAX_CAPACITY) > 0) {
            throw new DraftValidationException("confirmedAmount", "Amount exceeds maximum capacity of 999,999,999,999.99");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private boolean isReadyToRecord(RecordDraft draft) {
        if (draft.getStatus() != DraftStatus.DRAFT) {
            return false;
        }
        if (draft.getAccountId() == null || draft.getEventType() == null || draft.getEffectiveDate() == null) {
            return false;
        }
        if (draft.getAmountInput() == null || !EXACT_TWO_DECIMAL_AMOUNT.matcher(draft.getAmountInput().trim()).matches()) {
            return false;
        }
        try {
            BigDecimal amt = new BigDecimal(draft.getAmountInput().trim());
            if (amt.compareTo(BigDecimal.ZERO) <= 0 || amt.compareTo(MAX_CAPACITY) > 0) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }

        LocalDate today = LocalDate.now(KARACHI_ZONE);
        if (draft.getEffectiveDate().isAfter(today)) {
            return false;
        }

        // Check account status and opening date if account exists
        Optional<FinancialAccount> accOpt = accountRepository.findByIdAndBusinessId(draft.getAccountId(), draft.getBusinessId());
        if (accOpt.isEmpty() || accOpt.get().getStatus() != AccountStatus.ACTIVE) {
            return false;
        }
        if (!"PKR".equals(accOpt.get().getCurrency())) {
            return false;
        }
        if (draft.getEffectiveDate().isBefore(accOpt.get().getOpeningDate())) {
            return false;
        }

        if (draft.getCounterpartyId() != null) {
            Optional<com.app.sme_health_backend.counterparty.Counterparty> cpOpt = counterpartyRepository != null
                    ? counterpartyRepository.findByIdAndBusinessId(draft.getCounterpartyId(), draft.getBusinessId())
                    : Optional.empty();
            if (cpOpt.isEmpty() || cpOpt.get().getStatus() != com.app.sme_health_backend.counterparty.CounterpartyStatus.ACTIVE) {
                return false;
            }
            if (draft.getEventType() == TransactionEventType.DIRECT_SALE_RECEIPT && !cpOpt.get().getType().isCustomerCompatible()) {
                return false;
            }
            if (draft.getEventType() == TransactionEventType.OPERATING_EXPENSE_PAYMENT && !cpOpt.get().getType().isSupplierCompatible()) {
                return false;
            }
        }

        return true;
    }

    private DraftDetailResponse toDetailResponse(RecordDraft draft) {
        com.app.sme_health_backend.counterparty.Counterparty cp = null;
        if (draft.getCounterpartyId() != null && counterpartyRepository != null) {
            cp = counterpartyRepository.findByIdAndBusinessId(draft.getCounterpartyId(), draft.getBusinessId()).orElse(null);
        }
        return new DraftDetailResponse(
                draft.getId(),
                draft.getAccountId(),
                draft.getEventType() != null ? draft.getEventType().name() : null,
                draft.getEffectiveDate() != null ? draft.getEffectiveDate().toString() : null,
                draft.getAmountInput(),
                draft.getDescription(),
                draft.getStatus().name(),
                isReadyToRecord(draft),
                draft.getVersion(),
                draft.getRecordedTransactionId(),
                draft.getCreatedById(),
                draft.getUpdatedById(),
                draft.getCreatedAt(),
                draft.getUpdatedAt(),
                cp != null ? cp.getId() : (draft.getCounterpartyId() != null ? draft.getCounterpartyId() : null),
                cp != null ? cp.getDisplayName() : null,
                cp != null ? cp.getType().name() : null
        );
    }

    private DraftSummaryItem toSummaryItem(RecordDraft draft) {
        return toSummaryItem(draft, null);
    }

    private DraftSummaryItem toSummaryItem(RecordDraft draft, com.app.sme_health_backend.counterparty.Counterparty cp) {
        return new DraftSummaryItem(
                draft.getId(),
                draft.getAccountId(),
                draft.getEventType() != null ? draft.getEventType().name() : null,
                draft.getEffectiveDate() != null ? draft.getEffectiveDate().toString() : null,
                draft.getAmountInput(),
                draft.getDescription(),
                draft.getStatus().name(),
                isReadyToRecord(draft),
                draft.getVersion(),
                draft.getRecordedTransactionId(),
                draft.getCreatedById(),
                draft.getUpdatedById(),
                draft.getCreatedAt(),
                draft.getUpdatedAt(),
                cp != null ? cp.getId() : (draft.getCounterpartyId() != null ? draft.getCounterpartyId() : null),
                cp != null ? cp.getDisplayName() : null,
                cp != null ? cp.getType().name() : null
        );
    }

    private String computeDraftPayloadHash(
            UUID accountId,
            String eventType,
            String effectiveDate,
            String amountInput,
            String description
    ) {
        return computeDraftPayloadHash(accountId, eventType, effectiveDate, amountInput, description, null);
    }

    private String computeDraftPayloadHash(
            UUID accountId,
            String eventType,
            String effectiveDate,
            String amountInput,
            String description,
            UUID counterpartyId
    ) {
        String canonical = (accountId != null ? accountId.toString() : "") + "|" +
                (eventType != null ? eventType : "") + "|" +
                (effectiveDate != null ? effectiveDate : "") + "|" +
                (amountInput != null ? amountInput : "") + "|" +
                (description != null ? description : "") + "|" +
                (counterpartyId != null ? counterpartyId.toString() : "");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private void saveIdempotencyRecord(
            UUID businessId,
            String operation,
            String key,
            String hash,
            UUID resourceId,
            int status,
            String body
    ) {
        FinancialIdempotencyRecord record = new FinancialIdempotencyRecord(
                businessId,
                operation,
                key,
                hash,
                resourceId,
                status,
                body
        );
        idempotencyRepository.save(record);
    }

    private String serializeDraftDetail(DraftDetailResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize draft response", e);
        }
    }

    private DraftDetailResponse deserializeDraftDetail(String json) {
        try {
            return objectMapper.readValue(json, DraftDetailResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize draft response", e);
        }
    }

    private TransactionDetailResponse deserializeTransactionDetail(String json) {
        try {
            return objectMapper.readValue(json, TransactionDetailResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize transaction response", e);
        }
    }
}
