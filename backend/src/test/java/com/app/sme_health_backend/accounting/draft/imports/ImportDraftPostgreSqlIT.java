package com.app.sme_health_backend.accounting.draft.imports;

import com.app.sme_health_backend.accounting.draft.DraftConflictException;
import com.app.sme_health_backend.accounting.draft.DraftDiscardRequest;
import com.app.sme_health_backend.accounting.draft.DraftRecordRequest;
import com.app.sme_health_backend.accounting.draft.DraftRecordResponse;
import com.app.sme_health_backend.accounting.draft.DraftStatus;
import com.app.sme_health_backend.accounting.draft.DraftUpdateRequest;
import com.app.sme_health_backend.accounting.draft.DraftValidationException;
import com.app.sme_health_backend.accounting.draft.RecordDraft;
import com.app.sme_health_backend.accounting.draft.RecordDraftRepository;
import com.app.sme_health_backend.accounting.draft.RecordDraftService;
import com.app.sme_health_backend.accounting.draft.imports.dto.ImportBatchDetailResponse;
import com.app.sme_health_backend.accounting.draft.imports.dto.ImportDraftRowItem;
import com.app.sme_health_backend.accounting.draft.imports.dto.ImportDraftRowResult;
import com.app.sme_health_backend.accounting.draft.imports.dto.ImportDraftsRequest;
import com.app.sme_health_backend.accounting.draft.imports.dto.ImportDraftsResponse;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.dto.CreateBusinessRequest;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.service.BusinessService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ImportDraftPostgreSqlIT extends DisposablePostgres {

    @Autowired
    private ImportDraftService importDraftService;

    @Autowired
    private RecordDraftService recordDraftService;

    @Autowired
    private RecordDraftRepository draftRepository;

    @Autowired
    private ImportBatchRepository batchRepository;

    @Autowired
    private ImportBatchRowRepository batchRowRepository;

    @Autowired
    private FinancialAccountRepository accountRepository;

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
        uA.setEmail("import-it-a-" + UUID.randomUUID() + "@example.test");
        uA.setFullName("Import User A");
        uA.setPasswordHash("hash-a");
        userA = userRepository.save(uA).getId();
        businessA = businessService.createBusiness(
                new CreateBusinessRequest("Import Business A", "retail", "en", null, false, null, null, null),
                userA
        ).businessId();
        contextA = new BusinessAccessContext(userA, businessA, MembershipRole.OWNER);

        AppUser uB = new AppUser();
        uB.setEmail("import-it-b-" + UUID.randomUUID() + "@example.test");
        uB.setFullName("Import User B");
        uB.setPasswordHash("hash-b");
        userB = userRepository.save(uB).getId();
        businessB = businessService.createBusiness(
                new CreateBusinessRequest("Import Business B", "services", "en", null, false, null, null, null),
                userB
        ).businessId();
        contextB = new BusinessAccessContext(userB, businessB, MembershipRole.OWNER);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM import_batch_rows WHERE business_id IN (?, ?)", businessA, businessB);
        jdbcTemplate.update("DELETE FROM import_batches WHERE business_id IN (?, ?)", businessA, businessB);
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
    @DisplayName("IT-IMPORT-01: Selected rows create ordinary drafts with provenance and zero financial effect")
    void importDrafts_createDraftsAndProvenanceZeroFinancialEffect() {
        FinancialAccount acc = createTestAccount(businessA, "Main Bank Account", AccountType.BANK, new BigDecimal("50000.00"));

        int initialTxCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM financial_transactions", Integer.class);
        int initialMovCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM account_movements", Integer.class);
        int initialRecordCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM monthly_records", Integer.class);

        UUID batchId = UUID.randomUUID();
        String verbatimAmount = "  005432.1000  ";
        ImportDraftRowItem row1 = new ImportDraftRowItem(
                "row-1", 1, acc.getId(), "DIRECT_SALE_RECEIPT", today.toString(), verbatimAmount, "Sale #1", "REF-001"
        );
        ImportDraftRowItem row2 = new ImportDraftRowItem(
                "row-2", 2, null, null, null, "1200", "Incomplete row", null
        );

        ImportDraftsRequest request = new ImportDraftsRequest(batchId, "sales_oct.csv", List.of(row1, row2));
        ImportDraftsResponse response = importDraftService.saveImportDrafts(contextA, null, request);

        assertEquals(batchId, response.batchId());
        assertEquals("sales_oct.csv", response.sourceLabel());
        assertEquals(2, response.savedCount());
        assertEquals(0, response.replayedCount());
        assertEquals(2, response.totalRowsInBatch());

        // Check Draft 1 in database
        UUID draft1Id = response.rows().get(0).draftId();
        RecordDraft draft1 = draftRepository.findByIdAndBusinessId(draft1Id, businessA).orElseThrow();
        assertEquals(verbatimAmount, draft1.getAmountInput());
        assertEquals(today, draft1.getEffectiveDate());
        assertEquals(TransactionEventType.DIRECT_SALE_RECEIPT, draft1.getEventType());
        assertEquals(acc.getId(), draft1.getAccountId());
        assertEquals(DraftStatus.DRAFT, draft1.getStatus());
        assertFalse(response.rows().get(0).readyToRecord());

        // Check Draft 2 in database (incomplete permitted fields survive)
        UUID draft2Id = response.rows().get(1).draftId();
        RecordDraft draft2 = draftRepository.findByIdAndBusinessId(draft2Id, businessA).orElseThrow();
        assertEquals("1200", draft2.getAmountInput());
        assertNull(draft2.getEffectiveDate());
        assertNull(draft2.getEventType());
        assertNull(draft2.getAccountId());
        assertFalse(response.rows().get(1).readyToRecord());

        // Check Provenance in database
        Optional<ImportBatch> batchOpt = batchRepository.findByIdAndBusinessId(batchId, businessA);
        assertTrue(batchOpt.isPresent());
        assertEquals(2, batchOpt.get().getTotalRowsImported());

        List<ImportBatchRow> batchRows = batchRowRepository.findByBusinessIdAndBatchIdOrderBySourceRowNumberAsc(businessA, batchId);
        assertEquals(2, batchRows.size());
        assertEquals("row-1", batchRows.get(0).getSourceRowId());
        assertEquals(1, batchRows.get(0).getSourceRowNumber());
        assertEquals("REF-001", batchRows.get(0).getSourceReference());
        assertEquals(verbatimAmount, batchRows.get(0).getRawAmountInput());
        assertEquals(draft1Id, batchRows.get(0).getDraftId());

        // Financial tables remain completely untouched
        int afterTxCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM financial_transactions", Integer.class);
        int afterMovCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM account_movements", Integer.class);
        int afterRecordCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM monthly_records", Integer.class);

        assertEquals(initialTxCount, afterTxCount);
        assertEquals(initialMovCount, afterMovCount);
        assertEquals(initialRecordCount, afterRecordCount);
    }

    @Test
    @DisplayName("IT-IMPORT-02: Tenant isolation prevents cross-business viewing or saving")
    void tenantIsolation() {
        FinancialAccount accB = createTestAccount(businessB, "Business B Account", AccountType.CASH, new BigDecimal("1000.00"));

        // Business A cannot save draft referencing Business B's account
        UUID batchId = UUID.randomUUID();
        ImportDraftRowItem row = new ImportDraftRowItem(
                "r-1", 1, accB.getId(), null, null, "100", "desc", null
        );
        ImportDraftsRequest request = new ImportDraftsRequest(batchId, "data.csv", List.of(row));

        assertThrows(DraftValidationException.class, () -> importDraftService.saveImportDrafts(contextA, null, request));

        // Save batch in Business A
        FinancialAccount accA = createTestAccount(businessA, "Business A Account", AccountType.CASH, new BigDecimal("1000.00"));
        ImportDraftRowItem rowA = new ImportDraftRowItem("r-1", 1, accA.getId(), null, null, "100", "desc", null);
        ImportDraftsRequest requestA = new ImportDraftsRequest(batchId, "data.csv", List.of(rowA));
        importDraftService.saveImportDrafts(contextA, null, requestA);

        // Business B cannot view Business A's batch
        assertThrows(ResourceNotFoundException.class, () -> importDraftService.getImportBatch(contextB, batchId));
    }

    @Test
    @DisplayName("IT-IMPORT-03: Durable uniqueness, identical replay and conflict on changed content")
    void uniquenessReplayAndConflict() {
        FinancialAccount acc = createTestAccount(businessA, "Cash Account", AccountType.CASH, new BigDecimal("5000.00"));
        UUID batchId = UUID.randomUUID();

        ImportDraftRowItem row = new ImportDraftRowItem(
                "row-alpha", 1, acc.getId(), "DIRECT_SALE_RECEIPT", today.toString(), "3500", "Product sale", "REF-A"
        );
        ImportDraftsRequest request = new ImportDraftsRequest(batchId, "sales.csv", List.of(row));

        // 1. Initial save
        ImportDraftsResponse initialResp = importDraftService.saveImportDrafts(contextA, null, request);
        assertEquals(1, initialResp.savedCount());
        assertEquals(0, initialResp.replayedCount());
        UUID draftId = initialResp.rows().get(0).draftId();

        // 2. Identical replay
        ImportDraftsResponse replayResp = importDraftService.saveImportDrafts(contextA, null, request);
        assertEquals(0, replayResp.savedCount());
        assertEquals(1, replayResp.replayedCount());
        assertEquals("REPLAYED", replayResp.rows().get(0).status());
        assertEquals(draftId, replayResp.rows().get(0).draftId());

        // 3. Changed payload on same source row ID returns 409 Conflict
        ImportDraftRowItem changedRow = new ImportDraftRowItem(
                "row-alpha", 1, acc.getId(), "DIRECT_SALE_RECEIPT", today.toString(), "4000", "Product sale", "REF-A"
        );
        ImportDraftsRequest conflictReq = new ImportDraftsRequest(batchId, "sales.csv", List.of(changedRow));
        assertThrows(DraftConflictException.class, () -> importDraftService.saveImportDrafts(contextA, null, conflictReq));
    }

    @Test
    @DisplayName("IT-IMPORT-04: Legitimate identical rows with distinct row identities both persist")
    void legitimateIdenticalRowsPersist() {
        UUID batchId = UUID.randomUUID();

        ImportDraftRowItem row1 = new ImportDraftRowItem(
                "row-1", 1, null, null, null, "500", "Same description", "SAME-REF"
        );
        ImportDraftRowItem row2 = new ImportDraftRowItem(
                "row-2", 2, null, null, null, "500", "Same description", "SAME-REF"
        );
        ImportDraftsRequest request = new ImportDraftsRequest(batchId, "items.csv", List.of(row1, row2));

        ImportDraftsResponse response = importDraftService.saveImportDrafts(contextA, null, request);
        assertEquals(2, response.savedCount());
        assertEquals(2, response.rows().size());

        UUID d1 = response.rows().get(0).draftId();
        UUID d2 = response.rows().get(1).draftId();
        assertFalse(d1.equals(d2));
    }

    @Test
    @DisplayName("IT-IMPORT-05: Atomic request rollback ensures all-or-nothing on failure")
    void atomicRequestRollback() {
        UUID batchId = UUID.randomUUID();

        // Row 1 is valid; Row 2 has malformed date
        ImportDraftRowItem row1 = new ImportDraftRowItem(
                "row-1", 1, null, null, null, "500", "Valid row", null
        );
        ImportDraftRowItem row2 = new ImportDraftRowItem(
                "row-2", 2, null, null, "invalid-date", "500", "Invalid row", null
        );
        ImportDraftsRequest request = new ImportDraftsRequest(batchId, "fail.csv", List.of(row1, row2));

        assertThrows(DraftValidationException.class, () -> importDraftService.saveImportDrafts(contextA, null, request));

        // Verify neither draft nor provenance row was persisted
        assertEquals(0, batchRowRepository.findByBusinessIdAndBatchIdOrderBySourceRowNumberAsc(businessA, batchId).size());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM record_drafts WHERE business_id = ?", Integer.class, businessA));
    }

    @Test
    @DisplayName("IT-IMPORT-06: Completed chunk recovery retains prior chunks when later chunk fails")
    void completedChunkRecovery() {
        UUID batchId = UUID.randomUUID();

        // Chunk 1 succeeds
        ImportDraftRowItem chunk1Row = new ImportDraftRowItem("c1-r1", 1, null, null, null, "100", "Chunk 1", null);
        ImportDraftsRequest chunk1Req = new ImportDraftsRequest(batchId, "chunks.csv", List.of(chunk1Row));
        ImportDraftsResponse c1Resp = importDraftService.saveImportDrafts(contextA, null, chunk1Req);
        assertEquals(1, c1Resp.savedCount());

        // Chunk 2 fails due to malformed date
        ImportDraftRowItem chunk2Row = new ImportDraftRowItem("c2-r1", 2, null, null, "not-a-date", "200", "Chunk 2", null);
        ImportDraftsRequest chunk2Req = new ImportDraftsRequest(batchId, "chunks.csv", List.of(chunk2Row));
        assertThrows(DraftValidationException.class, () -> importDraftService.saveImportDrafts(contextA, null, chunk2Req));

        // Verify Chunk 1 remains saved and recoverable
        ImportBatchDetailResponse detail = importDraftService.getImportBatch(contextA, batchId);
        assertEquals(1, detail.totalRows());
        assertEquals(1, detail.rows().size());
        assertEquals("c1-r1", detail.rows().get(0).sourceRowId());
    }

    @Test
    @DisplayName("IT-IMPORT-07: Linked draft recorded or discarded before replay reflects current status without recreation")
    void linkedDraftEditedOrRecordedBeforeReplay() {
        FinancialAccount acc = createTestAccount(businessA, "Main Bank", AccountType.BANK, new BigDecimal("10000.00"));
        UUID batchId = UUID.randomUUID();

        ImportDraftRowItem row = new ImportDraftRowItem(
                "row-rec", 1, acc.getId(), "DIRECT_SALE_RECEIPT", today.toString(), "2500.00", "To Record", "REF-REC"
        );
        ImportDraftsRequest request = new ImportDraftsRequest(batchId, "rec.csv", List.of(row));

        ImportDraftsResponse resp1 = importDraftService.saveImportDrafts(contextA, null, request);
        UUID draftId = resp1.rows().get(0).draftId();

        // Now record the draft using existing RecordDraftService
        DraftRecordRequest recordReq = new DraftRecordRequest(0L, "2500.00", null, null, null, null);
        DraftRecordResponse recorded = recordDraftService.recordDraft(contextA, draftId, "IDEM-REC-1", recordReq);
        assertEquals("RECORDED", recorded.draft().status());
        assertNotNull(recorded.transaction().id());

        // Now replay the import row
        ImportDraftsResponse replayResp = importDraftService.saveImportDrafts(contextA, null, request);
        assertEquals(0, replayResp.savedCount());
        assertEquals(1, replayResp.replayedCount());

        ImportDraftRowResult result = replayResp.rows().get(0);
        assertEquals("REPLAYED", result.status());
        assertEquals(draftId, result.draftId());
        assertEquals("RECORDED", result.draftStatus());
        assertEquals(recorded.transaction().id(), result.recordedTransactionId());

        // Check recovery detail endpoint as well
        ImportBatchDetailResponse detail = importDraftService.getImportBatch(contextA, batchId);
        assertEquals("RECORDED", detail.rows().get(0).draftStatus());
        assertEquals(recorded.transaction().id(), detail.rows().get(0).recordedTransactionId());
    }

    @Test
    @DisplayName("IT-IMPORT-08: Concurrent same-batch/row saving safely succeeds or rejects")
    void concurrentSameBatchRowSaving() throws Exception {
        UUID batchId = UUID.randomUUID();
        ImportDraftRowItem row = new ImportDraftRowItem(
                "row-concurrent", 1, null, null, null, "1000", "Concurrent test", "REF-C"
        );
        ImportDraftsRequest request = new ImportDraftsRequest(batchId, "concurrent.csv", List.of(row));

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<ImportDraftsResponse>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                return importDraftService.saveImportDrafts(contextA, null, request);
            }));
        }

        startLatch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        int savedOrReplayed = 0;
        for (Future<ImportDraftsResponse> f : futures) {
            try {
                ImportDraftsResponse r = f.get();
                if (r.savedCount() == 1 || r.replayedCount() == 1) {
                    savedOrReplayed++;
                }
            } catch (Exception ignored) {
                // In high-concurrency races, some threads may catch a unique constraint violation
            }
        }

        assertTrue(savedOrReplayed >= 1);
        // Ensure strictly one row is in database for this sourceRowId
        List<ImportBatchRow> rows = batchRowRepository.findByBusinessIdAndBatchIdOrderBySourceRowNumberAsc(businessA, batchId);
        assertEquals(1, rows.size());
    }
}
