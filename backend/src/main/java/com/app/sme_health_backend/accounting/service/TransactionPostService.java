package com.app.sme_health_backend.accounting.service;

import com.app.sme_health_backend.accounting.dto.MovementDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionPostRequest;
import com.app.sme_health_backend.accounting.entity.AccountMovement;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialIdempotencyRecord;
import com.app.sme_health_backend.accounting.entity.FinancialTransaction;
import com.app.sme_health_backend.accounting.exception.TransactionValidationException;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.CurrencyRegistry;
import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.AccountMovementRepository;
import com.app.sme_health_backend.accounting.repository.AccountMovementSum;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialIdempotencyRepository;
import com.app.sme_health_backend.accounting.repository.FinancialTransactionRepository;
import com.app.sme_health_backend.audit.entity.SecurityAuditEvent;
import com.app.sme_health_backend.audit.model.AuditOutcome;
import com.app.sme_health_backend.audit.repository.SecurityAuditRepository;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
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

@Service
public class TransactionPostService {

    public static final String OPERATION_TRANSACTION_POST = "FINANCIAL_TRANSACTION";
    private static final ZoneId KARACHI_ZONE = ZoneId.of("Asia/Karachi");
    private static final DateTimeFormatter ISO_INSTANT_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final BigDecimal MAX_CAPACITY = new BigDecimal("999999999999.99");
    private static final Pattern EXACT_TWO_DECIMAL_AMOUNT = Pattern.compile("^(?:0|[1-9]\\d*)\\.\\d{2}$");

    private final FinancialAccountRepository accountRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final AccountMovementRepository movementRepository;
    private final FinancialIdempotencyRepository idempotencyRepository;
    private final SecurityAuditRepository securityAuditRepository;
    private final com.app.sme_health_backend.counterparty.CounterpartyRepository counterpartyRepository;
    private final ObjectMapper objectMapper;

    public TransactionPostService(
            FinancialAccountRepository accountRepository,
            FinancialTransactionRepository transactionRepository,
            AccountMovementRepository movementRepository,
            FinancialIdempotencyRepository idempotencyRepository,
            SecurityAuditRepository securityAuditRepository,
            ObjectMapper objectMapper
    ) {
        this(accountRepository, transactionRepository, movementRepository, idempotencyRepository, securityAuditRepository, null, objectMapper);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public TransactionPostService(
            FinancialAccountRepository accountRepository,
            FinancialTransactionRepository transactionRepository,
            AccountMovementRepository movementRepository,
            FinancialIdempotencyRepository idempotencyRepository,
            SecurityAuditRepository securityAuditRepository,
            com.app.sme_health_backend.counterparty.CounterpartyRepository counterpartyRepository,
            ObjectMapper objectMapper
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.movementRepository = movementRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.securityAuditRepository = securityAuditRepository;
        this.counterpartyRepository = counterpartyRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TransactionDetailResponse postTransaction(
            BusinessAccessContext context,
            String idempotencyKey,
            TransactionPostRequest request
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new TransactionValidationException("Idempotency-Key", "Missing required header: Idempotency-Key");
        }
        String trimmedKey = idempotencyKey.trim();
        if (trimmedKey.length() > 100) {
            throw new TransactionValidationException("Idempotency-Key", "Idempotency-Key must not exceed 100 characters");
        }

        if (request == null) {
            throw new TransactionValidationException("body", "Request body is required");
        }

        Map<String, String> errors = new LinkedHashMap<>();

        // 1. Account ID
        if (request.accountId() == null) {
            errors.put("accountId", "Account ID is required");
        }

        // 2. Event Type
        TransactionEventType eventType = null;
        if (request.eventType() == null || request.eventType().trim().isEmpty()) {
            errors.put("eventType", "Event type is required");
        } else {
            try {
                eventType = TransactionEventType.valueOf(request.eventType().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errors.put("eventType", "Invalid eventType: " + request.eventType() + ". Supported types: DIRECT_SALE_RECEIPT, OPERATING_EXPENSE_PAYMENT");
            }
        }

        // 3. Amount
        BigDecimal amount = null;
        if (request.amount() == null || request.amount().trim().isEmpty()) {
            errors.put("amount", "Amount is required");
        } else {
            String rawAmount = request.amount().trim();
            if (!EXACT_TWO_DECIMAL_AMOUNT.matcher(rawAmount).matches()) {
                errors.put("amount", "Amount must be an exact two-decimal positive amount (e.g. 10.00)");
            } else {
                try {
                    BigDecimal parsed = new BigDecimal(rawAmount);
                    if (parsed.compareTo(BigDecimal.ZERO) <= 0) {
                        errors.put("amount", "Amount must be greater than zero");
                    } else if (parsed.compareTo(MAX_CAPACITY) > 0) {
                        errors.put("amount", "Amount exceeds maximum capacity of 999,999,999,999.99");
                    } else {
                        amount = parsed.setScale(2, RoundingMode.UNNECESSARY);
                    }
                } catch (NumberFormatException e) {
                    errors.put("amount", "Invalid decimal format for amount");
                }
            }
        }

        // 4. Effective Date format
        LocalDate effectiveDate = null;
        LocalDate today = LocalDate.now(KARACHI_ZONE);
        if (request.effectiveDate() == null || request.effectiveDate().trim().isEmpty()) {
            errors.put("effectiveDate", "Effective date is required");
        } else {
            try {
                effectiveDate = LocalDate.parse(request.effectiveDate().trim());
                if (effectiveDate.isAfter(today)) {
                    errors.put("effectiveDate", "Effective date cannot be in the future");
                }
            } catch (DateTimeParseException e) {
                errors.put("effectiveDate", "Invalid date format. Expected YYYY-MM-DD");
            }
        }

        // 5. Description
        String trimmedDescription = (request.description() != null && !request.description().trim().isEmpty())
                ? request.description().trim()
                : null;
        if (trimmedDescription != null && trimmedDescription.length() > 255) {
            errors.put("description", "Description must not exceed 255 characters");
        }

        // 6. Counterparty (optional)
        com.app.sme_health_backend.counterparty.Counterparty counterparty = null;
        if (request.counterpartyId() != null) {
            java.util.Optional<com.app.sme_health_backend.counterparty.Counterparty> cpOpt = counterpartyRepository != null
                    ? counterpartyRepository.findByIdAndBusinessId(request.counterpartyId(), context.businessId())
                    : java.util.Optional.empty();
            if (cpOpt.isEmpty()) {
                errors.put("counterpartyId", "Counterparty not found");
            } else {
                counterparty = cpOpt.get();
                if (counterparty.getStatus() != com.app.sme_health_backend.counterparty.CounterpartyStatus.ACTIVE) {
                    errors.put("counterpartyId", "Selected counterparty is archived and cannot be newly assigned");
                } else if (eventType == TransactionEventType.DIRECT_SALE_RECEIPT && !counterparty.getType().isCustomerCompatible()) {
                    errors.put("counterpartyId", "Counterparty must be a CUSTOMER or BOTH for a direct sale receipt");
                } else if (eventType == TransactionEventType.OPERATING_EXPENSE_PAYMENT && !counterparty.getType().isSupplierCompatible()) {
                    errors.put("counterpartyId", "Counterparty must be a SUPPLIER or BOTH for an operating expense payment");
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new TransactionValidationException(errors);
        }

        // Lock the account to guarantee atomic concurrency
        FinancialAccount account = accountRepository.findForUpdateByIdAndBusinessId(request.accountId(), context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Financial account not found"));

        // Validate account constraints
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new TransactionValidationException("accountId", "Financial account is not active");
        }
        if (!"PKR".equals(account.getCurrency())) {
            throw new TransactionValidationException("currency", "Posting is currently only supported for PKR accounts");
        }
        if (effectiveDate.isBefore(account.getOpeningDate())) {
            throw new TransactionValidationException(
                    "effectiveDate",
                    "Effective date cannot precede account opening date (" + account.getOpeningDate() + ")"
            );
        }

        // Compute normalized payload digest
        String payloadHash = computePayloadHash(
                request.accountId(),
                eventType,
                amount,
                effectiveDate,
                trimmedDescription,
                request.counterpartyId()
        );

        // Check durable idempotency
        Optional<FinancialIdempotencyRecord> existingOpt = idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(
                context.businessId(),
                OPERATION_TRANSACTION_POST,
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

        MovementDirection direction = eventType.getMovementDirection();

        // Sequence & Capacity validation: Simulate complete chronological movement sequence
        List<AccountMovement> existingMovements = movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(account.getId());

        List<AccountMovement> simulated = new ArrayList<>(existingMovements);
        AccountMovement proposedMovement = new AccountMovement(
                context.businessId(),
                null,
                account.getId(),
                direction,
                amount,
                "PKR",
                effectiveDate
        );
        simulated.add(proposedMovement);

        // Chronological sort: by effectiveDate ASC. Equal dates: existing movements apply before proposed
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
                        "Transaction would cause cash account balance to become negative on " + m.getEffectiveDate()
                );
            }
            if (running.abs().compareTo(CurrencyRegistry.MAX_CAPACITY) >= 0) {
                throw new TransactionValidationException(
                        "amount",
                        "Transaction would cause account balance to exceed maximum capacity of 999,999,999,999.99"
                );
            }
        }

        try {
            // 1. Create and persist FinancialTransaction
            FinancialTransaction tx = new FinancialTransaction(
                    context.businessId(),
                    account.getId(),
                    eventType,
                    direction,
                    amount,
                    "PKR",
                    amount, // baseAmountPkr = amount for PKR
                    effectiveDate,
                    trimmedDescription,
                    context.userId(),
                    request.counterpartyId()
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
                    effectiveDate
            );
            movement = movementRepository.saveAndFlush(movement);

            // 3. Compute resulting balance as of today
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

            MovementDetailResponse movementDto = new MovementDetailResponse(
                    movement.getId(),
                    movement.getAccountId(),
                    movement.getMovementDirection().name(),
                    movement.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                    movement.getCurrency(),
                    movement.getEffectiveDate(),
                    ISO_INSTANT_FORMATTER.format(movement.getCreatedAt().truncatedTo(ChronoUnit.SECONDS))
            );

            TransactionDetailResponse response = new TransactionDetailResponse(
                    tx.getId(),
                    tx.getBusinessId(),
                    tx.getAccountId(),
                    account.getAccountName(),
                    account.getAccountType().name(),
                    tx.getEventType().name(),
                    tx.getMovementDirection().name(),
                    tx.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                    tx.getCurrency(),
                    tx.getBaseAmountPkr().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                    tx.getEffectiveDate(),
                    tx.getDescription(),
                    resultingBalance,
                    Collections.singletonList(movementDto),
                    tx.getCreatedByUserId(),
                    ISO_INSTANT_FORMATTER.format(tx.getCreatedAt().truncatedTo(ChronoUnit.SECONDS)),
                    null,
                    null,
                    null,
                    null,
                    false,
                    null,
                    false,
                    null,
                    null,
                    counterparty != null ? counterparty.getId() : null,
                    counterparty != null ? counterparty.getDisplayName() : null,
                    counterparty != null ? counterparty.getType().name() : null
            );

            // 4. Save mandatory SecurityAuditEvent atomically
            SecurityAuditEvent auditEvent = new SecurityAuditEvent();
            auditEvent.setEventType("FINANCIAL_TRANSACTION_POSTED");
            auditEvent.setActorUserId(context.userId());
            auditEvent.setBusinessId(context.businessId());
            auditEvent.setTargetType("FINANCIAL_TRANSACTION");
            auditEvent.setTargetId(tx.getId().toString());
            auditEvent.setOutcome(AuditOutcome.SUCCESS);
            auditEvent.setMetadata("{\"accountId\":\"" + account.getId() + "\",\"eventType\":\"" + eventType.name() + "\",\"amount\":\"" + amount.toPlainString() + "\"}");
            securityAuditRepository.saveAndFlush(auditEvent);

            // 5. Save Idempotency record atomically
            String responseJson = serializeResponse(response);
            FinancialIdempotencyRecord idempotencyRecord = new FinancialIdempotencyRecord(
                    context.businessId(),
                    OPERATION_TRANSACTION_POST,
                    trimmedKey,
                    payloadHash,
                    tx.getId(),
                    201,
                    responseJson
            );
            idempotencyRepository.saveAndFlush(idempotencyRecord);

            return response;
        } catch (DataIntegrityViolationException e) {
            // Concurrent race handling
            Optional<FinancialIdempotencyRecord> racedIdempotency = idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(
                    context.businessId(),
                    OPERATION_TRANSACTION_POST,
                    trimmedKey
            );
            if (racedIdempotency.isPresent()) {
                FinancialIdempotencyRecord record = racedIdempotency.get();
                if (record.getRequestHash().equals(payloadHash)) {
                    return deserializeResponse(record.getResponseBody());
                } else {
                    throw new DuplicateResourceException(
                            "Idempotency key '" + trimmedKey + "' has already been used with a different request payload"
                    );
                }
            }
            throw e;
        }
    }

    public static String computePayloadHash(
            UUID accountId,
            TransactionEventType eventType,
            BigDecimal amount,
            LocalDate effectiveDate,
            String description
    ) {
        return computePayloadHash(accountId, eventType, amount, effectiveDate, description, null);
    }

    public static String computePayloadHash(
            UUID accountId,
            TransactionEventType eventType,
            BigDecimal amount,
            LocalDate effectiveDate,
            String description,
            UUID counterpartyId
    ) {
        String canonicalAmount = amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
        String canonicalPayload = "accountId=" + accountId
                + "&eventType=" + eventType.name()
                + "&amount=" + canonicalAmount
                + "&effectiveDate=" + effectiveDate
                + "&description=" + (description != null ? description : "")
                + (counterpartyId != null ? "&counterpartyId=" + counterpartyId : "");

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(canonicalPayload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }

    private String serializeResponse(TransactionDetailResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize TransactionDetailResponse for idempotency storage", e);
        }
    }

    private TransactionDetailResponse deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, TransactionDetailResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize TransactionDetailResponse from idempotency storage", e);
        }
    }
}
