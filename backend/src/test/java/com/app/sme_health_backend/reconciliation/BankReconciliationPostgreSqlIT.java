package com.app.sme_health_backend.reconciliation;

import com.app.sme_health_backend.accounting.dto.TransactionDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionPostRequest;
import com.app.sme_health_backend.accounting.dto.TransactionReverseRequest;
import com.app.sme_health_backend.accounting.dto.TransferPostRequest;
import com.app.sme_health_backend.accounting.draft.DraftDetailResponse;
import com.app.sme_health_backend.accounting.draft.DraftRecordRequest;
import com.app.sme_health_backend.accounting.draft.DraftRecordResponse;
import com.app.sme_health_backend.accounting.draft.RecordDraftService;
import com.app.sme_health_backend.accounting.entity.AccountMovement;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.AccountMovementRepository;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialTransactionRepository;
import com.app.sme_health_backend.accounting.service.TransactionPostService;
import com.app.sme_health_backend.accounting.service.TransactionReverseService;
import com.app.sme_health_backend.accounting.service.TransferService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.dto.CreateBusinessRequest;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.service.BusinessService;
import com.app.sme_health_backend.reconciliation.dto.*;
import com.app.sme_health_backend.reconciliation.entity.BankReconciliationMatch;
import com.app.sme_health_backend.reconciliation.entity.BankStatementEntry;
import com.app.sme_health_backend.reconciliation.exception.ReconciliationConflictException;
import com.app.sme_health_backend.reconciliation.model.ReconciliationMatchStatus;
import com.app.sme_health_backend.reconciliation.model.StatementEntryStatus;
import com.app.sme_health_backend.reconciliation.repository.BankReconciliationMatchRepository;
import com.app.sme_health_backend.reconciliation.repository.BankStatementEntryRepository;
import com.app.sme_health_backend.reconciliation.repository.BankStatementImportRepository;
import com.app.sme_health_backend.reconciliation.service.BankReconciliationService;
import com.app.sme_health_backend.reconciliation.service.BankStatementImportService;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BankReconciliationPostgreSqlIT extends DisposablePostgres {

    @Autowired
    private BankStatementImportService importService;

    @Autowired
    private BankReconciliationService reconciliationService;

    @Autowired
    private BankStatementImportRepository importRepository;

    @Autowired
    private BankStatementEntryRepository entryRepository;

    @Autowired
    private BankReconciliationMatchRepository matchRepository;

    @Autowired
    private FinancialAccountRepository accountRepository;

    @Autowired
    private FinancialTransactionRepository transactionRepository;

    @Autowired
    private AccountMovementRepository movementRepository;

    @Autowired
    private TransactionPostService transactionPostService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private TransactionReverseService transactionReverseService;

    @Autowired
    private RecordDraftService recordDraftService;

    @Autowired
    private BusinessService businessService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private BusinessAccessContext context;
    private UUID businessId;
    private UUID bankAccountAId;
    private UUID bankAccountBId;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(ZoneId.of("Asia/Karachi"));
        String unique = UUID.randomUUID().toString().substring(0, 8);

        AppUser user = new AppUser();
        user.setEmail("owner-" + unique + "@example.com");
        user.setPasswordHash("hash_secret");
        user.setFullName("Reconciliation Owner " + unique);
        user = userRepository.saveAndFlush(user);

        businessId = businessService.createBusiness(
                new CreateBusinessRequest("Reconciliation Biz " + unique, "retail", "en", null, false, null, null, null),
                user.getId()
        ).businessId();

        context = new BusinessAccessContext(user.getId(), businessId, MembershipRole.OWNER);

        // Account A (HBL)
        FinancialAccount accA = new FinancialAccount(
                businessId,
                "HBL Bank " + unique,
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("500000.00"),
                today.minusDays(30),
                AccountStatus.ACTIVE,
                null,
                null,
                "HBL Main Account"
        );
        accA = accountRepository.saveAndFlush(accA);
        bankAccountAId = accA.getId();

        // Account B (Meezan)
        FinancialAccount accB = new FinancialAccount(
                businessId,
                "Meezan Bank " + unique,
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("200000.00"),
                today.minusDays(30),
                AccountStatus.ACTIVE,
                null,
                null,
                "Meezan Islamic Account"
        );
        accB = accountRepository.saveAndFlush(accB);
        bankAccountBId = accB.getId();
    }

    @Test
    @DisplayName("V28 migration integrity: tables, composite keys and partial unique indices exist")
    void testFullV28MigrationAndSchemaIntegrity() {
        Integer importCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'bank_statement_imports'",
                Integer.class
        );
        assertEquals(1, importCount);

        Integer entryCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'bank_statement_entries'",
                Integer.class
        );
        assertEquals(1, entryCount);

        Integer matchCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'bank_reconciliation_matches'",
                Integer.class
        );
        assertEquals(1, matchCount);

        // Verify partial unique index for 1-to-1 active match exists
        Integer indexCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE indexname = 'uq_reconciliation_active_statement_entry'",
                Integer.class
        );
        assertEquals(1, indexCount);

        Integer movIndexCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE indexname = 'uq_reconciliation_active_account_movement'",
                Integer.class
        );
        assertEquals(1, movIndexCount);
    }

    @Test
    @DisplayName("Statement import persistence, entry reload, and idempotent replay")
    void testStatementImportPersistenceAndReplay() {
        List<StatementEntryItemRequest> items = List.of(
                new StatementEntryItemRequest(1, today.minusDays(5), null, MovementDirection.INCREASE,
                        new BigDecimal("45000.00"), "Customer deposit, Karachi", "CHQ-901", null, null),
                new StatementEntryItemRequest(2, today.minusDays(3), null, MovementDirection.DECREASE,
                        new BigDecimal("12000.00"), "Office Utilities", "BILL-555", null, null)
        );

        StatementImportRequest req = new StatementImportRequest(
                bankAccountAId, "statement_oct.csv", null, "{\"dateCol\":0}",
                new BigDecimal("100000.00"), new BigDecimal("133000.00"), items
        );

        String idempKey = "idemp-" + UUID.randomUUID();
        StatementImportResponse resp = importService.importStatement(context, idempKey, req);
        assertNotNull(resp);
        assertEquals(2, resp.totalEntries());
        assertEquals(bankAccountAId, resp.accountId());

        // Verify persisted entries
        List<BankStatementEntry> entries = entryRepository.findByBusinessIdAndImportIdOrderBySourceRowNumberAsc(
                businessId, resp.id()
        );
        assertEquals(2, entries.size());
        assertEquals(MovementDirection.INCREASE, entries.get(0).getDirection());
        assertEquals(new BigDecimal("45000.00"), entries.get(0).getAmount());
        assertEquals(MovementDirection.DECREASE, entries.get(1).getDirection());
        assertEquals(new BigDecimal("12000.00"), entries.get(1).getAmount());

        // Replay identical import request -> idempotent cached response
        StatementImportResponse replayResp = importService.importStatement(context, idempKey, req);
        assertEquals(resp.id(), replayResp.id());

        // Changed payload under same idempotency key -> Conflict
        StatementImportRequest conflictReq = new StatementImportRequest(
                bankAccountAId, "statement_oct.csv", null, "{\"dateCol\":0}",
                new BigDecimal("100000.00"), new BigDecimal("133000.00"),
                List.of(new StatementEntryItemRequest(1, today, null, MovementDirection.INCREASE, new BigDecimal("100.00"), "changed", null, null, null))
        );
        assertThrows(ReconciliationConflictException.class, () ->
                importService.importStatement(context, idempKey, conflictReq)
        );
    }

    @Test
    @DisplayName("Exact match, candidate ranking and 1-to-1 active match uniqueness")
    void testExactMatchAndOneToOneActiveConstraint() {
        // 1. Post real sale receipt in FinSight (PKR 50,000 to Bank A)
        TransactionPostRequest postReq = new TransactionPostRequest(
                bankAccountAId,
                "DIRECT_SALE_RECEIPT",
                "50000.00",
                today.minusDays(2).toString(),
                "Customer Payment Alpha"
        );
        TransactionDetailResponse txDetail = transactionPostService.postTransaction(context, "sale-" + UUID.randomUUID(), postReq);
        assertNotNull(txDetail);

        List<AccountMovement> movements = movementRepository.findByTransactionIdOrderByCreatedAtAscIdAsc(txDetail.id());
        assertEquals(1, movements.size());
        AccountMovement mov = movements.get(0);
        assertEquals(bankAccountAId, mov.getAccountId());
        assertEquals(MovementDirection.INCREASE, mov.getMovementDirection());

        // 2. Import matching bank statement entry
        StatementImportRequest importReq = new StatementImportRequest(
                bankAccountAId, "hbl.csv", null, null, null, null,
                List.of(new StatementEntryItemRequest(1, today.minusDays(2), null, MovementDirection.INCREASE,
                        new BigDecimal("50000.00"), "Customer Payment Alpha", "REF-ALPHA", null, null))
        );
        StatementImportResponse impResp = importService.importStatement(context, null, importReq);

        List<BankStatementEntry> entries = entryRepository.findByBusinessIdAndImportIdOrderBySourceRowNumberAsc(businessId, impResp.id());
        assertEquals(1, entries.size());
        BankStatementEntry entry = entries.get(0);

        // 3. Find candidates
        List<ReconciliationMatchCandidateResponse> candidates = reconciliationService.findCandidates(context, entry.getId());
        assertEquals(1, candidates.size());
        assertEquals(mov.getId(), candidates.get(0).accountMovementId());
        assertTrue(candidates.get(0).confidenceScore() >= 90);

        // 4. Confirm match
        StatementEntryDetailResponse matchResp = reconciliationService.matchEntry(
                context, entry.getId(), new ReconciliationMatchRequest(mov.getId())
        );
        assertEquals(StatementEntryStatus.MATCHED, matchResp.status());
        assertNotNull(matchResp.matchedAccountMovementId());
        assertEquals(mov.getId(), matchResp.matchedAccountMovementId());

        // 5. Attempt second match with another entry on the SAME movement -> must fail with Conflict
        StatementImportRequest importReq2 = new StatementImportRequest(
                bankAccountAId, "hbl2.csv", null, null, null, null,
                List.of(new StatementEntryItemRequest(1, today.minusDays(2), null, MovementDirection.INCREASE,
                        new BigDecimal("50000.00"), "Duplicate claim", "REF-ALPHA", null, null))
        );
        StatementImportResponse impResp2 = importService.importStatement(context, null, importReq2);
        BankStatementEntry entry2 = entryRepository.findByBusinessIdAndImportIdOrderBySourceRowNumberAsc(businessId, impResp2.id()).get(0);

        assertThrows(ReconciliationConflictException.class, () ->
                reconciliationService.matchEntry(context, entry2.getId(), new ReconciliationMatchRequest(mov.getId()))
        );
    }

    @Test
    @DisplayName("Reversed transaction excluded from matching; unmatch creates cancelled audit record")
    void testReversalExclusionAndUnmatchAudit() {
        TransactionPostRequest postReq = new TransactionPostRequest(
                bankAccountAId,
                "DIRECT_SALE_RECEIPT",
                "30000.00",
                today.minusDays(4).toString(),
                "Mistaken Entry"
        );
        TransactionDetailResponse txDetail = transactionPostService.postTransaction(context, "sale-" + UUID.randomUUID(), postReq);

        // Import matching statement entry
        StatementImportRequest impReq = new StatementImportRequest(
                bankAccountAId, "st.csv", null, null, null, null,
                List.of(new StatementEntryItemRequest(1, today.minusDays(4), null, MovementDirection.INCREASE,
                        new BigDecimal("30000.00"), "Mistaken Entry", null, null, null))
        );
        StatementImportResponse impResp = importService.importStatement(context, null, impReq);
        BankStatementEntry entry = entryRepository.findByBusinessIdAndImportIdOrderBySourceRowNumberAsc(businessId, impResp.id()).get(0);

        AccountMovement mov = movementRepository.findByTransactionIdOrderByCreatedAtAscIdAsc(txDetail.id()).get(0);

        // Match it
        reconciliationService.matchEntry(context, entry.getId(), new ReconciliationMatchRequest(mov.getId()));
        assertEquals(StatementEntryStatus.MATCHED, entryRepository.findById(entry.getId()).get().getStatus());

        // Unmatch it
        StatementEntryDetailResponse unmatchResp = reconciliationService.unmatchEntry(
                context, entry.getId(), new ReconciliationUnmatchRequest("Transaction was voided")
        );
        assertEquals(StatementEntryStatus.UNMATCHED, unmatchResp.status());

        // Reverse the FinSight transaction
        transactionReverseService.postReversal(context, "rev-" + UUID.randomUUID(), txDetail.id(), new TransactionReverseRequest("Error correction"));

        // Candidate search must now return 0 candidates because transaction is reversed!
        List<ReconciliationMatchCandidateResponse> candidates = reconciliationService.findCandidates(context, entry.getId());
        assertTrue(candidates.isEmpty(), "Reversed transactions must not be proposed as candidates");

        // Match audit table has 1 CANCELLED record
        List<BankReconciliationMatch> matches = matchRepository.findByBusinessIdAndAccountIdAndStatus(
                businessId, bankAccountAId, ReconciliationMatchStatus.CANCELLED
        );
        assertEquals(1, matches.size());
        assertEquals(ReconciliationMatchStatus.CANCELLED, matches.get(0).getStatus());
        assertEquals(context.userId(), matches.get(0).getUnmatchedByUserId());
        assertTrue(matches.get(0).getUnmatchReason().contains("Transaction was voided"));
    }

    @Test
    @DisplayName("Internal transfer: Bank A debit and Bank B credit matched to respective legs")
    void testInternalTransferLegsReconciliation() {
        // Internal transfer: PKR 25,000 from Bank A to Bank B
        TransferPostRequest transferReq = new TransferPostRequest(
                bankAccountAId,
                bankAccountBId,
                "25000.00",
                today.minusDays(1).toString(),
                "Transfer to Meezan for payroll"
        );
        TransactionDetailResponse txDetail = transferService.postTransfer(context, "trf-" + UUID.randomUUID(), transferReq);
        List<AccountMovement> movements = movementRepository.findByTransactionIdOrderByCreatedAtAscIdAsc(txDetail.id());
        assertEquals(2, movements.size());

        AccountMovement legA = movements.stream().filter(m -> m.getAccountId().equals(bankAccountAId)).findFirst().get();
        assertEquals(MovementDirection.DECREASE, legA.getMovementDirection());

        AccountMovement legB = movements.stream().filter(m -> m.getAccountId().equals(bankAccountBId)).findFirst().get();
        assertEquals(MovementDirection.INCREASE, legB.getMovementDirection());

        // Import Bank A statement (Debit 25,000)
        StatementImportRequest reqA = new StatementImportRequest(
                bankAccountAId, "hbl.csv", null, null, null, null,
                List.of(new StatementEntryItemRequest(1, today.minusDays(1), null, MovementDirection.DECREASE,
                        new BigDecimal("25000.00"), "Transfer Out", null, null, null))
        );
        StatementImportResponse impA = importService.importStatement(context, null, reqA);
        BankStatementEntry entryA = entryRepository.findByBusinessIdAndImportIdOrderBySourceRowNumberAsc(businessId, impA.id()).get(0);

        // Import Bank B statement (Credit 25,000)
        StatementImportRequest reqB = new StatementImportRequest(
                bankAccountBId, "meezan.csv", null, null, null, null,
                List.of(new StatementEntryItemRequest(1, today.minusDays(1), null, MovementDirection.INCREASE,
                        new BigDecimal("25000.00"), "Transfer In", null, null, null))
        );
        StatementImportResponse impB = importService.importStatement(context, null, reqB);
        BankStatementEntry entryB = entryRepository.findByBusinessIdAndImportIdOrderBySourceRowNumberAsc(businessId, impB.id()).get(0);

        // Candidates for A should only show leg A
        List<ReconciliationMatchCandidateResponse> candsA = reconciliationService.findCandidates(context, entryA.getId());
        assertEquals(1, candsA.size());
        assertEquals(legA.getId(), candsA.get(0).accountMovementId());

        // Candidates for B should only show leg B
        List<ReconciliationMatchCandidateResponse> candsB = reconciliationService.findCandidates(context, entryB.getId());
        assertEquals(1, candsB.size());
        assertEquals(legB.getId(), candsB.get(0).accountMovementId());

        // Match both
        reconciliationService.matchEntry(context, entryA.getId(), new ReconciliationMatchRequest(legA.getId()));
        reconciliationService.matchEntry(context, entryB.getId(), new ReconciliationMatchRequest(legB.getId()));

        assertEquals(StatementEntryStatus.MATCHED, entryRepository.findById(entryA.getId()).get().getStatus());
        assertEquals(StatementEntryStatus.MATCHED, entryRepository.findById(entryB.getId()).get().getStatus());
    }

    @Test
    @DisplayName("Unmatched entry creates unclassified draft with provenance")
    void testUnmatchedEntryCreatesDraftWithProvenance() {
        StatementImportRequest req = new StatementImportRequest(
                bankAccountAId, "hbl.csv", null, null, null, null,
                List.of(new StatementEntryItemRequest(1, today.minusDays(1), null, MovementDirection.DECREASE,
                        new BigDecimal("350.00"), "Unknown Bank Fee", "FEE-001", null, null))
        );
        StatementImportResponse imp = importService.importStatement(context, null, req);
        BankStatementEntry entry = entryRepository.findByBusinessIdAndImportIdOrderBySourceRowNumberAsc(businessId, imp.id()).get(0);

        DraftDetailResponse draft = reconciliationService.createDraftFromEntry(context, entry.getId());

        assertNotNull(draft);
        assertNotNull(draft.id());
        assertEquals(bankAccountAId, draft.accountId());
        assertEquals("350.00", draft.amountInput());
        assertEquals(today.minusDays(1).toString(), draft.effectiveDate());
        assertNull(draft.eventType(), "Financial event type MUST remain unresolved until user classifies it");

        // Verify entry has linked draft ID
        BankStatementEntry updatedEntry = entryRepository.findById(entry.getId()).get();
        assertEquals(draft.id(), updatedEntry.getLinkedDraftId());

        // Calling createDraft again on the same entry returns the existing draft idempotently
        DraftDetailResponse draftAgain = reconciliationService.createDraftFromEntry(context, entry.getId());
        assertEquals(draft.id(), draftAgain.id());
    }

    @Test
    @DisplayName("STRICT INVARIANT: Reconciliation operations create zero financial transactions or movements")
    void testFinancialPostingZeroMutationInvariant() {
        // Record baseline financial posting counts
        long initialTxCount = transactionRepository.count();
        long initialMovCount = movementRepository.count();
        Integer initialMonthlyCount = jdbcTemplate.queryForObject("SELECT count(*) FROM monthly_records", Integer.class);

        // Perform statement import
        StatementImportRequest req = new StatementImportRequest(
                bankAccountAId, "audit.csv", null, null, null, null,
                List.of(
                        new StatementEntryItemRequest(1, today.minusDays(5), null, MovementDirection.INCREASE,
                                new BigDecimal("80000.00"), "Client Payment", "INV-88", null, null),
                        new StatementEntryItemRequest(2, today.minusDays(2), null, MovementDirection.DECREASE,
                                new BigDecimal("15000.00"), "Hardware Store", "HW-99", null, null)
                )
        );
        StatementImportResponse imp = importService.importStatement(context, null, req);

        List<BankStatementEntry> entries = entryRepository.findByBusinessIdAndImportIdOrderBySourceRowNumberAsc(businessId, imp.id());
        assertEquals(2, entries.size());

        // Exclude row 2
        reconciliationService.excludeEntry(context, entries.get(1).getId(), new ReconciliationExcludeRequest("Duplicate statement line"));

        // Create draft from row 1
        reconciliationService.createDraftFromEntry(context, entries.get(0).getId());

        // Fetch candidates for row 1
        reconciliationService.findCandidates(context, entries.get(0).getId());

        // Fetch account summary
        AccountReconciliationSummaryResponse summary = reconciliationService.getAccountSummary(context, bankAccountAId);
        assertNotNull(summary);
        assertEquals(2, summary.totalStatementEntries());
        assertEquals(1, summary.excludedEntries());

        // ASSERTION: ZERO financial transactions, ZERO account movements, ZERO monthly record changes
        assertEquals(initialTxCount, transactionRepository.count(), "Reconciliation must NEVER insert financial transactions");
        assertEquals(initialMovCount, movementRepository.count(), "Reconciliation must NEVER insert account movements");
        assertEquals(initialMonthlyCount, jdbcTemplate.queryForObject("SELECT count(*) FROM monthly_records", Integer.class),
                "Reconciliation must NEVER mutate monthly records");
    }

    @Test
    @DisplayName("When an existing transaction is reversed, its active reconciliation is automatically cancelled with audit trail")
    void testReconciliationInvalidatedWhenTransactionReversed() {
        // 1. Post a valid direct sale receipt: 45,000 PKR into Bank Account A
        TransactionPostRequest postReq = new TransactionPostRequest(
                bankAccountAId,
                "DIRECT_SALE_RECEIPT",
                "45000.00",
                today.minusDays(1).toString(),
                "Client consulting revenue"
        );
        TransactionDetailResponse tx = transactionPostService.postTransaction(context, "tx-rev-test-" + UUID.randomUUID(), postReq);
        assertNotNull(tx);
        UUID movementId = tx.movements().get(0).id();

        // 2. Import corresponding statement entry (45,000 INCREASE)
        StatementImportRequest impReq = new StatementImportRequest(
                bankAccountAId, "statement_rev.csv", null, null, null, null,
                List.of(new StatementEntryItemRequest(1, today.minusDays(1), null, MovementDirection.INCREASE,
                        new BigDecimal("45000.00"), "Deposit Client Consulting", "DEP-450", null, null))
        );
        StatementImportResponse imp = importService.importStatement(context, null, impReq);
        BankStatementEntry entry = entryRepository.findByBusinessIdAndImportIdOrderBySourceRowNumberAsc(businessId, imp.id()).get(0);

        // 3. Confirm match
        reconciliationService.matchEntry(context, entry.getId(), new ReconciliationMatchRequest(movementId));

        // Verify entry is MATCHED and match is ACTIVE
        BankStatementEntry matchedEntry = entryRepository.findById(entry.getId()).get();
        assertEquals(StatementEntryStatus.MATCHED, matchedEntry.getStatus());

        BankReconciliationMatch activeMatch = matchRepository.findByBusinessIdAndStatementEntryIdAndStatus(
                businessId, entry.getId(), ReconciliationMatchStatus.ACTIVE
        ).orElseThrow();
        assertEquals(movementId, activeMatch.getAccountMovementId());

        // Verify summary reports 1 matched
        AccountReconciliationSummaryResponse summaryBefore = reconciliationService.getAccountSummary(context, bankAccountAId);
        assertEquals(1, summaryBefore.matchedEntries());
        assertEquals(0, summaryBefore.unmatchedEntries());

        long txCountBeforeRev = transactionRepository.count();
        long movCountBeforeRev = movementRepository.count();

        // 4. Reverse the underlying financial transaction
        TransactionReverseRequest revReq = new TransactionReverseRequest("Check bounced / NSF");
        TransactionDetailResponse revTx = transactionReverseService.postReversal(context, "rev-key-" + UUID.randomUUID(), tx.id(), revReq);
        assertNotNull(revTx);

        // 5. Query reconciliation state: match MUST be CANCELLED with audit reason
        BankReconciliationMatch cancelledMatch = matchRepository.findById(activeMatch.getId()).get();
        assertEquals(ReconciliationMatchStatus.CANCELLED, cancelledMatch.getStatus());
        assertNotNull(cancelledMatch.getUnmatchedAt());
        assertNotNull(cancelledMatch.getUnmatchedByUserId());
        assertTrue(cancelledMatch.getUnmatchReason().contains("Automatically cancelled due to transaction reversal"));

        // Statement entry MUST revert to UNMATCHED
        BankStatementEntry revertedEntry = entryRepository.findById(entry.getId()).get();
        assertEquals(StatementEntryStatus.UNMATCHED, revertedEntry.getStatus());

        // Summary MUST reflect 0 matched, 1 unmatched
        AccountReconciliationSummaryResponse summaryAfter = reconciliationService.getAccountSummary(context, bankAccountAId);
        assertEquals(0, summaryAfter.matchedEntries());
        assertEquals(1, summaryAfter.unmatchedEntries());

        // 6. Verify candidates for the statement entry exclude the reversed transaction and its reversal
        List<ReconciliationMatchCandidateResponse> candidates = reconciliationService.findCandidates(context, entry.getId());
        for (var cand : candidates) {
            assertNotEquals(tx.id(), cand.transactionId(), "Reversed transaction MUST NOT be suggested as candidate");
            assertNotEquals(revTx.id(), cand.transactionId(), "Reversal transaction MUST NOT be suggested as candidate");
        }

        // 7. Verify no additional financial posting mutations occurred from reconciliation cancellation
        // (Only the 1 reversal transaction and 1 compensating movement created by postReversal)
        assertEquals(txCountBeforeRev + 1, transactionRepository.count(), "Reconciliation cancellation must not create extra transactions");
        assertEquals(movCountBeforeRev + 1, movementRepository.count(), "Reconciliation cancellation must not create extra account movements");
    }

    @Test
    @DisplayName("Complete statement-to-draft-to-posting-to-match lifecycle with provenance")
    void testCompleteUnmatchedEntryToDraftToPostingToMatchLifecycle() {
        // 1. Import unmatched debit: 12,000 PKR
        StatementImportRequest req = new StatementImportRequest(
                bankAccountAId, "unmatched_lifecycle.csv", null, null, null, null,
                List.of(new StatementEntryItemRequest(1, today.minusDays(2), null, MovementDirection.DECREASE,
                        new BigDecimal("12000.00"), "Software Annual License", "SW-LIC-12", null, null))
        );
        StatementImportResponse imp = importService.importStatement(context, null, req);
        BankStatementEntry entry = entryRepository.findByBusinessIdAndImportIdOrderBySourceRowNumberAsc(businessId, imp.id()).get(0);
        assertEquals(StatementEntryStatus.UNMATCHED, entry.getStatus());

        // Baseline financial counts
        long initialTxCount = transactionRepository.count();
        long initialMovCount = movementRepository.count();

        // 2. Create persistent draft from statement entry
        DraftDetailResponse draft = reconciliationService.createDraftFromEntry(context, entry.getId());
        assertNotNull(draft);
        assertNull(draft.eventType(), "Draft event type MUST be initially unclassified");
        assertEquals("12000.00", draft.amountInput());
        assertEquals(draft.id(), entryRepository.findById(entry.getId()).get().getLinkedDraftId());

        // Replaying createDraft returns the same draft without duplicates
        DraftDetailResponse draftReplay = reconciliationService.createDraftFromEntry(context, entry.getId());
        assertEquals(draft.id(), draftReplay.id());

        // 3. Classify and record draft via RecordDraftService
        DraftRecordRequest recordRequest = new DraftRecordRequest(
                draft.version(),
                "12000.00",
                bankAccountAId,
                TransactionEventType.OPERATING_EXPENSE_PAYMENT.name(),
                today.minusDays(2).toString(),
                "Software Annual License - Classified & Approved"
        );
        var recorded = recordDraftService.recordDraft(context, draft.id(), "record-draft-recon-" + UUID.randomUUID(), recordRequest);
        assertNotNull(recorded);
        assertNotNull(recorded.transaction());

        // Verify exactly one new transaction and one new movement
        assertEquals(initialTxCount + 1, transactionRepository.count());
        assertEquals(initialMovCount + 1, movementRepository.count());

        UUID recordedMovementId = recorded.transaction().movements().get(0).id();

        // 4. Return to bank reconciliation: the newly posted movement MUST appear as eligible candidate
        List<ReconciliationMatchCandidateResponse> candidates = reconciliationService.findCandidates(context, entry.getId());
        assertFalse(candidates.isEmpty(), "Recorded transaction movement MUST appear as candidate for the statement entry");
        ReconciliationMatchCandidateResponse candidate = candidates.stream()
                .filter(c -> c.accountMovementId().equals(recordedMovementId))
                .findFirst()
                .orElseThrow();
        assertEquals(TransactionEventType.OPERATING_EXPENSE_PAYMENT, candidate.eventType());
        assertEquals(0, new BigDecimal("12000.00").compareTo(candidate.amount()));

        // 5. Confirm reconciliation match
        reconciliationService.matchEntry(context, entry.getId(), new ReconciliationMatchRequest(recordedMovementId));

        // Verify statement entry is MATCHED
        BankStatementEntry finalEntry = entryRepository.findById(entry.getId()).get();
        assertEquals(StatementEntryStatus.MATCHED, finalEntry.getStatus());
        assertEquals(draft.id(), finalEntry.getLinkedDraftId(), "Statement-to-draft provenance link MUST remain traceable after recording");

        // Verify no additional transactions or movements created by matching
        assertEquals(initialTxCount + 1, transactionRepository.count());
        assertEquals(initialMovCount + 1, movementRepository.count());
    }
}
