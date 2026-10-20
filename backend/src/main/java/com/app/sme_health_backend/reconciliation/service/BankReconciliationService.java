package com.app.sme_health_backend.reconciliation.service;

import com.app.sme_health_backend.accounting.draft.DraftCreateRequest;
import com.app.sme_health_backend.accounting.draft.DraftDetailResponse;
import com.app.sme_health_backend.accounting.draft.RecordDraftService;
import com.app.sme_health_backend.accounting.dto.AccountDetailResponse;
import com.app.sme_health_backend.accounting.entity.AccountMovement;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialTransaction;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.accounting.repository.AccountMovementRepository;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialTransactionRepository;
import com.app.sme_health_backend.accounting.service.AccountReadService;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.reconciliation.dto.*;
import com.app.sme_health_backend.reconciliation.entity.BankReconciliationMatch;
import com.app.sme_health_backend.reconciliation.entity.BankStatementEntry;
import com.app.sme_health_backend.reconciliation.exception.ReconciliationConflictException;
import com.app.sme_health_backend.reconciliation.exception.ReconciliationValidationException;
import com.app.sme_health_backend.reconciliation.model.ReconciliationMatchMethod;
import com.app.sme_health_backend.reconciliation.model.ReconciliationMatchStatus;
import com.app.sme_health_backend.reconciliation.model.StatementEntryStatus;
import com.app.sme_health_backend.reconciliation.repository.BankReconciliationMatchRepository;
import com.app.sme_health_backend.reconciliation.repository.BankStatementEntryRepository;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BankReconciliationService {

    private final BankStatementEntryRepository entryRepository;
    private final BankReconciliationMatchRepository matchRepository;
    private final AccountMovementRepository movementRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final FinancialAccountRepository accountRepository;
    private final AccountReadService accountReadService;
    private final RecordDraftService recordDraftService;
    private final CounterpartyRepository counterpartyRepository;

    public BankReconciliationService(
            BankStatementEntryRepository entryRepository,
            BankReconciliationMatchRepository matchRepository,
            AccountMovementRepository movementRepository,
            FinancialTransactionRepository transactionRepository,
            FinancialAccountRepository accountRepository,
            AccountReadService accountReadService,
            RecordDraftService recordDraftService,
            CounterpartyRepository counterpartyRepository
    ) {
        this.entryRepository = entryRepository;
        this.matchRepository = matchRepository;
        this.movementRepository = movementRepository;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.accountReadService = accountReadService;
        this.recordDraftService = recordDraftService;
        this.counterpartyRepository = counterpartyRepository;
    }

    @Transactional(readOnly = true)
    public Page<StatementEntryResponse> listEntries(
            BusinessAccessContext context,
            UUID accountId,
            StatementEntryStatus status,
            Pageable pageable
    ) {
        if (accountId != null) {
            accountRepository.findByIdAndBusinessId(accountId, context.businessId())
                    .orElseThrow(() -> new ResourceNotFoundException("Financial account not found: " + accountId));
        }

        Specification<BankStatementEntry> spec = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("businessId"), context.businessId()));
            if (accountId != null) {
                predicates.add(cb.equal(root.get("accountId"), accountId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<BankStatementEntry> page = entryRepository.findAll(spec, pageable);
        return page.map(this::toEntryResponse);
    }

    @Transactional(readOnly = true)
    public StatementEntryDetailResponse getEntryDetail(BusinessAccessContext context, UUID entryId) {
        BankStatementEntry entry = entryRepository.findByIdAndBusinessId(entryId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("BankStatementEntry not found: " + entryId));

        Optional<BankReconciliationMatch> activeMatch = matchRepository.findByBusinessIdAndStatementEntryIdAndStatus(
                context.businessId(),
                entry.getId(),
                ReconciliationMatchStatus.ACTIVE
        );

        UUID matchId = null;
        UUID matchedAccountMovementId = null;
        UUID matchedTransactionId = null;
        ReconciliationMatchMethod matchMethod = null;
        Integer confidenceScore = null;
        Instant matchedAt = null;

        if (activeMatch.isPresent()) {
            BankReconciliationMatch match = activeMatch.get();
            matchId = match.getId();
            matchedAccountMovementId = match.getAccountMovementId();
            matchMethod = match.getMatchMethod();
            confidenceScore = match.getConfidenceScore();
            matchedAt = match.getMatchedAt();

            Optional<AccountMovement> movOpt = movementRepository.findById(matchedAccountMovementId);
            if (movOpt.isPresent()) {
                AccountMovement mov = movOpt.get();
                Optional<FinancialTransaction> revTx = transactionRepository.findByReversedTransactionIdAndBusinessId(mov.getTransactionId(), context.businessId());
                if (revTx.isPresent()) {
                    match.setStatus(ReconciliationMatchStatus.CANCELLED);
                    match.setUnmatchedAt(Instant.now());
                    match.setUnmatchedByUserId(context.userId());
                    match.setUnmatchReason("Automatically cancelled on access due to transaction reversal: " + revTx.get().getId());
                    matchRepository.save(match);

                    entry.setStatus(StatementEntryStatus.UNMATCHED);
                    entryRepository.save(entry);

                    matchId = null;
                    matchedAccountMovementId = null;
                    matchMethod = null;
                    confidenceScore = null;
                    matchedAt = null;
                } else {
                    matchedTransactionId = mov.getTransactionId();
                }
            }
        }

        List<ReconciliationMatchCandidateResponse> candidates = Collections.emptyList();
        if (entry.getStatus() == StatementEntryStatus.UNMATCHED) {
            candidates = findCandidatesForEntry(context, entry);
        }

        boolean duplicateWarning = checkDuplicateWarning(entry);

        return new StatementEntryDetailResponse(
                entry.getId(),
                entry.getImportId(),
                entry.getAccountId(),
                entry.getSourceRowNumber(),
                entry.getPostedDate(),
                entry.getValueDate(),
                entry.getDirection(),
                entry.getAmount(),
                entry.getCurrency(),
                entry.getDescription(),
                entry.getBankReference(),
                entry.getExternalTransactionId(),
                entry.getRunningBalance(),
                entry.getStatus(),
                entry.getExclusionReason(),
                entry.getLinkedDraftId(),
                duplicateWarning,
                entry.getCreatedAt(),
                matchId,
                matchedAccountMovementId,
                matchedTransactionId,
                matchMethod,
                confidenceScore,
                matchedAt,
                candidates
        );
    }

    @Transactional(readOnly = true)
    public List<ReconciliationMatchCandidateResponse> findCandidates(BusinessAccessContext context, UUID entryId) {
        BankStatementEntry entry = entryRepository.findByIdAndBusinessId(entryId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("BankStatementEntry not found: " + entryId));
        return findCandidatesForEntry(context, entry);
    }

    private List<ReconciliationMatchCandidateResponse> findCandidatesForEntry(
            BusinessAccessContext context,
            BankStatementEntry entry
    ) {
        // 1. Get all active matched movement IDs for this account to exclude them
        Set<UUID> activeMatchedMovementIds = matchRepository.findActiveMatchedMovementIds(
                context.businessId(),
                entry.getAccountId()
        );

        // 2. Query movements on this account
        List<AccountMovement> movements = movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(
                entry.getAccountId()
        );

        if (movements.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. Batch lookup transactions to verify they are unreversed
        Set<UUID> txIds = movements.stream().map(AccountMovement::getTransactionId).collect(Collectors.toSet());
        Map<UUID, FinancialTransaction> txMap = transactionRepository.findAllById(txIds).stream()
                .filter(t -> t.getBusinessId().equals(context.businessId()))
                .collect(Collectors.toMap(FinancialTransaction::getId, t -> t));

        // Find which transactions have been reversed
        List<FinancialTransaction> reversalTxList = transactionRepository.findByBusinessIdAndReversedTransactionIdIn(
                context.businessId(),
                txIds
        );
        Set<UUID> reversedTxIds = reversalTxList.stream()
                .map(FinancialTransaction::getReversedTransactionId)
                .collect(Collectors.toSet());

        // Batch lookup counterparties
        Set<UUID> cpIds = txMap.values().stream()
                .map(FinancialTransaction::getCounterpartyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> cpNameMap = cpIds.isEmpty() ? Collections.emptyMap() :
                counterpartyRepository.findAllById(cpIds).stream()
                        .collect(Collectors.toMap(Counterparty::getId, Counterparty::getDisplayName));

        List<ReconciliationMatchCandidateResponse> candidates = new ArrayList<>();

        for (AccountMovement mov : movements) {
            // Hard criteria:
            // a) Account must match entry account (already enforced by query)
            // b) Currency must be PKR
            if (!"PKR".equalsIgnoreCase(mov.getCurrency())) continue;

            // c) Direction must match exactly
            if (mov.getMovementDirection() != entry.getDirection()) continue;

            // d) Amount must match exactly (scale 2)
            if (mov.getAmount().setScale(2, RoundingMode.HALF_UP).compareTo(entry.getAmount().setScale(2, RoundingMode.HALF_UP)) != 0) {
                continue;
            }

            // e) Movement must not be already actively matched
            if (activeMatchedMovementIds.contains(mov.getId())) continue;

            // f) Transaction must not be reversed or a reversal
            FinancialTransaction tx = txMap.get(mov.getTransactionId());
            if (tx == null) continue;
            if (tx.getReversedTransactionId() != null) continue; // compensating tx
            if (reversedTxIds.contains(tx.getId())) continue; // original tx has been reversed

            // Candidate is eligible! Now compute confidence score and explainable reasons
            List<String> reasons = new ArrayList<>();
            reasons.add("Exact amount and direction match on account (" + mov.getAmount().setScale(2, RoundingMode.HALF_UP) + " PKR " + mov.getMovementDirection() + ")");

            int score = 60; // Base score for exact amount, account, direction

            long daysDiff = Math.abs(ChronoUnit.DAYS.between(entry.getPostedDate(), mov.getEffectiveDate()));
            if (daysDiff == 0) {
                score += 30;
                reasons.add("Exact date match (" + mov.getEffectiveDate() + ")");
            } else if (daysDiff <= 3) {
                score += 20;
                reasons.add("Date within 3 days (" + mov.getEffectiveDate() + ", " + daysDiff + " days difference)");
            } else if (daysDiff <= 7) {
                score += 10;
                reasons.add("Date within 7 days (" + mov.getEffectiveDate() + ", " + daysDiff + " days difference)");
            } else if (daysDiff <= 14) {
                score += 5;
                reasons.add("Date within 14 days (" + mov.getEffectiveDate() + ")");
            }

            // Reference text matching
            String bankRef = entry.getBankReference() != null ? entry.getBankReference().trim().toLowerCase() : "";
            String txDesc = tx.getDescription() != null ? tx.getDescription().trim().toLowerCase() : "";
            if (!bankRef.isEmpty() && txDesc.contains(bankRef)) {
                score += 10;
                reasons.add("Statement reference matches transaction description");
            }

            // Counterparty matching
            String cpName = tx.getCounterpartyId() != null ? cpNameMap.get(tx.getCounterpartyId()) : null;
            if (cpName != null && entry.getDescription() != null) {
                if (entry.getDescription().toLowerCase().contains(cpName.toLowerCase())) {
                    score += 10;
                    reasons.add("Counterparty name matches statement description: " + cpName);
                }
            }

            int finalScore = Math.min(100, score);

            candidates.add(new ReconciliationMatchCandidateResponse(
                    mov.getId(),
                    tx.getId(),
                    tx.getEventType(),
                    mov.getMovementDirection(),
                    mov.getAmount(),
                    mov.getCurrency(),
                    mov.getEffectiveDate(),
                    tx.getDescription(),
                    null,
                    cpName,
                    finalScore,
                    reasons
            ));
        }

        // Sort: highest confidence first, then smallest date difference
        candidates.sort((a, b) -> {
            int scoreCmp = Integer.compare(b.confidenceScore(), a.confidenceScore());
            if (scoreCmp != 0) return scoreCmp;
            long diffA = Math.abs(ChronoUnit.DAYS.between(entry.getPostedDate(), a.effectiveDate()));
            long diffB = Math.abs(ChronoUnit.DAYS.between(entry.getPostedDate(), b.effectiveDate()));
            return Long.compare(diffA, diffB);
        });

        return candidates;
    }

    @Transactional
    public StatementEntryDetailResponse matchEntry(
            BusinessAccessContext context,
            UUID entryId,
            ReconciliationMatchRequest request
    ) {
        if (request == null || request.accountMovementId() == null) {
            throw new ReconciliationValidationException("accountMovementId", "accountMovementId is required");
        }

        // 1. Lock statement entry
        BankStatementEntry entry = entryRepository.findForUpdateByIdAndBusinessId(entryId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("BankStatementEntry not found: " + entryId));

        if (entry.getStatus() == StatementEntryStatus.MATCHED) {
            throw new ReconciliationValidationException("status", "Statement entry is already matched");
        }

        // 2. Fetch and validate account movement
        AccountMovement movement = movementRepository.findById(request.accountMovementId())
                .orElseThrow(() -> new ReconciliationValidationException("accountMovementId", "Account movement not found"));

        if (!movement.getBusinessId().equals(context.businessId())) {
            throw new ReconciliationValidationException("accountMovementId", "Account movement does not belong to active business");
        }

        if (!movement.getAccountId().equals(entry.getAccountId())) {
            throw new ReconciliationValidationException("accountMovementId", "Account movement belongs to a different financial account");
        }

        if (movement.getMovementDirection() != entry.getDirection()) {
            throw new ReconciliationValidationException("direction", "Movement direction (" + movement.getMovementDirection() + ") does not match statement entry direction (" + entry.getDirection() + ")");
        }

        if (movement.getAmount().setScale(2, RoundingMode.HALF_UP).compareTo(entry.getAmount().setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new ReconciliationValidationException("amount", "Movement amount (" + movement.getAmount() + ") does not match statement entry amount (" + entry.getAmount() + ")");
        }

        // Check if movement transaction is reversed
        FinancialTransaction tx = transactionRepository.findByIdAndBusinessId(movement.getTransactionId(), context.businessId())
                .orElseThrow(() -> new ReconciliationValidationException("accountMovementId", "Underlying financial transaction not found"));

        if (tx.getReversedTransactionId() != null) {
            throw new ReconciliationValidationException("accountMovementId", "Cannot reconcile a compensating/reversal transaction");
        }

        Optional<FinancialTransaction> reversingTx = transactionRepository.findByReversedTransactionIdAndBusinessId(tx.getId(), context.businessId());
        if (reversingTx.isPresent()) {
            throw new ReconciliationValidationException("accountMovementId", "Underlying transaction has been reversed and cannot be reconciled");
        }

        // 3. Verify movement is not already matched to an active reconciliation
        boolean movementAlreadyMatched = matchRepository.existsByBusinessIdAndAccountMovementIdAndStatus(
                context.businessId(),
                movement.getId(),
                ReconciliationMatchStatus.ACTIVE
        );
        if (movementAlreadyMatched) {
            throw new ReconciliationConflictException("Account movement is already matched to another statement entry");
        }

        // 4. Create match
        long daysDiff = Math.abs(ChronoUnit.DAYS.between(entry.getPostedDate(), movement.getEffectiveDate()));
        ReconciliationMatchMethod method = (daysDiff == 0) ? ReconciliationMatchMethod.EXACT : ReconciliationMatchMethod.SUGGESTED;
        int confidence = (daysDiff == 0) ? 100 : 80;

        BankReconciliationMatch match = new BankReconciliationMatch(
                context.businessId(),
                entry.getId(),
                movement.getId(),
                entry.getAccountId(),
                method,
                confidence,
                context.userId()
        );

        matchRepository.save(match);

        // 5. Update entry status
        entry.setStatus(StatementEntryStatus.MATCHED);
        entry.setExclusionReason(null);
        entryRepository.save(entry);

        return getEntryDetail(context, entryId);
    }

    @Transactional
    public StatementEntryDetailResponse unmatchEntry(
            BusinessAccessContext context,
            UUID entryId,
            ReconciliationUnmatchRequest request
    ) {
        BankStatementEntry entry = entryRepository.findForUpdateByIdAndBusinessId(entryId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("BankStatementEntry not found: " + entryId));

        BankReconciliationMatch match = matchRepository.findByBusinessIdAndStatementEntryIdAndStatus(
                context.businessId(),
                entry.getId(),
                ReconciliationMatchStatus.ACTIVE
        ).orElseThrow(() -> new ReconciliationValidationException("status", "Statement entry is not currently matched"));

        match.setStatus(ReconciliationMatchStatus.CANCELLED);
        match.setUnmatchedAt(Instant.now());
        match.setUnmatchedByUserId(context.userId());
        match.setUnmatchReason(request != null && request.reason() != null ? request.reason().trim() : "Unmatched by user");
        matchRepository.save(match);

        entry.setStatus(StatementEntryStatus.UNMATCHED);
        entryRepository.save(entry);

        return getEntryDetail(context, entryId);
    }

    @Transactional
    public StatementEntryDetailResponse excludeEntry(
            BusinessAccessContext context,
            UUID entryId,
            ReconciliationExcludeRequest request
    ) {
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new ReconciliationValidationException("reason", "Exclusion reason is required");
        }

        BankStatementEntry entry = entryRepository.findForUpdateByIdAndBusinessId(entryId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("BankStatementEntry not found: " + entryId));

        // If currently matched, cancel match first
        Optional<BankReconciliationMatch> activeMatch = matchRepository.findByBusinessIdAndStatementEntryIdAndStatus(
                context.businessId(),
                entry.getId(),
                ReconciliationMatchStatus.ACTIVE
        );
        if (activeMatch.isPresent()) {
            BankReconciliationMatch match = activeMatch.get();
            match.setStatus(ReconciliationMatchStatus.CANCELLED);
            match.setUnmatchedAt(Instant.now());
            match.setUnmatchedByUserId(context.userId());
            match.setUnmatchReason("Entry excluded: " + request.reason().trim());
            matchRepository.save(match);
        }

        entry.setStatus(StatementEntryStatus.EXCLUDED);
        entry.setExclusionReason(request.reason().trim());
        entryRepository.save(entry);

        return getEntryDetail(context, entryId);
    }

    @Transactional
    public DraftDetailResponse createDraftFromEntry(BusinessAccessContext context, UUID entryId) {
        BankStatementEntry entry = entryRepository.findForUpdateByIdAndBusinessId(entryId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("BankStatementEntry not found: " + entryId));

        // If draft already linked, return it or check its existence
        if (entry.getLinkedDraftId() != null) {
            try {
                return recordDraftService.getDraft(context, entry.getLinkedDraftId());
            } catch (ResourceNotFoundException e) {
                // Draft was discarded/deleted, allow creating a new one
            }
        }

        String desc = entry.getDescription();
        if (desc == null || desc.isBlank()) {
            desc = entry.getBankReference() != null ? entry.getBankReference() : "Bank statement entry " + entry.getPostedDate();
        }

        // Leave eventType null so classification remains unassigned until explicit user review
        DraftCreateRequest draftRequest = new DraftCreateRequest(
                entry.getAccountId(),
                null,
                entry.getPostedDate().toString(),
                entry.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                desc,
                null
        );

        DraftDetailResponse draft = recordDraftService.createDraft(
                context,
                "RECON-DRAFT-" + entry.getId(),
                draftRequest
        );

        entry.setLinkedDraftId(draft.id());
        entryRepository.save(entry);

        return draft;
    }

    @Transactional(readOnly = true)
    public AccountReconciliationSummaryResponse getAccountSummary(BusinessAccessContext context, UUID accountId) {
        FinancialAccount account = accountRepository.findByIdAndBusinessId(accountId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Financial account not found"));

        if (account.getAccountType() != AccountType.BANK) {
            throw new ReconciliationValidationException("accountId", "Reconciliation summary is only applicable for BANK accounts");
        }

        AccountDetailResponse detail = accountReadService.getAccountDetail(context.businessId(), accountId);
        BigDecimal ledgerBalance = BigDecimal.ZERO;
        if (detail.currentBalance() != null) {
            try {
                ledgerBalance = new BigDecimal(detail.currentBalance());
            } catch (NumberFormatException ignored) {}
        }

        long totalEntries = entryRepository.countByBusinessIdAndAccountId(context.businessId(), accountId);
        long matchedEntries = entryRepository.countByBusinessIdAndAccountIdAndStatus(context.businessId(), accountId, StatementEntryStatus.MATCHED);
        long unmatchedEntries = entryRepository.countByBusinessIdAndAccountIdAndStatus(context.businessId(), accountId, StatementEntryStatus.UNMATCHED);
        long excludedEntries = entryRepository.countByBusinessIdAndAccountIdAndStatus(context.businessId(), accountId, StatementEntryStatus.EXCLUDED);

        // Direction sums: DECREASE is debit/withdrawal, INCREASE is credit/deposit
        BigDecimal importedDebitTotal = BigDecimal.ZERO;
        BigDecimal importedCreditTotal = BigDecimal.ZERO;
        for (var dirSum : entryRepository.sumAmountsByDirection(context.businessId(), accountId)) {
            if (dirSum.getDirection() == MovementDirection.DECREASE && dirSum.getTotalAmount() != null) {
                importedDebitTotal = dirSum.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
            } else if (dirSum.getDirection() == MovementDirection.INCREASE && dirSum.getTotalAmount() != null) {
                importedCreditTotal = dirSum.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
            }
        }

        // Status sums
        BigDecimal matchedTotal = BigDecimal.ZERO;
        BigDecimal unmatchedTotal = BigDecimal.ZERO;
        for (var stSum : entryRepository.sumAmountsByStatus(context.businessId(), accountId)) {
            if (stSum.getStatus() == StatementEntryStatus.MATCHED && stSum.getTotalAmount() != null) {
                matchedTotal = stSum.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
            } else if (stSum.getStatus() == StatementEntryStatus.UNMATCHED && stSum.getTotalAmount() != null) {
                unmatchedTotal = stSum.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
            }
        }

        // Unmatched movements count
        List<UnmatchedMovementResponse> unmatchedMovements = getUnmatchedMovements(context, accountId);

        // Last reconciled at
        List<BankReconciliationMatch> activeMatches = matchRepository.findByBusinessIdAndAccountIdAndStatus(
                context.businessId(),
                accountId,
                ReconciliationMatchStatus.ACTIVE
        );
        Instant lastReconciledAt = activeMatches.stream()
                .map(BankReconciliationMatch::getMatchedAt)
                .max(Instant::compareTo)
                .orElse(null);

        return new AccountReconciliationSummaryResponse(
                account.getId(),
                account.getAccountName(),
                account.getAccountType().name(),
                account.getCurrency(),
                ledgerBalance,
                totalEntries,
                matchedEntries,
                unmatchedEntries,
                excludedEntries,
                unmatchedMovements.size(),
                importedDebitTotal,
                importedCreditTotal,
                matchedTotal,
                unmatchedTotal,
                account.getOpeningDate(),
                lastReconciledAt
        );
    }

    @Transactional(readOnly = true)
    public List<UnmatchedMovementResponse> getUnmatchedMovements(BusinessAccessContext context, UUID accountId) {
        accountRepository.findByIdAndBusinessId(accountId, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Financial account not found: " + accountId));

        Set<UUID> activeMatchedMovementIds = matchRepository.findActiveMatchedMovementIds(
                context.businessId(),
                accountId
        );

        List<AccountMovement> movements = movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(
                accountId
        );

        if (movements.isEmpty()) {
            return Collections.emptyList();
        }

        Set<UUID> txIds = movements.stream().map(AccountMovement::getTransactionId).collect(Collectors.toSet());
        Map<UUID, FinancialTransaction> txMap = transactionRepository.findAllById(txIds).stream()
                .filter(t -> t.getBusinessId().equals(context.businessId()))
                .collect(Collectors.toMap(FinancialTransaction::getId, t -> t));

        List<FinancialTransaction> reversalTxList = transactionRepository.findByBusinessIdAndReversedTransactionIdIn(
                context.businessId(),
                txIds
        );
        Set<UUID> reversedTxIds = reversalTxList.stream()
                .map(FinancialTransaction::getReversedTransactionId)
                .collect(Collectors.toSet());

        Set<UUID> cpIds = txMap.values().stream()
                .map(FinancialTransaction::getCounterpartyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> cpNameMap = cpIds.isEmpty() ? Collections.emptyMap() :
                counterpartyRepository.findAllById(cpIds).stream()
                        .collect(Collectors.toMap(Counterparty::getId, Counterparty::getDisplayName));

        List<UnmatchedMovementResponse> result = new ArrayList<>();
        for (AccountMovement mov : movements) {
            if (activeMatchedMovementIds.contains(mov.getId())) continue;

            FinancialTransaction tx = txMap.get(mov.getTransactionId());
            if (tx == null) continue;
            if (tx.getReversedTransactionId() != null) continue;
            if (reversedTxIds.contains(tx.getId())) continue;

            String cpName = tx.getCounterpartyId() != null ? cpNameMap.get(tx.getCounterpartyId()) : null;

            result.add(new UnmatchedMovementResponse(
                    mov.getId(),
                    tx.getId(),
                    tx.getEventType(),
                    mov.getMovementDirection(),
                    mov.getAmount(),
                    mov.getCurrency(),
                    mov.getEffectiveDate(),
                    tx.getDescription(),
                    cpName
            ));
        }

        return result;
    }

    private StatementEntryResponse toEntryResponse(BankStatementEntry e) {
        boolean duplicateWarning = checkDuplicateWarning(e);

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

    private boolean checkDuplicateWarning(BankStatementEntry e) {
        List<BankStatementEntry> sameFingerprint = entryRepository.findByBusinessIdAndAccountIdAndSourceFingerprint(
                e.getBusinessId(),
                e.getAccountId(),
                e.getSourceFingerprint()
        );
        return sameFingerprint.size() > 1;
    }
}
