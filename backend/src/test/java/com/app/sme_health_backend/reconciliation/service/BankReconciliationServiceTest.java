package com.app.sme_health_backend.reconciliation.service;

import com.app.sme_health_backend.accounting.dto.AccountDetailResponse;
import com.app.sme_health_backend.accounting.entity.AccountMovement;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialTransaction;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.AccountMovementRepository;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialTransactionRepository;
import com.app.sme_health_backend.accounting.service.AccountReadService;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.accounting.draft.DraftDetailResponse;
import com.app.sme_health_backend.accounting.draft.RecordDraftService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankReconciliationServiceTest {

    @Mock
    private BankStatementEntryRepository entryRepository;

    @Mock
    private BankReconciliationMatchRepository matchRepository;

    @Mock
    private AccountMovementRepository movementRepository;

    @Mock
    private FinancialTransactionRepository transactionRepository;

    @Mock
    private FinancialAccountRepository accountRepository;

    @Mock
    private CounterpartyRepository counterpartyRepository;

    @Mock
    private RecordDraftService recordDraftService;

    @Mock
    private AccountReadService accountReadService;

    @InjectMocks
    private BankReconciliationService reconciliationService;

    private BusinessAccessContext context;
    private UUID businessId;
    private UUID accountId;
    private UUID entryId;
    private UUID movementId;
    private UUID txId;
    private FinancialAccount bankAccount;
    private BankStatementEntry statementEntry;
    private AccountMovement movement;
    private FinancialTransaction transaction;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        entryId = UUID.randomUUID();
        movementId = UUID.randomUUID();
        txId = UUID.randomUUID();

        context = new BusinessAccessContext(UUID.randomUUID(), businessId, MembershipRole.OWNER);

        bankAccount = new FinancialAccount(
                businessId,
                "HBL Main Account",
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("100000.00"),
                LocalDate.of(2026, 1, 1),
                AccountStatus.ACTIVE,
                null,
                null,
                "Operating bank account"
        );

        statementEntry = new BankStatementEntry(
                businessId,
                UUID.randomUUID(),
                accountId,
                1,
                LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 10, 15),
                MovementDirection.INCREASE, // Credit PKR 50,000
                new BigDecimal("50000.00"),
                "PKR",
                "Customer Payment - ABC Traders",
                "REF-9988",
                "EXT-001",
                new BigDecimal("150000.00"),
                "fp-12345"
        );
        statementEntry.setId(entryId);

        movement = new AccountMovement(
                businessId,
                txId,
                accountId,
                MovementDirection.INCREASE,
                new BigDecimal("50000.00"),
                "PKR",
                LocalDate.of(2026, 10, 15)
        );
        movement.setId(movementId);

        transaction = new FinancialTransaction(
                businessId,
                accountId,
                TransactionEventType.DIRECT_SALE_RECEIPT,
                MovementDirection.INCREASE,
                new BigDecimal("50000.00"),
                "PKR",
                new BigDecimal("50000.00"),
                LocalDate.of(2026, 10, 15),
                "Customer Payment - ABC Traders",
                context.userId()
        );
        transaction.setId(txId);
    }

    @Test
    @DisplayName("1. Exact amount, same account, same direction produces high-confidence candidate")
    void testExactAmountSameAccountSameDirectionCandidate() {
        when(entryRepository.findByIdAndBusinessId(entryId, businessId))
                .thenReturn(Optional.of(statementEntry));
        when(matchRepository.findActiveMatchedMovementIds(businessId, accountId))
                .thenReturn(Collections.emptySet());
        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(accountId))
                .thenReturn(List.of(movement));
        when(transactionRepository.findAllById(Set.of(txId)))
                .thenReturn(List.of(transaction));
        when(transactionRepository.findByBusinessIdAndReversedTransactionIdIn(eq(businessId), any()))
                .thenReturn(Collections.emptyList());

        List<ReconciliationMatchCandidateResponse> candidates = reconciliationService.findCandidates(context, entryId);

        assertEquals(1, candidates.size());
        var c = candidates.get(0);
        assertEquals(movement.getId(), c.accountMovementId());
        assertEquals(transaction.getId(), c.transactionId());
        assertEquals(MovementDirection.INCREASE, c.movementDirection());
        assertEquals(new BigDecimal("50000.00"), c.amount());
        assertTrue(c.confidenceScore() >= 90);
        assertTrue(c.matchReasons().stream().anyMatch(r -> r.contains("Exact amount")));
    }

    @Test
    @DisplayName("2. Opposite direction movement is rejected from candidates")
    void testOppositeDirectionRejection() {
        movement.setMovementDirection(MovementDirection.DECREASE);

        when(entryRepository.findByIdAndBusinessId(entryId, businessId))
                .thenReturn(Optional.of(statementEntry));
        when(matchRepository.findActiveMatchedMovementIds(businessId, accountId))
                .thenReturn(Collections.emptySet());
        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(accountId))
                .thenReturn(List.of(movement));
        when(transactionRepository.findAllById(Set.of(txId)))
                .thenReturn(List.of(transaction));
        when(transactionRepository.findByBusinessIdAndReversedTransactionIdIn(eq(businessId), any()))
                .thenReturn(Collections.emptyList());

        List<ReconciliationMatchCandidateResponse> candidates = reconciliationService.findCandidates(context, entryId);
        assertTrue(candidates.isEmpty(), "Opposite direction must not be considered a match candidate");
    }

    @Test
    @DisplayName("3. Wrong account movement is rejected")
    void testWrongAccountRejection() {
        when(entryRepository.findByIdAndBusinessId(entryId, businessId))
                .thenReturn(Optional.of(statementEntry));
        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(accountId))
                .thenReturn(Collections.emptyList());

        List<ReconciliationMatchCandidateResponse> candidates = reconciliationService.findCandidates(context, entryId);
        assertTrue(candidates.isEmpty());
    }

    @Test
    @DisplayName("4. Wrong business transaction is filtered out")
    void testWrongBusinessRejection() {
        UUID otherBusinessId = UUID.randomUUID();
        transaction.setBusinessId(otherBusinessId);

        when(entryRepository.findByIdAndBusinessId(entryId, businessId))
                .thenReturn(Optional.of(statementEntry));
        when(matchRepository.findActiveMatchedMovementIds(businessId, accountId))
                .thenReturn(Collections.emptySet());
        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(accountId))
                .thenReturn(List.of(movement));
        when(transactionRepository.findAllById(Set.of(txId)))
                .thenReturn(List.of(transaction));
        when(transactionRepository.findByBusinessIdAndReversedTransactionIdIn(eq(businessId), any()))
                .thenReturn(Collections.emptyList());

        List<ReconciliationMatchCandidateResponse> candidates = reconciliationService.findCandidates(context, entryId);
        assertTrue(candidates.isEmpty());
    }

    @Test
    @DisplayName("5. Different amount movement is rejected")
    void testDifferentAmountRejection() {
        movement.setAmount(new BigDecimal("50001.00"));

        when(entryRepository.findByIdAndBusinessId(entryId, businessId))
                .thenReturn(Optional.of(statementEntry));
        when(matchRepository.findActiveMatchedMovementIds(businessId, accountId))
                .thenReturn(Collections.emptySet());
        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(accountId))
                .thenReturn(List.of(movement));
        when(transactionRepository.findAllById(Set.of(txId)))
                .thenReturn(List.of(transaction));
        when(transactionRepository.findByBusinessIdAndReversedTransactionIdIn(eq(businessId), any()))
                .thenReturn(Collections.emptyList());

        List<ReconciliationMatchCandidateResponse> candidates = reconciliationService.findCandidates(context, entryId);
        assertTrue(candidates.isEmpty(), "Different amount must be rejected under exact amount policy");
    }

    @Test
    @DisplayName("6. Same amount with nearby date ranks lower than exact date")
    void testNearbyDateCandidateRanking() {
        AccountMovement exactDateMov = new AccountMovement(
                businessId, txId, accountId, MovementDirection.INCREASE, new BigDecimal("50000.00"), "PKR",
                LocalDate.of(2026, 10, 15)
        );
        UUID exactMovId = UUID.randomUUID();
        exactDateMov.setId(exactMovId);

        UUID txId2 = UUID.randomUUID();
        AccountMovement nearbyDateMov = new AccountMovement(
                businessId, txId2, accountId, MovementDirection.INCREASE, new BigDecimal("50000.00"), "PKR",
                LocalDate.of(2026, 10, 17)
        );
        nearbyDateMov.setId(UUID.randomUUID());

        FinancialTransaction tx2 = new FinancialTransaction(
                businessId, accountId, TransactionEventType.DIRECT_SALE_RECEIPT, MovementDirection.INCREASE,
                new BigDecimal("50000.00"), "PKR", new BigDecimal("50000.00"), LocalDate.of(2026, 10, 17),
                "Sale 2", context.userId()
        );
        tx2.setId(txId2);

        when(entryRepository.findByIdAndBusinessId(entryId, businessId))
                .thenReturn(Optional.of(statementEntry));
        when(matchRepository.findActiveMatchedMovementIds(businessId, accountId))
                .thenReturn(Collections.emptySet());
        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(accountId))
                .thenReturn(List.of(nearbyDateMov, exactDateMov));
        when(transactionRepository.findAllById(any()))
                .thenReturn(List.of(transaction, tx2));
        when(transactionRepository.findByBusinessIdAndReversedTransactionIdIn(eq(businessId), any()))
                .thenReturn(Collections.emptyList());

        List<ReconciliationMatchCandidateResponse> candidates = reconciliationService.findCandidates(context, entryId);

        assertEquals(2, candidates.size());
        assertEquals(exactMovId, candidates.get(0).accountMovementId());
        assertTrue(candidates.get(0).confidenceScore() > candidates.get(1).confidenceScore());
    }

    @Test
    @DisplayName("7. Multiple ambiguous candidates are returned for user review")
    void testAmbiguousCandidatesReturned() {
        UUID txId2 = UUID.randomUUID();
        AccountMovement mov2 = new AccountMovement(
                businessId, txId2, accountId, MovementDirection.INCREASE, new BigDecimal("50000.00"), "PKR",
                LocalDate.of(2026, 10, 15)
        );
        mov2.setId(UUID.randomUUID());

        FinancialTransaction tx2 = new FinancialTransaction(
                businessId, accountId, TransactionEventType.DIRECT_SALE_RECEIPT, MovementDirection.INCREASE,
                new BigDecimal("50000.00"), "PKR", new BigDecimal("50000.00"), LocalDate.of(2026, 10, 15),
                "Sale 2", context.userId()
        );
        tx2.setId(txId2);

        when(entryRepository.findByIdAndBusinessId(entryId, businessId))
                .thenReturn(Optional.of(statementEntry));
        when(matchRepository.findActiveMatchedMovementIds(businessId, accountId))
                .thenReturn(Collections.emptySet());
        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(accountId))
                .thenReturn(List.of(movement, mov2));
        when(transactionRepository.findAllById(any()))
                .thenReturn(List.of(transaction, tx2));
        when(transactionRepository.findByBusinessIdAndReversedTransactionIdIn(eq(businessId), any()))
                .thenReturn(Collections.emptyList());

        List<ReconciliationMatchCandidateResponse> candidates = reconciliationService.findCandidates(context, entryId);

        assertEquals(2, candidates.size());
        assertFalse(candidates.get(0).accountMovementId().equals(candidates.get(1).accountMovementId()));
    }

    @Test
    @DisplayName("8. Reference text matching boosts confidence score")
    void testReferenceMatchingScoreBoost() {
        transaction.setDescription("Payment with bank reference REF-9988 received");

        when(entryRepository.findByIdAndBusinessId(entryId, businessId))
                .thenReturn(Optional.of(statementEntry));
        when(matchRepository.findActiveMatchedMovementIds(businessId, accountId))
                .thenReturn(Collections.emptySet());
        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(accountId))
                .thenReturn(List.of(movement));
        when(transactionRepository.findAllById(Set.of(txId)))
                .thenReturn(List.of(transaction));
        when(transactionRepository.findByBusinessIdAndReversedTransactionIdIn(eq(businessId), any()))
                .thenReturn(Collections.emptyList());

        List<ReconciliationMatchCandidateResponse> candidates = reconciliationService.findCandidates(context, entryId);

        assertEquals(1, candidates.size());
        assertEquals(100, candidates.get(0).confidenceScore());
        assertTrue(candidates.get(0).matchReasons().stream().anyMatch(r -> r.contains("reference matches")));
    }

    @Test
    @DisplayName("9. Missing reference still matches on amount, account and direction")
    void testMissingReferenceCandidateHandling() {
        statementEntry.setBankReference(null);

        when(entryRepository.findByIdAndBusinessId(entryId, businessId))
                .thenReturn(Optional.of(statementEntry));
        when(matchRepository.findActiveMatchedMovementIds(businessId, accountId))
                .thenReturn(Collections.emptySet());
        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(accountId))
                .thenReturn(List.of(movement));
        when(transactionRepository.findAllById(Set.of(txId)))
                .thenReturn(List.of(transaction));
        when(transactionRepository.findByBusinessIdAndReversedTransactionIdIn(eq(businessId), any()))
                .thenReturn(Collections.emptyList());

        List<ReconciliationMatchCandidateResponse> candidates = reconciliationService.findCandidates(context, entryId);

        assertEquals(1, candidates.size());
        assertEquals(90, candidates.get(0).confidenceScore());
    }

    @Test
    @DisplayName("13. Already-matched movement is rejected from candidates")
    void testAlreadyMatchedMovementExcluded() {
        when(entryRepository.findByIdAndBusinessId(entryId, businessId))
                .thenReturn(Optional.of(statementEntry));
        when(matchRepository.findActiveMatchedMovementIds(businessId, accountId))
                .thenReturn(Set.of(movement.getId()));
        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(accountId))
                .thenReturn(List.of(movement));
        when(transactionRepository.findAllById(Set.of(txId)))
                .thenReturn(List.of(transaction));
        when(transactionRepository.findByBusinessIdAndReversedTransactionIdIn(eq(businessId), any()))
                .thenReturn(Collections.emptyList());

        List<ReconciliationMatchCandidateResponse> candidates = reconciliationService.findCandidates(context, entryId);
        assertTrue(candidates.isEmpty(), "Already matched movements must not appear as candidates");
    }

    @Test
    @DisplayName("14. Confirm match on already-matched statement throws conflict")
    void testMatchAlreadyMatchedStatementThrowsConflict() {
        statementEntry.setStatus(StatementEntryStatus.MATCHED);
        when(entryRepository.findForUpdateByIdAndBusinessId(entryId, businessId))
                .thenReturn(Optional.of(statementEntry));

        ReconciliationMatchRequest req = new ReconciliationMatchRequest(movement.getId());

        assertThrows(ReconciliationValidationException.class, () ->
                reconciliationService.matchEntry(context, entryId, req)
        );
    }

    @Test
    @DisplayName("15. Confirm match when movement is already matched throws conflict")
    void testMatchAlreadyMatchedMovementThrowsConflict() {
        when(entryRepository.findForUpdateByIdAndBusinessId(entryId, businessId))
                .thenReturn(Optional.of(statementEntry));
        when(movementRepository.findById(movement.getId()))
                .thenReturn(Optional.of(movement));
        when(transactionRepository.findByIdAndBusinessId(txId, businessId))
                .thenReturn(Optional.of(transaction));
        when(transactionRepository.findByReversedTransactionIdAndBusinessId(txId, businessId))
                .thenReturn(Optional.empty());
        when(matchRepository.existsByBusinessIdAndAccountMovementIdAndStatus(businessId, movement.getId(), ReconciliationMatchStatus.ACTIVE))
                .thenReturn(true);

        ReconciliationMatchRequest req = new ReconciliationMatchRequest(movement.getId());

        assertThrows(ReconciliationConflictException.class, () ->
                reconciliationService.matchEntry(context, entryId, req)
        );
    }

    @Test
    @DisplayName("16. Unmatch creates auditable cancelled record and resets statement entry to UNMATCHED")
    void testUnmatchAuditableLifecycle() {
        statementEntry.setStatus(StatementEntryStatus.MATCHED);

        BankReconciliationMatch activeMatch = new BankReconciliationMatch(
                businessId,
                statementEntry.getId(),
                movement.getId(),
                accountId,
                ReconciliationMatchMethod.EXACT,
                90,
                context.userId()
        );

        when(entryRepository.findForUpdateByIdAndBusinessId(entryId, businessId))
                .thenReturn(Optional.of(statementEntry));
        when(entryRepository.findByIdAndBusinessId(entryId, businessId))
                .thenReturn(Optional.of(statementEntry));
        when(matchRepository.findByBusinessIdAndStatementEntryIdAndStatus(businessId, entryId, ReconciliationMatchStatus.ACTIVE))
                .thenReturn(Optional.of(activeMatch));
        when(matchRepository.save(any(BankReconciliationMatch.class))).thenAnswer(i -> i.getArgument(0));

        ReconciliationUnmatchRequest req = new ReconciliationUnmatchRequest("Matched by mistake");
        StatementEntryDetailResponse response = reconciliationService.unmatchEntry(context, entryId, req);

        assertNotNull(response);
        assertEquals(StatementEntryStatus.UNMATCHED, response.status());
        assertEquals(StatementEntryStatus.UNMATCHED, statementEntry.getStatus());
        assertEquals(ReconciliationMatchStatus.CANCELLED, activeMatch.getStatus());
        assertEquals(context.userId(), activeMatch.getUnmatchedByUserId());
        assertNotNull(activeMatch.getUnmatchedAt());
        assertEquals("Matched by mistake", activeMatch.getUnmatchReason());
    }

    @Test
    @DisplayName("17. Reversed original transaction is excluded from candidates")
    void testReversedTransactionsExcludedFromCandidates() {
        FinancialTransaction reversalTx = new FinancialTransaction(
                businessId, accountId, TransactionEventType.DIRECT_SALE_RECEIPT, MovementDirection.DECREASE,
                new BigDecimal("50000.00"), "PKR", new BigDecimal("50000.00"), LocalDate.of(2026, 10, 16),
                "Reversal of sale", context.userId()
        );
        reversalTx.setReversedTransactionId(txId);

        when(entryRepository.findByIdAndBusinessId(entryId, businessId))
                .thenReturn(Optional.of(statementEntry));
        when(matchRepository.findActiveMatchedMovementIds(businessId, accountId))
                .thenReturn(Collections.emptySet());
        when(movementRepository.findByAccountIdOrderByEffectiveDateAscCreatedAtAscIdAsc(accountId))
                .thenReturn(List.of(movement));
        when(transactionRepository.findAllById(Set.of(txId)))
                .thenReturn(List.of(transaction));
        when(transactionRepository.findByBusinessIdAndReversedTransactionIdIn(eq(businessId), any()))
                .thenReturn(List.of(reversalTx));

        List<ReconciliationMatchCandidateResponse> candidates = reconciliationService.findCandidates(context, entryId);
        assertTrue(candidates.isEmpty(), "Reversed original transactions must not appear as candidates");
    }

    @Test
    @DisplayName("20. Financial Posting Invariant: Confirming match creates zero FinancialTransaction or AccountMovement")
    void testMatchingDoesNotCreateFinancialPostings() {
        when(entryRepository.findForUpdateByIdAndBusinessId(entryId, businessId))
                .thenReturn(Optional.of(statementEntry));
        when(entryRepository.findByIdAndBusinessId(entryId, businessId))
                .thenReturn(Optional.of(statementEntry));
        when(movementRepository.findById(movement.getId()))
                .thenReturn(Optional.of(movement));
        when(transactionRepository.findByIdAndBusinessId(txId, businessId))
                .thenReturn(Optional.of(transaction));
        when(transactionRepository.findByReversedTransactionIdAndBusinessId(txId, businessId))
                .thenReturn(Optional.empty());
        when(matchRepository.existsByBusinessIdAndAccountMovementIdAndStatus(businessId, movement.getId(), ReconciliationMatchStatus.ACTIVE))
                .thenReturn(false);
        when(matchRepository.save(any(BankReconciliationMatch.class))).thenAnswer(i -> i.getArgument(0));

        ReconciliationMatchRequest req = new ReconciliationMatchRequest(movement.getId());
        StatementEntryDetailResponse response = reconciliationService.matchEntry(context, entryId, req);

        assertNotNull(response);
        assertEquals(StatementEntryStatus.MATCHED, statementEntry.getStatus());

        // STRICT INVARIANT: No writes to financial transactions or account movements
        verify(transactionRepository, never()).save(any());
        verify(movementRepository, never()).save(any());
    }
}
