package com.app.sme_health_backend.fx.service;

import com.app.sme_health_backend.accounting.entity.AccountMovement;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialIdempotencyRecord;
import com.app.sme_health_backend.accounting.entity.FinancialTransaction;
import com.app.sme_health_backend.accounting.exception.TransactionValidationException;
import com.app.sme_health_backend.accounting.model.*;
import com.app.sme_health_backend.accounting.repository.AccountMovementRepository;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialIdempotencyRepository;
import com.app.sme_health_backend.accounting.repository.FinancialTransactionRepository;
import com.app.sme_health_backend.accounting.util.CanonicalAmountValidator;
import com.app.sme_health_backend.audit.entity.SecurityAuditEvent;
import com.app.sme_health_backend.audit.model.AuditOutcome;
import com.app.sme_health_backend.audit.repository.SecurityAuditRepository;
import com.app.sme_health_backend.fx.dto.*;
import com.app.sme_health_backend.fx.entity.FxInternalTransfer;
import com.app.sme_health_backend.fx.exception.FxValidationException;
import com.app.sme_health_backend.fx.model.FxSettlementStatus;
import com.app.sme_health_backend.fx.model.FxTransferFeePlacement;
import com.app.sme_health_backend.fx.model.RateConvention;
import com.app.sme_health_backend.fx.repository.FxInternalTransferRepository;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
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
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class FxTransferService {

    public static final String OPERATION_FX_TRANSFER = "FINANCIAL_FX_TRANSFER";
    private static final ZoneId KARACHI_ZONE = ZoneId.of("Asia/Karachi");

    private final FinancialAccountRepository accountRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final AccountMovementRepository movementRepository;
    private final FxInternalTransferRepository fxTransferRepository;
    private final FinancialIdempotencyRepository idempotencyRepository;
    private final SecurityAuditRepository securityAuditRepository;
    private final CurrencyMetadataService currencyMetadataService;
    private final ObjectMapper objectMapper;

    public FxTransferService(
            FinancialAccountRepository accountRepository,
            FinancialTransactionRepository transactionRepository,
            AccountMovementRepository movementRepository,
            FxInternalTransferRepository fxTransferRepository,
            FinancialIdempotencyRepository idempotencyRepository,
            SecurityAuditRepository securityAuditRepository,
            CurrencyMetadataService currencyMetadataService,
            ObjectMapper objectMapper
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.movementRepository = movementRepository;
        this.fxTransferRepository = fxTransferRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.securityAuditRepository = securityAuditRepository;
        this.currencyMetadataService = currencyMetadataService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public FxTransferPreviewResponse previewTransfer(BusinessAccessContext context, FxTransferPreviewRequest request) {
        if (request == null) {
            throw new FxValidationException("request", "Preview request body is required");
        }
        if (request.sourceAccountId() == null) {
            throw new FxValidationException("sourceAccountId", "Source account ID is required");
        }
        if (request.destinationAccountId() == null) {
            throw new FxValidationException("destinationAccountId", "Destination account ID is required");
        }
        if (request.sourceAccountId().equals(request.destinationAccountId())) {
            throw new FxValidationException("destinationAccountId", "Source and destination accounts must be distinct");
        }

        FinancialAccount sourceAccount = accountRepository.findByIdAndBusinessId(request.sourceAccountId(), context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));
        FinancialAccount destAccount = accountRepository.findByIdAndBusinessId(request.destinationAccountId(), context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination account not found"));

        if (sourceAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new FxValidationException("sourceAccountId", "Source account is not active");
        }
        if (destAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new FxValidationException("destinationAccountId", "Destination account is not active");
        }
        if (sourceAccount.getCurrency().equalsIgnoreCase(destAccount.getCurrency())) {
            throw new FxValidationException("destinationAccountId",
                    "Source and destination accounts must have different currencies for cross-currency transfers. Use standard transfer for same-currency transfers.");
        }

        if (request.sourceAmount() == null || request.sourceAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new FxValidationException("sourceAmount", "Source transfer amount must be greater than zero");
        }
        if (request.exchangeRate() == null || request.exchangeRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new FxValidationException("exchangeRate", "Exchange rate must be greater than zero");
        }

        BigDecimal feeAmount = request.feeAmount() != null ? request.feeAmount() : BigDecimal.ZERO;
        if (feeAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new FxValidationException("feeAmount", "Fee amount cannot be negative");
        }

        FxTransferFeePlacement placement = FxTransferFeePlacement.DESTINATION_DEDUCTED;
        if (request.feePlacement() != null && !request.feePlacement().isBlank()) {
            try {
                placement = FxTransferFeePlacement.valueOf(request.feePlacement().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new FxValidationException("feePlacement", "Invalid fee placement. Expected DESTINATION_DEDUCTED or SOURCE_CHARGED");
            }
        }
        if (feeAmount.compareTo(BigDecimal.ZERO) == 0) {
            placement = FxTransferFeePlacement.NO_FEE;
        }

        return calculateTransferAmounts(sourceAccount, destAccount, request.sourceAmount(), request.exchangeRate(), feeAmount, placement);
    }

    private FxTransferPreviewResponse calculateTransferAmounts(
            FinancialAccount sourceAccount,
            FinancialAccount destAccount,
            BigDecimal sourceAmountRaw,
            BigDecimal exchangeRateRaw,
            BigDecimal feeAmountRaw,
            FxTransferFeePlacement placement
    ) {
        String srcCurr = sourceAccount.getCurrency();
        String dstCurr = destAccount.getCurrency();

        BigDecimal sourceAmount = currencyMetadataService.round(srcCurr, sourceAmountRaw);
        BigDecimal exchangeRate = exchangeRateRaw.setScale(6, RoundingMode.HALF_UP);

        BigDecimal convertedPrincipal = currencyMetadataService.round(
                dstCurr,
                sourceAmount.multiply(exchangeRate)
        );

        BigDecimal feeAmount;
        String feeCurrency;
        BigDecimal netDestinationAmount;
        BigDecimal totalSourceDebit;
        BigDecimal effectiveRate;
        String explanation;

        if (placement == FxTransferFeePlacement.DESTINATION_DEDUCTED) {
            feeCurrency = dstCurr;
            feeAmount = currencyMetadataService.round(dstCurr, feeAmountRaw);
            if (feeAmount.compareTo(convertedPrincipal) >= 0) {
                throw new FxValidationException("feeAmount", "Fee cannot exceed or equal converted principal amount");
            }
            netDestinationAmount = convertedPrincipal.subtract(feeAmount);
            totalSourceDebit = sourceAmount;
            effectiveRate = netDestinationAmount.divide(sourceAmount, 6, RoundingMode.HALF_UP);
            explanation = String.format(Locale.ROOT,
                    "Converted %s %s @ %s -> %s %s. Fee of %s %s deducted from proceeds -> Net credit %s %s.",
                    sourceAmount.toPlainString(), srcCurr, exchangeRate.toPlainString(),
                    convertedPrincipal.toPlainString(), dstCurr,
                    feeAmount.toPlainString(), dstCurr,
                    netDestinationAmount.toPlainString(), dstCurr);
        } else if (placement == FxTransferFeePlacement.SOURCE_CHARGED) {
            feeCurrency = srcCurr;
            feeAmount = currencyMetadataService.round(srcCurr, feeAmountRaw);
            netDestinationAmount = convertedPrincipal;
            totalSourceDebit = sourceAmount.add(feeAmount);
            effectiveRate = netDestinationAmount.divide(totalSourceDebit, 6, RoundingMode.HALF_UP);
            explanation = String.format(Locale.ROOT,
                    "Converted %s %s @ %s -> %s %s. Separate fee of %s %s added to debit -> Total debit %s %s.",
                    sourceAmount.toPlainString(), srcCurr, exchangeRate.toPlainString(),
                    convertedPrincipal.toPlainString(), dstCurr,
                    feeAmount.toPlainString(), srcCurr,
                    totalSourceDebit.toPlainString(), srcCurr);
        } else {
            placement = FxTransferFeePlacement.NO_FEE;
            feeCurrency = dstCurr;
            feeAmount = currencyMetadataService.round(dstCurr, BigDecimal.ZERO);
            netDestinationAmount = convertedPrincipal;
            totalSourceDebit = sourceAmount;
            effectiveRate = exchangeRate;
            explanation = String.format(Locale.ROOT,
                    "Converted %s %s @ %s -> %s %s with zero fees.",
                    sourceAmount.toPlainString(), srcCurr, exchangeRate.toPlainString(),
                    convertedPrincipal.toPlainString(), dstCurr);
        }

        return new FxTransferPreviewResponse(
                sourceAccount.getId(),
                sourceAccount.getAccountName(),
                srcCurr,
                destAccount.getId(),
                destAccount.getAccountName(),
                dstCurr,
                sourceAmount,
                exchangeRate,
                convertedPrincipal,
                feeAmount,
                feeCurrency,
                placement.name(),
                netDestinationAmount,
                totalSourceDebit,
                effectiveRate,
                explanation
        );
    }

    @Transactional
    public FxTransferResponse postTransfer(
            BusinessAccessContext context,
            String idempotencyKey,
            FxTransferPostRequest request
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

        BigDecimal sourceAmount = null;
        if (request.sourceAmount() == null || request.sourceAmount().trim().isEmpty()) {
            errors.put("sourceAmount", "Source amount is required");
        } else {
            try {
                sourceAmount = new BigDecimal(request.sourceAmount().trim());
                if (sourceAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    errors.put("sourceAmount", "Source amount must be greater than zero");
                }
            } catch (NumberFormatException e) {
                errors.put("sourceAmount", "Invalid source amount format");
            }
        }

        BigDecimal exchangeRate = null;
        if (request.exchangeRate() == null || request.exchangeRate().trim().isEmpty()) {
            errors.put("exchangeRate", "Exchange rate is required");
        } else {
            try {
                exchangeRate = new BigDecimal(request.exchangeRate().trim());
                if (exchangeRate.compareTo(BigDecimal.ZERO) <= 0) {
                    errors.put("exchangeRate", "Exchange rate must be greater than zero");
                }
            } catch (NumberFormatException e) {
                errors.put("exchangeRate", "Invalid exchange rate format");
            }
        }

        BigDecimal feeAmount = BigDecimal.ZERO;
        if (request.feeAmount() != null && !request.feeAmount().trim().isEmpty()) {
            try {
                feeAmount = new BigDecimal(request.feeAmount().trim());
                if (feeAmount.compareTo(BigDecimal.ZERO) < 0) {
                    errors.put("feeAmount", "Fee amount cannot be negative");
                }
            } catch (NumberFormatException e) {
                errors.put("feeAmount", "Invalid fee amount format");
            }
        }

        FxTransferFeePlacement placement = FxTransferFeePlacement.DESTINATION_DEDUCTED;
        if (request.feePlacement() != null && !request.feePlacement().isBlank()) {
            try {
                placement = FxTransferFeePlacement.valueOf(request.feePlacement().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errors.put("feePlacement", "Invalid fee placement. Expected DESTINATION_DEDUCTED or SOURCE_CHARGED");
            }
        }
        if (feeAmount.compareTo(BigDecimal.ZERO) == 0) {
            placement = FxTransferFeePlacement.NO_FEE;
        }

        LocalDate transferDate = null;
        if (request.transferDate() == null || request.transferDate().trim().isEmpty()) {
            errors.put("transferDate", "Transfer date is required");
        } else {
            try {
                transferDate = LocalDate.parse(request.transferDate().trim());
                LocalDate today = LocalDate.now(KARACHI_ZONE);
                if (transferDate.isAfter(today)) {
                    errors.put("transferDate", "Transfer date cannot be in the future (Asia/Karachi)");
                }
            } catch (DateTimeParseException e) {
                errors.put("transferDate", "Invalid date format for transferDate. Expected YYYY-MM-DD");
            }
        }

        String trimmedNotes = (request.notes() != null && !request.notes().trim().isEmpty())
                ? request.notes().trim()
                : null;
        if (trimmedNotes != null && trimmedNotes.length() > 1000) {
            errors.put("notes", "Notes must not exceed 1000 characters");
        }

        if (!errors.isEmpty()) {
            throw new TransactionValidationException(errors);
        }

        // 1. Deterministic Lock Ordering: sort UUIDs to prevent deadlock
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

        if (sourceAccount.getStatus() != AccountStatus.ACTIVE) {
            errors.put("sourceAccountId", "Source account is not active");
        }
        if (destAccount.getStatus() != AccountStatus.ACTIVE) {
            errors.put("destinationAccountId", "Destination account is not active");
        }
        if (sourceAccount.getCurrency().equalsIgnoreCase(destAccount.getCurrency())) {
            errors.put("destinationAccountId", "Source and destination accounts must have different currencies for cross-currency transfers");
        }
        if (transferDate != null) {
            if (transferDate.isBefore(sourceAccount.getOpeningDate())) {
                errors.put("transferDate", "Transfer date cannot precede source account opening date (" + sourceAccount.getOpeningDate() + ")");
            }
            if (transferDate.isBefore(destAccount.getOpeningDate())) {
                errors.put("transferDate", "Transfer date cannot precede destination account opening date (" + destAccount.getOpeningDate() + ")");
            }
        }

        if (!errors.isEmpty()) {
            throw new TransactionValidationException(errors);
        }

        // 2. Compute Preview / Authoritative Calculations
        FxTransferPreviewResponse preview = calculateTransferAmounts(
                sourceAccount, destAccount, sourceAmount, exchangeRate, feeAmount, placement
        );

        // 3. Check Idempotency Record
        String payloadHash = computePayloadHash(
                sourceAccount.getId(),
                destAccount.getId(),
                preview.sourceAmount(),
                preview.exchangeRate(),
                preview.feeAmount(),
                preview.feePlacement(),
                transferDate,
                trimmedNotes
        );

        Optional<FinancialIdempotencyRecord> existingOpt = idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(
                context.businessId(),
                OPERATION_FX_TRANSFER,
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

        // 4. Validate Balance Sequences:
        // Source account: decrease by totalSourceDebit
        validateChronologicalSequence(sourceAccount, MovementDirection.DECREASE, preview.totalSourceDebit(), transferDate, context.businessId(), "source");
        // Destination account: increase by netDestinationAmount
        validateChronologicalSequence(destAccount, MovementDirection.INCREASE, preview.netDestinationAmount(), transferDate, context.businessId(), "destination");

        // 5. Atomic Persistence
        // 5a. FinancialTransaction
        BigDecimal baseAmountPkr = "PKR".equalsIgnoreCase(destAccount.getCurrency())
                ? preview.convertedDestinationPrincipal()
                : ("PKR".equalsIgnoreCase(sourceAccount.getCurrency())
                ? preview.sourceAmount()
                : preview.convertedDestinationPrincipal());

        String txDesc = trimmedNotes != null
                ? trimmedNotes
                : ("FX Transfer: " + preview.sourceAmount() + " " + sourceAccount.getCurrency() +
                " -> " + preview.netDestinationAmount() + " " + destAccount.getCurrency() + " @ " + preview.exchangeRate());

        FinancialTransaction tx = new FinancialTransaction(
                context.businessId(),
                sourceAccount.getId(),
                destAccount.getId(),
                TransactionEventType.INTERNAL_TRANSFER,
                MovementDirection.DECREASE,
                preview.sourceAmount(),
                sourceAccount.getCurrency(),
                baseAmountPkr,
                transferDate,
                txDesc,
                context.userId()
        );
        tx = transactionRepository.saveAndFlush(tx);

        // 5b. Source Account Movement (DECREASE by totalSourceDebit)
        AccountMovement sourceMovement = new AccountMovement(
                context.businessId(),
                tx.getId(),
                sourceAccount.getId(),
                MovementDirection.DECREASE,
                preview.totalSourceDebit(),
                sourceAccount.getCurrency(),
                transferDate
        );
        sourceMovement = movementRepository.saveAndFlush(sourceMovement);

        // 5c. Destination Account Movement (INCREASE by netDestinationAmount)
        AccountMovement destMovement = new AccountMovement(
                context.businessId(),
                tx.getId(),
                destAccount.getId(),
                MovementDirection.INCREASE,
                preview.netDestinationAmount(),
                destAccount.getCurrency(),
                transferDate
        );
        destMovement = movementRepository.saveAndFlush(destMovement);

        // 5d. FxInternalTransfer
        FxInternalTransfer transfer = new FxInternalTransfer();
        transfer.setBusinessId(context.businessId());
        transfer.setSourceAccountId(sourceAccount.getId());
        transfer.setDestinationAccountId(destAccount.getId());
        transfer.setSourceCurrency(sourceAccount.getCurrency());
        transfer.setDestinationCurrency(destAccount.getCurrency());
        transfer.setSourceAmount(preview.sourceAmount());
        transfer.setExchangeRate(preview.exchangeRate());
        transfer.setRateConvention(RateConvention.BASE_TO_QUOTE);
        transfer.setBaseCurrency(sourceAccount.getCurrency());
        transfer.setQuoteCurrency(destAccount.getCurrency());
        transfer.setRateSource("MANUAL_USER_RATE");
        transfer.setRateTimestamp(Instant.now());
        transfer.setConvertedDestinationPrincipal(preview.convertedDestinationPrincipal());
        transfer.setFeeAmount(preview.feeAmount());
        transfer.setFeeCurrency(preview.feeCurrency());
        transfer.setFeePlacement(FxTransferFeePlacement.valueOf(preview.feePlacement()));
        transfer.setNetDestinationAmount(preview.netDestinationAmount());
        transfer.setTotalSourceAmount(preview.totalSourceDebit());
        transfer.setFinancialTransactionId(tx.getId());
        transfer.setSourceMovementId(sourceMovement.getId());
        transfer.setDestinationMovementId(destMovement.getId());
        transfer.setTransferDate(transferDate);
        transfer.setStatus(FxSettlementStatus.SETTLED);
        transfer.setNotes(trimmedNotes);
        transfer.setIdempotencyKey(trimmedKey);
        transfer.setCreatedByUserId(context.userId());
        transfer.setCreatedAt(Instant.now());
        transfer = fxTransferRepository.saveAndFlush(transfer);

        // 5e. Security Audit Event
        SecurityAuditEvent auditEvent = new SecurityAuditEvent();
        auditEvent.setEventType("FINANCIAL_FX_TRANSFER_POSTED");
        auditEvent.setActorUserId(context.userId());
        auditEvent.setBusinessId(context.businessId());
        auditEvent.setTargetType("FX_INTERNAL_TRANSFER");
        auditEvent.setTargetId(transfer.getId().toString());
        auditEvent.setOutcome(AuditOutcome.SUCCESS);
        auditEvent.setMetadata("{\"transferId\":\"" + transfer.getId() + "\",\"sourceCurrency\":\"" + sourceAccount.getCurrency() + "\",\"destCurrency\":\"" + destAccount.getCurrency() + "\"}");
        securityAuditRepository.saveAndFlush(auditEvent);

        FxTransferResponse response = mapToResponse(transfer, sourceAccount.getAccountName(), destAccount.getAccountName());

        // 5f. Save Idempotency Record
        String responseJson = serializeResponse(response);
        FinancialIdempotencyRecord idempotencyRecord = new FinancialIdempotencyRecord(
                context.businessId(),
                OPERATION_FX_TRANSFER,
                trimmedKey,
                payloadHash,
                transfer.getId(),
                HttpStatus.CREATED.value(),
                responseJson
        );
        idempotencyRepository.saveAndFlush(idempotencyRecord);

        return response;
    }

    @Transactional
    public FxTransferResponse reverseTransfer(
            BusinessAccessContext context,
            UUID transferId,
            FxTransferReverseRequest request
    ) {
        if (transferId == null) {
            throw new FxValidationException("transferId", "Transfer ID is required");
        }

        FxInternalTransfer transfer = fxTransferRepository.findByIdAndBusinessId(transferId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("FX internal transfer not found"));

        if (transfer.getStatus() == FxSettlementStatus.REVERSED) {
            throw new DuplicateResourceException("Transfer has already been reversed");
        }

        String reason = (request != null && request.reason() != null && !request.reason().isBlank())
                ? request.reason().trim()
                : "Transfer reversal by authorized user";

        // 1. Lock accounts in deterministic order
        UUID firstLockId = transfer.getSourceAccountId().compareTo(transfer.getDestinationAccountId()) < 0
                ? transfer.getSourceAccountId()
                : transfer.getDestinationAccountId();
        UUID secondLockId = transfer.getSourceAccountId().compareTo(transfer.getDestinationAccountId()) < 0
                ? transfer.getDestinationAccountId()
                : transfer.getSourceAccountId();

        FinancialAccount firstAccount = accountRepository.findForUpdateByIdAndBusinessId(firstLockId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Financial account not found"));
        FinancialAccount secondAccount = accountRepository.findForUpdateByIdAndBusinessId(secondLockId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Financial account not found"));

        FinancialAccount sourceAccount = transfer.getSourceAccountId().equals(firstLockId) ? firstAccount : secondAccount;
        FinancialAccount destAccount = transfer.getDestinationAccountId().equals(firstLockId) ? firstAccount : secondAccount;

        // 2. Validate simulation for reversal:
        // Destination account: decrease by netDestinationAmount (funds being returned)
        validateChronologicalSequence(destAccount, MovementDirection.DECREASE, transfer.getNetDestinationAmount(), transfer.getTransferDate(), context.businessId(), "destination");
        // Source account: increase by totalSourceAmount (funds being restored)
        validateChronologicalSequence(sourceAccount, MovementDirection.INCREASE, transfer.getTotalSourceAmount(), transfer.getTransferDate(), context.businessId(), "source");

        // 3. Persist Compensating FinancialTransaction
        FinancialTransaction originalTx = transactionRepository.findById(transfer.getFinancialTransactionId()).orElse(null);
        BigDecimal baseAmountPkr = originalTx != null ? originalTx.getBaseAmountPkr() : transfer.getConvertedDestinationPrincipal();

        FinancialTransaction revTx = new FinancialTransaction(
                context.businessId(),
                sourceAccount.getId(),
                destAccount.getId(),
                TransactionEventType.INTERNAL_TRANSFER,
                MovementDirection.INCREASE,
                transfer.getSourceAmount(),
                sourceAccount.getCurrency(),
                baseAmountPkr,
                transfer.getTransferDate(),
                "Reversal of FX Transfer " + transfer.getId() + ": " + reason,
                context.userId()
        );
        revTx.setReversedTransactionId(transfer.getFinancialTransactionId());
        revTx.setReversalReason(reason);
        revTx = transactionRepository.saveAndFlush(revTx);

        // 4. Persist Compensating Movements
        // Compensating Source Movement: INCREASE by totalSourceAmount
        AccountMovement revSourceMov = new AccountMovement(
                context.businessId(),
                revTx.getId(),
                sourceAccount.getId(),
                MovementDirection.INCREASE,
                transfer.getTotalSourceAmount(),
                sourceAccount.getCurrency(),
                transfer.getTransferDate()
        );
        revSourceMov = movementRepository.saveAndFlush(revSourceMov);

        // Compensating Destination Movement: DECREASE by netDestinationAmount
        AccountMovement revDestMov = new AccountMovement(
                context.businessId(),
                revTx.getId(),
                destAccount.getId(),
                MovementDirection.DECREASE,
                transfer.getNetDestinationAmount(),
                destAccount.getCurrency(),
                transfer.getTransferDate()
        );
        revDestMov = movementRepository.saveAndFlush(revDestMov);

        // 5. Update FxInternalTransfer
        transfer.setStatus(FxSettlementStatus.REVERSED);
        transfer.setReversalTransactionId(revTx.getId());
        transfer.setReversalSourceMovementId(revSourceMov.getId());
        transfer.setReversalDestinationMovementId(revDestMov.getId());
        transfer.setReversalReason(reason);
        transfer.setReversedAt(Instant.now());
        transfer = fxTransferRepository.saveAndFlush(transfer);

        // 6. Security Audit Event
        SecurityAuditEvent auditEvent = new SecurityAuditEvent();
        auditEvent.setEventType("FINANCIAL_FX_TRANSFER_REVERSED");
        auditEvent.setActorUserId(context.userId());
        auditEvent.setBusinessId(context.businessId());
        auditEvent.setTargetType("FX_INTERNAL_TRANSFER");
        auditEvent.setTargetId(transfer.getId().toString());
        auditEvent.setOutcome(AuditOutcome.SUCCESS);
        auditEvent.setMetadata("{\"transferId\":\"" + transfer.getId() + "\",\"reason\":\"" + reason + "\"}");
        securityAuditRepository.saveAndFlush(auditEvent);

        return mapToResponse(transfer, sourceAccount.getAccountName(), destAccount.getAccountName());
    }

    @Transactional(readOnly = true)
    public List<FxTransferResponse> listTransfers(BusinessAccessContext context, String status) {
        List<FxInternalTransfer> list = fxTransferRepository.findAllByBusinessIdOrderByTransferDateDescCreatedAtDesc(context.businessId());
        if (status != null && !status.isBlank()) {
            list = list.stream().filter(t -> t.getStatus().name().equalsIgnoreCase(status.trim())).toList();
        }

        Map<UUID, String> accountNames = new HashMap<>();
        List<FinancialAccount> accounts = accountRepository.findByBusinessId(context.businessId());
        accounts.forEach(a -> accountNames.put(a.getId(), a.getAccountName()));

        return list.stream()
                .map(t -> mapToResponse(t, accountNames.get(t.getSourceAccountId()), accountNames.get(t.getDestinationAccountId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public FxTransferResponse getTransfer(BusinessAccessContext context, UUID transferId) {
        FxInternalTransfer transfer = fxTransferRepository.findByIdAndBusinessId(transferId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("FX internal transfer not found"));

        FinancialAccount sourceAccount = accountRepository.findByIdAndBusinessId(transfer.getSourceAccountId(), context.businessId()).orElse(null);
        FinancialAccount destAccount = accountRepository.findByIdAndBusinessId(transfer.getDestinationAccountId(), context.businessId()).orElse(null);

        return mapToResponse(
                transfer,
                sourceAccount != null ? sourceAccount.getAccountName() : "Source Account",
                destAccount != null ? destAccount.getAccountName() : "Destination Account"
        );
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
                account.getCurrency(),
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

    private FxTransferResponse mapToResponse(FxInternalTransfer transfer, String srcName, String dstName) {
        return new FxTransferResponse(
                transfer.getId(),
                transfer.getBusinessId(),
                transfer.getSourceAccountId(),
                srcName,
                transfer.getSourceCurrency(),
                transfer.getDestinationAccountId(),
                dstName,
                transfer.getDestinationCurrency(),
                transfer.getSourceAmount(),
                transfer.getExchangeRate(),
                transfer.getConvertedDestinationPrincipal(),
                transfer.getFeeAmount(),
                transfer.getFeeCurrency(),
                transfer.getFeePlacement().name(),
                transfer.getNetDestinationAmount(),
                transfer.getTotalSourceAmount(),
                transfer.getFinancialTransactionId(),
                transfer.getSourceMovementId(),
                transfer.getDestinationMovementId(),
                transfer.getTransferDate(),
                transfer.getStatus().name(),
                transfer.getReversalTransactionId(),
                transfer.getReversalReason(),
                transfer.getReversedAt(),
                transfer.getNotes(),
                transfer.getCreatedByUserId(),
                transfer.getCreatedAt()
        );
    }

    private String computePayloadHash(
            UUID sourceAccountId,
            UUID destinationAccountId,
            BigDecimal sourceAmount,
            BigDecimal exchangeRate,
            BigDecimal feeAmount,
            String feePlacement,
            LocalDate transferDate,
            String notes
    ) {
        String canonical = (sourceAccountId != null ? sourceAccountId.toString() : "") + "|" +
                (destinationAccountId != null ? destinationAccountId.toString() : "") + "|" +
                (sourceAmount != null ? sourceAmount.toPlainString() : "") + "|" +
                (exchangeRate != null ? exchangeRate.toPlainString() : "") + "|" +
                (feeAmount != null ? feeAmount.toPlainString() : "") + "|" +
                (feePlacement != null ? feePlacement : "") + "|" +
                (transferDate != null ? transferDate.toString() : "") + "|" +
                (notes != null ? notes : "");

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String serializeResponse(FxTransferResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize FX transfer response", e);
        }
    }

    private FxTransferResponse deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, FxTransferResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize FX transfer response", e);
        }
    }
}
