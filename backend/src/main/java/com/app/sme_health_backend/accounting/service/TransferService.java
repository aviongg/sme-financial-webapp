package com.app.sme_health_backend.accounting.service;

import com.app.sme_health_backend.accounting.model.CurrencyRegistry;
import com.app.sme_health_backend.accounting.dto.MovementDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransferPostRequest;
import com.app.sme_health_backend.accounting.entity.AccountMovement;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialIdempotencyRecord;
import com.app.sme_health_backend.accounting.entity.FinancialTransaction;
import com.app.sme_health_backend.accounting.exception.TransactionValidationException;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.AccountMovementRepository;
import com.app.sme_health_backend.accounting.repository.AccountMovementSum;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialIdempotencyRepository;
import com.app.sme_health_backend.accounting.repository.FinancialTransactionRepository;
import com.app.sme_health_backend.accounting.util.CanonicalAmountValidator;
import com.app.sme_health_backend.audit.entity.SecurityAuditEvent;
import com.app.sme_health_backend.audit.model.AuditOutcome;
import com.app.sme_health_backend.audit.repository.SecurityAuditRepository;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import tools.jackson.databind.ObjectMapper;
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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing atomic, idempotent funds transfers between accounts.
 */
@Service
public class TransferService {

    public static final String OPERATION_FINANCIAL_TRANSFER = "FINANCIAL_TRANSFER";
    private static final ZoneId KARACHI_ZONE = ZoneId.of("Asia/Karachi");
    private static final DateTimeFormatter ISO_INSTANT_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final FinancialAccountRepository accountRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final AccountMovementRepository movementRepository;
    private final FinancialIdempotencyRepository idempotencyRepository;
    private final SecurityAuditRepository securityAuditRepository;
    private final ObjectMapper objectMapper;

    public TransferService(
            FinancialAccountRepository accountRepository,
            FinancialTransactionRepository transactionRepository,
            AccountMovementRepository movementRepository,
            FinancialIdempotencyRepository idempotencyRepository,
            SecurityAuditRepository securityAuditRepository,
            ObjectMapper objectMapper
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.movementRepository = movementRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.securityAuditRepository = securityAuditRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TransactionDetailResponse postTransfer(
            BusinessAccessContext context,
            String idempotencyKey,
            TransferPostRequest request
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

        // 1. Validate accounts presence and distinctness
        if (request.sourceAccountId() == null) {
            errors.put("sourceAccountId", "Source account ID is required");
        }
        if (request.destinationAccountId() == null) {
            errors.put("destinationAccountId", "Destination account ID is required");
        }
        if (request.sourceAccountId() != null && request.destinationAccountId() != null
                && request.sourceAccountId().equals(request.destinationAccountId())) {
            errors.put("destinationAccountId", "Source and destination accounts must be distinct");
        }

        // 2. Validate amount before trimming or numeric parsing
        BigDecimal amount = null;
        try {
            amount = CanonicalAmountValidator.validateCanonicalAmount(request.amount(), "amount");
        } catch (TransactionValidationException ex) {
            errors.putAll(ex.getErrors());
        }

        // 3. Validate effective date
        LocalDate effectiveDate = null;
        if (request.effectiveDate() == null || request.effectiveDate().trim().isEmpty()) {
            errors.put("effectiveDate", "Effective date is required");
        } else {
            try {
                effectiveDate = LocalDate.parse(request.effectiveDate().trim());
                LocalDate today = LocalDate.now(KARACHI_ZONE);
                if (effectiveDate.isAfter(today)) {
                    errors.put("effectiveDate", "Effective date cannot be in the future (Asia/Karachi)");
                }
            } catch (DateTimeParseException e) {
                errors.put("effectiveDate", "Invalid date format for effectiveDate. Expected YYYY-MM-DD");
            }
        }

        // 4. Validate description length
        String trimmedDescription = (request.description() != null && !request.description().trim().isEmpty())
                ? request.description().trim()
                : null;
        if (trimmedDescription != null && trimmedDescription.length() > 255) {
            errors.put("description", "Description must not exceed 255 characters");
        }

        if (!errors.isEmpty()) {
            throw new TransactionValidationException(errors);
        }

        // 5. Deterministic Locking Order: Sort UUIDs to prevent concurrency deadlocks
        UUID firstLockId = request.sourceAccountId().compareTo(request.destinationAccountId()) < 0
                ? request.sourceAccountId()
                : request.destinationAccountId();
        UUID secondLockId = request.sourceAccountId().compareTo(request.destinationAccountId()) < 0
                ? request.destinationAccountId()
                : request.sourceAccountId();

        FinancialAccount firstAccount = accountRepository.findForUpdateByIdAndBusinessId(firstLockId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Financial account not found"));
        FinancialAccount secondAccount = accountRepository.findForUpdateByIdAndBusinessId(secondLockId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Financial account not found"));

        FinancialAccount sourceAccount = request.sourceAccountId().equals(firstLockId) ? firstAccount : secondAccount;
        FinancialAccount destAccount = request.destinationAccountId().equals(firstLockId) ? firstAccount : secondAccount;

        // 6. Validate accounts state, currency, and opening dates
        if (sourceAccount.getStatus() != AccountStatus.ACTIVE) {
            errors.put("sourceAccountId", "Source account is not active");
        }
        if (destAccount.getStatus() != AccountStatus.ACTIVE) {
            errors.put("destinationAccountId", "Destination account is not active");
        }
        if (!"PKR".equals(sourceAccount.getCurrency())) {
            errors.put("sourceAccountId", "Source account must be a PKR account");
        }
        if (!"PKR".equals(destAccount.getCurrency())) {
            errors.put("destinationAccountId", "Destination account must be a PKR account");
        }
        if (effectiveDate != null) {
            if (effectiveDate.isBefore(sourceAccount.getOpeningDate())) {
                errors.put("effectiveDate", "Effective date cannot precede source account opening date (" + sourceAccount.getOpeningDate() + ")");
            }
            if (effectiveDate.isBefore(destAccount.getOpeningDate())) {
                errors.put("effectiveDate", "Effective date cannot precede destination account opening date (" + destAccount.getOpeningDate() + ")");
            }
        }

        if (!errors.isEmpty()) {
            throw new TransactionValidationException(errors);
        }

        // 7. Check Idempotency Record
        String payloadHash = computePayloadHash(
                sourceAccount.getId(),
                destAccount.getId(),
                amount,
                effectiveDate,
                trimmedDescription
        );

        Optional<FinancialIdempotencyRecord> existingOpt = idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(
                context.businessId(),
                OPERATION_FINANCIAL_TRANSFER,
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

        // 8. Validate Full Affected Chronological Balance Sequences
        validateChronologicalSequence(sourceAccount, MovementDirection.DECREASE, amount, effectiveDate, context.businessId(), "source");
        validateChronologicalSequence(destAccount, MovementDirection.INCREASE, amount, effectiveDate, context.businessId(), "destination");

        // 9. Atomic Persistence
        // 9a. Persist FinancialTransaction
        FinancialTransaction tx = new FinancialTransaction(
                context.businessId(),
                sourceAccount.getId(),
                destAccount.getId(),
                TransactionEventType.INTERNAL_TRANSFER,
                MovementDirection.DECREASE,
                amount,
                "PKR",
                amount, // baseAmountPkr = amount for PKR
                effectiveDate,
                trimmedDescription,
                context.userId()
        );
        tx = transactionRepository.saveAndFlush(tx);

        // 9b. Persist Source Account Movement (DECREASE)
        AccountMovement sourceMovement = new AccountMovement(
                context.businessId(),
                tx.getId(),
                sourceAccount.getId(),
                MovementDirection.DECREASE,
                amount,
                "PKR",
                effectiveDate
        );
        sourceMovement = movementRepository.saveAndFlush(sourceMovement);

        // 9c. Persist Destination Account Movement (INCREASE)
        AccountMovement destMovement = new AccountMovement(
                context.businessId(),
                tx.getId(),
                destAccount.getId(),
                MovementDirection.INCREASE,
                amount,
                "PKR",
                effectiveDate
        );
        destMovement = movementRepository.saveAndFlush(destMovement);

        // 9d. Compute resulting balance of source account as of today
        LocalDate today = LocalDate.now(KARACHI_ZONE);
        String sourceResultingBalance = computeCurrentBalanceString(sourceAccount, today);

        List<MovementDetailResponse> movements = List.of(
                new MovementDetailResponse(
                        sourceMovement.getId(),
                        sourceMovement.getAccountId(),
                        sourceMovement.getMovementDirection().name(),
                        sourceMovement.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                        sourceMovement.getCurrency(),
                        sourceMovement.getEffectiveDate(),
                        ISO_INSTANT_FORMATTER.format(sourceMovement.getCreatedAt().truncatedTo(ChronoUnit.SECONDS))
                ),
                new MovementDetailResponse(
                        destMovement.getId(),
                        destMovement.getAccountId(),
                        destMovement.getMovementDirection().name(),
                        destMovement.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                        destMovement.getCurrency(),
                        destMovement.getEffectiveDate(),
                        ISO_INSTANT_FORMATTER.format(destMovement.getCreatedAt().truncatedTo(ChronoUnit.SECONDS))
                )
        );

        TransactionDetailResponse response = new TransactionDetailResponse(
                tx.getId(),
                tx.getBusinessId(),
                sourceAccount.getId(),
                sourceAccount.getAccountName(),
                sourceAccount.getAccountType().name(),
                tx.getEventType().name(),
                tx.getMovementDirection().name(),
                tx.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                tx.getCurrency(),
                tx.getBaseAmountPkr().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                tx.getEffectiveDate(),
                tx.getDescription(),
                sourceResultingBalance,
                movements,
                tx.getCreatedByUserId(),
                ISO_INSTANT_FORMATTER.format(tx.getCreatedAt().truncatedTo(ChronoUnit.SECONDS)),
                sourceAccount.getId(),
                sourceAccount.getAccountName(),
                destAccount.getId(),
                destAccount.getAccountName()
        );

        // 9e. Save SecurityAuditEvent atomically
        SecurityAuditEvent auditEvent = new SecurityAuditEvent();
        auditEvent.setEventType("FINANCIAL_TRANSFER_POSTED");
        auditEvent.setActorUserId(context.userId());
        auditEvent.setBusinessId(context.businessId());
        auditEvent.setTargetType("FINANCIAL_TRANSACTION");
        auditEvent.setTargetId(tx.getId().toString());
        auditEvent.setOutcome(AuditOutcome.SUCCESS);
        auditEvent.setMetadata("{\"sourceAccountId\":\"" + sourceAccount.getId() + "\",\"destinationAccountId\":\"" + destAccount.getId() + "\",\"amount\":\"" + amount.toPlainString() + "\"}");
        securityAuditRepository.saveAndFlush(auditEvent);

        // 9f. Save Idempotency Record atomically
        String responseJson = serializeResponse(response);
        FinancialIdempotencyRecord idempotencyRecord = new FinancialIdempotencyRecord(
                context.businessId(),
                OPERATION_FINANCIAL_TRANSFER,
                trimmedKey,
                payloadHash,
                tx.getId(),
                HttpStatus.CREATED.value(),
                responseJson
        );
        idempotencyRepository.saveAndFlush(idempotencyRecord);

        return response;
    }

    private void validateChronologicalSequence(
            FinancialAccount account,
            MovementDirection direction,
            BigDecimal amount,
            LocalDate effectiveDate,
            UUID businessId,
            String accountRole
    ) {
        List<AccountMovement> existingMovements = movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(account.getId());
        List<AccountMovement> simulated = new ArrayList<>(existingMovements);
        AccountMovement proposed = new AccountMovement(
                businessId,
                null,
                account.getId(),
                direction,
                amount,
                "PKR",
                effectiveDate
        );
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
                        "Transfer would cause cash account balance to become negative on " + m.getEffectiveDate()
                );
            }

            if (running.abs().compareTo(CurrencyRegistry.MAX_CAPACITY) >= 0) {
                throw new TransactionValidationException(
                        "amount",
                        "Transfer would cause " + accountRole + " account balance to exceed maximum capacity of 999,999,999,999.99"
                );
            }
        }
    }

    private String computeCurrentBalanceString(FinancialAccount account, LocalDate cutoffDate) {
        List<AccountMovementSum> sums = movementRepository.findMovementSumsByAccountIdAndCutoffDate(account.getId(), cutoffDate);
        BigDecimal totalIncrease = BigDecimal.ZERO;
        BigDecimal totalDecrease = BigDecimal.ZERO;
        for (AccountMovementSum sum : sums) {
            if (sum.getMovementDirection() == MovementDirection.INCREASE) {
                totalIncrease = totalIncrease.add(sum.getTotalAmount());
            } else if (sum.getMovementDirection() == MovementDirection.DECREASE) {
                totalDecrease = totalDecrease.add(sum.getTotalAmount());
            }
        }
        BigDecimal current = account.getOpeningBalance().add(totalIncrease).subtract(totalDecrease);
        return current.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    public static String computePayloadHash(
            UUID sourceAccountId,
            UUID destinationAccountId,
            BigDecimal amount,
            LocalDate effectiveDate,
            String description
    ) {
        String canonical = (sourceAccountId != null ? sourceAccountId.toString() : "") + "|" +
                (destinationAccountId != null ? destinationAccountId.toString() : "") + "|" +
                (amount != null ? amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString() : "") + "|" +
                (effectiveDate != null ? effectiveDate.toString() : "") + "|" +
                (description != null ? description : "");

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String serializeResponse(TransactionDetailResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize transaction detail response", e);
        }
    }

    private TransactionDetailResponse deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, TransactionDetailResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize transaction detail response", e);
        }
    }
}
