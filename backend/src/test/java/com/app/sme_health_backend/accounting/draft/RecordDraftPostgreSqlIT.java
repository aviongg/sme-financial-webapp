package com.app.sme_health_backend.accounting.draft;

import com.app.sme_health_backend.accounting.dto.AccountDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionDetailResponse;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.exception.TransactionValidationException;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.service.AccountReadService;
import com.app.sme_health_backend.accounting.service.TransactionPostService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.dto.CreateBusinessRequest;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.service.BusinessService;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RecordDraftPostgreSqlIT extends DisposablePostgres {

    @Autowired
    private RecordDraftService draftService;

    @Autowired
    private FinancialAccountRepository accountRepository;

    @Autowired
    private AccountReadService accountReadService;

    @Autowired
    private TransactionPostService transactionPostService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private BusinessService businessService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userA;
    private UUID businessA;
    private BusinessAccessContext contextA;

    private UUID userB;
    private UUID businessB;
    private BusinessAccessContext contextB;

    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(ZoneId.of("Asia/Karachi"));

        AppUser uA = new AppUser();
        uA.setEmail("draft-it-a-" + UUID.randomUUID() + "@example.test");
        uA.setFullName("Draft User A");
        uA.setPasswordHash("hash-a");
        userA = userRepository.save(uA).getId();
        businessA = businessService.createBusiness(
                new CreateBusinessRequest("Draft Business A", "retail", "en", null, false, null, null, null),
                userA
        ).businessId();
        contextA = new BusinessAccessContext(userA, businessA, MembershipRole.OWNER);

        AppUser uB = new AppUser();
        uB.setEmail("draft-it-b-" + UUID.randomUUID() + "@example.test");
        uB.setFullName("Draft User B");
        uB.setPasswordHash("hash-b");
        userB = userRepository.save(uB).getId();
        businessB = businessService.createBusiness(
                new CreateBusinessRequest("Draft Business B", "services", "en", null, false, null, null, null),
                userB
        ).businessId();
        contextB = new BusinessAccessContext(userB, businessB, MembershipRole.OWNER);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM record_drafts WHERE business_id IN (?, ?)", businessA, businessB);
        jdbcTemplate.update("DELETE FROM account_movements WHERE business_id IN (?, ?)", businessA, businessB);
        jdbcTemplate.update("DELETE FROM financial_transactions WHERE business_id IN (?, ?)", businessA, businessB);
        jdbcTemplate.update("DELETE FROM financial_idempotency_records WHERE business_id IN (?, ?)", businessA, businessB);
        jdbcTemplate.update("DELETE FROM financial_accounts WHERE business_id IN (?, ?)", businessA, businessB);
        jdbcTemplate.update("DELETE FROM business_memberships WHERE business_id IN (?, ?)", businessA, businessB);
        jdbcTemplate.update("DELETE FROM business_profiles WHERE user_id IN (?, ?)", businessA, businessB);
        jdbcTemplate.update("DELETE FROM businesses WHERE id IN (?, ?)", businessA, businessB);
        jdbcTemplate.update("DELETE FROM app_users WHERE id IN (?, ?)", userA, userB);
    }

    private FinancialAccount createTestAccount(UUID businessId, String name, AccountType type, BigDecimal balance) {
        FinancialAccount account = new FinancialAccount(
                businessId,
                name,
                type,
                "PKR",
                2,
                balance,
                today.minusDays(10),
                AccountStatus.ACTIVE,
                null,
                null,
                "Test Account"
        );
        return accountRepository.save(account);
    }

    @Test
    @DisplayName("IT-DRAFT-01: Saving and resuming an incomplete draft preserves raw text and has no financial effect")
    void saveAndResumeIncompleteDraft() {
        int initialTxCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM financial_transactions", Integer.class);
        int initialMovCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM account_movements", Integer.class);
        int initialRecordCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM monthly_records", Integer.class);

        DraftCreateRequest createReq = new DraftCreateRequest(
                null,
                null,
                null,
                "45000.",
                "Incomplete shop receipt notes"
        );

        DraftDetailResponse created = draftService.createDraft(contextA, null, createReq);
        assertNotNull(created.id());
        assertNull(created.accountId());
        assertNull(created.eventType());
        assertNull(created.effectiveDate());
        assertEquals("45000.", created.amountInput());
        assertEquals("Incomplete shop receipt notes", created.description());
        assertEquals("DRAFT", created.status());
        assertFalse(created.readyToRecord());
        assertEquals(0L, created.version());
        assertNull(created.recordedTransactionId());

        // Resume draft
        DraftDetailResponse resumed = draftService.getDraft(contextA, created.id());
        assertEquals(created.id(), resumed.id());
        assertEquals("45000.", resumed.amountInput());
        assertEquals("DRAFT", resumed.status());

        // Verify zero financial effect
        int afterTxCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM financial_transactions", Integer.class);
        int afterMovCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM account_movements", Integer.class);
        int afterRecordCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM monthly_records", Integer.class);

        assertEquals(initialTxCount, afterTxCount);
        assertEquals(initialMovCount, afterMovCount);
        assertEquals(initialRecordCount, afterRecordCount);
    }

    @Test
    @DisplayName("IT-DRAFT-02: Cross-business access and cross-business references are strictly rejected")
    void crossBusinessRejections() {
        FinancialAccount accB = createTestAccount(businessB, "Business B Account", AccountType.BANK, new BigDecimal("10000.00"));

        // Business A cannot create draft referencing Business B's account
        DraftCreateRequest invalidAccReq = new DraftCreateRequest(
                accB.getId(),
                "DIRECT_SALE_RECEIPT",
                today.toString(),
                "100.00",
                "Cross business account test"
        );
        assertThrows(DraftValidationException.class, () -> draftService.createDraft(contextA, null, invalidAccReq));

        // Create valid draft in Business A
        FinancialAccount accA = createTestAccount(businessA, "Business A Account", AccountType.CASH, new BigDecimal("5000.00"));
        DraftCreateRequest validReq = new DraftCreateRequest(
                accA.getId(),
                "DIRECT_SALE_RECEIPT",
                today.toString(),
                "100.00",
                "A draft"
        );
        DraftDetailResponse draftA = draftService.createDraft(contextA, null, validReq);

        // Business B cannot read Business A's draft
        assertThrows(ResourceNotFoundException.class, () -> draftService.getDraft(contextB, draftA.id()));

        // Business B cannot update Business A's draft
        DraftUpdateRequest updateReq = new DraftUpdateRequest(0L, accB.getId(), "DIRECT_SALE_RECEIPT", today.toString(), "200.00", "Hijack");
        assertThrows(ResourceNotFoundException.class, () -> draftService.updateDraft(contextB, draftA.id(), updateReq));

        // Business B cannot discard Business A's draft
        assertThrows(ResourceNotFoundException.class, () -> draftService.discardDraft(contextB, draftA.id(), new DraftDiscardRequest(0L, null)));

        // Business B cannot record Business A's draft
        DraftRecordRequest recordReq = new DraftRecordRequest(0L, "100.00", null, null, null, null);
        assertThrows(ResourceNotFoundException.class, () -> draftService.recordDraft(contextB, draftA.id(), "B-KEY", recordReq));
    }

    @Test
    @DisplayName("IT-DRAFT-03: Optimistic concurrency conflict on stale update and discard")
    void optimisticConcurrencyConflict() {
        FinancialAccount acc = createTestAccount(businessA, "Bank Account", AccountType.BANK, new BigDecimal("20000.00"));
        DraftCreateRequest createReq = new DraftCreateRequest(acc.getId(), "DIRECT_SALE_RECEIPT", today.toString(), "500.00", "Initial");
        DraftDetailResponse draft = draftService.createDraft(contextA, null, createReq);
        assertEquals(0L, draft.version());

        // Update to version 1
        DraftUpdateRequest updateReq1 = new DraftUpdateRequest(0L, acc.getId(), "DIRECT_SALE_RECEIPT", today.toString(), "600.00", "First update");
        DraftDetailResponse updated1 = draftService.updateDraft(contextA, draft.id(), updateReq1);
        assertEquals(1L, updated1.version());
        assertEquals("600.00", updated1.amountInput());

        // Attempt second update with stale version 0
        DraftUpdateRequest staleUpdateReq = new DraftUpdateRequest(0L, acc.getId(), "DIRECT_SALE_RECEIPT", today.toString(), "700.00", "Stale update");
        DraftConflictException conflictEx = assertThrows(DraftConflictException.class, () ->
                draftService.updateDraft(contextA, draft.id(), staleUpdateReq)
        );
        assertTrue(conflictEx.getMessage().contains("Draft version mismatch"));

        // Attempt discard with stale version 0
        assertThrows(DraftConflictException.class, () ->
                draftService.discardDraft(contextA, draft.id(), new DraftDiscardRequest(0L, null))
        );

        // Discard with correct version 1 succeeds
        DraftDetailResponse discarded = draftService.discardDraft(contextA, draft.id(), new DraftDiscardRequest(1L, "Finished testing"));
        assertEquals("DISCARDED", discarded.status());
        assertEquals(2L, discarded.version());

        // Cannot update discarded draft
        DraftUpdateRequest updateDiscarded = new DraftUpdateRequest(2L, acc.getId(), "DIRECT_SALE_RECEIPT", today.toString(), "800.00", "Try update");
        assertThrows(DraftConflictException.class, () ->
                draftService.updateDraft(contextA, draft.id(), updateDiscarded)
        );
    }

    @Test
    @DisplayName("IT-DRAFT-04: Draft create idempotency protects against duplicates and rejects payload tampering")
    void draftCreateIdempotency() {
        String idempotencyKey = "CREATE-KEY-" + UUID.randomUUID();
        DraftCreateRequest createReq = new DraftCreateRequest(null, null, null, "123.45", "Idempotent draft");

        DraftDetailResponse first = draftService.createDraft(contextA, idempotencyKey, createReq);
        assertNotNull(first.id());

        // Replay with identical key and payload
        DraftDetailResponse replay = draftService.createDraft(contextA, idempotencyKey, createReq);
        assertEquals(first.id(), replay.id());
        assertEquals("123.45", replay.amountInput());

        // Verify only 1 draft row in database
        Integer draftCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM record_drafts WHERE business_id = ?", Integer.class, businessA
        );
        assertEquals(1, draftCount);

        // Mismatched payload with same key throws DuplicateResourceException
        DraftCreateRequest alteredReq = new DraftCreateRequest(null, null, null, "999.99", "Tampered draft");
        assertThrows(DuplicateResourceException.class, () ->
                draftService.createDraft(contextA, idempotencyKey, alteredReq)
        );
    }

    @Test
    @DisplayName("IT-DRAFT-05: Successful recording atomically creates transaction, movements, and links draft")
    void successfulRecording() {
        FinancialAccount acc = createTestAccount(businessA, "Till", AccountType.CASH, new BigDecimal("10000.00"));
        DraftCreateRequest createReq = new DraftCreateRequest(
                acc.getId(),
                "DIRECT_SALE_RECEIPT",
                today.toString(),
                "1500.",
                "Sale draft"
        );
        DraftDetailResponse draft = draftService.createDraft(contextA, null, createReq);

        DraftRecordRequest recordReq = new DraftRecordRequest(
                0L,
                "1500.00",
                null,
                null,
                null,
                null
        );

        String recordKey = "REC-KEY-" + UUID.randomUUID();
        DraftRecordResponse result = draftService.recordDraft(contextA, draft.id(), recordKey, recordReq);

        assertNotNull(result.transaction());
        assertNotNull(result.transaction().id());
        assertEquals("1500.00", result.transaction().amount());
        assertEquals("DIRECT_SALE_RECEIPT", result.transaction().eventType());

        assertEquals("RECORDED", result.draft().status());
        assertEquals(result.transaction().id(), result.draft().recordedTransactionId());
        assertEquals(1L, result.draft().version());

        // Account read reflects new balance
        AccountDetailResponse accDetail = accountReadService.getAccountDetail(businessA, acc.getId());
        assertEquals("11500.00", accDetail.currentBalance());

        // Transaction row and movement row exist
        Integer txCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM financial_transactions WHERE id = ?", Integer.class, result.transaction().id()
        );
        assertEquals(1, txCount);

        Integer movCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_movements WHERE transaction_id = ?", Integer.class, result.transaction().id()
        );
        assertEquals(1, movCount);

        // Subsequent update or discard fails with 409
        DraftUpdateRequest updateReq = new DraftUpdateRequest(1L, acc.getId(), "DIRECT_SALE_RECEIPT", today.toString(), "2000.00", "Try update");
        assertThrows(DraftConflictException.class, () -> draftService.updateDraft(contextA, draft.id(), updateReq));

        DraftDiscardRequest discardReq = new DraftDiscardRequest(1L, null);
        assertThrows(DraftConflictException.class, () -> draftService.discardDraft(contextA, draft.id(), discardReq));
    }

    @Test
    @DisplayName("IT-DRAFT-06: Failed posting rolls back transaction and movement, preserving draft in DRAFT state")
    void failedPostingRollsBack() {
        // Cash account with PKR 500 balance
        FinancialAccount acc = createTestAccount(businessA, "Small Cash Till", AccountType.CASH, new BigDecimal("500.00"));

        DraftCreateRequest createReq = new DraftCreateRequest(
                acc.getId(),
                "OPERATING_EXPENSE_PAYMENT",
                today.toString(),
                "10000.00",
                "Overdrawn expense"
        );
        DraftDetailResponse draft = draftService.createDraft(contextA, null, createReq);

        DraftRecordRequest recordReq = new DraftRecordRequest(
                0L,
                "10000.00",
                null,
                null,
                null,
                null
        );

        String recordKey = "REC-FAIL-" + UUID.randomUUID();
        // Posting 10000 expense against 500 cash violates non-negative rule
        assertThrows(TransactionValidationException.class, () ->
                draftService.recordDraft(contextA, draft.id(), recordKey, recordReq)
        );

        // Draft must still be in DRAFT status with version 0 and no recordedTransactionId
        DraftDetailResponse draftAfter = draftService.getDraft(contextA, draft.id());
        assertEquals("DRAFT", draftAfter.status());
        assertEquals(0L, draftAfter.version());
        assertNull(draftAfter.recordedTransactionId());

        // 0 transactions or movements recorded
        Integer txCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM financial_transactions WHERE business_id = ?", Integer.class, businessA
        );
        assertEquals(0, txCount);

        Integer movCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_movements WHERE business_id = ?", Integer.class, businessA
        );
        assertEquals(0, movCount);
    }

    @Test
    @DisplayName("IT-DRAFT-07: Concurrent same-draft recording race creates exactly one transaction; competitors get 409 Conflict")
    void concurrentRecordingRace() throws Exception {
        FinancialAccount acc = createTestAccount(businessA, "Counter Till", AccountType.CASH, new BigDecimal("50000.00"));
        DraftCreateRequest createReq = new DraftCreateRequest(
                acc.getId(),
                "DIRECT_SALE_RECEIPT",
                today.toString(),
                "2000.00",
                "Race test draft"
        );
        DraftDetailResponse draft = draftService.createDraft(contextA, null, createReq);

        int threadCount = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger unexpectedErrorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final String threadKey = "CONCURRENT-REC-" + i + "-" + UUID.randomUUID();
            executor.submit(() -> {
                try {
                    startGate.await();
                    DraftRecordRequest req = new DraftRecordRequest(0L, "2000.00", null, null, null, null);
                    draftService.recordDraft(contextA, draft.id(), threadKey, req);
                    successCount.incrementAndGet();
                } catch (DraftConflictException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    unexpectedErrorCount.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Concurrent recording execution timed out");
        assertEquals(1, successCount.get(), "Exactly one concurrent thread must succeed");
        assertEquals(threadCount - 1, conflictCount.get(), "All other concurrent threads must receive DraftConflictException");
        assertEquals(0, unexpectedErrorCount.get(), "No unexpected errors should occur");

        // Exactly one transaction and one movement created
        Integer txCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM financial_transactions WHERE business_id = ?", Integer.class, businessA
        );
        assertEquals(1, txCount);

        Integer movCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_movements WHERE business_id = ?", Integer.class, businessA
        );
        assertEquals(1, movCount);

        DraftDetailResponse finalDraft = draftService.getDraft(contextA, draft.id());
        assertEquals("RECORDED", finalDraft.status());
        assertNotNull(finalDraft.recordedTransactionId());
    }

    @Test
    @DisplayName("IT-DRAFT-08: Repeat recording request with identical key recovers cached transaction safely")
    void repeatRecordingRecoveryWithSameKey() {
        FinancialAccount acc = createTestAccount(businessA, "Bank Account", AccountType.BANK, new BigDecimal("30000.00"));
        DraftCreateRequest createReq = new DraftCreateRequest(
                acc.getId(),
                "DIRECT_SALE_RECEIPT",
                today.toString(),
                "5000.00",
                "Idempotent record test"
        );
        DraftDetailResponse draft = draftService.createDraft(contextA, null, createReq);

        String recordKey = "REC-REPEAT-" + UUID.randomUUID();
        DraftRecordRequest recordReq = new DraftRecordRequest(0L, "5000.00", null, null, null, null);

        DraftRecordResponse initialResponse = draftService.recordDraft(contextA, draft.id(), recordKey, recordReq);
        assertNotNull(initialResponse.transaction().id());

        // Repeat identical recording request
        DraftRecordResponse repeatedResponse = draftService.recordDraft(contextA, draft.id(), recordKey, recordReq);
        assertEquals(initialResponse.transaction().id(), repeatedResponse.transaction().id());
        assertEquals("RECORDED", repeatedResponse.draft().status());

        // Still only 1 transaction and 1 movement
        Integer txCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM financial_transactions WHERE business_id = ?", Integer.class, businessA
        );
        assertEquals(1, txCount);

        Integer movCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_movements WHERE business_id = ?", Integer.class, businessA
        );
        assertEquals(1, movCount);
    }

    @Test
    @DisplayName("IT-DRAFT-09: Replay with same key but changed confirmedAmount returns 409 Conflict without creating transactions")
    void replayWithChangedConfirmedAmountThrowsConflict() {
        FinancialAccount acc = createTestAccount(businessA, "Revenue Till", AccountType.CASH, new BigDecimal("20000.00"));
        DraftCreateRequest createReq = new DraftCreateRequest(
                acc.getId(),
                "DIRECT_SALE_RECEIPT",
                today.toString(),
                "15.55",
                "Live defect reproduction draft"
        );
        DraftDetailResponse draft = draftService.createDraft(contextA, null, createReq);

        String recordKey = "REC-REPLAY-DEFECT-" + UUID.randomUUID();
        DraftRecordRequest originalReq = new DraftRecordRequest(0L, "15.55", null, null, null, null);

        // 1. Initial record -> 201 Created
        DraftRecordResponse first = draftService.recordDraft(contextA, draft.id(), recordKey, originalReq);
        assertNotNull(first.transaction());
        assertEquals("15.55", first.transaction().amount());
        assertEquals("RECORDED", first.draft().status());

        // 2. Same key + same original request -> safely returns cached result (HTTP 201 / identical transaction)
        DraftRecordResponse replay = draftService.recordDraft(contextA, draft.id(), recordKey, originalReq);
        assertEquals(first.transaction().id(), replay.transaction().id());
        assertEquals("15.55", replay.transaction().amount());

        // 3. Same key + changed confirmed amount (15.55 -> 16.55) -> must return 409 Conflict (DuplicateResourceException)
        DraftRecordRequest changedAmountReq = new DraftRecordRequest(0L, "16.55", null, null, null, null);
        assertThrows(DuplicateResourceException.class, () ->
                draftService.recordDraft(contextA, draft.id(), recordKey, changedAmountReq)
        );

        // 4. Same key + changed version -> 409 Conflict
        DraftRecordRequest changedVersionReq = new DraftRecordRequest(1L, "15.55", null, null, null, null);
        assertThrows(DuplicateResourceException.class, () ->
                draftService.recordDraft(contextA, draft.id(), recordKey, changedVersionReq)
        );

        // 5. Distinct key cannot record already-recorded draft -> 409 Conflict (DraftConflictException)
        String differentKey = "REC-DIFF-" + UUID.randomUUID();
        assertThrows(DraftConflictException.class, () ->
                draftService.recordDraft(contextA, draft.id(), differentKey, originalReq)
        );

        // 6. Verify database integrity: exactly 1 transaction and 1 movement exists
        Integer txCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM financial_transactions WHERE business_id = ?", Integer.class, businessA
        );
        assertEquals(1, txCount);

        Integer movCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_movements WHERE business_id = ?", Integer.class, businessA
        );
        assertEquals(1, movCount);

        // Verify account balance reflects only the single original 15.55 transaction
        AccountDetailResponse accDetail = accountReadService.getAccountDetail(businessA, acc.getId());
        assertEquals("20015.55", accDetail.currentBalance());
    }

    @Test
    @DisplayName("IT-DRAFT-10: Round-trip create, read, and update preserves verbatim raw amountInput with whitespace")
    void verbatimRawAmountInputRoundTrip() {
        // 1. Create with raw whitespace and Eastern Arabic/Urdu digits
        String rawInput1 = " ۱۵۰۰. ";
        DraftCreateRequest createReq = new DraftCreateRequest(null, null, null, rawInput1, "Raw input draft");
        DraftDetailResponse created = draftService.createDraft(contextA, null, createReq);
        assertEquals(rawInput1, created.amountInput(), "Creation must preserve verbatim amountInput without trimming");

        // 2. Read draft verifies persistence of verbatim text
        DraftDetailResponse read1 = draftService.getDraft(contextA, created.id());
        assertEquals(rawInput1, read1.amountInput(), "Read must return exact persisted raw text");

        // Verify database raw column directly
        String dbValue = jdbcTemplate.queryForObject(
                "SELECT amount_input FROM record_drafts WHERE id = ?", String.class, created.id()
        );
        assertEquals(rawInput1, dbValue, "Database column amount_input must store verbatim string");

        // 3. Update with whitespace, Western separators, and decimal point
        String rawInput2 = "  ۱,۵۰۰.۵  ";
        DraftUpdateRequest updateReq1 = new DraftUpdateRequest(0L, null, null, null, rawInput2, "Updated raw");
        DraftDetailResponse updated1 = draftService.updateDraft(contextA, created.id(), updateReq1);
        assertEquals(rawInput2, updated1.amountInput(), "Update must preserve verbatim amountInput");

        DraftDetailResponse read2 = draftService.getDraft(contextA, created.id());
        assertEquals(rawInput2, read2.amountInput(), "Read after update must return exact verbatim string");

        // 4. Update with unfinished input like "1500."
        String rawInput3 = "1500.";
        DraftUpdateRequest updateReq2 = new DraftUpdateRequest(1L, null, null, null, rawInput3, "Unfinished raw");
        DraftDetailResponse updated2 = draftService.updateDraft(contextA, created.id(), updateReq2);
        assertEquals(rawInput3, updated2.amountInput(), "Update must preserve unfinished input");

        // 5. Raw input capacity limit (100 characters) is enforced on raw string without trimming
        String longInput = " " + "1".repeat(100); // 101 characters with leading space
        DraftCreateRequest invalidCreate = new DraftCreateRequest(null, null, null, longInput, "Too long");
        assertThrows(DraftValidationException.class, () ->
                draftService.createDraft(contextA, null, invalidCreate)
        );
    }

    @Test
    @DisplayName("IT-DRAFT-11: Canonical confirmedAmount syntax enforced on first recording and replay; whitespace/control characters rejected with 400 Bad Request")
    void canonicalConfirmedAmountSyntaxEnforcedOnRecordingAndReplay() {
        FinancialAccount acc = createTestAccount(businessA, "Revenue Till 2", AccountType.CASH, new BigDecimal("20000.00"));
        DraftCreateRequest createReq = new DraftCreateRequest(
                acc.getId(),
                "DIRECT_SALE_RECEIPT",
                today.toString(),
                "15.55",
                "Syntax validation draft"
        );
        DraftDetailResponse draft = draftService.createDraft(contextA, null, createReq);

        String[] malformedAmounts = {
                " 15.55 ", "\t15.55\t", "15.55\n", "\u000015.55\u0000",
                "15.55 ", " 15.55", "\r\n15.55\r\n", "15\u0000.55", "15.5 5", "\f15.55", "\b15.55",
                "15.550", "1.555e1", "+15.55", "-15.55", "015.55", "15.5", "15", "15,55",
                "NaN", "Infinity", "۱۵.۵۵", "١٥.٥٥", "", "   ", "0.00", "1000000000000.00"
        };

        String initialKey = "REC-SYNTAX-INIT-" + UUID.randomUUID();

        // 1. First recording with malformed amounts is rejected with DraftValidationException (HTTP 400)
        for (String malformed : malformedAmounts) {
            DraftRecordRequest badReq = new DraftRecordRequest(0L, malformed, null, null, null, null);
            DraftValidationException ex = assertThrows(DraftValidationException.class, () ->
                    draftService.recordDraft(contextA, draft.id(), initialKey, badReq)
            );
            assertTrue(ex.getErrors().containsKey("confirmedAmount"), "Expected 400 error on confirmedAmount for first recording with: " + malformed);
        }

        // Draft must still be in DRAFT status
        DraftDetailResponse draftStillDraft = draftService.getDraft(contextA, draft.id());
        assertEquals("DRAFT", draftStillDraft.status());
        assertEquals(0L, draftStillDraft.version());
        assertNull(draftStillDraft.recordedTransactionId());

        // Zero transactions created so far
        Integer txCount0 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM financial_transactions WHERE business_id = ?", Integer.class, businessA
        );
        assertEquals(0, txCount0);

        // 2. Initial record with valid canonical "15.55" -> 201 Created
        DraftRecordRequest validReq = new DraftRecordRequest(0L, "15.55", null, null, null, null);
        DraftRecordResponse first = draftService.recordDraft(contextA, draft.id(), initialKey, validReq);
        assertNotNull(first.transaction());
        assertEquals("15.55", first.transaction().amount());
        assertEquals("RECORDED", first.draft().status());

        // 3. Replay with the same key and malformed amounts must be rejected with 400 (DraftValidationException), NOT returned as 201
        for (String malformed : malformedAmounts) {
            DraftRecordRequest badReplayReq = new DraftRecordRequest(0L, malformed, null, null, null, null);
            DraftValidationException ex = assertThrows(DraftValidationException.class, () ->
                    draftService.recordDraft(contextA, draft.id(), initialKey, badReplayReq)
            );
            assertTrue(ex.getErrors().containsKey("confirmedAmount"), "Replay should reject confirmedAmount with 400 for: " + malformed);
        }

        // 4. Replay with valid canonical amount and same key returns cached 201
        DraftRecordResponse validReplay = draftService.recordDraft(contextA, draft.id(), initialKey, validReq);
        assertEquals(first.transaction().id(), validReplay.transaction().id());
        assertEquals("15.55", validReplay.transaction().amount());
        assertEquals("RECORDED", validReplay.draft().status());

        // 5. Replay with valid canonical amount but changed amount ("16.55") throws DuplicateResourceException (409 Conflict)
        DraftRecordRequest changedAmountReq = new DraftRecordRequest(0L, "16.55", null, null, null, null);
        assertThrows(DuplicateResourceException.class, () ->
                draftService.recordDraft(contextA, draft.id(), initialKey, changedAmountReq)
        );

        // 6. Verify database integrity: exactly 1 transaction, 1 movement, exactly 1 idempotency record, balance changed by 15.55
        Integer txCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM financial_transactions WHERE business_id = ?", Integer.class, businessA
        );
        assertEquals(1, txCount);

        Integer movCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_movements WHERE business_id = ?", Integer.class, businessA
        );
        assertEquals(1, movCount);

        Integer idempCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM financial_idempotency_records WHERE business_id = ? AND idempotency_key = ?",
                Integer.class, businessA, initialKey
        );
        assertEquals(1, idempCount);

        AccountDetailResponse accDetail = accountReadService.getAccountDetail(businessA, acc.getId());
        assertEquals("20015.55", accDetail.currentBalance());
    }
}
