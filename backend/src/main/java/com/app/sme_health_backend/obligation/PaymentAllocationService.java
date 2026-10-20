package com.app.sme_health_backend.obligation;

import com.app.sme_health_backend.accounting.entity.AccountMovement;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialIdempotencyRecord;
import com.app.sme_health_backend.accounting.entity.FinancialTransaction;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.AccountMovementRepository;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialIdempotencyRepository;
import com.app.sme_health_backend.accounting.repository.FinancialTransactionRepository;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.obligation.aging.ObligationDirection;
import com.app.sme_health_backend.obligation.dto.*;
import com.app.sme_health_backend.accounting.model.CurrencyRegistry;
import com.app.sme_health_backend.audit.repository.SecurityAuditRepository;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PaymentAllocationService {

    public static final String OPERATION_PAYMENT_ALLOCATE = "PAYMENT_ALLOCATE";
    private static final ZoneId KARACHI_ZONE = ZoneId.of("Asia/Karachi");
    private static final DateTimeFormatter ISO_INSTANT_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final BigDecimal MAX_CAPACITY = new BigDecimal("999999999999.99");
    private static final Pattern EXACT_TWO_DECIMAL_AMOUNT = Pattern.compile("^(?:0|[1-9]\\d*)\\.\\d{2}$");
    private static final int MAX_ALLOCATIONS_COUNT = 50;

    private final FinancialObligationRepository obligationRepository;
    private final ObligationPaymentAllocationRepository allocationRepository;
    private final CounterpartyAdvanceRepository advanceRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final FinancialAccountRepository accountRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final AccountMovementRepository movementRepository;
    private final FinancialIdempotencyRepository idempotencyRepository;
    private final SecurityAuditRepository securityAuditRepository;
    private final ObjectMapper objectMapper;

    public PaymentAllocationService(
            FinancialObligationRepository obligationRepository,
            ObligationPaymentAllocationRepository allocationRepository,
            CounterpartyAdvanceRepository advanceRepository,
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
        this.advanceRepository = advanceRepository;
        this.counterpartyRepository = counterpartyRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.movementRepository = movementRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.securityAuditRepository = securityAuditRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentAllocateResponse allocatePayment(
            BusinessAccessContext context,
            String idempotencyKey,
            PaymentAllocateRequest request
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

        // 1. Compute payload hash for durable idempotency
        String payloadHash = computePayloadHash(request);
        Optional<FinancialIdempotencyRecord> existingOpt = idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(
                context.businessId(),
                OPERATION_PAYMENT_ALLOCATE,
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

        // 2. Validate counterparty
        if (request.counterpartyId() == null) {
            throw new ObligationValidationException("counterpartyId", "Counterparty ID is required");
        }
        Counterparty counterparty = counterpartyRepository.findByIdAndBusinessId(request.counterpartyId(), context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Counterparty not found"));

        // 3. Validate direction
        if (request.direction() == null || request.direction().isBlank()) {
            throw new ObligationValidationException("direction", "Direction is required. Must be RECEIVABLE or PAYABLE");
        }
        ObligationDirection direction;
        try {
            direction = ObligationDirection.valueOf(request.direction().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ObligationValidationException("direction", "Invalid direction. Expected RECEIVABLE or PAYABLE");
        }

        // 4. Validate amount
        if (request.amount() == null || request.amount().isBlank()) {
            throw new ObligationValidationException("amount", "Payment amount is required");
        }
        String trimmedAmount = request.amount().trim();
        if (!EXACT_TWO_DECIMAL_AMOUNT.matcher(trimmedAmount).matches()) {
            throw new ObligationValidationException("amount", "Amount must have exactly 2 decimal places (e.g. 100.00)");
        }
        BigDecimal paymentAmount = new BigDecimal(trimmedAmount);
        if (paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ObligationValidationException("amount", "Payment amount must be strictly positive");
        }
        if (paymentAmount.compareTo(MAX_CAPACITY) > 0) {
            throw new ObligationValidationException("amount", "Payment amount exceeds maximum capacity of 999,999,999,999.99");
        }

        // 5. Validate account ID
        if (request.accountId() == null) {
            throw new ObligationValidationException("accountId", "Account ID is required");
        }

        // 6. Validate payment date
        if (request.paymentDate() == null || request.paymentDate().isBlank()) {
            throw new ObligationValidationException("paymentDate", "Payment date is required");
        }
        LocalDate paymentDate;
        try {
            paymentDate = LocalDate.parse(request.paymentDate().trim());
        } catch (DateTimeParseException e) {
            throw new ObligationValidationException("paymentDate", "Invalid paymentDate format. Expected YYYY-MM-DD");
        }

        // 7. Validate allocations list
        List<AllocationItemRequest> allocationItems = request.allocations() != null ? request.allocations() : Collections.emptyList();
        if (allocationItems.size() > MAX_ALLOCATIONS_COUNT) {
            throw new ObligationValidationException("allocations", "Cannot allocate to more than " + MAX_ALLOCATIONS_COUNT + " obligations in a single payment");
        }

        Set<UUID> seenObligationIds = new HashSet<>();
        BigDecimal totalAllocated = BigDecimal.ZERO;
        Map<UUID, BigDecimal> itemAmountMap = new LinkedHashMap<>();

        for (int i = 0; i < allocationItems.size(); i++) {
            AllocationItemRequest item = allocationItems.get(i);
            if (item == null || item.obligationId() == null) {
                throw new ObligationValidationException("allocations[" + i + "].obligationId", "Obligation ID is required in allocation item");
            }
            if (!seenObligationIds.add(item.obligationId())) {
                throw new ObligationValidationException("allocations[" + i + "].obligationId", "Duplicate obligation ID in allocation list: " + item.obligationId());
            }
            if (item.amount() == null || item.amount().isBlank()) {
                throw new ObligationValidationException("allocations[" + i + "].amount", "Allocation amount is required");
            }
            String itemAmtStr = item.amount().trim();
            if (!EXACT_TWO_DECIMAL_AMOUNT.matcher(itemAmtStr).matches()) {
                throw new ObligationValidationException("allocations[" + i + "].amount", "Allocation amount must have exactly 2 decimal places");
            }
            BigDecimal itemAmount = new BigDecimal(itemAmtStr);
            if (itemAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ObligationValidationException("allocations[" + i + "].amount", "Allocation amount must be strictly positive");
            }
            totalAllocated = totalAllocated.add(itemAmount);
            itemAmountMap.put(item.obligationId(), itemAmount);
        }

        if (totalAllocated.compareTo(paymentAmount) > 0) {
            throw new ObligationValidationException(
                    "amount",
                    "Total allocated amount (" + totalAllocated.setScale(2, RoundingMode.UNNECESSARY).toPlainString() +
                            ") cannot exceed payment amount (" + paymentAmount.setScale(2, RoundingMode.UNNECESSARY).toPlainString() + ")"
            );
        }

        BigDecimal residualAdvance = paymentAmount.subtract(totalAllocated);
        boolean retainAdvance = Boolean.TRUE.equals(request.retainAdvance());
        if (residualAdvance.compareTo(BigDecimal.ZERO) > 0 && !retainAdvance) {
            throw new ObligationValidationException(
                    "retainAdvance",
                    "Payment amount (" + paymentAmount.setScale(2, RoundingMode.UNNECESSARY).toPlainString() +
                            ") exceeds total allocations (" + totalAllocated.setScale(2, RoundingMode.UNNECESSARY).toPlainString() +
                            ") by PKR " + residualAdvance.setScale(2, RoundingMode.UNNECESSARY).toPlainString() +
                            ". Please confirm retaining this residual as an unapplied advance (retainAdvance: true) or adjust the payment amount."
            );
        }

        // 8. Deterministic Locking:
        // Sort obligation IDs to prevent database deadlocks across concurrent payments
        List<UUID> sortedObligationIds = new ArrayList<>(itemAmountMap.keySet());
        sortedObligationIds.sort(UUID::compareTo);

        Map<UUID, FinancialObligation> lockedObligations = new LinkedHashMap<>();
        for (UUID obId : sortedObligationIds) {
            FinancialObligation ob = obligationRepository.findForUpdateByIdAndBusinessId(obId, context.businessId())
                    .orElseThrow(() -> new ResourceNotFoundException("Obligation not found: " + obId));

            if (ob.getStatus() != ObligationStatus.OPEN) {
                throw new ObligationConflictException("Obligation " + ob.getReferenceNumber() + " is voided and cannot accept payment");
            }
            if (ob.getDirection() != direction) {
                throw new ObligationConflictException("Obligation " + ob.getReferenceNumber() + " direction (" + ob.getDirection() + ") does not match payment direction (" + direction + ")");
            }
            if (!ob.getCounterpartyId().equals(counterparty.getId())) {
                throw new ObligationConflictException("Obligation " + ob.getReferenceNumber() + " belongs to a different counterparty");
            }
            if (ob.getIssueDate().isAfter(paymentDate)) {
                throw new ObligationValidationException("paymentDate", "Payment date (" + paymentDate + ") cannot precede issue date (" + ob.getIssueDate() + ") of obligation " + ob.getReferenceNumber());
            }

            BigDecimal paidSoFar = allocationRepository.sumEffectiveAllocations(context.businessId(), ob.getId());
            BigDecimal outstanding = ob.getOriginalAmount().subtract(paidSoFar);
            BigDecimal toAllocate = itemAmountMap.get(obId);

            if (toAllocate.compareTo(outstanding) > 0) {
                throw new ObligationConflictException(
                        "Allocation amount (" + toAllocate.setScale(2, RoundingMode.UNNECESSARY).toPlainString() +
                                ") exceeds remaining outstanding balance (" + outstanding.setScale(2, RoundingMode.UNNECESSARY).toPlainString() +
                                ") for obligation " + (ob.getReferenceNumber() != null ? ob.getReferenceNumber() : ob.getId())
                );
            }
            lockedObligations.put(obId, ob);
        }

        // Lock primary account
        FinancialAccount account = accountRepository.findForUpdateByIdAndBusinessId(request.accountId(), context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Financial account not found"));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ObligationValidationException("accountId", "Financial account is not active");
        }
        if (!"PKR".equals(account.getCurrency())) {
            throw new ObligationValidationException("accountId", "Financial account must be a PKR account");
        }
        if (paymentDate.isBefore(account.getOpeningDate())) {
            throw new ObligationValidationException("paymentDate", "Payment date cannot precede account opening date (" + account.getOpeningDate() + ")");
        }

        MovementDirection cashDirection = (direction == ObligationDirection.RECEIVABLE)
                ? MovementDirection.INCREASE
                : MovementDirection.DECREASE;

        TransactionEventType eventType = (direction == ObligationDirection.RECEIVABLE)
                ? TransactionEventType.RECEIVABLE_COLLECTION
                : TransactionEventType.PAYABLE_SETTLEMENT;

        validateChronologicalSequence(account, cashDirection, paymentAmount, paymentDate, context.businessId());

        // 9. Persist FinancialTransaction
        String trimmedRef = request.referenceNumber() != null && !request.referenceNumber().isBlank()
                ? request.referenceNumber().trim()
                : null;
        String desc = request.description() != null && !request.description().isBlank()
                ? request.description().trim()
                : (direction == ObligationDirection.RECEIVABLE
                ? "Customer payment collection" + (trimmedRef != null ? " (" + trimmedRef + ")" : "")
                : "Supplier payment settlement" + (trimmedRef != null ? " (" + trimmedRef + ")" : ""));

        FinancialTransaction tx = new FinancialTransaction(
                context.businessId(),
                account.getId(),
                eventType,
                cashDirection,
                paymentAmount,
                "PKR",
                paymentAmount,
                paymentDate,
                desc,
                context.userId()
        );
        tx.setCounterpartyId(counterparty.getId());
        tx = transactionRepository.saveAndFlush(tx);

        // 10. Persist AccountMovement (Exactly ONE movement for external cash event)
        AccountMovement movement = new AccountMovement(
                context.businessId(),
                tx.getId(),
                account.getId(),
                cashDirection,
                paymentAmount,
                "PKR",
                paymentDate
        );
        movementRepository.saveAndFlush(movement);

        // 11. Persist Allocations
        List<AllocatedObligationItemResponse> allocatedItemResponses = new ArrayList<>();
        for (AllocationItemRequest item : allocationItems) {
            FinancialObligation ob = lockedObligations.get(item.obligationId());
            BigDecimal allocAmount = itemAmountMap.get(item.obligationId());

            ObligationPaymentAllocation alloc = new ObligationPaymentAllocation(
                    context.businessId(),
                    ob.getId(),
                    tx.getId(),
                    allocAmount,
                    paymentDate,
                    context.userId()
            );
            alloc = allocationRepository.saveAndFlush(alloc);

            BigDecimal paidAfter = allocationRepository.sumEffectiveAllocations(context.businessId(), ob.getId());
            BigDecimal remainingOut = ob.getOriginalAmount().subtract(paidAfter);
            if (remainingOut.compareTo(BigDecimal.ZERO) < 0) remainingOut = BigDecimal.ZERO;

            String settlementStatus = (remainingOut.compareTo(BigDecimal.ZERO) == 0)
                    ? "FULLY_PAID"
                    : (paidAfter.compareTo(BigDecimal.ZERO) > 0 ? "PARTIALLY_PAID" : "UNPAID");

            allocatedItemResponses.add(new AllocatedObligationItemResponse(
                    alloc.getId(),
                    ob.getId(),
                    ob.getReferenceNumber(),
                    allocAmount.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                    remainingOut.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                    settlementStatus
            ));
        }

        // 12. Persist Unapplied Advance if residual > 0
        UUID advanceId = null;
        if (residualAdvance.compareTo(BigDecimal.ZERO) > 0) {
            CounterpartyAdvance advance = new CounterpartyAdvance(
                    context.businessId(),
                    counterparty.getId(),
                    direction,
                    tx.getId(),
                    residualAdvance,
                    paymentDate,
                    trimmedRef != null ? trimmedRef : "ADV-" + tx.getId().toString().substring(0, 8).toUpperCase(Locale.ROOT),
                    desc,
                    context.userId()
            );
            advance = advanceRepository.saveAndFlush(advance);
            advanceId = advance.getId();
        }

        LocalDate today = LocalDate.now(KARACHI_ZONE);
        String resultingBalance = computeCurrentBalanceString(account, today);

        PaymentAllocateResponse response = new PaymentAllocateResponse(
                tx.getId(),
                tx.getId(),
                counterparty.getId(),
                counterparty.getDisplayName(),
                account.getId(),
                account.getAccountName(),
                paymentDate,
                paymentAmount.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                totalAllocated.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                residualAdvance.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                advanceId,
                resultingBalance,
                allocatedItemResponses,
                "COMPLETED"
        );

        // Save idempotency record
        String responseJson = serializeResponse(response);
        FinancialIdempotencyRecord idempotencyRecord = new FinancialIdempotencyRecord(
                context.businessId(),
                OPERATION_PAYMENT_ALLOCATE,
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
    public PaymentDetailResponse getPayment(BusinessAccessContext context, UUID paymentId) {
        FinancialTransaction tx = transactionRepository.findByIdAndBusinessId(paymentId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment transaction not found"));

        FinancialAccount account = accountRepository.findByIdAndBusinessId(tx.getAccountId(), context.businessId()).orElse(null);
        Counterparty counterparty = tx.getCounterpartyId() != null
                ? counterpartyRepository.findByIdAndBusinessId(tx.getCounterpartyId(), context.businessId()).orElse(null)
                : null;

        List<ObligationPaymentAllocation> allocations = allocationRepository.findAllByBusinessIdAndFinancialTransactionId(context.businessId(), paymentId);
        List<ObligationAllocationResponse> allocResponses = allocations.stream().map(a -> new ObligationAllocationResponse(
                a.getId(),
                a.getFinancialTransactionId(),
                a.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                a.getEffectiveDate(),
                a.getStatus().name(),
                a.getCancelledAt() != null ? ISO_INSTANT_FORMATTER.format(a.getCancelledAt().truncatedTo(ChronoUnit.SECONDS)) : null,
                a.getCancellationReason(),
                a.getReversalTransactionId(),
                ISO_INSTANT_FORMATTER.format(a.getCreatedAt().truncatedTo(ChronoUnit.SECONDS)),
                tx.getDescription(),
                account != null ? account.getAccountName() : "Unknown Account",
                account != null ? account.getId() : null,
                a.getAllocationType() != null ? a.getAllocationType().name() : "CASH",
                a.getAdvanceId()
        )).toList();

        AdvanceDetailResponse advanceDto = null;
        Optional<CounterpartyAdvance> advanceOpt = advanceRepository.findByBusinessIdAndFinancialTransactionId(context.businessId(), paymentId);
        if (advanceOpt.isPresent()) {
            CounterpartyAdvance adv = advanceOpt.get();
            BigDecimal applied = allocationRepository.sumEffectiveAllocationsByAdvanceId(context.businessId(), adv.getId());
            BigDecimal unapplied = adv.getOriginalAmount().subtract(applied);
            advanceDto = new AdvanceDetailResponse(
                    adv.getId(),
                    adv.getBusinessId(),
                    adv.getCounterpartyId(),
                    counterparty != null ? counterparty.getDisplayName() : "Unknown Counterparty",
                    counterparty != null ? counterparty.getType().name() : "OTHER",
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
        }

        String direction = (tx.getEventType() == TransactionEventType.RECEIVABLE_COLLECTION)
                ? "RECEIVABLE"
                : (tx.getEventType() == TransactionEventType.PAYABLE_SETTLEMENT ? "PAYABLE" : tx.getMovementDirection().name());

        boolean isReversal = tx.getReversedTransactionId() != null;
        UUID reversedTxId = tx.getReversedTransactionId();
        boolean isReversed = false;
        UUID reversedByTxId = null;
        Optional<FinancialTransaction> revOpt = transactionRepository.findByReversedTransactionIdAndBusinessId(tx.getId(), context.businessId());
        if (revOpt.isPresent()) {
            isReversed = true;
            reversedByTxId = revOpt.get().getId();
        }

        return new PaymentDetailResponse(
                tx.getId(),
                tx.getBusinessId(),
                account != null ? account.getId() : tx.getAccountId(),
                account != null ? account.getAccountName() : "Unknown Account",
                counterparty != null ? counterparty.getId() : tx.getCounterpartyId(),
                counterparty != null ? counterparty.getDisplayName() : "Unknown Counterparty",
                tx.getEventType().name(),
                direction,
                tx.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                tx.getEffectiveDate(),
                tx.getDescription(),
                isReversal,
                reversedTxId,
                isReversed,
                reversedByTxId,
                ISO_INSTANT_FORMATTER.format(tx.getCreatedAt().truncatedTo(ChronoUnit.SECONDS)),
                allocResponses,
                advanceDto
        );
    }

    private void validateChronologicalSequence(
            FinancialAccount account,
            MovementDirection direction,
            BigDecimal amount,
            LocalDate effectiveDate,
            UUID businessId
    ) {
        List<AccountMovement> existing = movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(account.getId());
        List<AccountMovement> simulated = new ArrayList<>(existing);
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
                throw new ObligationValidationException(
                        "amount",
                        "Payment would cause cash account balance to become negative on " + m.getEffectiveDate()
                );
            }

            if (running.abs().compareTo(MAX_CAPACITY) >= 0) {
                throw new ObligationValidationException(
                        "amount",
                        "Payment would cause account balance to exceed maximum capacity of 999,999,999,999.99"
                );
            }
        }
    }

    private String computeCurrentBalanceString(FinancialAccount account, LocalDate cutoffDate) {
        List<AccountMovement> movements = movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(account.getId());
        BigDecimal totalIncrease = BigDecimal.ZERO;
        BigDecimal totalDecrease = BigDecimal.ZERO;
        for (AccountMovement m : movements) {
            if (!m.getEffectiveDate().isAfter(cutoffDate)) {
                if (m.getMovementDirection() == MovementDirection.INCREASE) {
                    totalIncrease = totalIncrease.add(m.getAmount());
                } else if (m.getMovementDirection() == MovementDirection.DECREASE) {
                    totalDecrease = totalDecrease.add(m.getAmount());
                }
            }
        }
        BigDecimal current = account.getOpeningBalance().add(totalIncrease).subtract(totalDecrease);
        return current.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    public static String computePayloadHash(PaymentAllocateRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append(req.counterpartyId() != null ? req.counterpartyId().toString() : "").append("|");
        sb.append(req.direction() != null ? req.direction() : "").append("|");
        sb.append(req.accountId() != null ? req.accountId().toString() : "").append("|");
        sb.append(req.paymentDate() != null ? req.paymentDate() : "").append("|");
        sb.append(req.amount() != null ? req.amount() : "").append("|");
        sb.append(Boolean.TRUE.equals(req.retainAdvance())).append("|");

        if (req.allocations() != null) {
            List<AllocationItemRequest> sorted = new ArrayList<>(req.allocations());
            sorted.sort((a1, a2) -> {
                if (a1.obligationId() == null || a2.obligationId() == null) return 0;
                return a1.obligationId().compareTo(a2.obligationId());
            });
            for (AllocationItemRequest it : sorted) {
                sb.append(it.obligationId()).append(":").append(it.amount()).append(";");
            }
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String serializeResponse(PaymentAllocateResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize payment allocate response", e);
        }
    }

    private PaymentAllocateResponse deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, PaymentAllocateResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize payment allocate response", e);
        }
    }
}
