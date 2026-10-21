package com.app.sme_health_backend.recurring.service;

import com.app.sme_health_backend.accounting.draft.DraftStatus;
import com.app.sme_health_backend.accounting.draft.RecordDraft;
import com.app.sme_health_backend.accounting.draft.RecordDraftRepository;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.util.CanonicalAmountValidator;
import com.app.sme_health_backend.audit.entity.SecurityAuditEvent;
import com.app.sme_health_backend.audit.model.AuditOutcome;
import com.app.sme_health_backend.audit.repository.SecurityAuditRepository;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.counterparty.CounterpartyStatus;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.recurring.calculator.RecurrenceCalculator;
import com.app.sme_health_backend.recurring.dto.OccurrenceGenerateDueResponse;
import com.app.sme_health_backend.recurring.dto.OccurrencePreviewItem;
import com.app.sme_health_backend.recurring.dto.OccurrenceSkipRequest;
import com.app.sme_health_backend.recurring.dto.RecurringOccurrenceResponse;
import com.app.sme_health_backend.recurring.dto.RecurringRuleCreateRequest;
import com.app.sme_health_backend.recurring.dto.RecurringRuleResponse;
import com.app.sme_health_backend.recurring.dto.RecurringRuleUpdateRequest;
import com.app.sme_health_backend.recurring.entity.RecurringRule;
import com.app.sme_health_backend.recurring.entity.RecurringRuleOccurrence;
import com.app.sme_health_backend.recurring.exception.RecurringConflictException;
import com.app.sme_health_backend.recurring.exception.RecurringValidationException;
import com.app.sme_health_backend.recurring.model.RecurringFrequency;
import com.app.sme_health_backend.recurring.model.RecurringOccurrenceStatus;
import com.app.sme_health_backend.recurring.model.RecurringRuleStatus;
import com.app.sme_health_backend.recurring.repository.RecurringRuleOccurrenceRepository;
import com.app.sme_health_backend.recurring.repository.RecurringRuleRepository;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class RecurringRuleService {

    private static final Logger log = LoggerFactory.getLogger(RecurringRuleService.class);
    private static final ZoneId KARACHI_ZONE = ZoneId.of("Asia/Karachi");
    public static final String OPERATION_RECURRING = "RECURRING_FINANCIAL_OPERATION";
    public static final int MAX_OCCURRENCES_PER_RUN = 50;
    private static final int MAX_CATCH_UP_PER_RULE = 50;

    private final RecurringRuleRepository ruleRepository;
    private final RecurringRuleOccurrenceRepository occurrenceRepository;
    private final FinancialAccountRepository accountRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final RecordDraftRepository draftRepository;
    private final SecurityAuditRepository auditRepository;

    public RecurringRuleService(
            RecurringRuleRepository ruleRepository,
            RecurringRuleOccurrenceRepository occurrenceRepository,
            FinancialAccountRepository accountRepository,
            CounterpartyRepository counterpartyRepository,
            RecordDraftRepository draftRepository,
            SecurityAuditRepository auditRepository
    ) {
        this.ruleRepository = ruleRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.accountRepository = accountRepository;
        this.counterpartyRepository = counterpartyRepository;
        this.draftRepository = draftRepository;
        this.auditRepository = auditRepository;
    }

    /**
     * Creates a new persistent recurring financial rule.
     */
    @Transactional
    public RecurringRuleResponse createRule(BusinessAccessContext context, RecurringRuleCreateRequest request) {
        assertWritePermission(context);

        if (request == null) {
            throw new RecurringValidationException("body", "Request body is required");
        }

        Map<String, String> errors = new LinkedHashMap<>();

        // 1. Name
        String name = (request.name() != null) ? request.name().trim() : "";
        if (name.isEmpty()) {
            errors.put("name", "Rule name is required");
        } else if (name.length() > 100) {
            errors.put("name", "Rule name must not exceed 100 characters");
        }

        // 2. Description
        String description = (request.description() != null && !request.description().trim().isEmpty())
                ? request.description().trim()
                : null;
        if (description != null && description.length() > 255) {
            errors.put("description", "Description must not exceed 255 characters");
        }

        // 3. Event Type
        TransactionEventType eventType = null;
        if (request.eventType() == null || request.eventType().trim().isEmpty()) {
            errors.put("eventType", "Event type is required");
        } else {
            try {
                eventType = TransactionEventType.valueOf(request.eventType().trim().toUpperCase());
                if (eventType != TransactionEventType.DIRECT_SALE_RECEIPT &&
                    eventType != TransactionEventType.OPERATING_EXPENSE_PAYMENT) {
                    errors.put("eventType", "Supported event types are DIRECT_SALE_RECEIPT and OPERATING_EXPENSE_PAYMENT");
                }
            } catch (IllegalArgumentException e) {
                errors.put("eventType", "Invalid event type: " + request.eventType());
            }
        }

        // 4. Account
        FinancialAccount account = null;
        if (request.accountId() == null) {
            errors.put("accountId", "Financial account is required");
        } else {
            Optional<FinancialAccount> accOpt = accountRepository.findByIdAndBusinessId(request.accountId(), context.businessId());
            if (accOpt.isEmpty()) {
                errors.put("accountId", "Financial account not found");
            } else {
                account = accOpt.get();
                if (account.getStatus() != AccountStatus.ACTIVE) {
                    errors.put("accountId", "Financial account is not active");
                }
                if (!"PKR".equalsIgnoreCase(account.getCurrency())) {
                    errors.put("accountId", "Recurring rules are strictly supported for PKR accounts");
                }
            }
        }

        // 5. Counterparty (optional)
        Counterparty counterparty = null;
        if (request.counterpartyId() != null) {
            Optional<Counterparty> cpOpt = counterpartyRepository.findByIdAndBusinessId(request.counterpartyId(), context.businessId());
            if (cpOpt.isEmpty()) {
                errors.put("counterpartyId", "Counterparty not found");
            } else {
                counterparty = cpOpt.get();
                if (counterparty.getStatus() != CounterpartyStatus.ACTIVE) {
                    errors.put("counterpartyId", "Counterparty is archived and cannot be assigned");
                } else if (eventType == TransactionEventType.DIRECT_SALE_RECEIPT && !counterparty.getType().isCustomerCompatible()) {
                    errors.put("counterpartyId", "Counterparty must be a CUSTOMER or BOTH for a direct sale receipt");
                } else if (eventType == TransactionEventType.OPERATING_EXPENSE_PAYMENT && !counterparty.getType().isSupplierCompatible()) {
                    errors.put("counterpartyId", "Counterparty must be a SUPPLIER or BOTH for an operating expense payment");
                }
            }
        }

        // 6. Amount
        BigDecimal amount = null;
        if (request.amount() == null || request.amount().trim().isEmpty()) {
            errors.put("amount", "Amount is required");
        } else {
            try {
                amount = CanonicalAmountValidator.validateCanonicalAmount(request.amount().trim(), "amount");
            } catch (RuntimeException e) {
                errors.put("amount", e.getMessage());
            }
        }

        // 7. Frequency
        RecurringFrequency frequency = null;
        if (request.frequency() == null || request.frequency().trim().isEmpty()) {
            errors.put("frequency", "Frequency is required");
        } else {
            try {
                frequency = RecurringFrequency.valueOf(request.frequency().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.put("frequency", "Invalid frequency: " + request.frequency() + ". Supported: WEEKLY, BIWEEKLY, MONTHLY, QUARTERLY, ANNUALLY");
            }
        }

        // 8. Start Date
        LocalDate startDate = null;
        if (request.startDate() == null || request.startDate().trim().isEmpty()) {
            errors.put("startDate", "Start date is required");
        } else {
            try {
                startDate = LocalDate.parse(request.startDate().trim());
                if (account != null && startDate.isBefore(account.getOpeningDate())) {
                    errors.put("startDate", "Start date cannot precede account opening date (" + account.getOpeningDate() + ")");
                }
            } catch (DateTimeParseException e) {
                errors.put("startDate", "Invalid start date format. Expected YYYY-MM-DD");
            }
        }

        // 9. End Date (optional)
        LocalDate endDate = null;
        if (request.endDate() != null && !request.endDate().trim().isEmpty()) {
            try {
                endDate = LocalDate.parse(request.endDate().trim());
                if (startDate != null && endDate.isBefore(startDate)) {
                    errors.put("endDate", "End date cannot precede start date");
                }
            } catch (DateTimeParseException e) {
                errors.put("endDate", "Invalid end date format. Expected YYYY-MM-DD");
            }
        }

        if (!errors.isEmpty()) {
            throw new RecurringValidationException(errors);
        }

        int anchorDayOfMonth = startDate.getDayOfMonth();
        int anchorMonth = startDate.getMonthValue();
        LocalDate nextDueDate = startDate;

        RecurringRule rule = new RecurringRule(
                context.businessId(),
                name,
                description,
                eventType,
                account.getId(),
                counterparty != null ? counterparty.getId() : null,
                amount,
                frequency,
                anchorDayOfMonth,
                anchorMonth,
                startDate,
                endDate,
                nextDueDate,
                context.userId()
        );

        rule = ruleRepository.saveAndFlush(rule);

        recordAudit(
                context.userId(),
                context.businessId(),
                OPERATION_RECURRING,
                "RECURRING_RULE",
                rule.getId().toString(),
                "Created recurring rule '" + rule.getName() + "' with amount " + rule.getAmount() + " PKR"
        );

        return toRuleResponse(rule, account, counterparty);
    }

    /**
     * Retrieves a single recurring rule by ID.
     */
    @Transactional(readOnly = true)
    public RecurringRuleResponse getRule(BusinessAccessContext context, UUID id) {
        RecurringRule rule = ruleRepository.findByIdAndBusinessId(id, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurring rule not found"));

        FinancialAccount account = accountRepository.findByIdAndBusinessId(rule.getAccountId(), context.businessId()).orElse(null);
        Counterparty counterparty = rule.getCounterpartyId() != null
                ? counterpartyRepository.findByIdAndBusinessId(rule.getCounterpartyId(), context.businessId()).orElse(null)
                : null;

        return toRuleResponse(rule, account, counterparty);
    }

    /**
     * Lists recurring rules for the active business.
     */
    @Transactional(readOnly = true)
    public Page<RecurringRuleResponse> listRules(
            BusinessAccessContext context,
            RecurringRuleStatus status,
            Pageable pageable
    ) {
        Page<RecurringRule> page = (status != null)
                ? ruleRepository.findByBusinessIdAndStatus(context.businessId(), status, pageable)
                : ruleRepository.findByBusinessId(context.businessId(), pageable);

        return page.map(rule -> {
            FinancialAccount account = accountRepository.findByIdAndBusinessId(rule.getAccountId(), context.businessId()).orElse(null);
            Counterparty counterparty = rule.getCounterpartyId() != null
                    ? counterpartyRepository.findByIdAndBusinessId(rule.getCounterpartyId(), context.businessId()).orElse(null)
                    : null;
            return toRuleResponse(rule, account, counterparty);
        });
    }

    /**
     * Updates an existing recurring rule's future configuration.
     * Historical occurrences and already-generated drafts remain unaffected.
     */
    @Transactional
    public RecurringRuleResponse updateRule(
            BusinessAccessContext context,
            UUID id,
            RecurringRuleUpdateRequest request
    ) {
        assertWritePermission(context);

        if (request == null) {
            throw new RecurringValidationException("body", "Request body is required");
        }
        if (request.version() == null) {
            throw new RecurringValidationException("version", "Version is required for updating a rule");
        }

        RecurringRule rule = ruleRepository.findForUpdateByIdAndBusinessId(id, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurring rule not found"));

        if (!Objects.equals(request.version(), rule.getVersion())) {
            throw new RecurringConflictException(
                    "Rule version mismatch: expected " + rule.getVersion() + " but received " + request.version()
            );
        }

        if (rule.getStatus() == RecurringRuleStatus.ARCHIVED) {
            throw new RecurringConflictException("Cannot edit an archived recurring rule");
        }

        Map<String, String> errors = new LinkedHashMap<>();

        String name = (request.name() != null) ? request.name().trim() : "";
        if (name.isEmpty()) {
            errors.put("name", "Rule name is required");
        } else if (name.length() > 100) {
            errors.put("name", "Rule name must not exceed 100 characters");
        }

        String description = (request.description() != null && !request.description().trim().isEmpty())
                ? request.description().trim()
                : null;
        if (description != null && description.length() > 255) {
            errors.put("description", "Description must not exceed 255 characters");
        }

        TransactionEventType eventType = null;
        if (request.eventType() == null || request.eventType().trim().isEmpty()) {
            errors.put("eventType", "Event type is required");
        } else {
            try {
                eventType = TransactionEventType.valueOf(request.eventType().trim().toUpperCase());
                if (eventType != TransactionEventType.DIRECT_SALE_RECEIPT &&
                    eventType != TransactionEventType.OPERATING_EXPENSE_PAYMENT) {
                    errors.put("eventType", "Supported event types are DIRECT_SALE_RECEIPT and OPERATING_EXPENSE_PAYMENT");
                }
            } catch (IllegalArgumentException e) {
                errors.put("eventType", "Invalid event type: " + request.eventType());
            }
        }

        FinancialAccount account = null;
        if (request.accountId() == null) {
            errors.put("accountId", "Financial account is required");
        } else {
            Optional<FinancialAccount> accOpt = accountRepository.findByIdAndBusinessId(request.accountId(), context.businessId());
            if (accOpt.isEmpty()) {
                errors.put("accountId", "Financial account not found");
            } else {
                account = accOpt.get();
                if (account.getStatus() != AccountStatus.ACTIVE) {
                    errors.put("accountId", "Financial account is not active");
                }
                if (!"PKR".equalsIgnoreCase(account.getCurrency())) {
                    errors.put("accountId", "Recurring rules are strictly supported for PKR accounts");
                }
            }
        }

        Counterparty counterparty = null;
        if (request.counterpartyId() != null) {
            Optional<Counterparty> cpOpt = counterpartyRepository.findByIdAndBusinessId(request.counterpartyId(), context.businessId());
            if (cpOpt.isEmpty()) {
                errors.put("counterpartyId", "Counterparty not found");
            } else {
                counterparty = cpOpt.get();
                if (counterparty.getStatus() != CounterpartyStatus.ACTIVE) {
                    errors.put("counterpartyId", "Counterparty is archived and cannot be assigned");
                } else if (eventType == TransactionEventType.DIRECT_SALE_RECEIPT && !counterparty.getType().isCustomerCompatible()) {
                    errors.put("counterpartyId", "Counterparty must be a CUSTOMER or BOTH for a direct sale receipt");
                } else if (eventType == TransactionEventType.OPERATING_EXPENSE_PAYMENT && !counterparty.getType().isSupplierCompatible()) {
                    errors.put("counterpartyId", "Counterparty must be a SUPPLIER or BOTH for an operating expense payment");
                }
            }
        }

        BigDecimal amount = null;
        if (request.amount() == null || request.amount().trim().isEmpty()) {
            errors.put("amount", "Amount is required");
        } else {
            try {
                amount = CanonicalAmountValidator.validateCanonicalAmount(request.amount().trim(), "amount");
            } catch (RuntimeException e) {
                errors.put("amount", e.getMessage());
            }
        }

        RecurringFrequency frequency = null;
        if (request.frequency() == null || request.frequency().trim().isEmpty()) {
            errors.put("frequency", "Frequency is required");
        } else {
            try {
                frequency = RecurringFrequency.valueOf(request.frequency().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.put("frequency", "Invalid frequency: " + request.frequency());
            }
        }

        LocalDate endDate = null;
        if (request.endDate() != null && !request.endDate().trim().isEmpty()) {
            try {
                endDate = LocalDate.parse(request.endDate().trim());
                if (endDate.isBefore(rule.getStartDate())) {
                    errors.put("endDate", "End date cannot precede start date (" + rule.getStartDate() + ")");
                }
            } catch (DateTimeParseException e) {
                errors.put("endDate", "Invalid end date format. Expected YYYY-MM-DD");
            }
        }

        if (!errors.isEmpty()) {
            throw new RecurringValidationException(errors);
        }

        rule.setName(name);
        rule.setDescription(description);
        rule.setEventType(eventType);
        rule.setAccountId(account.getId());
        rule.setCounterpartyId(counterparty != null ? counterparty.getId() : null);
        rule.setAmount(amount);
        rule.setFrequency(frequency);
        rule.setEndDate(endDate);
        rule.setUpdatedByUserId(context.userId());

        // Check if next due date needs re-evaluation against new end date
        if (rule.getNextDueDate() != null && endDate != null && rule.getNextDueDate().isAfter(endDate)) {
            rule.setNextDueDate(null);
        }

        rule = ruleRepository.saveAndFlush(rule);

        recordAudit(
                context.userId(),
                context.businessId(),
                OPERATION_RECURRING,
                "RECURRING_RULE",
                rule.getId().toString(),
                "Updated recurring rule '" + rule.getName() + "' to amount " + rule.getAmount() + " PKR"
        );

        return toRuleResponse(rule, account, counterparty);
    }

    /**
     * Pauses an active recurring rule.
     */
    @Transactional
    public RecurringRuleResponse pauseRule(BusinessAccessContext context, UUID id) {
        assertWritePermission(context);

        RecurringRule rule = ruleRepository.findForUpdateByIdAndBusinessId(id, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurring rule not found"));

        if (rule.getStatus() == RecurringRuleStatus.ARCHIVED) {
            throw new RecurringConflictException("Cannot pause an archived recurring rule");
        }
        if (rule.getStatus() == RecurringRuleStatus.PAUSED) {
            return toRuleResponse(rule);
        }

        rule.setStatus(RecurringRuleStatus.PAUSED);
        rule.setUpdatedByUserId(context.userId());
        rule = ruleRepository.saveAndFlush(rule);

        recordAudit(
                context.userId(),
                context.businessId(),
                OPERATION_RECURRING,
                "RECURRING_RULE",
                rule.getId().toString(),
                "Paused recurring rule '" + rule.getName() + "'"
        );

        return toRuleResponse(rule);
    }

    /**
     * Resumes a paused recurring rule.
     */
    @Transactional
    public RecurringRuleResponse resumeRule(BusinessAccessContext context, UUID id) {
        assertWritePermission(context);

        RecurringRule rule = ruleRepository.findForUpdateByIdAndBusinessId(id, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurring rule not found"));

        if (rule.getStatus() == RecurringRuleStatus.ARCHIVED) {
            throw new RecurringConflictException("Cannot resume an archived recurring rule");
        }
        if (rule.getStatus() == RecurringRuleStatus.ACTIVE) {
            return toRuleResponse(rule);
        }

        rule.setStatus(RecurringRuleStatus.ACTIVE);
        rule.setUpdatedByUserId(context.userId());
        rule = ruleRepository.saveAndFlush(rule);

        recordAudit(
                context.userId(),
                context.businessId(),
                OPERATION_RECURRING,
                "RECURRING_RULE",
                rule.getId().toString(),
                "Resumed recurring rule '" + rule.getName() + "'"
        );

        return toRuleResponse(rule);
    }

    /**
     * Archives a recurring rule.
     */
    @Transactional
    public RecurringRuleResponse archiveRule(BusinessAccessContext context, UUID id) {
        assertWritePermission(context);

        RecurringRule rule = ruleRepository.findForUpdateByIdAndBusinessId(id, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurring rule not found"));

        if (rule.getStatus() == RecurringRuleStatus.ARCHIVED) {
            return toRuleResponse(rule);
        }

        rule.setStatus(RecurringRuleStatus.ARCHIVED);
        rule.setNextDueDate(null);
        rule.setUpdatedByUserId(context.userId());
        rule = ruleRepository.saveAndFlush(rule);

        recordAudit(
                context.userId(),
                context.businessId(),
                OPERATION_RECURRING,
                "RECURRING_RULE",
                rule.getId().toString(),
                "Archived recurring rule '" + rule.getName() + "'"
        );

        return toRuleResponse(rule);
    }

    /**
     * Previews calculated upcoming occurrences for a rule.
     */
    @Transactional(readOnly = true)
    public List<OccurrencePreviewItem> previewSchedule(BusinessAccessContext context, UUID id, Integer countParam) {
        RecurringRule rule = ruleRepository.findByIdAndBusinessId(id, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurring rule not found"));

        int count = (countParam != null && countParam > 0) ? Math.min(countParam, 50) : 5;

        LocalDate startPreviewDate = (rule.getNextDueDate() != null)
                ? rule.getNextDueDate()
                : rule.getStartDate();

        List<LocalDate> dates = RecurrenceCalculator.previewSchedule(
                startPreviewDate,
                rule.getEndDate(),
                rule.getFrequency(),
                rule.getAnchorDayOfMonth(),
                rule.getAnchorMonth(),
                count
        );

        String canonicalAmount = rule.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString();
        List<OccurrencePreviewItem> items = new ArrayList<>();
        for (LocalDate d : dates) {
            items.add(new OccurrencePreviewItem(
                    d.toString(),
                    canonicalAmount,
                    rule.getCurrency(),
                    rule.getEventType().name()
            ));
        }

        return Collections.unmodifiableList(items);
    }

    /**
     * Lists occurrences for a rule or business.
     */
    @Transactional(readOnly = true)
    public Page<RecurringOccurrenceResponse> listOccurrences(
            BusinessAccessContext context,
            UUID ruleId,
            RecurringOccurrenceStatus status,
            Pageable pageable
    ) {
        Page<RecurringRuleOccurrence> page;
        if (ruleId != null) {
            page = occurrenceRepository.findByBusinessIdAndRecurringRuleId(context.businessId(), ruleId, pageable);
        } else if (status != null) {
            page = occurrenceRepository.findByBusinessIdAndStatus(context.businessId(), status, pageable);
        } else {
            page = occurrenceRepository.findByBusinessId(context.businessId(), pageable);
        }

        return page.map(this::toOccurrenceResponse);
    }

    /**
     * Retrieves a single occurrence by ID.
     */
    @Transactional(readOnly = true)
    public RecurringOccurrenceResponse getOccurrence(BusinessAccessContext context, UUID id) {
        RecurringRuleOccurrence occurrence = occurrenceRepository.findByIdAndBusinessId(id, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurring rule occurrence not found"));

        return toOccurrenceResponse(occurrence);
    }

    /**
     * Skips an eligible occurrence and cancels any unrecorded linked draft.
     */
    @Transactional
    public RecurringOccurrenceResponse skipOccurrence(
            BusinessAccessContext context,
            UUID id,
            OccurrenceSkipRequest request
    ) {
        assertWritePermission(context);

        RecurringRuleOccurrence occurrence = occurrenceRepository.findForUpdateByIdAndBusinessId(id, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurring rule occurrence not found"));

        if (occurrence.getStatus() == RecurringOccurrenceStatus.POSTED) {
            throw new RecurringConflictException("Cannot skip an already posted occurrence");
        }
        if (occurrence.getStatus() == RecurringOccurrenceStatus.REVERSED) {
            throw new RecurringConflictException("Cannot skip a reversed occurrence");
        }
        if (occurrence.getStatus() == RecurringOccurrenceStatus.SKIPPED) {
            return toOccurrenceResponse(occurrence);
        }

        // If draft exists and is still in DRAFT status, discard it
        if (occurrence.getDraftId() != null) {
            Optional<RecordDraft> draftOpt = draftRepository.findByIdAndBusinessId(occurrence.getDraftId(), context.businessId());
            if (draftOpt.isPresent()) {
                RecordDraft draft = draftOpt.get();
                if (draft.getStatus() == DraftStatus.RECORDED) {
                    throw new RecurringConflictException("Cannot skip occurrence: linked draft has already been recorded");
                }
                if (draft.getStatus() == DraftStatus.DRAFT) {
                    draft.setStatus(DraftStatus.DISCARDED);
                    draft.setUpdatedById(context.userId());
                    draftRepository.saveAndFlush(draft);
                }
            }
        }

        occurrence.setStatus(RecurringOccurrenceStatus.SKIPPED);
        occurrence.setSkippedAt(Instant.now());
        occurrence.setSkippedReason(request != null && request.reason() != null ? request.reason().trim() : null);
        occurrence = occurrenceRepository.saveAndFlush(occurrence);

        recordAudit(
                context.userId(),
                context.businessId(),
                OPERATION_RECURRING,
                "RECURRING_OCCURRENCE",
                occurrence.getId().toString(),
                "Skipped occurrence " + occurrence.getId() + " on " + occurrence.getScheduledDate()
        );

        return toOccurrenceResponse(occurrence);
    }

    /**
     * Processes due occurrences for the active business tenant (manual trigger).
     */
    @Transactional
    public OccurrenceGenerateDueResponse generateDueOccurrencesForBusiness(BusinessAccessContext context) {
        assertWritePermission(context);

        LocalDate today = LocalDate.now(KARACHI_ZONE);
        List<RecurringRule> dueRules = ruleRepository.findByBusinessIdAndStatusAndNextDueDateLessThanEqual(
                context.businessId(),
                RecurringRuleStatus.ACTIVE,
                today
        );

        int generatedCount = 0;
        int processedRulesCount = 0;

        for (RecurringRule ruleSummary : dueRules) {
            if (generatedCount >= MAX_OCCURRENCES_PER_RUN) {
                break;
            }

            RecurringRule lockedRule = ruleRepository.findForUpdateByIdAndBusinessId(ruleSummary.getId(), context.businessId())
                    .orElse(null);
            if (lockedRule == null || lockedRule.getStatus() != RecurringRuleStatus.ACTIVE) {
                continue;
            }

            int remainingCapacity = MAX_OCCURRENCES_PER_RUN - generatedCount;
            int countForRule = processDueRule(lockedRule, today, context.userId(), false, remainingCapacity);
            if (countForRule > 0) {
                generatedCount += countForRule;
                processedRulesCount++;
            }
        }

        String msg = (generatedCount > 0)
                ? "Generated " + generatedCount + " draft(s) across " + processedRulesCount + " recurring rule(s)"
                : "No new recurring occurrences due for processing";

        return new OccurrenceGenerateDueResponse(generatedCount, processedRulesCount, msg);
    }

    /**
     * Processes due occurrences for all active businesses across the application (background scheduler).
     */
    @Transactional
    public int generateDueOccurrencesAllBusinesses() {
        LocalDate today = LocalDate.now(KARACHI_ZONE);
        List<RecurringRule> dueRules = ruleRepository.findByStatusAndNextDueDateLessThanEqual(
                RecurringRuleStatus.ACTIVE,
                today
        );

        int totalGenerated = 0;
        for (RecurringRule ruleSummary : dueRules) {
            if (totalGenerated >= MAX_OCCURRENCES_PER_RUN) {
                break;
            }

            RecurringRule lockedRule = ruleRepository.findForUpdateByIdAndBusinessId(ruleSummary.getId(), ruleSummary.getBusinessId())
                    .orElse(null);
            if (lockedRule == null || lockedRule.getStatus() != RecurringRuleStatus.ACTIVE) {
                continue;
            }

            int remainingCapacity = MAX_OCCURRENCES_PER_RUN - totalGenerated;
            int count = processDueRule(lockedRule, today, lockedRule.getCreatedByUserId(), true, remainingCapacity);
            totalGenerated += count;
        }

        if (totalGenerated > 0) {
            log.info("RecurringTransactionScheduler: Generated {} drafts across due recurring rules", totalGenerated);
        }

        return totalGenerated;
    }

    /**
     * Internal helper to process a single locked due rule up to {@code today}, advancing its nextDueDate.
     */
    private int processDueRule(RecurringRule rule, LocalDate today, UUID actorUserId, boolean isSystem, int maxCapacity) {
        int generated = 0;
        int catchUpCount = 0;

        while (rule.getNextDueDate() != null &&
               !rule.getNextDueDate().isAfter(today) &&
               catchUpCount < Math.min(MAX_CATCH_UP_PER_RULE, maxCapacity) &&
               generated < maxCapacity) {

            LocalDate scheduledDate = rule.getNextDueDate();

            // Check if end_date boundary is breached
            if (rule.getEndDate() != null && scheduledDate.isAfter(rule.getEndDate())) {
                rule.setNextDueDate(null);
                ruleRepository.saveAndFlush(rule);
                break;
            }

            // Check if an occurrence already exists on this scheduled date
            Optional<RecurringRuleOccurrence> existingOpt = occurrenceRepository
                    .findByBusinessIdAndRecurringRuleIdAndScheduledDate(rule.getBusinessId(), rule.getId(), scheduledDate);

            if (existingOpt.isEmpty()) {
                // Generate RecordDraft
                String description = (rule.getDescription() != null && !rule.getDescription().isBlank())
                        ? rule.getDescription()
                        : rule.getName();

                RecordDraft draft = new RecordDraft(
                        rule.getBusinessId(),
                        rule.getAccountId(),
                        rule.getEventType(),
                        scheduledDate,
                        rule.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                        description,
                        actorUserId
                );
                draft.setCounterpartyId(rule.getCounterpartyId());
                draft = draftRepository.saveAndFlush(draft);

                // Create RecurringRuleOccurrence linked to draft
                RecurringRuleOccurrence occurrence = new RecurringRuleOccurrence(
                        rule.getBusinessId(),
                        rule.getId(),
                        rule.getVersion(),
                        scheduledDate,
                        rule.getAmount(),
                        rule.getEventType(),
                        rule.getAccountId(),
                        rule.getCounterpartyId(),
                        rule.getDescription(),
                        RecurringOccurrenceStatus.DRAFT_READY,
                        actorUserId
                );
                occurrence.setDraftId(draft.getId());
                occurrence.setGeneratedAt(Instant.now());
                occurrence = occurrenceRepository.saveAndFlush(occurrence);

                recordAudit(
                        actorUserId,
                        rule.getBusinessId(),
                        "RECURRING_OCCURRENCE_GENERATED",
                        "RECURRING_OCCURRENCE",
                        occurrence.getId().toString(),
                        "Generated unposted draft for scheduled date " + scheduledDate,
                        isSystem
                );

                generated++;
            }

            // Advance nextDueDate
            LocalDate nextDate = RecurrenceCalculator.calculateNextDueDate(
                    scheduledDate,
                    rule.getFrequency(),
                    rule.getAnchorDayOfMonth(),
                    rule.getAnchorMonth()
            );

            if (rule.getEndDate() != null && nextDate.isAfter(rule.getEndDate())) {
                rule.setNextDueDate(null);
            } else {
                rule.setNextDueDate(nextDate);
            }

            rule = ruleRepository.saveAndFlush(rule);
            catchUpCount++;
        }

        return generated;
    }

    private void assertWritePermission(BusinessAccessContext context) {
        if (context.role() != MembershipRole.OWNER && context.role() != MembershipRole.ACCOUNTANT) {
            throw new AccessDeniedException("Only OWNER and ACCOUNTANT roles can modify recurring financial rules");
        }
    }

    private RecurringRuleResponse toRuleResponse(RecurringRule rule) {
        FinancialAccount account = accountRepository.findByIdAndBusinessId(rule.getAccountId(), rule.getBusinessId()).orElse(null);
        Counterparty counterparty = rule.getCounterpartyId() != null
                ? counterpartyRepository.findByIdAndBusinessId(rule.getCounterpartyId(), rule.getBusinessId()).orElse(null)
                : null;
        return toRuleResponse(rule, account, counterparty);
    }

    private RecurringRuleResponse toRuleResponse(RecurringRule rule, FinancialAccount account, Counterparty counterparty) {
        return new RecurringRuleResponse(
                rule.getId(),
                rule.getName(),
                rule.getDescription(),
                rule.getEventType().name(),
                rule.getAccountId(),
                account != null ? account.getAccountName() : null,
                account != null ? account.getAccountType().name() : null,
                rule.getCounterpartyId(),
                counterparty != null ? counterparty.getDisplayName() : null,
                rule.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                rule.getCurrency(),
                rule.getFrequency().name(),
                rule.getStartDate().toString(),
                rule.getEndDate() != null ? rule.getEndDate().toString() : null,
                rule.getNextDueDate() != null ? rule.getNextDueDate().toString() : null,
                rule.getStatus().name(),
                rule.getVersion(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }

    private RecurringOccurrenceResponse toOccurrenceResponse(RecurringRuleOccurrence occurrence) {
        RecurringRule rule = ruleRepository.findByIdAndBusinessId(occurrence.getRecurringRuleId(), occurrence.getBusinessId()).orElse(null);
        FinancialAccount account = accountRepository.findByIdAndBusinessId(occurrence.getAccountId(), occurrence.getBusinessId()).orElse(null);
        Counterparty counterparty = occurrence.getCounterpartyId() != null
                ? counterpartyRepository.findByIdAndBusinessId(occurrence.getCounterpartyId(), occurrence.getBusinessId()).orElse(null)
                : null;

        return new RecurringOccurrenceResponse(
                occurrence.getId(),
                occurrence.getRecurringRuleId(),
                rule != null ? rule.getName() : null,
                occurrence.getRuleVersion(),
                occurrence.getScheduledDate().toString(),
                occurrence.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                "PKR",
                occurrence.getEventType().name(),
                occurrence.getAccountId(),
                account != null ? account.getAccountName() : null,
                occurrence.getCounterpartyId(),
                counterparty != null ? counterparty.getDisplayName() : null,
                occurrence.getDescription(),
                occurrence.getStatus().name(),
                occurrence.getDraftId(),
                occurrence.getPostedTransactionId(),
                occurrence.getGeneratedAt(),
                occurrence.getPostedAt(),
                occurrence.getSkippedAt(),
                occurrence.getSkippedReason(),
                occurrence.getReversedAt()
        );
    }

    private void recordAudit(UUID actorUserId, UUID businessId, String eventType, String targetType, String targetId, String description) {
        recordAudit(actorUserId, businessId, eventType, targetType, targetId, description, false);
    }

    private void recordAudit(UUID actorUserId, UUID businessId, String eventType, String targetType, String targetId, String description, boolean isSystem) {
        if (auditRepository == null) {
            return;
        }
        com.app.sme_health_backend.audit.entity.SecurityAuditEvent event = new com.app.sme_health_backend.audit.entity.SecurityAuditEvent();
        event.setActorUserId(actorUserId);
        event.setBusinessId(businessId);
        event.setEventType(eventType);
        event.setTargetType(targetType);
        event.setTargetId(targetId);
        event.setOutcome(AuditOutcome.SUCCESS);
        event.setSystem(isSystem);
        String safeDesc = description != null ? description.replace("\"", "\\\"") : "";
        event.setMetadata("{\"message\":\"" + safeDesc + "\",\"automated\":" + isSystem + "}");
        auditRepository.save(event);
    }
}
