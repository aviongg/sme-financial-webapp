package com.app.sme_health_backend.accounting.draft.imports;

import com.app.sme_health_backend.accounting.draft.DraftConflictException;
import com.app.sme_health_backend.accounting.draft.DraftValidationException;
import com.app.sme_health_backend.accounting.draft.RecordDraft;
import com.app.sme_health_backend.accounting.draft.RecordDraftRepository;
import com.app.sme_health_backend.accounting.draft.RecordDraftService;
import com.app.sme_health_backend.accounting.draft.imports.dto.ImportBatchDetailResponse;
import com.app.sme_health_backend.accounting.draft.imports.dto.ImportBatchListResponse;
import com.app.sme_health_backend.accounting.draft.imports.dto.ImportBatchSummaryItem;
import com.app.sme_health_backend.accounting.draft.imports.dto.ImportDraftRowItem;
import com.app.sme_health_backend.accounting.draft.imports.dto.ImportDraftRowResult;
import com.app.sme_health_backend.accounting.draft.imports.dto.ImportDraftsRequest;
import com.app.sme_health_backend.accounting.draft.imports.dto.ImportDraftsResponse;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialIdempotencyRecord;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialIdempotencyRepository;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service managing persistent spreadsheet import batches, draft linkages,
 * and chunked saving with durable replay protection.
 */
@Service
@Transactional(readOnly = true)
public class ImportDraftService {

    public static final String OPERATION_DRAFT_IMPORT = "RECORD_DRAFT_IMPORT";
    public static final int MAX_ROWS_PER_REQUEST = 100;
    private static final java.util.regex.Pattern EXACT_TWO_DECIMAL_AMOUNT = java.util.regex.Pattern.compile("^[0-9]+\\.[0-9]{2}$");
    private static final java.math.BigDecimal MAX_CAPACITY = new java.math.BigDecimal("999999999999.99");

    private final ImportBatchRepository batchRepository;
    private final ImportBatchRowRepository batchRowRepository;
    private final RecordDraftRepository draftRepository;
    private final FinancialAccountRepository accountRepository;
    private final FinancialIdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public ImportDraftService(
            ImportBatchRepository batchRepository,
            ImportBatchRowRepository batchRowRepository,
            RecordDraftRepository draftRepository,
            FinancialAccountRepository accountRepository,
            FinancialIdempotencyRepository idempotencyRepository,
            ObjectMapper objectMapper
    ) {
        this.batchRepository = batchRepository;
        this.batchRowRepository = batchRowRepository;
        this.draftRepository = draftRepository;
        this.accountRepository = accountRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Atomically saves a batch chunk of spreadsheet rows as ordinary persistent drafts.
     */
    @Transactional
    public ImportDraftsResponse saveImportDrafts(
            BusinessAccessContext context,
            String idempotencyKey,
            ImportDraftsRequest request
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

        if (request.batchId() == null) {
            throw new DraftValidationException("batchId", "batchId is required");
        }

        if (request.sourceLabel() == null || request.sourceLabel().trim().isEmpty()) {
            throw new DraftValidationException("sourceLabel", "sourceLabel is required");
        }

        if (request.sourceLabel().trim().length() > 255) {
            throw new DraftValidationException("sourceLabel", "sourceLabel must not exceed 255 characters");
        }

        if (request.rows() == null || request.rows().isEmpty()) {
            throw new DraftValidationException("rows", "Request must contain at least one row");
        }

        if (request.rows().size() > MAX_ROWS_PER_REQUEST) {
            throw new DraftValidationException("rows", "Request rows count (" + request.rows().size() + ") exceeds maximum allowed limit of " + MAX_ROWS_PER_REQUEST);
        }

        // Validate uniqueness of sourceRowId within the request
        Set<String> requestRowIds = new HashSet<>();
        for (int i = 0; i < request.rows().size(); i++) {
            ImportDraftRowItem row = request.rows().get(i);
            if (row.sourceRowId() == null || row.sourceRowId().trim().isEmpty()) {
                throw new DraftValidationException("rows[" + i + "].sourceRowId", "sourceRowId is required");
            }
            if (!requestRowIds.add(row.sourceRowId().trim())) {
                throw new DraftValidationException("rows", "Duplicate sourceRowId in request: " + row.sourceRowId());
            }
        }

        // Request-level idempotency check if key supplied
        String requestHash = null;
        if (trimmedKey != null) {
            requestHash = computeRequestHash(request);
            Optional<FinancialIdempotencyRecord> existingOpt = idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(
                    context.businessId(),
                    OPERATION_DRAFT_IMPORT,
                    trimmedKey
            );

            if (existingOpt.isPresent()) {
                FinancialIdempotencyRecord existing = existingOpt.get();
                if (existing.getRequestHash().equals(requestHash)) {
                    return deserializeImportDraftsResponse(existing.getResponseBody());
                } else {
                    throw new DuplicateResourceException(
                            "Idempotency key '" + trimmedKey + "' has already been used with a different request payload"
                    );
                }
            }
        }

        // Account lookup and validation across all rows in request
        Set<UUID> accountIds = request.rows().stream()
                .map(ImportDraftRowItem::accountId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, FinancialAccount> accountMap = new HashMap<>();
        for (UUID accId : accountIds) {
            Optional<FinancialAccount> accOpt = accountRepository.findByIdAndBusinessId(accId, context.businessId());
            accOpt.ifPresent(acc -> accountMap.put(accId, acc));
        }

        // Detailed row validation
        Map<String, String> errors = new LinkedHashMap<>();
        List<ParsedRow> parsedRows = new ArrayList<>(request.rows().size());

        for (int i = 0; i < request.rows().size(); i++) {
            ImportDraftRowItem row = request.rows().get(i);
            String rowId = row.sourceRowId() != null ? row.sourceRowId().trim() : "";

            if (rowId.isEmpty()) {
                errors.put("rows[" + i + "].sourceRowId", "sourceRowId must not be blank");
            } else if (rowId.length() > 100) {
                errors.put("rows[" + i + "].sourceRowId", "sourceRowId must not exceed 100 characters");
            }

            if (row.sourceRowNumber() < 1) {
                errors.put("rows[" + i + "].sourceRowNumber", "sourceRowNumber must be greater than or equal to 1");
            }

            if (row.accountId() != null) {
                FinancialAccount acc = accountMap.get(row.accountId());
                if (acc == null) {
                    errors.put("rows[" + i + "].accountId", "Referenced account not found in active business");
                } else if (acc.getStatus() != AccountStatus.ACTIVE) {
                    errors.put("rows[" + i + "].accountId", "Account " + acc.getAccountName() + " is not ACTIVE");
                } else if (!"PKR".equalsIgnoreCase(acc.getCurrency())) {
                    errors.put("rows[" + i + "].accountId", "Account " + acc.getAccountName() + " has currency " + acc.getCurrency() + "; only PKR accounts are supported");
                }
            }

            TransactionEventType eventType = null;
            if (row.eventType() != null && !row.eventType().trim().isEmpty()) {
                try {
                    eventType = TransactionEventType.valueOf(row.eventType().trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    errors.put("rows[" + i + "].eventType", "Invalid eventType: " + row.eventType() + ". Supported types: DIRECT_SALE_RECEIPT, OPERATING_EXPENSE_PAYMENT");
                }
            }

            LocalDate effectiveDate = null;
            if (row.effectiveDate() != null && !row.effectiveDate().trim().isEmpty()) {
                try {
                    effectiveDate = LocalDate.parse(row.effectiveDate().trim());
                } catch (DateTimeParseException e) {
                    errors.put("rows[" + i + "].effectiveDate", "effectiveDate must be in ISO format (YYYY-MM-DD)");
                }
            }

            String amountInput = row.amountInput();
            if (amountInput != null && amountInput.length() > 100) {
                errors.put("rows[" + i + "].amountInput", "Amount input must not exceed 100 characters");
            }

            String description = row.description();
            if (description != null && description.length() > 255) {
                errors.put("rows[" + i + "].description", "Description must not exceed 255 characters");
            }

            String sourceReference = row.sourceReference();
            if (sourceReference != null && sourceReference.length() > 255) {
                errors.put("rows[" + i + "].sourceReference", "Source reference must not exceed 255 characters");
            }

            parsedRows.add(new ParsedRow(row, rowId, eventType, effectiveDate, amountInput, description, sourceReference));
        }

        if (!errors.isEmpty()) {
            throw new DraftValidationException("Validation Failed: " + errors, errors);
        }

        // Ensure ImportBatch exists and is flushed prior to inserting child ImportBatchRows (FK constraint)
        Optional<ImportBatch> batchOpt = batchRepository.findByIdAndBusinessId(request.batchId(), context.businessId());
        ImportBatch batch;
        if (batchOpt.isPresent()) {
            batch = batchOpt.get();
            batch.setSourceLabel(request.sourceLabel().trim());
            batch = batchRepository.saveAndFlush(batch);
        } else {
            batch = new ImportBatch(
                    request.batchId(),
                    context.businessId(),
                    request.sourceLabel().trim(),
                    context.userId()
            );
            batch = batchRepository.saveAndFlush(batch);
        }

        // Fetch existing rows for this batch within active business
        List<ImportBatchRow> existingRows = batchRowRepository.findByBusinessIdAndBatchIdAndSourceRowIdIn(
                context.businessId(),
                request.batchId(),
                requestRowIds
        );
        Map<String, ImportBatchRow> existingMap = existingRows.stream()
                .collect(Collectors.toMap(ImportBatchRow::getSourceRowId, r -> r));

        List<ImportDraftRowResult> results = new ArrayList<>(request.rows().size());
        int savedCount = 0;
        int replayedCount = 0;

        for (ParsedRow pr : parsedRows) {
            String payloadHash = computeRowPayloadHash(
                    pr.row.accountId(),
                    pr.eventType != null ? pr.eventType.name() : null,
                    pr.effectiveDate != null ? pr.effectiveDate.toString() : null,
                    pr.amountInput,
                    pr.description,
                    pr.sourceReference
            );

            ImportBatchRow existing = existingMap.get(pr.sourceRowId);
            if (existing != null) {
                if (!existing.getPayloadHash().equals(payloadHash)) {
                    throw new DraftConflictException(
                            "Row " + pr.sourceRowId + " was previously saved with different content in batch " + request.batchId()
                    );
                }

                // Matching payload: safe replay of existing draft
                Optional<RecordDraft> draftOpt = draftRepository.findByIdAndBusinessId(existing.getDraftId(), context.businessId());
                if (draftOpt.isPresent()) {
                    RecordDraft draft = draftOpt.get();
                    results.add(new ImportDraftRowResult(
                            pr.sourceRowId,
                            pr.row.sourceRowNumber(),
                            pr.sourceReference,
                            draft.getId(),
                            "REPLAYED",
                            draft.getStatus().name(),
                            isReadyToRecord(draft),
                            draft.getVersion(),
                            draft.getRecordedTransactionId()
                    ));
                    replayedCount++;
                } else {
                    // Fallback in case draft was purged: re-create draft
                    RecordDraft draft = new RecordDraft(
                            context.businessId(),
                            pr.row.accountId(),
                            pr.eventType,
                            pr.effectiveDate,
                            pr.amountInput,
                            pr.description,
                            context.userId()
                    );
                    draft = draftRepository.saveAndFlush(draft);
                    existing.setDraftId(draft.getId());
                    batchRowRepository.save(existing);
                    results.add(new ImportDraftRowResult(
                            pr.sourceRowId,
                            pr.row.sourceRowNumber(),
                            pr.sourceReference,
                            draft.getId(),
                            "REPLAYED",
                            draft.getStatus().name(),
                            isReadyToRecord(draft),
                            draft.getVersion(),
                            draft.getRecordedTransactionId()
                    ));
                    replayedCount++;
                }
            } else {
                // New row: create RecordDraft and ImportBatchRow
                RecordDraft draft = new RecordDraft(
                        context.businessId(),
                        pr.row.accountId(),
                        pr.eventType,
                        pr.effectiveDate,
                        pr.amountInput,
                        pr.description,
                        context.userId()
                );
                draft = draftRepository.saveAndFlush(draft);

                ImportBatchRow batchRow = new ImportBatchRow(
                        context.businessId(),
                        request.batchId(),
                        pr.sourceRowId,
                        pr.row.sourceRowNumber(),
                        pr.sourceReference,
                        pr.amountInput,
                        payloadHash,
                        draft.getId()
                );
                batchRowRepository.save(batchRow);

                results.add(new ImportDraftRowResult(
                        pr.sourceRowId,
                        pr.row.sourceRowNumber(),
                        pr.sourceReference,
                        draft.getId(),
                        "SAVED",
                        draft.getStatus().name(),
                        isReadyToRecord(draft),
                        draft.getVersion(),
                        draft.getRecordedTransactionId()
                ));
                savedCount++;
            }
        }

        // Update batch imported row count
        int totalRowsInBatch = batchRowRepository.countByBusinessIdAndBatchId(context.businessId(), request.batchId());
        batch.setTotalRowsImported(totalRowsInBatch);
        batchRepository.save(batch);

        ImportDraftsResponse response = new ImportDraftsResponse(
                request.batchId(),
                request.sourceLabel().trim(),
                savedCount,
                replayedCount,
                totalRowsInBatch,
                results
        );

        if (trimmedKey != null) {
            saveIdempotencyRecord(
                    context.businessId(),
                    OPERATION_DRAFT_IMPORT,
                    trimmedKey,
                    requestHash,
                    request.batchId(),
                    HttpStatus.CREATED.value(),
                    serializeResponse(response)
            );
        }

        return response;
    }

    /**
     * Retrieves detail and draft linkages of an import batch for recovery.
     */
    public ImportBatchDetailResponse getImportBatch(BusinessAccessContext context, UUID batchId) {
        ImportBatch batch = batchRepository.findByIdAndBusinessId(batchId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Import batch not found: " + batchId));

        List<ImportBatchRow> rows = batchRowRepository.findByBusinessIdAndBatchIdOrderBySourceRowNumberAsc(context.businessId(), batchId);
        List<ImportDraftRowResult> results = new ArrayList<>(rows.size());

        for (ImportBatchRow row : rows) {
            Optional<RecordDraft> draftOpt = draftRepository.findByIdAndBusinessId(row.getDraftId(), context.businessId());
            if (draftOpt.isPresent()) {
                RecordDraft draft = draftOpt.get();
                results.add(new ImportDraftRowResult(
                        row.getSourceRowId(),
                        row.getSourceRowNumber(),
                        row.getSourceReference(),
                        draft.getId(),
                        "SAVED",
                        draft.getStatus().name(),
                        isReadyToRecord(draft),
                        draft.getVersion(),
                        draft.getRecordedTransactionId()
                ));
            }
        }

        return new ImportBatchDetailResponse(
                batch.getId(),
                batch.getSourceLabel(),
                batch.getTotalRowsImported(),
                batch.getCreatedAt(),
                batch.getUpdatedAt(),
                results
        );
    }

    /**
     * Lists import batches for active business.
     */
    public ImportBatchListResponse listImportBatches(BusinessAccessContext context, Pageable pageable) {
        Page<ImportBatch> page = batchRepository.findByBusinessIdOrderByCreatedAtDesc(context.businessId(), pageable);
        List<ImportBatchSummaryItem> items = page.getContent().stream()
                .map(b -> new ImportBatchSummaryItem(
                        b.getId(),
                        b.getSourceLabel(),
                        b.getTotalRowsImported(),
                        b.getCreatedAt(),
                        b.getUpdatedAt()
                ))
                .toList();

        return new ImportBatchListResponse(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    public String computeRowPayloadHash(
            UUID accountId,
            String eventType,
            String effectiveDate,
            String amountInput,
            String description,
            String sourceReference
    ) {
        String canonical = (accountId != null ? accountId.toString() : "") + "|" +
                (eventType != null ? eventType : "") + "|" +
                (effectiveDate != null ? effectiveDate : "") + "|" +
                (amountInput != null ? amountInput : "") + "|" +
                (description != null ? description : "") + "|" +
                (sourceReference != null ? sourceReference : "");
        return sha256Hex(canonical);
    }

    private String computeRequestHash(ImportDraftsRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(request.batchId() != null ? request.batchId().toString() : "").append("|");
        sb.append(request.sourceLabel() != null ? request.sourceLabel().trim() : "").append("|");
        if (request.rows() != null) {
            for (ImportDraftRowItem row : request.rows()) {
                sb.append(row.sourceRowId() != null ? row.sourceRowId() : "").append(":")
                        .append(row.sourceRowNumber()).append(":")
                        .append(row.accountId() != null ? row.accountId().toString() : "").append(":")
                        .append(row.eventType() != null ? row.eventType() : "").append(":")
                        .append(row.effectiveDate() != null ? row.effectiveDate() : "").append(":")
                        .append(row.amountInput() != null ? row.amountInput() : "").append(":")
                        .append(row.description() != null ? row.description() : "").append(":")
                        .append(row.sourceReference() != null ? row.sourceReference() : "").append(";");
            }
        }
        return sha256Hex(sb.toString());
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
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

    private String serializeResponse(ImportDraftsResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize import drafts response", e);
        }
    }

    private ImportDraftsResponse deserializeImportDraftsResponse(String json) {
        try {
            return objectMapper.readValue(json, ImportDraftsResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize import drafts response", e);
        }
    }

    private boolean isReadyToRecord(RecordDraft draft) {
        if (draft.getStatus() != com.app.sme_health_backend.accounting.draft.DraftStatus.DRAFT) {
            return false;
        }
        if (draft.getAccountId() == null || draft.getEventType() == null || draft.getEffectiveDate() == null) {
            return false;
        }
        if (draft.getAmountInput() == null || !EXACT_TWO_DECIMAL_AMOUNT.matcher(draft.getAmountInput().trim()).matches()) {
            return false;
        }
        try {
            java.math.BigDecimal amt = new java.math.BigDecimal(draft.getAmountInput().trim());
            if (amt.compareTo(java.math.BigDecimal.ZERO) <= 0 || amt.compareTo(MAX_CAPACITY) > 0) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    private record ParsedRow(
            ImportDraftRowItem row,
            String sourceRowId,
            TransactionEventType eventType,
            LocalDate effectiveDate,
            String amountInput,
            String description,
            String sourceReference
    ) {}
}
