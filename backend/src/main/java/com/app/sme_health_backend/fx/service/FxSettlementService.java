package com.app.sme_health_backend.fx.service;

import com.app.sme_health_backend.accounting.entity.AccountMovement;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialTransaction;
import com.app.sme_health_backend.accounting.exception.TransactionValidationException;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.AccountMovementRepository;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialTransactionRepository;
import com.app.sme_health_backend.audit.entity.SecurityAuditEvent;
import com.app.sme_health_backend.audit.model.AuditOutcome;
import com.app.sme_health_backend.audit.repository.SecurityAuditRepository;
import com.app.sme_health_backend.fx.dto.*;
import com.app.sme_health_backend.fx.entity.FxObligationRecognitionEvidence;
import com.app.sme_health_backend.fx.entity.FxObligationSettlement;
import com.app.sme_health_backend.fx.exception.FxValidationException;
import com.app.sme_health_backend.fx.model.FxSettlementStatus;
import com.app.sme_health_backend.fx.model.RealizedFxImpact;
import com.app.sme_health_backend.fx.model.RecognitionEvidenceStatus;
import com.app.sme_health_backend.fx.model.SettlementDirection;
import com.app.sme_health_backend.fx.repository.FxObligationRecognitionEvidenceRepository;
import com.app.sme_health_backend.fx.repository.FxObligationSettlementRepository;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.obligation.*;
import com.app.sme_health_backend.obligation.aging.ObligationDirection;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class FxSettlementService {

    private static final BigDecimal MAX_CAPACITY = new BigDecimal("999999999999.99");

    private final FinancialObligationRepository obligationRepository;
    private final ObligationPaymentAllocationRepository allocationRepository;
    private final FinancialAccountRepository accountRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final AccountMovementRepository movementRepository;
    private final FxObligationSettlementRepository fxSettlementRepository;
    private final FxObligationRecognitionEvidenceRepository fxRecognitionEvidenceRepository;
    private final CurrencyMetadataService currencyMetadataService;
    private final SecurityAuditRepository auditRepository;

    public FxSettlementService(
            FinancialObligationRepository obligationRepository,
            ObligationPaymentAllocationRepository allocationRepository,
            FinancialAccountRepository accountRepository,
            FinancialTransactionRepository transactionRepository,
            AccountMovementRepository movementRepository,
            FxObligationSettlementRepository fxSettlementRepository,
            FxObligationRecognitionEvidenceRepository fxRecognitionEvidenceRepository,
            CurrencyMetadataService currencyMetadataService,
            SecurityAuditRepository auditRepository
    ) {
        this.obligationRepository = obligationRepository;
        this.allocationRepository = allocationRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.movementRepository = movementRepository;
        this.fxSettlementRepository = fxSettlementRepository;
        this.fxRecognitionEvidenceRepository = fxRecognitionEvidenceRepository;
        this.currencyMetadataService = currencyMetadataService;
        this.auditRepository = auditRepository;
    }

    @Transactional
    public FxRecognitionEvidenceResponse recordRecognitionEvidence(
            BusinessAccessContext context,
            UUID obligationId,
            FxRecognitionEvidenceRequest request
    ) {
        if (obligationId == null) {
            throw new FxValidationException("obligationId is required");
        }
        if (request.recognitionRate() == null || request.recognitionRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new FxValidationException("recognitionRate must be greater than zero");
        }

        FinancialObligation obligation = obligationRepository.findByIdAndBusinessId(obligationId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Financial obligation not found"));

        if (fxRecognitionEvidenceRepository.existsByBusinessIdAndObligationId(context.businessId(), obligationId)) {
            throw new DuplicateResourceException("Recognition evidence already recorded for this obligation");
        }

        String functionalCurrency = (request.functionalCurrency() != null && !request.functionalCurrency().isBlank())
                ? request.functionalCurrency().trim().toUpperCase(Locale.ROOT)
                : "PKR";

        LocalDate recognitionDate = request.recognitionDate() != null
                ? request.recognitionDate()
                : obligation.getIssueDate();

        BigDecimal bookValue = currencyMetadataService.round(
                functionalCurrency,
                obligation.getOriginalAmount().multiply(request.recognitionRate())
        );

        FxObligationRecognitionEvidence evidence = new FxObligationRecognitionEvidence(
                context.businessId(),
                obligation.getId(),
                obligation.getCurrency(),
                obligation.getOriginalAmount(),
                functionalCurrency,
                request.recognitionRate(),
                "BASE_TO_QUOTE",
                bookValue,
                recognitionDate,
                request.recognitionSource(),
                request.evidenceProvenance(),
                request.notes(),
                context.userId()
        );

        evidence = fxRecognitionEvidenceRepository.saveAndFlush(evidence);

        SecurityAuditEvent auditEvent = new SecurityAuditEvent();
        auditEvent.setEventType("OBLIGATION_FX_RECOGNITION_RECORDED");
        auditEvent.setActorUserId(context.userId());
        auditEvent.setBusinessId(context.businessId());
        auditEvent.setTargetType("FX_OBLIGATION_RECOGNITION_EVIDENCE");
        auditEvent.setTargetId(evidence.getId().toString());
        auditEvent.setOutcome(AuditOutcome.SUCCESS);
        auditEvent.setMetadata("{\"obligationId\":\"" + obligation.getId() + "\",\"recognitionRate\":\"" + request.recognitionRate() + "\",\"bookValue\":\"" + bookValue + "\"}");
        auditRepository.saveAndFlush(auditEvent);

        return FxRecognitionEvidenceResponse.fromEntity(evidence);
    }

    @Transactional(readOnly = true)
    public FxRecognitionEvidenceResponse getRecognitionEvidence(BusinessAccessContext context, UUID obligationId) {
        FxObligationRecognitionEvidence evidence = fxRecognitionEvidenceRepository.findByBusinessIdAndObligationId(context.businessId(), obligationId)
                .orElseThrow(() -> new ResourceNotFoundException("Recognition evidence not found for obligation"));
        return FxRecognitionEvidenceResponse.fromEntity(evidence);
    }

    @Transactional(readOnly = true)
    public FxSettlementPreviewResponse previewSettlement(
            BusinessAccessContext context,
            FxSettlementPreviewRequest request
    ) {
        if (request.obligationId() == null) {
            throw new FxValidationException("obligationId is required");
        }
        if (request.accountId() == null) {
            throw new FxValidationException("accountId is required");
        }
        if (request.exchangeRate() == null || request.exchangeRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new FxValidationException("exchangeRate must be greater than zero");
        }

        FinancialObligation obligation = obligationRepository.findByIdAndBusinessId(request.obligationId(), context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Financial obligation not found"));

        if (obligation.getStatus() == ObligationStatus.VOIDED) {
            throw new ObligationConflictException("Cannot settle a voided obligation");
        }

        BigDecimal currentPaid = allocationRepository.sumEffectiveAllocations(context.businessId(), obligation.getId());
        BigDecimal outstanding = obligation.getOriginalAmount().subtract(currentPaid);

        BigDecimal settleAmount = request.amountInObligationCurrency();
        if (settleAmount == null) {
            settleAmount = outstanding;
        }
        if (settleAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new FxValidationException("amountInObligationCurrency must be greater than zero");
        }
        if (settleAmount.compareTo(outstanding) > 0) {
            throw new ObligationConflictException(
                    "Settlement amount (" + settleAmount + " " + obligation.getCurrency() +
                            ") exceeds outstanding balance (" + outstanding + " " + obligation.getCurrency() + ")"
            );
        }

        FinancialAccount account = accountRepository.findByIdAndBusinessId(request.accountId(), context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Financial account not found"));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ObligationValidationException("accountId", "Financial account is not active");
        }

        BigDecimal convertedPrincipal = currencyMetadataService.round(
                account.getCurrency(),
                settleAmount.multiply(request.exchangeRate())
        );

        BigDecimal fixedFee = request.fixedFee() != null
                ? currencyMetadataService.round(account.getCurrency(), request.fixedFee())
                : BigDecimal.ZERO;

        BigDecimal percentageFee = BigDecimal.ZERO;
        if (request.percentageFeePercent() != null && request.percentageFeePercent().compareTo(BigDecimal.ZERO) > 0) {
            percentageFee = currencyMetadataService.round(
                    account.getCurrency(),
                    convertedPrincipal.multiply(request.percentageFeePercent())
                            .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
            );
        }

        BigDecimal totalFee = fixedFee.add(percentageFee);
        BigDecimal totalAccountMovement;
        String movementType;

        if (obligation.getDirection() == ObligationDirection.PAYABLE) {
            movementType = "DECREASE";
            totalAccountMovement = convertedPrincipal.add(totalFee);
        } else {
            movementType = "INCREASE";
            totalAccountMovement = convertedPrincipal.subtract(totalFee);
            if (totalAccountMovement.compareTo(BigDecimal.ZERO) <= 0) {
                throw new FxValidationException("Fee cannot exceed or equal converted principal amount for collection");
            }
        }

        BigDecimal effectiveRate = totalAccountMovement.divide(settleAmount, 6, RoundingMode.HALF_UP);
        BigDecimal remainingOutstanding = outstanding.subtract(settleAmount);

        return new FxSettlementPreviewResponse(
                obligation.getId(),
                obligation.getDirection().name(),
                obligation.getCurrency(),
                obligation.getOriginalAmount(),
                currentPaid,
                outstanding,
                settleAmount,
                remainingOutstanding,
                account.getId(),
                account.getAccountName(),
                account.getCurrency(),
                request.exchangeRate(),
                "BASE_TO_QUOTE",
                convertedPrincipal,
                fixedFee,
                percentageFee,
                totalFee,
                account.getCurrency(),
                totalAccountMovement,
                movementType,
                effectiveRate
        );
    }

    @Transactional
    public FxSettlementResponse postSettlement(
            BusinessAccessContext context,
            String idempotencyKey,
            FxSettlementPostRequest request
    ) {
        String trimmedKey = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey.trim() : null;
        if (trimmedKey != null) {
            Optional<FxObligationSettlement> existingOpt = fxSettlementRepository.findByBusinessIdAndIdempotencyKey(
                    context.businessId(), trimmedKey
            );
            if (existingOpt.isPresent()) {
                FxObligationSettlement existing = existingOpt.get();
                if (existing.getObligationId().equals(request.obligationId())
                        && existing.getAccountId().equals(request.accountId())
                        && existing.getObligationAmount().compareTo(request.amountInObligationCurrency()) == 0
                        && existing.getExchangeRate().compareTo(request.exchangeRate()) == 0) {
                    return FxSettlementResponse.fromEntity(existing);
                } else {
                    throw new DuplicateResourceException("Idempotency key '" + trimmedKey + "' has already been used with different request parameters");
                }
            }
        }

        if (request.obligationId() == null) {
            throw new FxValidationException("obligationId is required");
        }
        if (request.accountId() == null) {
            throw new FxValidationException("accountId is required");
        }
        if (request.amountInObligationCurrency() == null || request.amountInObligationCurrency().compareTo(BigDecimal.ZERO) <= 0) {
            throw new FxValidationException("amountInObligationCurrency must be greater than zero");
        }
        if (request.exchangeRate() == null || request.exchangeRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new FxValidationException("exchangeRate must be greater than zero");
        }

        LocalDate settlementDate = request.settlementDate() != null ? request.settlementDate() : LocalDate.now();

        FinancialObligation obligation = obligationRepository.findByIdAndBusinessIdForUpdate(request.obligationId(), context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Financial obligation not found"));

        if (obligation.getStatus() == ObligationStatus.VOIDED) {
            throw new ObligationConflictException("Cannot settle a voided obligation");
        }

        BigDecimal currentPaid = allocationRepository.sumEffectiveAllocations(context.businessId(), obligation.getId());
        BigDecimal outstanding = obligation.getOriginalAmount().subtract(currentPaid);

        if (request.amountInObligationCurrency().compareTo(outstanding) > 0) {
            throw new ObligationConflictException(
                    "Settlement amount (" + request.amountInObligationCurrency() + " " + obligation.getCurrency() +
                            ") exceeds outstanding balance (" + outstanding + " " + obligation.getCurrency() + ")"
            );
        }

        FinancialAccount account = accountRepository.findForUpdateByIdAndBusinessId(request.accountId(), context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Financial account not found"));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ObligationValidationException("accountId", "Financial account is not active");
        }
        if (settlementDate.isBefore(account.getOpeningDate())) {
            throw new ObligationValidationException("settlementDate", "Settlement date cannot precede account opening date (" + account.getOpeningDate() + ")");
        }
        if (settlementDate.isBefore(obligation.getIssueDate())) {
            throw new ObligationValidationException("settlementDate", "Settlement date cannot precede obligation issue date (" + obligation.getIssueDate() + ")");
        }

        // Inline recognition recording if recognitionRate is supplied and no evidence exists yet
        if (request.recognitionRate() != null && request.recognitionRate().compareTo(BigDecimal.ZERO) > 0) {
            if (!fxRecognitionEvidenceRepository.existsByBusinessIdAndObligationId(context.businessId(), obligation.getId())) {
                recordRecognitionEvidence(context, obligation.getId(), new FxRecognitionEvidenceRequest(
                        request.recognitionRate(),
                        account.getCurrency(),
                        obligation.getIssueDate(),
                        "INLINE_SETTLEMENT_DECLARATION",
                        "VERIFIED_AT_SETTLEMENT",
                        "Recorded during settlement"
                ));
            }
        }

        BigDecimal convertedPrincipal = currencyMetadataService.round(
                account.getCurrency(),
                request.amountInObligationCurrency().multiply(request.exchangeRate())
        );

        BigDecimal fixedFee = request.fixedFee() != null
                ? currencyMetadataService.round(account.getCurrency(), request.fixedFee())
                : BigDecimal.ZERO;

        BigDecimal percentageFee = BigDecimal.ZERO;
        if (request.percentageFeePercent() != null && request.percentageFeePercent().compareTo(BigDecimal.ZERO) > 0) {
            percentageFee = currencyMetadataService.round(
                    account.getCurrency(),
                    convertedPrincipal.multiply(request.percentageFeePercent())
                            .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
            );
        }

        BigDecimal totalFee = fixedFee.add(percentageFee);
        BigDecimal totalAccountMovement;
        MovementDirection movementDirection;
        TransactionEventType eventType;
        SettlementDirection settlementDirection;

        if (obligation.getDirection() == ObligationDirection.PAYABLE) {
            settlementDirection = SettlementDirection.PAYABLE_SETTLEMENT;
            eventType = TransactionEventType.PAYABLE_SETTLEMENT;
            movementDirection = MovementDirection.DECREASE;
            totalAccountMovement = convertedPrincipal.add(totalFee);
            validateChronologicalSequence(account, MovementDirection.DECREASE, totalAccountMovement, settlementDate, context.businessId());
        } else {
            settlementDirection = SettlementDirection.RECEIVABLE_COLLECTION;
            eventType = TransactionEventType.RECEIVABLE_COLLECTION;
            movementDirection = MovementDirection.INCREASE;
            totalAccountMovement = convertedPrincipal.subtract(totalFee);
            if (totalAccountMovement.compareTo(BigDecimal.ZERO) <= 0) {
                throw new FxValidationException("Fee cannot exceed or equal converted principal amount for collection");
            }
        }

        BigDecimal effectiveRate = totalAccountMovement.divide(request.amountInObligationCurrency(), 6, RoundingMode.HALF_UP);

        String txDesc = request.notes() != null && !request.notes().isBlank()
                ? request.notes()
                : (settlementDirection + ": " + request.amountInObligationCurrency() + " " + obligation.getCurrency() +
                " @ " + request.exchangeRate() + " (" + account.getCurrency() + ")");

        FinancialTransaction tx = new FinancialTransaction(
                context.businessId(),
                account.getId(),
                eventType,
                movementDirection,
                totalAccountMovement,
                account.getCurrency(),
                totalAccountMovement, // baseAmountPkr
                settlementDate,
                txDesc,
                context.userId(),
                obligation.getCounterpartyId()
        );
        tx = transactionRepository.saveAndFlush(tx);

        AccountMovement movement = new AccountMovement(
                context.businessId(),
                tx.getId(),
                account.getId(),
                movementDirection,
                totalAccountMovement,
                account.getCurrency(),
                settlementDate
        );
        movementRepository.saveAndFlush(movement);

        ObligationPaymentAllocation allocation = new ObligationPaymentAllocation(
                context.businessId(),
                obligation.getId(),
                tx.getId(),
                request.amountInObligationCurrency(), // stored in native obligation currency
                settlementDate,
                context.userId()
        );
        allocation = allocationRepository.saveAndFlush(allocation);

        FxObligationSettlement settlement = new FxObligationSettlement(
                context.businessId(),
                obligation.getId(),
                tx.getId(),
                allocation.getId(),
                settlementDirection,
                obligation.getCurrency(),
                request.amountInObligationCurrency(),
                account.getId(),
                account.getCurrency(),
                convertedPrincipal,
                totalFee,
                account.getCurrency(),
                totalAccountMovement,
                request.exchangeRate(),
                "BASE_TO_QUOTE",
                obligation.getCurrency(),
                account.getCurrency(),
                request.rateSource(),
                Instant.now(),
                settlementDate,
                effectiveRate,
                request.notes(),
                trimmedKey,
                context.userId()
        );

        // Realized FX Calculation & Recognition Evidence Evaluation
        if (obligation.getCurrency().equalsIgnoreCase(account.getCurrency())) {
            settlement.setRecognitionEvidenceStatus(RecognitionEvidenceStatus.EXCLUDED);
            settlement.setMissingEvidenceReason("SAME_CURRENCY_SETTLEMENT");
        } else {
            Optional<FxObligationRecognitionEvidence> evidenceOpt = fxRecognitionEvidenceRepository
                    .findByBusinessIdAndObligationId(context.businessId(), obligation.getId());

            if (evidenceOpt.isPresent()) {
                FxObligationRecognitionEvidence evidence = evidenceOpt.get();
                BigDecimal totalBookValue = evidence.getBookValue();
                BigDecimal recRate = evidence.getRecognitionRate();

                // Sum prior allocated book values for this obligation from active settlements
                List<FxObligationSettlement> priorSettlements = fxSettlementRepository
                        .findAllByBusinessIdAndObligationIdOrderBySettlementDateDescCreatedAtDesc(context.businessId(), obligation.getId());

                BigDecimal priorAllocatedBookValue = priorSettlements.stream()
                        .filter(s -> s.getStatus() == FxSettlementStatus.SETTLED && s.getAllocatedBookValue() != null)
                        .map(FxObligationSettlement::getAllocatedBookValue)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal allocatedBookValue;
                // If this is the final settlement that exhausts the outstanding balance
                if (outstanding.compareTo(request.amountInObligationCurrency()) == 0) {
                    allocatedBookValue = totalBookValue.subtract(priorAllocatedBookValue);
                    if (allocatedBookValue.compareTo(BigDecimal.ZERO) < 0) {
                        allocatedBookValue = BigDecimal.ZERO;
                    }
                } else {
                    allocatedBookValue = currencyMetadataService.round(
                            account.getCurrency(),
                            request.amountInObligationCurrency().multiply(recRate)
                    );
                    if (priorAllocatedBookValue.add(allocatedBookValue).compareTo(totalBookValue) > 0) {
                        allocatedBookValue = totalBookValue.subtract(priorAllocatedBookValue);
                    }
                }

                BigDecimal diff = convertedPrincipal.subtract(allocatedBookValue);
                RealizedFxImpact impact;
                BigDecimal fxDifference;

                if (obligation.getDirection() == ObligationDirection.PAYABLE) {
                    // Payable: if convertedPrincipal > allocatedBookValue, SME paid more cash than recognized book debt -> LOSS
                    if (diff.compareTo(BigDecimal.ZERO) > 0) {
                        impact = RealizedFxImpact.LOSS;
                        fxDifference = diff.abs();
                    } else if (diff.compareTo(BigDecimal.ZERO) < 0) {
                        impact = RealizedFxImpact.GAIN;
                        fxDifference = diff.abs();
                    } else {
                        impact = RealizedFxImpact.NEUTRAL;
                        fxDifference = BigDecimal.ZERO;
                    }
                } else {
                    // Receivable: if convertedPrincipal > allocatedBookValue, SME collected more cash than recognized book credit -> GAIN
                    if (diff.compareTo(BigDecimal.ZERO) > 0) {
                        impact = RealizedFxImpact.GAIN;
                        fxDifference = diff.abs();
                    } else if (diff.compareTo(BigDecimal.ZERO) < 0) {
                        impact = RealizedFxImpact.LOSS;
                        fxDifference = diff.abs();
                    } else {
                        impact = RealizedFxImpact.NEUTRAL;
                        fxDifference = BigDecimal.ZERO;
                    }
                }

                settlement.setAllocatedBookValue(allocatedBookValue);
                settlement.setRealizedFxDifference(fxDifference);
                settlement.setRealizedFxImpact(impact);
                settlement.setRecognitionEvidenceStatus(RecognitionEvidenceStatus.AVAILABLE);
                settlement.setMissingEvidenceReason(null);
            } else {
                // Fail-closed: Never substitute zero or today's rate
                settlement.setAllocatedBookValue(null);
                settlement.setRealizedFxDifference(null);
                settlement.setRealizedFxImpact(null);
                settlement.setRecognitionEvidenceStatus(RecognitionEvidenceStatus.UNAVAILABLE);
                settlement.setMissingEvidenceReason("ORIGINAL_RECOGNITION_EVIDENCE_MISSING");
            }
        }

        settlement = fxSettlementRepository.saveAndFlush(settlement);

        SecurityAuditEvent auditEvent = new SecurityAuditEvent();
        auditEvent.setEventType("OBLIGATION_FX_SETTLED");
        auditEvent.setActorUserId(context.userId());
        auditEvent.setBusinessId(context.businessId());
        auditEvent.setTargetType("FX_OBLIGATION_SETTLEMENT");
        auditEvent.setTargetId(settlement.getId().toString());
        auditEvent.setOutcome(AuditOutcome.SUCCESS);
        auditEvent.setMetadata("{\"transactionId\":\"" + tx.getId() + "\",\"obligationId\":\"" + obligation.getId() + "\",\"settlementId\":\"" + settlement.getId() + "\"}");
        auditRepository.saveAndFlush(auditEvent);

        return FxSettlementResponse.fromEntity(settlement);
    }

    @Transactional
    public FxSettlementResponse reverseSettlement(
            BusinessAccessContext context,
            String idempotencyKey,
            UUID settlementId,
            FxSettlementReverseRequest request
    ) {
        String trimmedReason = (request != null && request.reason() != null && !request.reason().isBlank())
                ? request.reason().trim()
                : "FX settlement reversal";

        FxObligationSettlement settlement = fxSettlementRepository.findForUpdateByIdAndBusinessId(settlementId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("FX settlement not found"));

        if (settlement.getStatus() == FxSettlementStatus.REVERSED) {
            throw new ObligationConflictException("FX Settlement is already reversed");
        }

        ObligationPaymentAllocation allocation = allocationRepository.findByIdAndBusinessId(settlement.getAllocationId(), context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Linked payment allocation not found"));

        FinancialAccount account = accountRepository.findForUpdateByIdAndBusinessId(settlement.getAccountId(), context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Linked financial account not found"));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ObligationValidationException("accountId", "Financial account is not active");
        }

        MovementDirection compensatingDirection;
        if (settlement.getSettlementDirection() == SettlementDirection.PAYABLE_SETTLEMENT) {
            compensatingDirection = MovementDirection.INCREASE;
        } else {
            compensatingDirection = MovementDirection.DECREASE;
            validateChronologicalSequence(account, MovementDirection.DECREASE, settlement.getTotalAccountMovement(), LocalDate.now(), context.businessId());
        }

        TransactionEventType reversalEventType = (settlement.getSettlementDirection() == SettlementDirection.PAYABLE_SETTLEMENT)
                ? TransactionEventType.PAYABLE_SETTLEMENT
                : TransactionEventType.RECEIVABLE_COLLECTION;

        FinancialTransaction reversalTx = new FinancialTransaction(
                context.businessId(),
                account.getId(),
                reversalEventType,
                compensatingDirection,
                settlement.getTotalAccountMovement(),
                settlement.getAccountCurrency(),
                settlement.getTotalAccountMovement(),
                LocalDate.now(),
                "Reversal of FX settlement: " + trimmedReason,
                context.userId()
        );
        reversalTx.setReversedTransactionId(settlement.getFinancialTransactionId());
        reversalTx.setReversalReason(trimmedReason);
        reversalTx = transactionRepository.saveAndFlush(reversalTx);

        AccountMovement compensatingMovement = new AccountMovement(
                context.businessId(),
                reversalTx.getId(),
                account.getId(),
                compensatingDirection,
                settlement.getTotalAccountMovement(),
                settlement.getAccountCurrency(),
                LocalDate.now()
        );
        movementRepository.saveAndFlush(compensatingMovement);

        allocation.setStatus(AllocationStatus.CANCELLED);
        allocation.setCancelledAt(Instant.now());
        allocation.setCancellationReason(trimmedReason);
        allocation.setReversalTransactionId(reversalTx.getId());
        allocationRepository.saveAndFlush(allocation);

        settlement.setStatus(FxSettlementStatus.REVERSED);
        settlement.setReversalTransactionId(reversalTx.getId());
        settlement.setReversalReason(trimmedReason);
        settlement.setReversedAt(Instant.now());
        settlement = fxSettlementRepository.saveAndFlush(settlement);

        SecurityAuditEvent auditEvent = new SecurityAuditEvent();
        auditEvent.setEventType("OBLIGATION_FX_REVERSED");
        auditEvent.setActorUserId(context.userId());
        auditEvent.setBusinessId(context.businessId());
        auditEvent.setTargetType("FX_OBLIGATION_SETTLEMENT");
        auditEvent.setTargetId(settlement.getId().toString());
        auditEvent.setOutcome(AuditOutcome.SUCCESS);
        auditEvent.setMetadata("{\"settlementId\":\"" + settlement.getId() + "\",\"reversalTxId\":\"" + reversalTx.getId() + "\"}");
        auditRepository.saveAndFlush(auditEvent);

        return FxSettlementResponse.fromEntity(settlement);
    }

    @Transactional(readOnly = true)
    public List<FxSettlementResponse> listSettlements(BusinessAccessContext context, UUID obligationId) {
        List<FxObligationSettlement> settlements;
        if (obligationId != null) {
            settlements = fxSettlementRepository.findAllByBusinessIdAndObligationIdOrderBySettlementDateDescCreatedAtDesc(
                    context.businessId(), obligationId
            );
        } else {
            settlements = fxSettlementRepository.findAllByBusinessIdOrderBySettlementDateDescCreatedAtDesc(context.businessId());
        }
        return settlements.stream().map(FxSettlementResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public FxSettlementResponse getSettlement(BusinessAccessContext context, UUID settlementId) {
        FxObligationSettlement settlement = fxSettlementRepository.findByIdAndBusinessId(settlementId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("FX settlement not found"));
        return FxSettlementResponse.fromEntity(settlement);
    }

    @Transactional(readOnly = true)
    public FxSettlementReconciliationResponse getSettlementReconciliation(BusinessAccessContext context, UUID settlementId) {
        FxObligationSettlement settlement = fxSettlementRepository.findByIdAndBusinessId(settlementId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("FX settlement not found"));

        FinancialObligation obligation = obligationRepository.findByIdAndBusinessId(settlement.getObligationId(), context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Financial obligation not found"));

        BigDecimal currentPaid = allocationRepository.sumEffectiveAllocations(context.businessId(), obligation.getId());
        BigDecimal remainingObligation = obligation.getOriginalAmount().subtract(currentPaid);

        String feeTreatment = settlement.getSettlementDirection() == SettlementDirection.PAYABLE_SETTLEMENT
                ? "FEE_ADDED_TO_CASH_DEBIT"
                : "FEE_DEDUCTED_FROM_CASH_CREDIT";

        boolean cashReconciliationVerified;
        if (settlement.getSettlementDirection() == SettlementDirection.PAYABLE_SETTLEMENT) {
            cashReconciliationVerified = settlement.getConvertedPrincipal().add(settlement.getFeeAmount())
                    .compareTo(settlement.getTotalAccountMovement()) == 0;
        } else {
            cashReconciliationVerified = settlement.getConvertedPrincipal().subtract(settlement.getFeeAmount())
                    .compareTo(settlement.getTotalAccountMovement()) == 0;
        }

        BigDecimal recognitionRate = null;
        BigDecimal originalBookValue = null;
        BigDecimal allocatedBookValue = settlement.getAllocatedBookValue();
        BigDecimal remainingBookValue = null;
        BigDecimal netEconomicVariance = null;
        String explanation;

        if (settlement.getRecognitionEvidenceStatus() == RecognitionEvidenceStatus.AVAILABLE) {
            Optional<FxObligationRecognitionEvidence> evidenceOpt = fxRecognitionEvidenceRepository
                    .findByBusinessIdAndObligationId(context.businessId(), obligation.getId());

            if (evidenceOpt.isPresent()) {
                FxObligationRecognitionEvidence evidence = evidenceOpt.get();
                recognitionRate = evidence.getRecognitionRate();
                originalBookValue = evidence.getBookValue();

                List<FxObligationSettlement> activeSettlements = fxSettlementRepository
                        .findAllByBusinessIdAndObligationIdOrderBySettlementDateDescCreatedAtDesc(context.businessId(), obligation.getId())
                        .stream()
                        .filter(s -> s.getStatus() == FxSettlementStatus.SETTLED && s.getAllocatedBookValue() != null)
                        .toList();

                BigDecimal totalAllocatedBook = activeSettlements.stream()
                        .map(FxObligationSettlement::getAllocatedBookValue)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                remainingBookValue = originalBookValue.subtract(totalAllocatedBook);
                if (remainingBookValue.compareTo(BigDecimal.ZERO) < 0) {
                    remainingBookValue = BigDecimal.ZERO;
                }
            }

            if (allocatedBookValue != null) {
                netEconomicVariance = settlement.getTotalAccountMovement().subtract(allocatedBookValue);
            }

            explanation = String.format(
                    Locale.US,
                    "%s settled for %s %s @ %s. Book Value: %s %s. Realized FX: %s %s (%s). Bank Fee: %s %s (%s). Total Movement: %s %s.",
                    settlement.getSettlementDirection(),
                    settlement.getObligationAmount(), settlement.getObligationCurrency(),
                    settlement.getExchangeRate(),
                    allocatedBookValue != null ? allocatedBookValue : BigDecimal.ZERO, settlement.getAccountCurrency(),
                    settlement.getRealizedFxDifference() != null ? settlement.getRealizedFxDifference() : BigDecimal.ZERO,
                    settlement.getAccountCurrency(),
                    settlement.getRealizedFxImpact() != null ? settlement.getRealizedFxImpact() : RealizedFxImpact.NEUTRAL,
                    settlement.getFeeAmount(), settlement.getFeeCurrency(), feeTreatment,
                    settlement.getTotalAccountMovement(), settlement.getAccountCurrency()
            );
        } else {
            explanation = String.format(
                    Locale.US,
                    "%s settled for %s %s @ %s. Recognition rate unavailable (%s). Realized FX cannot be determined. Total Movement: %s %s.",
                    settlement.getSettlementDirection(),
                    settlement.getObligationAmount(), settlement.getObligationCurrency(),
                    settlement.getExchangeRate(),
                    settlement.getMissingEvidenceReason() != null ? settlement.getMissingEvidenceReason() : "UNAVAILABLE",
                    settlement.getTotalAccountMovement(), settlement.getAccountCurrency()
            );
        }

        return new FxSettlementReconciliationResponse(
                settlement.getId(),
                settlement.getBusinessId(),
                settlement.getObligationId(),
                settlement.getFinancialTransactionId(),
                settlement.getAllocationId(),
                settlement.getSettlementDirection(),
                settlement.getSettlementDate(),
                settlement.getObligationCurrency(),
                settlement.getObligationAmount(),
                remainingObligation,
                settlement.getAccountId(),
                settlement.getAccountCurrency(),
                settlement.getExchangeRate(),
                settlement.getRateConvention(),
                settlement.getConvertedPrincipal(),
                settlement.getFeeAmount(),
                settlement.getFeeCurrency(),
                feeTreatment,
                settlement.getTotalAccountMovement(),
                settlement.getEffectiveRate(),
                settlement.getStatus(),
                settlement.getReversalTransactionId(),
                settlement.getReversalReason(),
                settlement.getRecognitionEvidenceStatus(),
                settlement.getMissingEvidenceReason(),
                recognitionRate,
                originalBookValue,
                allocatedBookValue,
                remainingBookValue,
                settlement.getRealizedFxDifference(),
                settlement.getRealizedFxImpact(),
                netEconomicVariance,
                cashReconciliationVerified,
                explanation
        );
    }

    @Transactional(readOnly = true)
    public List<FxSettlementReconciliationResponse> listSettlementReconciliations(BusinessAccessContext context, UUID obligationId) {
        List<FxObligationSettlement> settlements;
        if (obligationId != null) {
            settlements = fxSettlementRepository.findAllByBusinessIdAndObligationIdOrderBySettlementDateDescCreatedAtDesc(context.businessId(), obligationId);
        } else {
            settlements = fxSettlementRepository.findAllByBusinessIdOrderBySettlementDateDescCreatedAtDesc(context.businessId());
        }
        return settlements.stream().map(s -> getSettlementReconciliation(context, s.getId())).toList();
    }

    @Transactional(readOnly = true)
    public FxPeriodSummaryResponse getPeriodSummary(BusinessAccessContext context, LocalDate startDate, LocalDate endDate) {
        List<FxObligationSettlement> allSettlements = fxSettlementRepository.findAllByBusinessIdOrderBySettlementDateDescCreatedAtDesc(context.businessId());

        List<FxObligationSettlement> filtered = allSettlements.stream()
                .filter(s -> (startDate == null || !s.getSettlementDate().isBefore(startDate))
                        && (endDate == null || !s.getSettlementDate().isAfter(endDate)))
                .toList();

        int totalCount = filtered.size();
        int activeCount = (int) filtered.stream().filter(s -> s.getStatus() == FxSettlementStatus.SETTLED).count();
        int reversedCount = (int) filtered.stream().filter(s -> s.getStatus() == FxSettlementStatus.REVERSED).count();
        int unrecognizedCount = (int) filtered.stream()
                .filter(s -> s.getStatus() == FxSettlementStatus.SETTLED && s.getRecognitionEvidenceStatus() == RecognitionEvidenceStatus.UNAVAILABLE)
                .count();

        List<FxObligationSettlement> active = filtered.stream()
                .filter(s -> s.getStatus() == FxSettlementStatus.SETTLED && (s.getAccountCurrency() == null || s.getAccountCurrency().equalsIgnoreCase("PKR")))
                .toList();

        BigDecimal totalPrincipal = currencyMetadataService.round("PKR", active.stream()
                .map(FxObligationSettlement::getConvertedPrincipal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        BigDecimal totalGains = currencyMetadataService.round("PKR", active.stream()
                .filter(s -> s.getRealizedFxImpact() == RealizedFxImpact.GAIN && s.getRealizedFxDifference() != null)
                .map(FxObligationSettlement::getRealizedFxDifference)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        BigDecimal totalLosses = currencyMetadataService.round("PKR", active.stream()
                .filter(s -> s.getRealizedFxImpact() == RealizedFxImpact.LOSS && s.getRealizedFxDifference() != null)
                .map(FxObligationSettlement::getRealizedFxDifference)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        BigDecimal netRealized = currencyMetadataService.round("PKR", totalGains.subtract(totalLosses));

        BigDecimal totalFees = currencyMetadataService.round("PKR", active.stream()
                .map(FxObligationSettlement::getFeeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        BigDecimal netCashImpact = currencyMetadataService.round("PKR", active.stream()
                .map(s -> s.getSettlementDirection() == SettlementDirection.RECEIVABLE_COLLECTION
                        ? s.getTotalAccountMovement()
                        : s.getTotalAccountMovement().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        return new FxPeriodSummaryResponse(
                context.businessId(),
                "PKR",
                totalCount,
                activeCount,
                reversedCount,
                unrecognizedCount,
                totalPrincipal,
                totalGains,
                totalLosses,
                netRealized,
                totalFees,
                netCashImpact,
                Instant.now()
        );
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
        AccountMovement proposed = new AccountMovement(businessId, null, account.getId(), direction, amount, account.getCurrency(), effectiveDate);
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
}
