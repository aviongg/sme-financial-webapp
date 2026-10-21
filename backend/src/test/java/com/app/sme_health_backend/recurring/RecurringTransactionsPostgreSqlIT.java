package com.app.sme_health_backend.recurring;

import com.app.sme_health_backend.accounting.draft.DraftConflictException;
import com.app.sme_health_backend.accounting.draft.DraftCreateRequest;
import com.app.sme_health_backend.accounting.draft.DraftDetailResponse;
import com.app.sme_health_backend.accounting.draft.DraftRecordRequest;
import com.app.sme_health_backend.accounting.draft.DraftRecordResponse;
import com.app.sme_health_backend.accounting.draft.RecordDraftService;
import com.app.sme_health_backend.accounting.dto.TransactionPostRequest;
import com.app.sme_health_backend.accounting.dto.TransactionReverseRequest;
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
import com.app.sme_health_backend.accounting.service.TransactionPostService;
import com.app.sme_health_backend.accounting.service.TransactionReverseService;
import com.app.sme_health_backend.audit.entity.SecurityAuditEvent;
import com.app.sme_health_backend.audit.repository.SecurityAuditRepository;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.counterparty.CounterpartyType;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.dto.CreateBusinessRequest;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.service.BusinessService;
import com.app.sme_health_backend.recurring.dto.*;
import com.app.sme_health_backend.recurring.entity.RecurringRule;
import com.app.sme_health_backend.recurring.entity.RecurringRuleOccurrence;
import com.app.sme_health_backend.recurring.model.RecurringFrequency;
import com.app.sme_health_backend.recurring.model.RecurringOccurrenceStatus;
import com.app.sme_health_backend.recurring.model.RecurringRuleStatus;
import com.app.sme_health_backend.recurring.repository.RecurringRuleOccurrenceRepository;
import com.app.sme_health_backend.recurring.repository.RecurringRuleRepository;
import com.app.sme_health_backend.recurring.service.RecurringRuleService;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("Recurring Transactions Real PostgreSQL Integration Tests")
class RecurringTransactionsPostgreSqlIT extends DisposablePostgres {

    @Autowired
    private RecurringRuleService recurringRuleService;

    @Autowired
    private RecurringRuleRepository recurringRuleRepository;

    @Autowired
    private RecurringRuleOccurrenceRepository recurringOccurrenceRepository;

    @Autowired
    private RecordDraftService recordDraftService;

    @Autowired
    private TransactionPostService transactionPostService;

    @Autowired
    private TransactionReverseService transactionReverseService;

    @Autowired
    private FinancialAccountRepository accountRepository;

    @Autowired
    private CounterpartyRepository counterpartyRepository;

    @Autowired
    private FinancialTransactionRepository transactionRepository;

    @Autowired
    private AccountMovementRepository movementRepository;

    @Autowired
    private BusinessService businessService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private SecurityAuditRepository auditRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private BusinessAccessContext context;
    private UUID businessId;
    private UUID userId;
    private UUID bankAccountId;
    private UUID supplierId;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(ZoneId.of("Asia/Karachi"));
        String unique = UUID.randomUUID().toString().substring(0, 8);

        AppUser user = new AppUser();
        user.setEmail("recurring-owner-" + unique + "@example.com");
        user.setPasswordHash("hashed_pw_secret");
        user.setFullName("Recurring Owner " + unique);
        user = userRepository.saveAndFlush(user);
        userId = user.getId();

        businessId = businessService.createBusiness(
                new CreateBusinessRequest("Recurring Biz " + unique, "retail", "en", null, false, null, null, null),
                userId
        ).businessId();

        context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);

        FinancialAccount account = new FinancialAccount(
                businessId,
                "HBL Main " + unique,
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("500000.00"),
                today.minusMonths(3),
                AccountStatus.ACTIVE,
                "HBL",
                "PKR-123",
                "Main Business Account"
        );
        account = accountRepository.saveAndFlush(account);
        bankAccountId = account.getId();

        Counterparty supplier = new Counterparty(
                businessId,
                "Office Property Ltd " + unique,
                CounterpartyType.SUPPLIER,
                "property@example.com",
                "03001234567",
                "Building Rent",
                null,
                null
        );
        supplier = counterpartyRepository.saveAndFlush(supplier);
        supplierId = supplier.getId();
    }

    @Test
    @DisplayName("Complete Lifecycle: Rule -> Due Draft -> Financial Invariant -> Confirm Posting -> Reversal")
    void testEndToEndRecurringOperationsLifecycle() {
        // Initial Financial Counts
        long initialTxCount = transactionRepository.count();
        long initialMovCount = movementRepository.count();

        // 1. Create Recurring Rule (Office Rent 50,000 PKR monthly, due today)
        RecurringRuleCreateRequest createReq = new RecurringRuleCreateRequest(
                "Office Rent",
                "Monthly office lease",
                "OPERATING_EXPENSE_PAYMENT",
                bankAccountId,
                supplierId,
                "50000.00",
                "MONTHLY",
                today.toString(),
                null
        );
        RecurringRuleResponse ruleResp = recurringRuleService.createRule(context, createReq);
        assertNotNull(ruleResp.id());
        assertEquals("Office Rent", ruleResp.name());
        assertEquals(RecurringRuleStatus.ACTIVE.name(), ruleResp.status());
        assertEquals(today.toString(), ruleResp.nextDueDate());

        // Invariant: Rule creation changes ZERO financial actuals
        assertEquals(initialTxCount, transactionRepository.count(), "Transaction count must not change on rule creation");
        assertEquals(initialMovCount, movementRepository.count(), "Movement count must not change on rule creation");

        // 2. Trigger Due Generation
        OccurrenceGenerateDueResponse genResp = recurringRuleService.generateDueOccurrencesForBusiness(context);
        assertEquals(1, genResp.generatedCount(), "Should generate 1 due occurrence");

        // Verify Occurrence persisted in DRAFT_READY status with linked draft
        List<RecurringRuleOccurrence> occurrences = recurringOccurrenceRepository.findAll();
        RecurringRuleOccurrence occ = occurrences.stream()
                .filter(o -> o.getBusinessId().equals(businessId) && o.getRecurringRuleId().equals(ruleResp.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(RecurringOccurrenceStatus.DRAFT_READY, occ.getStatus());
        assertEquals(today, occ.getScheduledDate());
        assertEquals(0, new BigDecimal("50000.00").compareTo(occ.getAmount()));
        assertNotNull(occ.getDraftId(), "Draft ID must be linked");
        assertNull(occ.getPostedTransactionId(), "Posted transaction must be null before user confirms");

        // Invariant: Draft generation changes ZERO financial actuals
        assertEquals(initialTxCount, transactionRepository.count(), "Transaction count must not change on draft generation");
        assertEquals(initialMovCount, movementRepository.count(), "Movement count must not change on draft generation");

        // 3. Idempotent Replay: Rerunning generation must produce 0 new occurrences and 0 new drafts
        OccurrenceGenerateDueResponse rerunResp = recurringRuleService.generateDueOccurrencesForBusiness(context);
        assertEquals(0, rerunResp.generatedCount(), "Duplicate rerun must generate 0 drafts");
        assertEquals(1, recurringOccurrenceRepository.countByBusinessIdAndStatus(businessId, RecurringOccurrenceStatus.DRAFT_READY));

        // 4. Verify draft appears in To Review
        DraftDetailResponse draft = recordDraftService.getDraft(context, occ.getDraftId());
        assertNotNull(draft);
        assertEquals("50000.00", draft.amountInput());
        assertEquals(bankAccountId, draft.accountId());

        // 5. User explicitly confirms and records the draft
        DraftRecordRequest recordReq = new DraftRecordRequest(
                draft.version(),
                "50000.00",
                bankAccountId,
                "OPERATING_EXPENSE_PAYMENT",
                today.toString(),
                "Office Rent: Monthly office lease",
                supplierId
        );
        DraftRecordResponse recordResp = recordDraftService.recordDraft(context, draft.id(), "REC-IDEMP-001", recordReq);
        assertNotNull(recordResp.transaction());
        UUID postedTxId = recordResp.transaction().id();

        // 6. Assert Financial Mutation Occurred EXACTLY ONCE
        assertEquals(initialTxCount + 1, transactionRepository.count(), "Exactly one transaction created");
        assertEquals(initialMovCount + 1, movementRepository.count(), "Exactly one account movement created");

        FinancialTransaction tx = transactionRepository.findById(postedTxId).orElseThrow();
        assertEquals(TransactionEventType.OPERATING_EXPENSE_PAYMENT, tx.getEventType());
        assertEquals(MovementDirection.DECREASE, tx.getMovementDirection());
        assertEquals(0, new BigDecimal("50000.00").compareTo(tx.getAmount()));

        // 7. Verify Occurrence state transitioned to POSTED
        RecurringRuleOccurrence postedOcc = recurringOccurrenceRepository.findById(occ.getId()).orElseThrow();
        assertEquals(RecurringOccurrenceStatus.POSTED, postedOcc.getStatus());
        assertEquals(postedTxId, postedOcc.getPostedTransactionId());
        assertNotNull(postedOcc.getPostedAt());

        // 8. Reverse the transaction and verify occurrence transitions to REVERSED
        TransactionReverseRequest revReq = new TransactionReverseRequest("Lease cancelled by mutual consent");
        transactionReverseService.postReversal(context, "REV-IDEMP-001", postedTxId, revReq);

        RecurringRuleOccurrence reversedOcc = recurringOccurrenceRepository.findById(occ.getId()).orElseThrow();
        assertEquals(RecurringOccurrenceStatus.REVERSED, reversedOcc.getStatus());
        assertNotNull(reversedOcc.getReversedAt());
    }

    @Test
    @DisplayName("Rule Editing preserves existing occurrences and applies new amount to future occurrences")
    void testRuleEditingPreservesHistory() {
        // Create rule
        RecurringRuleCreateRequest createReq = new RecurringRuleCreateRequest(
                "Cleaning Service",
                "Weekly office cleaning",
                "OPERATING_EXPENSE_PAYMENT",
                bankAccountId,
                supplierId,
                "10000.00",
                "WEEKLY",
                today.toString(),
                null
        );
        RecurringRuleResponse rule = recurringRuleService.createRule(context, createReq);

        // Generate occurrence for today
        recurringRuleService.generateDueOccurrencesForBusiness(context);

        RecurringRuleOccurrence occ1 = recurringOccurrenceRepository.findAll().stream()
                .filter(o -> o.getBusinessId().equals(businessId) && o.getScheduledDate().equals(today))
                .findFirst()
                .orElseThrow();
        assertEquals(0, new BigDecimal("10000.00").compareTo(occ1.getAmount()));

        // Reload rule to get updated version after nextDueDate advancement
        RecurringRuleResponse reloadedRule = recurringRuleService.getRule(context, rule.id());

        // Update future rule amount to 12,000 PKR
        RecurringRuleUpdateRequest updateReq = new RecurringRuleUpdateRequest(
                "Cleaning Service Enhanced",
                "Weekly deep cleaning",
                "OPERATING_EXPENSE_PAYMENT",
                bankAccountId,
                supplierId,
                "12000.00",
                "WEEKLY",
                null,
                reloadedRule.version()
        );
        RecurringRuleResponse updatedRule = recurringRuleService.updateRule(context, rule.id(), updateReq);
        assertEquals("12000.00", updatedRule.amount());
        assertEquals(reloadedRule.version() + 1, updatedRule.version());

        // Verify historical occurrence 1 retained original 10,000 PKR
        RecurringRuleOccurrence reloadedOcc1 = recurringOccurrenceRepository.findById(occ1.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("10000.00").compareTo(reloadedOcc1.getAmount()));
    }

    @Test
    @DisplayName("Skip Occurrence discards linked draft without posting")
    void testSkipOccurrenceDiscardsDraft() {
        RecurringRuleCreateRequest createReq = new RecurringRuleCreateRequest(
                "Optional Maintenance",
                "Quarterly maintenance",
                "OPERATING_EXPENSE_PAYMENT",
                bankAccountId,
                supplierId,
                "25000.00",
                "QUARTERLY",
                today.toString(),
                null
        );
        RecurringRuleResponse rule = recurringRuleService.createRule(context, createReq);
        recurringRuleService.generateDueOccurrencesForBusiness(context);

        RecurringRuleOccurrence occ = recurringOccurrenceRepository.findAll().stream()
                .filter(o -> o.getBusinessId().equals(businessId) && o.getRecurringRuleId().equals(rule.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(RecurringOccurrenceStatus.DRAFT_READY, occ.getStatus());

        // Skip the occurrence
        RecurringOccurrenceResponse skipped = recurringRuleService.skipOccurrence(
                context,
                occ.getId(),
                new OccurrenceSkipRequest("Building closed for repairs")
        );
        assertEquals(RecurringOccurrenceStatus.SKIPPED.name(), skipped.status());

        // Verify draft was discarded
        DraftDetailResponse draft = recordDraftService.getDraft(context, occ.getDraftId());
        assertEquals("DISCARDED", draft.status());

        // Invariant: Zero financial actuals
        assertEquals(0, transactionRepository.findAll().stream().filter(t -> t.getBusinessId().equals(businessId)).count());
    }

    @Test
    @DisplayName("Multi-tenant isolation: Tenant B cannot access or modify Tenant A's recurring rules")
    void testTenantIsolation() {
        // Create Tenant A rule
        RecurringRuleCreateRequest req = new RecurringRuleCreateRequest(
                "Tenant A Secret Rent",
                null,
                "OPERATING_EXPENSE_PAYMENT",
                bankAccountId,
                null,
                "40000.00",
                "MONTHLY",
                today.toString(),
                null
        );
        RecurringRuleResponse ruleA = recurringRuleService.createRule(context, req);

        // Tenant B setup
        AppUser otherUser = new AppUser();
        otherUser.setEmail("other-tenant@example.com");
        otherUser.setPasswordHash("pw");
        otherUser.setFullName("Other User");
        UUID otherUserId = userRepository.saveAndFlush(otherUser).getId();
        UUID otherBizId = businessService.createBusiness(
                new CreateBusinessRequest("Other Biz", "retail", "en", null, false, null, null, null),
                otherUserId
        ).businessId();
        BusinessAccessContext contextB = new BusinessAccessContext(otherUserId, otherBizId, MembershipRole.OWNER);

        // Tenant B attempts to read Tenant A's rule -> ResourceNotFoundException
        assertThrows(
                ResourceNotFoundException.class,
                () -> recurringRuleService.getRule(contextB, ruleA.id())
        );

        // Tenant B attempts to pause Tenant A's rule -> ResourceNotFoundException
        assertThrows(
                ResourceNotFoundException.class,
                () -> recurringRuleService.pauseRule(contextB, ruleA.id())
        );
    }

    @Test
    @DisplayName("Automatic scheduler: processes due rules across all businesses, ignores paused/archived, logs system audit")
    void testAutomaticSchedulerProcessingAcrossBusinesses() {
        // Business A active rule
        RecurringRuleCreateRequest reqA = new RecurringRuleCreateRequest(
                "Biz A Rent",
                "Rent for A",
                "OPERATING_EXPENSE_PAYMENT",
                bankAccountId,
                null,
                "50000.00",
                "MONTHLY",
                today.toString(),
                null
        );
        RecurringRuleResponse ruleA = recurringRuleService.createRule(context, reqA);

        // Business A paused rule
        RecurringRuleCreateRequest reqPaused = new RecurringRuleCreateRequest(
                "Biz A Paused Subscription",
                null,
                "OPERATING_EXPENSE_PAYMENT",
                bankAccountId,
                null,
                "5000.00",
                "MONTHLY",
                today.toString(),
                null
        );
        RecurringRuleResponse rulePaused = recurringRuleService.createRule(context, reqPaused);
        recurringRuleService.pauseRule(context, rulePaused.id());

        // Business A archived rule
        RecurringRuleCreateRequest reqArchived = new RecurringRuleCreateRequest(
                "Biz A Archived Gym",
                null,
                "OPERATING_EXPENSE_PAYMENT",
                bankAccountId,
                null,
                "3000.00",
                "MONTHLY",
                today.toString(),
                null
        );
        RecurringRuleResponse ruleArchived = recurringRuleService.createRule(context, reqArchived);
        recurringRuleService.archiveRule(context, ruleArchived.id());

        // Business B setup with an active rule
        AppUser userB = new AppUser();
        userB.setEmail("biz-b-owner-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
        userB.setPasswordHash("pw");
        userB.setFullName("Biz B Owner");
        UUID userBId = userRepository.saveAndFlush(userB).getId();
        UUID bizBId = businessService.createBusiness(
                new CreateBusinessRequest("Biz B Operations", "retail", "en", null, false, null, null, null),
                userBId
        ).businessId();
        BusinessAccessContext contextB = new BusinessAccessContext(userBId, bizBId, MembershipRole.OWNER);

        FinancialAccount accountB = new FinancialAccount(
                bizBId,
                "Biz B Bank",
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("300000.00"),
                today.minusMonths(2),
                AccountStatus.ACTIVE,
                "MBL",
                "PKR-B-1",
                "Biz B Account"
        );
        accountB = accountRepository.saveAndFlush(accountB);

        RecurringRuleCreateRequest reqB = new RecurringRuleCreateRequest(
                "Biz B Cloud Server",
                "AWS Hosting",
                "OPERATING_EXPENSE_PAYMENT",
                accountB.getId(),
                null,
                "15000.00",
                "MONTHLY",
                today.toString(),
                null
        );
        RecurringRuleResponse ruleB = recurringRuleService.createRule(contextB, reqB);

        // Run automated background generation across ALL businesses
        int totalGenerated = recurringRuleService.generateDueOccurrencesAllBusinesses();
        assertTrue(totalGenerated >= 2, "Must generate drafts for active rules in both businesses");

        // Verify Business A active rule generated draft
        List<RecurringRuleOccurrence> occsA = recurringOccurrenceRepository.findAll().stream()
                .filter(o -> o.getBusinessId().equals(businessId) && o.getRecurringRuleId().equals(ruleA.id()))
                .toList();
        assertEquals(1, occsA.size(), "Business A active rule must have 1 occurrence");
        assertEquals(RecurringOccurrenceStatus.DRAFT_READY, occsA.get(0).getStatus());

        // Verify Business B active rule generated draft
        List<RecurringRuleOccurrence> occsB = recurringOccurrenceRepository.findAll().stream()
                .filter(o -> o.getBusinessId().equals(bizBId) && o.getRecurringRuleId().equals(ruleB.id()))
                .toList();
        assertEquals(1, occsB.size(), "Business B active rule must have 1 occurrence");
        assertEquals(RecurringOccurrenceStatus.DRAFT_READY, occsB.get(0).getStatus());

        // Verify paused and archived rules generated ZERO occurrences
        List<RecurringRuleOccurrence> pausedOccs = recurringOccurrenceRepository.findAll().stream()
                .filter(o -> o.getBusinessId().equals(businessId) && o.getRecurringRuleId().equals(rulePaused.id()))
                .toList();
        assertEquals(0, pausedOccs.size(), "Paused rule must not generate occurrences");

        List<RecurringRuleOccurrence> archivedOccs = recurringOccurrenceRepository.findAll().stream()
                .filter(o -> o.getBusinessId().equals(businessId) && o.getRecurringRuleId().equals(ruleArchived.id()))
                .toList();
        assertEquals(0, archivedOccs.size(), "Archived rule must not generate occurrences");

        // Verify security audit events logged with automated system flag
        List<SecurityAuditEvent> audits = auditRepository.findAll().stream()
                .filter(a -> "RECURRING_OCCURRENCE_GENERATED".equals(a.getEventType()) && a.isSystem())
                .toList();
        assertFalse(audits.isEmpty(), "Security audit repository must record is_system=true for automated occurrences");
    }

    @Test
    @DisplayName("Concurrency: Simultaneous racing generation calls produce exactly one occurrence")
    void testConcurrentGenerationRacingAndIdempotency() throws Exception {
        RecurringRuleCreateRequest req = new RecurringRuleCreateRequest(
                "Concurrent Rent Test",
                "High concurrency check",
                "OPERATING_EXPENSE_PAYMENT",
                bankAccountId,
                null,
                "25000.00",
                "MONTHLY",
                today.toString(),
                null
        );
        RecurringRuleResponse rule = recurringRuleService.createRule(context, req);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<Integer> task = () -> {
            readyLatch.countDown();
            startLatch.await();
            return recurringRuleService.generateDueOccurrencesForBusiness(context).generatedCount();
        };

        Future<Integer> f1 = executor.submit(task);
        Future<Integer> f2 = executor.submit(task);

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        int res1 = f1.get(10, TimeUnit.SECONDS);
        int res2 = f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one thread should generate the draft, while the other sees 0 due
        assertEquals(1, res1 + res2, "Across racing executions, exactly 1 occurrence must be generated");

        List<RecurringRuleOccurrence> occs = recurringOccurrenceRepository.findAll().stream()
                .filter(o -> o.getBusinessId().equals(businessId) && o.getRecurringRuleId().equals(rule.id()))
                .toList();
        assertEquals(1, occs.size(), "Exactly 1 occurrence record must exist in DB");
    }

    @Test
    @DisplayName("Bounded catch-up: 70 overdue weeks generates 50 in batch 1, remaining 21 in batch 2")
    void testBoundedCatchUpAfterDowntime() {
        FinancialAccount oldAccount = new FinancialAccount(
                businessId,
                "Old HBL Account",
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("500000.00"),
                today.minusWeeks(75),
                AccountStatus.ACTIVE,
                "HBL",
                "PKR-OLD-1",
                "Old Bank Account"
        );
        oldAccount = accountRepository.saveAndFlush(oldAccount);

        // Start date 70 weeks in the past
        LocalDate pastDate = today.minusWeeks(70);
        RecurringRuleCreateRequest req = new RecurringRuleCreateRequest(
                "Long Backlog Service",
                "Simulated downtime catch-up",
                "OPERATING_EXPENSE_PAYMENT",
                oldAccount.getId(),
                null,
                "1000.00",
                "WEEKLY",
                pastDate.toString(),
                null
        );
        RecurringRuleResponse rule = recurringRuleService.createRule(context, req);

        // Run batch 1
        OccurrenceGenerateDueResponse batch1 = recurringRuleService.generateDueOccurrencesForBusiness(context);
        assertEquals(50, batch1.generatedCount(), "Batch 1 must be strictly capped at MAX_OCCURRENCES_PER_RUN = 50");

        // Verify rule still has nextDueDate in the past (backlog remains discoverable)
        RecurringRuleResponse reloadedRule = recurringRuleService.getRule(context, rule.id());
        assertNotNull(reloadedRule.nextDueDate());
        LocalDate remainingNextDue = LocalDate.parse(reloadedRule.nextDueDate());
        assertFalse(remainingNextDue.isAfter(today), "Remaining nextDueDate must remain <= today for next catch-up");

        // Run batch 2
        OccurrenceGenerateDueResponse batch2 = recurringRuleService.generateDueOccurrencesForBusiness(context);
        assertEquals(21, batch2.generatedCount(), "Batch 2 must generate remaining 21 occurrences (71 total weeks)");

        // Verify total occurrences for this rule is now 71
        long totalOccs = recurringOccurrenceRepository.findAll().stream()
                .filter(o -> o.getBusinessId().equals(businessId) && o.getRecurringRuleId().equals(rule.id()))
                .count();
        assertEquals(71, totalOccs, "Total occurrences generated across batches must equal 71");
    }

    @Test
    @DisplayName("Shared Service Regression: Manual draft and ordinary reversal unaffected by recurrence hooks")
    void testRegressionManualDraftAndOrdinaryReversalUnaffected() {
        long initialOccCount = recurringOccurrenceRepository.count();

        // 1. Create a pure manual draft without recurring rule
        DraftCreateRequest draftReq = new DraftCreateRequest(
                bankAccountId,
                "OPERATING_EXPENSE_PAYMENT",
                today.toString(),
                "4500.00",
                "Manual printer paper"
        );
        DraftDetailResponse manualDraft = recordDraftService.createDraft(context, "MAN-DRAFT-KEY-" + UUID.randomUUID(), draftReq);
        assertNotNull(manualDraft.id());

        // Record the manual draft
        DraftRecordRequest recordReq = new DraftRecordRequest(
                manualDraft.version(),
                "4500.00",
                bankAccountId,
                "OPERATING_EXPENSE_PAYMENT",
                today.toString(),
                "Manual printer paper confirmed",
                null
        );
        DraftRecordResponse recordResp = recordDraftService.recordDraft(context, manualDraft.id(), "MAN-DRAFT-IDEMP-" + UUID.randomUUID(), recordReq);
        assertNotNull(recordResp.transaction());
        assertEquals(initialOccCount, recurringOccurrenceRepository.count(), "Manual draft recording must not create or alter recurring occurrences");

        // 2. Post an ordinary manual transaction directly
        TransactionPostRequest txReq = new TransactionPostRequest(
                bankAccountId,
                "DIRECT_SALE_RECEIPT",
                "18000.00",
                today.toString(),
                "Walk-in cash receipt",
                null
        );
        var directTx = transactionPostService.postTransaction(context, "DIRECT-TX-" + UUID.randomUUID(), txReq);
        assertNotNull(directTx.id());

        // Reverse the ordinary transaction
        TransactionReverseRequest revReq = new TransactionReverseRequest("Customer return");
        var revResp = transactionReverseService.postReversal(context, "REV-DIRECT-" + UUID.randomUUID(), directTx.id(), revReq);
        assertNotNull(revResp.id());

        assertEquals(initialOccCount, recurringOccurrenceRepository.count(), "Reversing ordinary transaction must not create or alter recurring occurrences");
    }

    @Test
    @DisplayName("Reversal of recurring transaction marks occurrence terminal and prevents reposting single and concurrently")
    void testReversalTerminalGuardPreventsRepostingAndPreservesProvenance() throws Exception {
        // 1. Create an active recurring operating expense rule
        long initialTxCount = transactionRepository.count();
        long initialMovCount = movementRepository.count();

        RecurringRuleCreateRequest createReq = new RecurringRuleCreateRequest(
                "Office Internet",
                "Monthly fiber internet connection",
                "OPERATING_EXPENSE_PAYMENT",
                bankAccountId,
                supplierId,
                "15000.00",
                "MONTHLY",
                today.toString(),
                null
        );
        RecurringRuleResponse ruleResp = recurringRuleService.createRule(context, createReq);
        assertNotNull(ruleResp.id());

        // 2. Generate a due occurrence and its reviewable draft
        OccurrenceGenerateDueResponse genResp = recurringRuleService.generateDueOccurrencesForBusiness(context);
        assertEquals(1, genResp.generatedCount(), "Should generate exactly 1 due occurrence");

        RecurringRuleOccurrence occ = recurringOccurrenceRepository.findAll().stream()
                .filter(o -> o.getBusinessId().equals(businessId) && o.getRecurringRuleId().equals(ruleResp.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(RecurringOccurrenceStatus.DRAFT_READY, occ.getStatus());
        assertNotNull(occ.getDraftId());
        assertNull(occ.getPostedTransactionId());

        DraftDetailResponse draft = recordDraftService.getDraft(context, occ.getDraftId());
        assertNotNull(draft);
        assertEquals("15000.00", draft.amountInput());

        // 3. Explicitly record the draft
        DraftRecordRequest recordReq = new DraftRecordRequest(
                draft.version(),
                "15000.00",
                bankAccountId,
                "OPERATING_EXPENSE_PAYMENT",
                today.toString(),
                "Office Internet: Monthly payment",
                supplierId
        );
        String initialIdempKey = "REC-GUARD-INIT-" + UUID.randomUUID();
        DraftRecordResponse recordResp = recordDraftService.recordDraft(context, draft.id(), initialIdempKey, recordReq);
        assertNotNull(recordResp.transaction());
        UUID postedTxId = recordResp.transaction().id();

        // 4. Confirm exactly one original FinancialTransaction and AccountMovement
        assertEquals(initialTxCount + 1, transactionRepository.count(), "Exactly one transaction created for initial recording");
        assertEquals(initialMovCount + 1, movementRepository.count(), "Exactly one movement created for initial recording");

        FinancialTransaction origTx = transactionRepository.findById(postedTxId).orElseThrow();
        assertEquals(TransactionEventType.OPERATING_EXPENSE_PAYMENT, origTx.getEventType());
        assertEquals(MovementDirection.DECREASE, origTx.getMovementDirection());
        assertEquals(0, new BigDecimal("15000.00").compareTo(origTx.getAmount()));

        RecurringRuleOccurrence postedOcc = recurringOccurrenceRepository.findById(occ.getId()).orElseThrow();
        assertEquals(RecurringOccurrenceStatus.POSTED, postedOcc.getStatus());
        assertEquals(postedTxId, postedOcc.getPostedTransactionId());
        assertNotNull(postedOcc.getPostedAt());
        assertNull(postedOcc.getReversedAt());

        // 5. Reverse the transaction
        String revIdempKey = "REV-GUARD-" + UUID.randomUUID();
        TransactionReverseRequest revReq = new TransactionReverseRequest("Billing correction and cancellation");
        var revResp = transactionReverseService.postReversal(context, revIdempKey, postedTxId, revReq);
        assertNotNull(revResp.id());

        // 6. Confirm one compensating FinancialTransaction and AccountMovement
        assertEquals(initialTxCount + 2, transactionRepository.count(), "Exactly one compensating reversal transaction created");
        assertEquals(initialMovCount + 2, movementRepository.count(), "Exactly one compensating movement created");

        FinancialTransaction revTx = transactionRepository.findById(revResp.id()).orElseThrow();
        assertEquals(postedTxId, revTx.getReversedTransactionId());
        assertEquals(MovementDirection.INCREASE, revTx.getMovementDirection());

        // 7. Verify the occurrence is REVERSED and retains its original posting/reversal provenance
        RecurringRuleOccurrence reversedOcc = recurringOccurrenceRepository.findById(occ.getId()).orElseThrow();
        assertEquals(RecurringOccurrenceStatus.REVERSED, reversedOcc.getStatus(), "Occurrence must transition to REVERSED");
        assertEquals(postedTxId, reversedOcc.getPostedTransactionId(), "Original posted transaction reference must be preserved");
        assertNotNull(reversedOcc.getPostedAt(), "Original posted timestamp must be preserved");
        assertNotNull(reversedOcc.getReversedAt(), "Reversed timestamp must be populated");

        // 8. Attempt to record the SAME draft again, using a NEW idempotency key
        String newIdempKey = "REC-GUARD-NEW-" + UUID.randomUUID();
        DraftRecordRequest secondRecordReq = new DraftRecordRequest(
                draft.version(),
                "15000.00",
                bankAccountId,
                "OPERATING_EXPENSE_PAYMENT",
                today.toString(),
                "Office Internet: Attempted second posting",
                supplierId
        );
        DraftConflictException singleEx = assertThrows(DraftConflictException.class, () ->
                recordDraftService.recordDraft(context, draft.id(), newIdempKey, secondRecordReq)
        );
        assertTrue(singleEx.getMessage().contains("already been recorded") || singleEx.getMessage().contains("reversed"),
                "Rejection message must indicate already recorded or reversed occurrence, got: " + singleEx.getMessage());

        // Confirm 0 new transactions after single attempt
        assertEquals(initialTxCount + 2, transactionRepository.count(), "Transaction count must not change on rejected single replay");
        assertEquals(initialMovCount + 2, movementRepository.count(), "Movement count must not change on rejected single replay");

        // 9. Attempt the same operation concurrently with two requests
        int concurrency = 2;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            final String threadKey = "REC-GUARD-CONCURRENT-" + i + "-" + UUID.randomUUID();
            futures.add(executor.submit(() -> {
                startLatch.await();
                try {
                    recordDraftService.recordDraft(context, draft.id(), threadKey, secondRecordReq);
                    return "SUCCESS";
                } catch (DraftConflictException e) {
                    return "CONFLICT: " + e.getMessage();
                } catch (Exception e) {
                    return "ERROR: " + e.getClass().getSimpleName() + " - " + e.getMessage();
                }
            }));
        }

        startLatch.countDown();
        for (Future<String> f : futures) {
            String outcome = f.get(5, TimeUnit.SECONDS);
            assertTrue(outcome.startsWith("CONFLICT"), "Concurrent requests must both be rejected with Conflict, got: " + outcome);
        }
        executor.shutdown();

        // 10. Verify that the original recurring occurrence cannot silently become POSTED again or overwrite its historical posting reference
        RecurringRuleOccurrence finalOcc = recurringOccurrenceRepository.findById(occ.getId()).orElseThrow();
        assertEquals(RecurringOccurrenceStatus.REVERSED, finalOcc.getStatus(), "Occurrence status must remain REVERSED");
        assertEquals(postedTxId, finalOcc.getPostedTransactionId(), "Posted transaction ID must not be overwritten");
        assertEquals(reversedOcc.getReversedAt(), finalOcc.getReversedAt(), "Reversed timestamp must remain unchanged");

        // Ledger actuals must remain exactly initialTxCount + 2 (1 original, 1 compensating reversal)
        assertEquals(initialTxCount + 2, transactionRepository.count(), "Zero duplicate transactions may be created");
        assertEquals(initialMovCount + 2, movementRepository.count(), "Zero duplicate movements may be created");
    }
}
