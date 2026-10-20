package com.app.sme_health_backend.reconciliation.service;

import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialIdempotencyRecord;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialIdempotencyRepository;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.reconciliation.dto.StatementEntryItemRequest;
import com.app.sme_health_backend.reconciliation.dto.StatementEntryResponse;
import com.app.sme_health_backend.reconciliation.dto.StatementImportDetailResponse;
import com.app.sme_health_backend.reconciliation.dto.StatementImportRequest;
import com.app.sme_health_backend.reconciliation.dto.StatementImportResponse;
import com.app.sme_health_backend.reconciliation.entity.BankStatementEntry;
import com.app.sme_health_backend.reconciliation.entity.BankStatementImport;
import com.app.sme_health_backend.reconciliation.exception.ReconciliationConflictException;
import com.app.sme_health_backend.reconciliation.exception.ReconciliationValidationException;
import com.app.sme_health_backend.reconciliation.repository.BankStatementEntryRepository;
import com.app.sme_health_backend.reconciliation.repository.BankStatementImportRepository;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BankStatementImportService {

    private static final String OPERATION_STATEMENT_IMPORT = "BANK_STATEMENT_IMPORT";

    private final BankStatementImportRepository importRepository;
    private final BankStatementEntryRepository entryRepository;
    private final FinancialAccountRepository accountRepository;
    private final FinancialIdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public BankStatementImportService(
            BankStatementImportRepository importRepository,
            BankStatementEntryRepository entryRepository,
            FinancialAccountRepository accountRepository,
            FinancialIdempotencyRepository idempotencyRepository,
            ObjectMapper objectMapper
    ) {
        this.importRepository = importRepository;
        this.entryRepository = entryRepository;
        this.accountRepository = accountRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public StatementImportResponse importStatement(
            BusinessAccessContext context,
            String idempotencyKey,
            StatementImportRequest request
    ) {
        String trimmedKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey.trim()
                : null;

        if (trimmedKey != null && trimmedKey.length() > 100) {
            throw new ReconciliationValidationException("Idempotency-Key", "Idempotency-Key must not exceed 100 characters");
        }

        if (request == null) {
            throw new ReconciliationValidationException("body", "Request body is required");
        }

        // Validate account
        if (request.accountId() == null) {
            throw new ReconciliationValidationException("accountId", "accountId is required");
        }

        FinancialAccount account = accountRepository.findByIdAndBusinessId(request.accountId(), context.businessId())
                .orElseThrow(() -> new ReconciliationValidationException("accountId", "Financial account not found in current business"));

        if (account.getAccountType() != AccountType.BANK) {
            throw new ReconciliationValidationException("accountId", "Reconciliation statement import is strictly supported for BANK accounts");
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ReconciliationValidationException("accountId", "Financial account must be ACTIVE to import statements");
        }

        if (!"PKR".equalsIgnoreCase(account.getCurrency())) {
            throw new ReconciliationValidationException("accountId", "Bank reconciliation is currently supported for PKR accounts only");
        }

        if (request.entries() == null || request.entries().isEmpty()) {
            throw new ReconciliationValidationException("entries", "Statement must contain at least one valid transaction row");
        }

        // Idempotency check
        String requestHash = computeRequestHash(request);
        if (trimmedKey != null) {
            Optional<FinancialIdempotencyRecord> existingRecord = idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(
                    context.businessId(),
                    OPERATION_STATEMENT_IMPORT,
                    trimmedKey
            );

            if (existingRecord.isPresent()) {
                FinancialIdempotencyRecord record = existingRecord.get();
                if (!record.getRequestHash().equals(requestHash)) {
                    throw new ReconciliationConflictException("Idempotency conflict: A statement import with key '" + trimmedKey + "' has already been processed with different content");
                }
                try {
                    return objectMapper.readValue(record.getResponseBody(), StatementImportResponse.class);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to deserialize idempotency response", e);
                }
            }
        }

        // Validate entries and calculate bounds
        LocalDate minDate = null;
        LocalDate maxDate = null;
        int rowNumber = 1;
        int duplicateWarningsCount = 0;
        List<BankStatementEntry> entriesToPersist = new ArrayList<>();

        for (StatementEntryItemRequest item : request.entries()) {
            if (item.amount() == null || item.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ReconciliationValidationException("entries[" + rowNumber + "].amount", "Entry amount must be greater than zero");
            }
            if (item.postedDate() == null) {
                throw new ReconciliationValidationException("entries[" + rowNumber + "].postedDate", "Entry postedDate is required");
            }
            if (item.direction() == null) {
                throw new ReconciliationValidationException("entries[" + rowNumber + "].direction", "Entry direction is required (INCOMING or OUTGOING)");
            }

            if (minDate == null || item.postedDate().isBefore(minDate)) {
                minDate = item.postedDate();
            }
            if (maxDate == null || item.postedDate().isAfter(maxDate)) {
                maxDate = item.postedDate();
            }

            String fingerprint = computeRowFingerprint(item);

            // Check if matching fingerprint already exists in the account
            List<BankStatementEntry> existingSameFingerprint = entryRepository.findByBusinessIdAndAccountIdAndSourceFingerprint(
                    context.businessId(),
                    account.getId(),
                    fingerprint
            );
            if (!existingSameFingerprint.isEmpty()) {
                duplicateWarningsCount++;
            }

            BankStatementEntry entry = new BankStatementEntry(
                    context.businessId(),
                    null, // set after import entity is saved
                    account.getId(),
                    item.sourceRowNumber() > 0 ? item.sourceRowNumber() : rowNumber,
                    item.postedDate(),
                    item.valueDate(),
                    item.direction(),
                    item.amount().setScale(2, RoundingMode.HALF_UP),
                    "PKR",
                    item.description(),
                    item.bankReference(),
                    item.externalTransactionId(),
                    item.runningBalance(),
                    fingerprint
            );

            entriesToPersist.add(entry);
            rowNumber++;
        }

        String fileFingerprint = (request.fileFingerprint() != null && !request.fileFingerprint().isBlank())
                ? request.fileFingerprint().trim()
                : computeBatchFingerprint(entriesToPersist);

        BankStatementImport statementImport = new BankStatementImport(
                context.businessId(),
                account.getId(),
                request.sourceFilename(),
                fileFingerprint,
                request.mappingConfig(),
                entriesToPersist.size(),
                request.openingBalance(),
                request.closingBalance(),
                minDate,
                maxDate,
                context.userId()
        );

        BankStatementImport savedImport = importRepository.save(statementImport);

        for (BankStatementEntry entry : entriesToPersist) {
            entry.setImportId(savedImport.getId());
        }
        entryRepository.saveAll(entriesToPersist);

        StatementImportResponse response = new StatementImportResponse(
                savedImport.getId(),
                savedImport.getAccountId(),
                savedImport.getSourceFilename(),
                savedImport.getTotalEntries(),
                savedImport.getOpeningBalance(),
                savedImport.getClosingBalance(),
                savedImport.getFromDate(),
                savedImport.getToDate(),
                savedImport.getStatus(),
                duplicateWarningsCount,
                savedImport.getCreatedAt()
        );

        // Store idempotency record if key was provided
        if (trimmedKey != null) {
            try {
                String responseJson = objectMapper.writeValueAsString(response);
                FinancialIdempotencyRecord record = new FinancialIdempotencyRecord(
                        context.businessId(),
                        OPERATION_STATEMENT_IMPORT,
                        trimmedKey,
                        requestHash,
                        savedImport.getId(),
                        201,
                        responseJson
                );
                idempotencyRepository.save(record);
            } catch (Exception e) {
                // Non-fatal if idempotency persistence fails after save
            }
        }

        return response;
    }

    @Transactional(readOnly = true)
    public List<StatementImportResponse> listImports(BusinessAccessContext context, UUID accountId) {
        if (accountId != null) {
            accountRepository.findByIdAndBusinessId(accountId, context.businessId())
                    .orElseThrow(() -> new ResourceNotFoundException("Financial account not found: " + accountId));
        }

        List<BankStatementImport> imports = (accountId != null)
                ? importRepository.findByBusinessIdAndAccountIdOrderByCreatedAtDesc(context.businessId(), accountId)
                : Collections.emptyList();

        return imports.stream()
                .map(i -> new StatementImportResponse(
                        i.getId(),
                        i.getAccountId(),
                        i.getSourceFilename(),
                        i.getTotalEntries(),
                        i.getOpeningBalance(),
                        i.getClosingBalance(),
                        i.getFromDate(),
                        i.getToDate(),
                        i.getStatus(),
                        0,
                        i.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public StatementImportDetailResponse getImportDetail(BusinessAccessContext context, UUID importId) {
        BankStatementImport imp = importRepository.findByIdAndBusinessId(importId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("BankStatementImport not found: " + importId));

        FinancialAccount account = accountRepository.findByIdAndBusinessId(imp.getAccountId(), context.businessId())
                .orElse(null);
        String accountName = account != null ? account.getAccountName() : "Unknown Account";

        List<BankStatementEntry> entries = entryRepository.findByBusinessIdAndImportIdOrderBySourceRowNumberAsc(
                context.businessId(),
                imp.getId()
        );

        List<StatementEntryResponse> entryResponses = entries.stream()
                .map(this::toEntryResponse)
                .collect(Collectors.toList());

        return new StatementImportDetailResponse(
                imp.getId(),
                imp.getAccountId(),
                accountName,
                imp.getSourceFilename(),
                imp.getTotalEntries(),
                imp.getOpeningBalance(),
                imp.getClosingBalance(),
                imp.getFromDate(),
                imp.getToDate(),
                imp.getStatus(),
                entryResponses,
                imp.getCreatedAt()
        );
    }

    public StatementEntryResponse toEntryResponse(BankStatementEntry e) {
        // Check if there are other entries with same fingerprint in this account
        List<BankStatementEntry> sameFingerprint = entryRepository.findByBusinessIdAndAccountIdAndSourceFingerprint(
                e.getBusinessId(),
                e.getAccountId(),
                e.getSourceFingerprint()
        );
        boolean duplicateWarning = sameFingerprint.size() > 1;

        return new StatementEntryResponse(
                e.getId(),
                e.getImportId(),
                e.getAccountId(),
                e.getSourceRowNumber(),
                e.getPostedDate(),
                e.getValueDate(),
                e.getDirection(),
                e.getAmount(),
                e.getCurrency(),
                e.getDescription(),
                e.getBankReference(),
                e.getExternalTransactionId(),
                e.getRunningBalance(),
                e.getStatus(),
                e.getExclusionReason(),
                e.getLinkedDraftId(),
                duplicateWarning,
                e.getCreatedAt()
        );
    }

    private String computeRowFingerprint(StatementEntryItemRequest item) {
        String raw = item.postedDate() + ":" +
                item.direction() + ":" +
                item.amount().setScale(2, RoundingMode.HALF_UP).toPlainString() + ":" +
                (item.description() != null ? item.description().trim().toLowerCase() : "") + ":" +
                (item.bankReference() != null ? item.bankReference().trim().toLowerCase() : "");
        return sha256(raw);
    }

    private String computeBatchFingerprint(List<BankStatementEntry> entries) {
        StringBuilder sb = new StringBuilder();
        for (BankStatementEntry e : entries) {
            sb.append(e.getSourceFingerprint()).append(";");
        }
        return sha256(sb.toString());
    }

    public String computeRequestHash(StatementImportRequest request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            return sha256(json);
        } catch (Exception e) {
            return sha256(String.valueOf(request.hashCode()));
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
