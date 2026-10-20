package com.app.sme_health_backend.accounting.service;

import com.app.sme_health_backend.accounting.dto.MovementDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionReverseRequest;
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
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class TransactionReverseService {

    public static final String OPERATION_TRANSACTION_REVERSE = "TRANSACTION_REVERSE";
    private static final ZoneId KARACHI_ZONE = ZoneId.of("Asia/Karachi");
    private static final DateTimeFormatter ISO_INSTANT_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final FinancialAccountRepository accountRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final AccountMovementRepository movementRepository;
    private final FinancialIdempotencyRepository idempotencyRepository;
    private final SecurityAuditRepository securityAuditRepository;
    private final com.app.sme_health_backend.obligation.ObligationPaymentAllocationRepository allocationRepository;
    private final com.app.sme_health_backend.obligation.CounterpartyAdvanceRepository advanceRepository;
    private final com.app.sme_health_backend.reconciliation.repository.BankReconciliationMatchRepository matchRepository;
    private final com.app.sme_health_backend.reconciliation.repository.BankStatementEntryRepository statementEntryRepository;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public TransactionReverseService(
            FinancialAccountRepository accountRepository,
            FinancialTransactionRepository transactionRepository,
            AccountMovementRepository movementRepository,
            FinancialIdempotencyRepository idempotencyRepository,
            SecurityAuditRepository securityAuditRepository,
            com.app.sme_health_backend.obligation.ObligationPaymentAllocationRepository allocationRepository,
            com.app.sme_health_backend.obligation.CounterpartyAdvanceRepository advanceRepository,
            com.app.sme_health_backend.reconciliation.repository.BankReconciliationMatchRepository matchRepository,
            com.app.sme_health_backend.reconciliation.repository.BankStatementEntryRepository statementEntryRepository,
            ObjectMapper objectMapper
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.movementRepository = movementRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.securityAuditRepository = securityAuditRepository;
        this.allocationRepository = allocationRepository;
        this.advanceRepository = advanceRepository;
        this.matchRepository = matchRepository;
        this.statementEntryRepository = statementEntryRepository;
        this.objectMapper = objectMapper;
    }

    public TransactionReverseService(
            FinancialAccountRepository accountRepository,
            FinancialTransactionRepository transactionRepository,
            AccountMovementRepository movementRepository,
            FinancialIdempotencyRepository idempotencyRepository,
            SecurityAuditRepository securityAuditRepository,
            com.app.sme_health_backend.obligation.ObligationPaymentAllocationRepository allocationRepository,
            com.app.sme_health_backend.obligation.CounterpartyAdvanceRepository advanceRepository,
            ObjectMapper objectMapper
    ) {
        this(accountRepository, transactionRepository, movementRepository, idempotencyRepository, securityAuditRepository, allocationRepository, advanceRepository, null, null, objectMapper);
    }

    public TransactionReverseService(
            FinancialAccountRepository accountRepository,
            FinancialTransactionRepository transactionRepository,
            AccountMovementRepository movementRepository,
            FinancialIdempotencyRepository idempotencyRepository,
            SecurityAuditRepository securityAuditRepository,
            com.app.sme_health_backend.obligation.ObligationPaymentAllocationRepository allocationRepository,
            ObjectMapper objectMapper
    ) {
        this(accountRepository, transactionRepository, movementRepository, idempotencyRepository, securityAuditRepository, allocationRepository, null, null, null, objectMapper);
    }

    public TransactionReverseService(
            FinancialAccountRepository accountRepository,
            FinancialTransactionRepository transactionRepository,
            AccountMovementRepository movementRepository,
            FinancialIdempotencyRepository idempotencyRepository,
            SecurityAuditRepository securityAuditRepository,
            ObjectMapper objectMapper
    ) {
        this(accountRepository, transactionRepository, movementRepository, idempotencyRepository, securityAuditRepository, null, null, null, null, objectMapper);
    }

    @Transactional
    public TransactionDetailResponse postReversal(
            BusinessAccessContext context,
            String idempotencyKey,
            UUID transactionId,
            TransactionReverseRequest request
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new TransactionValidationException("Idempotency-Key", "Missing required header: Idempotency-Key");
        }
        String trimmedKey = idempotencyKey.trim();
        if (trimmedKey.length() > 100) {
            throw new TransactionValidationException("Idempotency-Key", "Idempotency-Key must not exceed 100 characters");
        }

        if (transactionId == null) {
            throw new TransactionValidationException("transactionId", "Transaction ID is required");
        }

        if (request == null) {
            throw new TransactionValidationException("body", "Request body is required");
        }

        Map<String, String> errors = new LinkedHashMap<>();

        // 1. Validate reason
        String trimmedReason = null;
        if (request.reason() == null || request.reason().trim().isEmpty()) {
            errors.put("reason", "Reason is required");
        } else {
            trimmedReason = request.reason().trim();
            if (trimmedReason.length() < 5 || trimmedReason.length() > 255) {
                errors.put("reason", "Reason must be between 5 and 255 characters");
            }
        }

        if (!errors.isEmpty()) {
            throw new TransactionValidationException(errors);
        }

        // 2. Compute idempotency payload hash
        String payloadHash = computePayloadHash(transactionId, trimmedReason);

        Optional<FinancialIdempotencyRecord> existingOpt = idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(
                context.businessId(),
                OPERATION_TRANSACTION_REVERSE,
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

        // 3. Lock target transaction for update
        FinancialTransaction original = transactionRepository.findForUpdateByIdAndBusinessId(transactionId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Financial transaction not found"));

        // 4. Validate original transaction status
        if (original.getReversedTransactionId() != null) {
            throw new TransactionValidationException("transactionId", "Cannot reverse a reversal transaction");
        }

        Optional<FinancialTransaction> existingReversal = transactionRepository.findByReversedTransactionIdAndBusinessId(
                original.getId(), context.businessId()
        );
        if (existingReversal.isPresent()) {
            throw new DuplicateResourceException("Transaction has already been reversed");
        }

        LocalDate today = LocalDate.now(KARACHI_ZONE);
        FinancialTransaction reversalTx;
        List<AccountMovement> createdMovements = new ArrayList<>();
        FinancialAccount primaryAccount;
        String resultingBalance;

        // 5. Account locking, validation & chronological simulation by event type
        if (original.getEventType() == TransactionEventType.INTERNAL_TRANSFER) {
            UUID srcId = original.getAccountId();
            UUID dstId = original.getDestinationAccountId();

            // Deterministic lock ordering: sort UUIDs to prevent deadlock
            UUID firstLockId = srcId.compareTo(dstId) < 0 ? srcId : dstId;
            UUID secondLockId = srcId.compareTo(dstId) < 0 ? dstId : srcId;

            FinancialAccount firstAccount = accountRepository.findForUpdateByIdAndBusinessId(firstLockId, context.businessId())
                    .orElseThrow(() -> new ResourceNotFoundException("Financial account not found"));
            FinancialAccount secondAccount = accountRepository.findForUpdateByIdAndBusinessId(secondLockId, context.businessId())
                    .orElseThrow(() -> new ResourceNotFoundException("Financial account not found"));

            FinancialAccount sourceAccount = srcId.equals(firstLockId) ? firstAccount : secondAccount;
            FinancialAccount destAccount = dstId.equals(firstLockId) ? firstAccount : secondAccount;

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

            if (!errors.isEmpty()) {
                throw new TransactionValidationException(errors);
            }

            // Simulate inverse movements:
            // Original transfer: source DECREASE, destination INCREASE
            // Reversal transfer: source INCREASE, destination DECREASE
            validateChronologicalSequence(sourceAccount, MovementDirection.INCREASE, original.getAmount(), original.getEffectiveDate(), context.businessId(), "source");
            validateChronologicalSequence(destAccount, MovementDirection.DECREASE, original.getAmount(), original.getEffectiveDate(), context.businessId(), "destination");

            // Persist reversal transaction
            reversalTx = new FinancialTransaction(
                    context.businessId(),
                    sourceAccount.getId(),
                    destAccount.getId(),
                    TransactionEventType.INTERNAL_TRANSFER,
                    MovementDirection.INCREASE,
                    original.getAmount(),
                    "PKR",
                    original.getBaseAmountPkr(),
                    original.getEffectiveDate(),
                    trimmedReason,
                    context.userId()
            );
            reversalTx.setReversedTransactionId(original.getId());
            reversalTx.setReversalReason(trimmedReason);
            reversalTx = transactionRepository.saveAndFlush(reversalTx);

            // Persist inverse movements
            AccountMovement srcMov = new AccountMovement(
                    context.businessId(),
                    reversalTx.getId(),
                    sourceAccount.getId(),
                    MovementDirection.INCREASE,
                    original.getAmount(),
                    "PKR",
                    original.getEffectiveDate()
            );
            srcMov = movementRepository.saveAndFlush(srcMov);
            createdMovements.add(srcMov);

            AccountMovement dstMov = new AccountMovement(
                    context.businessId(),
                    reversalTx.getId(),
                    destAccount.getId(),
                    MovementDirection.DECREASE,
                    original.getAmount(),
                    "PKR",
                    original.getEffectiveDate()
            );
            dstMov = movementRepository.saveAndFlush(dstMov);
            createdMovements.add(dstMov);

            primaryAccount = sourceAccount;
            resultingBalance = computeCurrentBalanceString(sourceAccount, today);

        } else if (original.getEventType() == TransactionEventType.DIRECT_SALE_RECEIPT) {
            primaryAccount = accountRepository.findForUpdateByIdAndBusinessId(original.getAccountId(), context.businessId())
                    .orElseThrow(() -> new ResourceNotFoundException("Financial account not found"));

            if (primaryAccount.getStatus() != AccountStatus.ACTIVE) {
                errors.put("accountId", "Account is not active");
            }
            if (!"PKR".equals(primaryAccount.getCurrency())) {
                errors.put("accountId", "Account must be a PKR account");
            }
            if (!errors.isEmpty()) {
                throw new TransactionValidationException(errors);
            }

            // Original receipt was INCREASE; reversal is DECREASE
            validateChronologicalSequence(primaryAccount, MovementDirection.DECREASE, original.getAmount(), original.getEffectiveDate(), context.businessId(), "account");

            reversalTx = new FinancialTransaction(
                    context.businessId(),
                    primaryAccount.getId(),
                    TransactionEventType.DIRECT_SALE_RECEIPT,
                    MovementDirection.DECREASE,
                    original.getAmount(),
                    "PKR",
                    original.getBaseAmountPkr(),
                    original.getEffectiveDate(),
                    trimmedReason,
                    context.userId()
            );
            reversalTx.setReversedTransactionId(original.getId());
            reversalTx.setReversalReason(trimmedReason);
            reversalTx = transactionRepository.saveAndFlush(reversalTx);

            AccountMovement mov = new AccountMovement(
                    context.businessId(),
                    reversalTx.getId(),
                    primaryAccount.getId(),
                    MovementDirection.DECREASE,
                    original.getAmount(),
                    "PKR",
                    original.getEffectiveDate()
            );
            mov = movementRepository.saveAndFlush(mov);
            createdMovements.add(mov);

            resultingBalance = computeCurrentBalanceString(primaryAccount, today);

        } else if (original.getEventType() == TransactionEventType.OPERATING_EXPENSE_PAYMENT) {
            primaryAccount = accountRepository.findForUpdateByIdAndBusinessId(original.getAccountId(), context.businessId())
                    .orElseThrow(() -> new ResourceNotFoundException("Financial account not found"));

            if (primaryAccount.getStatus() != AccountStatus.ACTIVE) {
                errors.put("accountId", "Account is not active");
            }
            if (!"PKR".equals(primaryAccount.getCurrency())) {
                errors.put("accountId", "Account must be a PKR account");
            }
            if (!errors.isEmpty()) {
                throw new TransactionValidationException(errors);
            }

            // Original expense was DECREASE; reversal is INCREASE
            validateChronologicalSequence(primaryAccount, MovementDirection.INCREASE, original.getAmount(), original.getEffectiveDate(), context.businessId(), "account");

            reversalTx = new FinancialTransaction(
                    context.businessId(),
                    primaryAccount.getId(),
                    TransactionEventType.OPERATING_EXPENSE_PAYMENT,
                    MovementDirection.INCREASE,
                    original.getAmount(),
                    "PKR",
                    original.getBaseAmountPkr(),
                    original.getEffectiveDate(),
                    trimmedReason,
                    context.userId()
            );
            reversalTx.setReversedTransactionId(original.getId());
            reversalTx.setCounterpartyId(original.getCounterpartyId());
            reversalTx = transactionRepository.saveAndFlush(reversalTx);

            AccountMovement mov = new AccountMovement(
                    context.businessId(),
                    reversalTx.getId(),
                    primaryAccount.getId(),
                    MovementDirection.INCREASE,
                    original.getAmount(),
                    "PKR",
                    original.getEffectiveDate()
            );
            mov = movementRepository.saveAndFlush(mov);
            createdMovements.add(mov);

            resultingBalance = computeCurrentBalanceString(primaryAccount, today);

        } else if (original.getEventType() == TransactionEventType.RECEIVABLE_COLLECTION) {
            primaryAccount = accountRepository.findForUpdateByIdAndBusinessId(original.getAccountId(), context.businessId())
                    .orElseThrow(() -> new ResourceNotFoundException("Financial account not found"));

            if (primaryAccount.getStatus() != AccountStatus.ACTIVE) {
                errors.put("accountId", "Account is not active");
            }
            if (!"PKR".equals(primaryAccount.getCurrency())) {
                errors.put("accountId", "Account must be a PKR account");
            }
            if (!errors.isEmpty()) {
                throw new TransactionValidationException(errors);
            }

            // Original collection was INCREASE; reversal is DECREASE
            validateChronologicalSequence(primaryAccount, MovementDirection.DECREASE, original.getAmount(), original.getEffectiveDate(), context.businessId(), "account");

            reversalTx = new FinancialTransaction(
                    context.businessId(),
                    primaryAccount.getId(),
                    TransactionEventType.RECEIVABLE_COLLECTION,
                    MovementDirection.DECREASE,
                    original.getAmount(),
                    "PKR",
                    original.getBaseAmountPkr(),
                    original.getEffectiveDate(),
                    trimmedReason,
                    context.userId()
            );
            reversalTx.setReversedTransactionId(original.getId());
            reversalTx.setReversalReason(trimmedReason);
            reversalTx.setCounterpartyId(original.getCounterpartyId());
            reversalTx = transactionRepository.saveAndFlush(reversalTx);

            AccountMovement mov = new AccountMovement(
                    context.businessId(),
                    reversalTx.getId(),
                    primaryAccount.getId(),
                    MovementDirection.DECREASE,
                    original.getAmount(),
                    "PKR",
                    original.getEffectiveDate()
            );
            mov = movementRepository.saveAndFlush(mov);
            createdMovements.add(mov);

            cancelAllocationForReversal(context.businessId(), original.getId(), reversalTx.getId(), trimmedReason);

            resultingBalance = computeCurrentBalanceString(primaryAccount, today);

        } else if (original.getEventType() == TransactionEventType.PAYABLE_SETTLEMENT) {
            primaryAccount = accountRepository.findForUpdateByIdAndBusinessId(original.getAccountId(), context.businessId())
                    .orElseThrow(() -> new ResourceNotFoundException("Financial account not found"));

            if (primaryAccount.getStatus() != AccountStatus.ACTIVE) {
                errors.put("accountId", "Account is not active");
            }
            if (!"PKR".equals(primaryAccount.getCurrency())) {
                errors.put("accountId", "Account must be a PKR account");
            }
            if (!errors.isEmpty()) {
                throw new TransactionValidationException(errors);
            }

            // Original settlement was DECREASE; reversal is INCREASE
            validateChronologicalSequence(primaryAccount, MovementDirection.INCREASE, original.getAmount(), original.getEffectiveDate(), context.businessId(), "account");

            reversalTx = new FinancialTransaction(
                    context.businessId(),
                    primaryAccount.getId(),
                    TransactionEventType.PAYABLE_SETTLEMENT,
                    MovementDirection.INCREASE,
                    original.getAmount(),
                    "PKR",
                    original.getBaseAmountPkr(),
                    original.getEffectiveDate(),
                    trimmedReason,
                    context.userId()
            );
            reversalTx.setReversedTransactionId(original.getId());
            reversalTx.setReversalReason(trimmedReason);
            reversalTx.setCounterpartyId(original.getCounterpartyId());
            reversalTx = transactionRepository.saveAndFlush(reversalTx);

            AccountMovement mov = new AccountMovement(
                    context.businessId(),
                    reversalTx.getId(),
                    primaryAccount.getId(),
                    MovementDirection.INCREASE,
                    original.getAmount(),
                    "PKR",
                    original.getEffectiveDate()
            );
            mov = movementRepository.saveAndFlush(mov);
            createdMovements.add(mov);

            cancelAllocationForReversal(context.businessId(), original.getId(), reversalTx.getId(), trimmedReason);

            resultingBalance = computeCurrentBalanceString(primaryAccount, today);

        } else {
            throw new TransactionValidationException("eventType", "Unsupported transaction event type for reversal: " + original.getEventType());
        }

        cancelReconciliationMatchesForReversal(context.businessId(), original.getId(), reversalTx.getId(), context.userId(), trimmedReason);

        // 6. Build response DTO
        List<MovementDetailResponse> movementDtos = createdMovements.stream()
                .map(m -> new MovementDetailResponse(
                        m.getId(),
                        m.getAccountId(),
                        m.getMovementDirection().name(),
                        m.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                        m.getCurrency(),
                        m.getEffectiveDate(),
                        ISO_INSTANT_FORMATTER.format(m.getCreatedAt().truncatedTo(ChronoUnit.SECONDS))
                ))
                .toList();

        UUID srcId = null;
        String srcName = null;
        UUID dstId = null;
        String dstName = null;

        if (original.getEventType() == TransactionEventType.INTERNAL_TRANSFER) {
            srcId = original.getAccountId();
            srcName = primaryAccount.getAccountName();
            dstId = original.getDestinationAccountId();
            if (dstId != null) {
                FinancialAccount dest = accountRepository.findByIdAndBusinessId(dstId, context.businessId()).orElse(null);
                dstName = dest != null ? dest.getAccountName() : null;
            }
        }

        TransactionDetailResponse response = new TransactionDetailResponse(
                reversalTx.getId(),
                reversalTx.getBusinessId(),
                primaryAccount.getId(),
                primaryAccount.getAccountName(),
                primaryAccount.getAccountType().name(),
                reversalTx.getEventType().name(),
                reversalTx.getMovementDirection().name(),
                reversalTx.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                reversalTx.getCurrency(),
                reversalTx.getBaseAmountPkr().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                reversalTx.getEffectiveDate(),
                reversalTx.getDescription(),
                resultingBalance,
                movementDtos,
                reversalTx.getCreatedByUserId(),
                ISO_INSTANT_FORMATTER.format(reversalTx.getCreatedAt().truncatedTo(ChronoUnit.SECONDS)),
                srcId,
                srcName,
                dstId,
                dstName,
                true,
                original.getId(),
                false,
                null,
                trimmedReason
        );

        // 7. Security audit event
        SecurityAuditEvent auditEvent = new SecurityAuditEvent();
        auditEvent.setEventType("FINANCIAL_TRANSACTION_REVERSED");
        auditEvent.setActorUserId(context.userId());
        auditEvent.setBusinessId(context.businessId());
        auditEvent.setTargetType("FINANCIAL_TRANSACTION");
        auditEvent.setTargetId(reversalTx.getId().toString());
        auditEvent.setOutcome(AuditOutcome.SUCCESS);
        auditEvent.setMetadata("{\"reversedTransactionId\":\"" + original.getId() + "\",\"reason\":\"" + trimmedReason + "\"}");
        securityAuditRepository.saveAndFlush(auditEvent);

        // 8. Durable idempotency record
        String responseJson = serializeResponse(response);
        FinancialIdempotencyRecord idempotencyRecord = new FinancialIdempotencyRecord(
                context.businessId(),
                OPERATION_TRANSACTION_REVERSE,
                trimmedKey,
                payloadHash,
                reversalTx.getId(),
                201,
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
                        "Reversal would cause cash account balance to become negative on " + m.getEffectiveDate()
                );
            }

            if (running.abs().compareTo(CurrencyRegistry.MAX_CAPACITY) >= 0) {
                throw new TransactionValidationException(
                        "amount",
                        "Reversal would cause " + accountRole + " account balance to exceed maximum capacity of 999,999,999,999.99"
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

    public static String computePayloadHash(UUID transactionId, String reason) {
        String canonical = (transactionId != null ? transactionId.toString() : "") + "|" +
                (reason != null ? reason : "");
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

    private void cancelAllocationForReversal(UUID businessId, UUID originalTxId, UUID reversalTxId, String reason) {
        if (advanceRepository != null) {
            advanceRepository.findForUpdateByBusinessIdAndFinancialTransactionId(businessId, originalTxId)
                    .ifPresent(advance -> {
                        if (allocationRepository != null) {
                            BigDecimal applied = allocationRepository.sumEffectiveAllocationsByAdvanceId(businessId, advance.getId());
                            if (applied != null && applied.compareTo(BigDecimal.ZERO) > 0) {
                                throw new TransactionValidationException(
                                        "advance",
                                        "Cannot reverse payment: unapplied advance has active downstream applications. Reverse applications first."
                                );
                            }
                        }
                        advance.setStatus(com.app.sme_health_backend.obligation.AdvanceStatus.CANCELLED);
                        advance.setCancelledAt(Instant.now());
                        advance.setCancellationReason(reason);
                        advance.setReversalTransactionId(reversalTxId);
                        advanceRepository.saveAndFlush(advance);
                    });
        }
        if (allocationRepository != null) {
            List<com.app.sme_health_backend.obligation.ObligationPaymentAllocation> allocations =
                    allocationRepository.findAllByBusinessIdAndFinancialTransactionId(businessId, originalTxId);
            for (var allocation : allocations) {
                allocation.setStatus(com.app.sme_health_backend.obligation.AllocationStatus.CANCELLED);
                allocation.setCancelledAt(Instant.now());
                allocation.setCancellationReason(reason);
                allocation.setReversalTransactionId(reversalTxId);
                allocationRepository.saveAndFlush(allocation);
            }
        }
    }

    private void cancelReconciliationMatchesForReversal(UUID businessId, UUID originalTxId, UUID reversalTxId, UUID userId, String reason) {
        if (matchRepository == null || statementEntryRepository == null) {
            return;
        }
        List<AccountMovement> movements = movementRepository.findByTransactionIdOrderByCreatedAtAscIdAsc(originalTxId);
        for (AccountMovement mov : movements) {
            matchRepository.findByBusinessIdAndAccountMovementIdAndStatus(
                    businessId,
                    mov.getId(),
                    com.app.sme_health_backend.reconciliation.model.ReconciliationMatchStatus.ACTIVE
            ).ifPresent(match -> {
                match.setStatus(com.app.sme_health_backend.reconciliation.model.ReconciliationMatchStatus.CANCELLED);
                match.setUnmatchedAt(Instant.now());
                match.setUnmatchedByUserId(userId);
                String auditMsg = "Automatically cancelled due to transaction reversal: " + reversalTxId;
                if (reason != null && !reason.isBlank()) {
                    auditMsg += " (" + reason.trim() + ")";
                }
                match.setUnmatchReason(auditMsg);
                matchRepository.saveAndFlush(match);

                statementEntryRepository.findByIdAndBusinessId(match.getStatementEntryId(), businessId)
                        .ifPresent(entry -> {
                            entry.setStatus(com.app.sme_health_backend.reconciliation.model.StatementEntryStatus.UNMATCHED);
                            statementEntryRepository.saveAndFlush(entry);
                        });
            });
        }
    }
}
