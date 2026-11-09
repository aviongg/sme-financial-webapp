package com.app.sme_health_backend.projectattribution;

import com.app.sme_health_backend.accounting.dto.TransactionPostRequest;
import com.app.sme_health_backend.accounting.dto.TransactionReverseRequest;
import com.app.sme_health_backend.accounting.dto.TransactionDetailResponse;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialTransactionRepository;
import com.app.sme_health_backend.accounting.service.TransactionPostService;
import com.app.sme_health_backend.accounting.service.TransactionReverseService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.dto.CsrfResponse;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.entity.Business;
import com.app.sme_health_backend.identity.entity.BusinessMembership;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.model.MembershipStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.repository.BusinessMembershipRepository;
import com.app.sme_health_backend.identity.repository.BusinessRepository;
import com.app.sme_health_backend.projectattribution.dto.AttributionCreateRequest;
import com.app.sme_health_backend.projectattribution.entity.ProjectFinancialAttribution;
import com.app.sme_health_backend.projectattribution.model.AttributionCostCategory;
import com.app.sme_health_backend.projectattribution.model.AttributionSourceType;
import com.app.sme_health_backend.projectattribution.model.AttributionStatus;
import com.app.sme_health_backend.projectattribution.repository.ProjectFinancialAttributionRepository;
import com.app.sme_health_backend.projectattribution.service.ProjectFinancialAttributionService;
import com.app.sme_health_backend.projectattribution.service.ProjectFinancialCostingService;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.whatsapp.provider=mock")
@AutoConfigureMockMvc
class ProjectAttributionPostgreSqlIT extends DisposablePostgres {

    private static final String DEFAULT_PASSWORD = "Password123!";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private BusinessRepository businesses;

    @Autowired
    private BusinessMembershipRepository memberships;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private FinancialAccountRepository accounts;

    @Autowired
    private FinancialTransactionRepository transactions;

    @Autowired
    private ProjectFinancialAttributionRepository attributionRepository;

    @Autowired
    private ProjectFinancialAttributionService attributionService;

    @Autowired
    private ProjectFinancialCostingService costingService;

    @Autowired
    private TransactionPostService postService;

    @Autowired
    private TransactionReverseService reverseService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private UserBusinessFixture ownerFixture;

    @BeforeEach
    void setUp() throws Exception {
        ownerFixture = createFixture("proj-owner-" + UUID.randomUUID().toString().substring(0, 8), MembershipRole.OWNER);
    }

    @Test
    @DisplayName("Fixture A: Project actuals vs approved budget cash-to-plan headroom")
    void testFixtureA_ProjectActualsVsApprovedBudget() throws Exception {
        UUID businessId = ownerFixture.businessId();
        UUID projectId = insertProject(businessId, "EXP-A102", "Export Order A-102", "PKR");

        // Approved budget: Material 200k, Labour 80k, Freight 30k, Overhead 20k -> Total 330k
        insertApprovedBudget(businessId, projectId, 1, new BigDecimal("400000.0000"), new BigDecimal("330000.0000"), List.of(
                new BudgetLine("MATERIAL", "Export fabric", new BigDecimal("200000.0000")),
                new BudgetLine("LABOUR", "Stitching & cutting", new BigDecimal("80000.0000")),
                new BudgetLine("FREIGHT", "Container freight", new BigDecimal("30000.0000")),
                new BudgetLine("OVERHEAD", "Facility allocation", new BigDecimal("20000.0000"))
        ));

        FinancialAccount bankAcc = createAccount(businessId, "Operating Bank", AccountType.BANK, "PKR", 2, "1000000.00");

        // Post real transactions
        UUID txMaterial = postPayment(businessId, bankAcc.getId(), new BigDecimal("120000.00"), "Material payment");
        UUID txLabour = postPayment(businessId, bankAcc.getId(), new BigDecimal("50000.00"), "Labour payment");
        UUID txFreight = postPayment(businessId, bankAcc.getId(), new BigDecimal("25000.00"), "Freight payment");

        // Attribute to project
        attributeSourceViaApi(ownerFixture, projectId, txMaterial, AttributionSourceType.TRANSACTION, new BigDecimal("120000.00"), AttributionCostCategory.MATERIAL);
        attributeSourceViaApi(ownerFixture, projectId, txLabour, AttributionSourceType.TRANSACTION, new BigDecimal("50000.00"), AttributionCostCategory.LABOUR);
        attributeSourceViaApi(ownerFixture, projectId, txFreight, AttributionSourceType.TRANSACTION, new BigDecimal("25000.00"), AttributionCostCategory.FREIGHT);

        // Fetch costing summary
        mvc.perform(get("/api/finance/projects/" + projectId + "/costing-summary")
                        .cookie(ownerFixture.session())
                        .header("X-XSRF-TOKEN", ownerFixture.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.projectCode").value("EXP-A102"))
                .andExpect(jsonPath("$.hasApprovedBudget").value(true))
                .andExpect(jsonPath("$.plannedCost").value(330000.0))
                .andExpect(jsonPath("$.attributedCashOutflows").value(195000.0))
                .andExpect(jsonPath("$.cashHeadroom").value(135000.0))
                .andExpect(jsonPath("$.profitabilityStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.unavailabilityReason").value("INCOMPLETE_COST_EVIDENCE"))
                // Check category headroom
                .andExpect(jsonPath("$.categories[?(@.category=='MATERIAL')].actualCashOutflow").value(120000.0))
                .andExpect(jsonPath("$.categories[?(@.category=='MATERIAL')].cashHeadroom").value(80000.0))
                .andExpect(jsonPath("$.categories[?(@.category=='LABOUR')].actualCashOutflow").value(50000.0))
                .andExpect(jsonPath("$.categories[?(@.category=='LABOUR')].cashHeadroom").value(30000.0))
                .andExpect(jsonPath("$.categories[?(@.category=='FREIGHT')].actualCashOutflow").value(25000.0))
                .andExpect(jsonPath("$.categories[?(@.category=='FREIGHT')].cashHeadroom").value(5000.0))
                .andExpect(jsonPath("$.categories[?(@.category=='OVERHEAD')].actualCashOutflow").value(0.0))
                .andExpect(jsonPath("$.categories[?(@.category=='OVERHEAD')].cashHeadroom").value(20000.0));
    }

    @Test
    @DisplayName("Fixture B: Split allocation across multiple projects and rejection of over-allocation")
    void testFixtureB_SplitAllocationAndOverAllocationRejection() throws Exception {
        UUID businessId = ownerFixture.businessId();
        UUID projectA = insertProject(businessId, "PROJ-A", "Project Alpha", "PKR");
        UUID projectB = insertProject(businessId, "PROJ-B", "Project Beta", "PKR");

        FinancialAccount bankAcc = createAccount(businessId, "Supplier Bank", AccountType.BANK, "PKR", 2, "500000.00");
        UUID supplierTx = postPayment(businessId, bankAcc.getId(), new BigDecimal("100000.00"), "Bulk steel supply");

        // Split: 60,000 to Project A
        attributeSourceViaApi(ownerFixture, projectA, supplierTx, AttributionSourceType.TRANSACTION, new BigDecimal("60000.00"), AttributionCostCategory.MATERIAL);

        // Split: 40,000 to Project B
        attributeSourceViaApi(ownerFixture, projectB, supplierTx, AttributionSourceType.TRANSACTION, new BigDecimal("40000.00"), AttributionCostCategory.MATERIAL);

        // Verify remaining eligible amount is now 0
        var eligibleA = attributionService.getEligibleSources(businessId, projectA);
        boolean existsInEligible = eligibleA.stream().anyMatch(e -> e.sourceId().equals(supplierTx));
        assertThat(existsInEligible).isFalse();

        // Attempting to attribute an additional 10,000 to Project A must be rejected with 400 Bad Request
        AttributionCreateRequest overReq = new AttributionCreateRequest(
                AttributionSourceType.TRANSACTION,
                supplierTx,
                new BigDecimal("10000.00"),
                AttributionCostCategory.MATERIAL,
                "Over allocation attempt",
                LocalDate.now(),
                null
        );

        mvc.perform(post("/api/finance/projects/" + projectA + "/attributions")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(overReq)))
                .andExpect(status().isBadRequest());

        // In DB, total active attributions for this transaction is exactly 100,000
        BigDecimal activeSum = attributionRepository.sumAttributedAmountBySource(
                businessId, AttributionSourceType.TRANSACTION, supplierTx, AttributionStatus.ACTIVE
        );
        assertThat(activeSum).isEqualByComparingTo("100000.0000");
    }

    @Test
    @DisplayName("Fixture C: Commission cost integration does not duplicate cash")
    void testFixtureC_CommissionCostNonDuplication() throws Exception {
        UUID businessId = ownerFixture.businessId();
        UUID projectId = insertProject(businessId, "COMM-PROJ", "Commission Test Project", "PKR");

        UUID counterpartyId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO counterparties (id, business_id, display_name, type, status)
                VALUES (?, ?, 'Test Customer', 'CUSTOMER', 'ACTIVE')
                """, counterpartyId, businessId);

        UUID salespersonId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO salesperson_profiles (id, business_id, display_name, email, status)
                VALUES (?, ?, 'Tariq Salesperson', 'tariq@test.local', 'ACTIVE')
                """, salespersonId, businessId);

        UUID ruleId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO commission_rules (id, business_id, salesperson_id, rule_name, calculation_type, earning_basis, rate_percentage, currency, effective_start_date, status)
                VALUES (?, ?, ?, 'Standard 5%', 'PERCENTAGE', 'SALES_BASED', 5.0000, 'PKR', '2026-01-01', 'ACTIVE')
                """, ruleId, businessId, salespersonId);

        UUID earningId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO commission_earnings (
                    id, business_id, salesperson_id, rule_id, counterparty_id, source_type, source_id, source_fingerprint,
                    sale_amount, commission_rate_snapshot, commission_amount, currency, status, approved_by_user_id
                ) VALUES (?, ?, ?, ?, ?, 'MANUAL_SALE_DECLARATION', 'SRC-1', 'FP-1', 100000.0000, 5.0000, 5000.0000, 'PKR', 'APPROVED', ?)
                """, earningId, businessId, salespersonId, ruleId, counterpartyId, ownerFixture.user().getId());

        // Attribute commission earning to project
        attributeSourceViaApi(ownerFixture, projectId, earningId, AttributionSourceType.COMMISSION_RECOGNITION, new BigDecimal("5000.00"), AttributionCostCategory.COMMISSION);

        // Fetch costing summary
        mvc.perform(get("/api/finance/projects/" + projectId + "/costing-summary")
                        .cookie(ownerFixture.session())
                        .header("X-XSRF-TOKEN", ownerFixture.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributedCashOutflows").value(0.0))
                .andExpect(jsonPath("$.recognizedCommissionCost").value(5000.0))
                .andExpect(jsonPath("$.categories[?(@.category=='COMMISSION')].recognizedCost").value(5000.0))
                .andExpect(jsonPath("$.categories[?(@.category=='COMMISSION')].actualCashOutflow").value(0.0));
    }

    @Test
    @DisplayName("Fixture D: Source reversal automatically invalidates active project attribution")
    void testFixtureD_SourceReversalInvalidatesAttribution() throws Exception {
        UUID businessId = ownerFixture.businessId();
        UUID projectId = insertProject(businessId, "REV-PROJ", "Reversal Test Project", "PKR");
        FinancialAccount bankAcc = createAccount(businessId, "Reversal Bank", AccountType.BANK, "PKR", 2, "500000.00");

        UUID txId = postPayment(businessId, bankAcc.getId(), new BigDecimal("50000.00"), "Temporary supplier charge");
        attributeSourceViaApi(ownerFixture, projectId, txId, AttributionSourceType.TRANSACTION, new BigDecimal("50000.00"), AttributionCostCategory.MATERIAL);

        // Costing summary shows 50,000 cash outflow
        var summaryBefore = costingService.getCostingSummary(businessId, projectId);
        assertThat(summaryBefore.attributedCashOutflows()).isEqualByComparingTo("50000.0000");

        // Now reverse the transaction using TransactionReverseService
        BusinessAccessContext context = new BusinessAccessContext(ownerFixture.user().getId(), businessId, MembershipRole.OWNER);
        reverseService.postReversal(context, "rev-idem-" + UUID.randomUUID(), txId, new TransactionReverseRequest("Wrong vendor charged"));

        // Verify attribution in DB is now REVERSED
        List<ProjectFinancialAttribution> attributions = attributionRepository.findByBusinessIdAndProjectIdOrderByAttributionDateDescCreatedAtDesc(businessId, projectId);
        assertThat(attributions).hasSize(1);
        assertThat(attributions.get(0).getStatus()).isEqualTo(AttributionStatus.REVERSED);
        assertThat(attributions.get(0).getReversalReason()).contains("Wrong vendor charged");

        // Costing summary now shows 0 active cash outflow
        var summaryAfter = costingService.getCostingSummary(businessId, projectId);
        assertThat(summaryAfter.attributedCashOutflows()).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("Fixture E: Different currencies require valid exchange rate evidence")
    void testFixtureE_DifferentCurrencies() throws Exception {
        UUID businessId = ownerFixture.businessId();
        UUID projectId = insertProject(businessId, "USD-PROJ", "USD Project", "PKR");
        FinancialAccount usdAcc = createAccount(businessId, "USD Bank", AccountType.BANK, "USD", 2, "10000.00");

        UUID usdTx = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO financial_transactions (
                    id, business_id, account_id, event_type, movement_direction,
                    amount, currency, base_amount_pkr, effective_date, description, created_by_user_id
                ) VALUES (?, ?, ?, 'OPERATING_EXPENSE_PAYMENT', 'DECREASE', 1000.0000, 'USD', 278500.0000, '2026-11-01', 'Foreign component USD', ?)
                """, usdTx, businessId, usdAcc.getId(), ownerFixture.user().getId());

        // Attempting to attribute without exchange rate fails
        AttributionCreateRequest noRateReq = new AttributionCreateRequest(
                AttributionSourceType.TRANSACTION,
                usdTx,
                new BigDecimal("1000.00"),
                AttributionCostCategory.MATERIAL,
                "No rate",
                LocalDate.now(),
                null
        );

        mvc.perform(post("/api/finance/projects/" + projectId + "/attributions")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(noRateReq)))
                .andExpect(status().isBadRequest());

        // Attributing with exchange rate 278.50 succeeds
        AttributionCreateRequest rateReq = new AttributionCreateRequest(
                AttributionSourceType.TRANSACTION,
                usdTx,
                new BigDecimal("1000.00"),
                AttributionCostCategory.MATERIAL,
                "Valid locked rate",
                LocalDate.now(),
                new BigDecimal("278.500000")
        );

        mvc.perform(post("/api/finance/projects/" + projectId + "/attributions")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(rateReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attributedAmount").value(1000.0))
                .andExpect(jsonPath("$.attributedCurrency").value("USD"))
                .andExpect(jsonPath("$.baseAmount").value(278500.0));
    }

    @Test
    @DisplayName("Split Allocation: Concurrent competing requests cannot exceed source limit")
    void testConcurrentCompetingAllocationsRace() throws Exception {
        UUID businessId = ownerFixture.businessId();
        UUID project1 = insertProject(businessId, "RACE-1", "Race Project 1", "PKR");
        UUID project2 = insertProject(businessId, "RACE-2", "Race Project 2", "PKR");

        FinancialAccount bankAcc = createAccount(businessId, "Race Bank", AccountType.BANK, "PKR", 2, "500000.00");
        UUID sharedTx = postPayment(businessId, bankAcc.getId(), new BigDecimal("100000.00"), "Shared pool disbursement");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Callable<Void> task1 = () -> {
            barrier.await();
            try {
                attributionService.createAttribution(
                        businessId,
                        project1,
                        new AttributionCreateRequest(AttributionSourceType.TRANSACTION, sharedTx, new BigDecimal("60000.00"), AttributionCostCategory.MATERIAL, "R1", LocalDate.now(), null),
                        ownerFixture.user().getId(),
                        "idem-race-1"
                );
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            }
            return null;
        };

        Callable<Void> task2 = () -> {
            barrier.await();
            try {
                attributionService.createAttribution(
                        businessId,
                        project2,
                        new AttributionCreateRequest(AttributionSourceType.TRANSACTION, sharedTx, new BigDecimal("60000.00"), AttributionCostCategory.MATERIAL, "R2", LocalDate.now(), null),
                        ownerFixture.user().getId(),
                        "idem-race-2"
                );
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            }
            return null;
        };

        List<Future<Void>> futures = pool.invokeAll(List.of(task1, task2));
        for (Future<Void> f : futures) {
            f.get();
        }
        pool.shutdown();

        // Exactly one should succeed, one should fail
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);

        BigDecimal activeSum = attributionRepository.sumAttributedAmountBySource(
                businessId, AttributionSourceType.TRANSACTION, sharedTx, AttributionStatus.ACTIVE
        );
        assertThat(activeSum).isEqualByComparingTo("60000.0000");
    }

    @Test
    @DisplayName("RBAC: Salesperson cannot manage or view project attributions")
    void testRbac_SalespersonDenied() throws Exception {
        UUID businessId = ownerFixture.businessId();
        UUID projectId = insertProject(businessId, "RBAC-PROJ", "RBAC Project", "PKR");

        UserBusinessFixture salesFixture = createFixture("sales-user-" + UUID.randomUUID().toString().substring(0, 8), MembershipRole.SALESPERSON, businessId);

        mvc.perform(get("/api/finance/projects/" + projectId + "/costing-summary")
                        .cookie(salesFixture.session())
                        .header("X-XSRF-TOKEN", salesFixture.csrfToken()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/finance/projects/" + projectId + "/eligible-sources")
                        .cookie(salesFixture.session())
                        .header("X-XSRF-TOKEN", salesFixture.csrfToken()))
                .andExpect(status().isForbidden());
    }

    // --- Helper Methods ---

    private UUID insertProject(UUID businessId, String code, String name, String baseCurrency) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO projects (id, business_id, code, name, kind, status, base_currency, created_by, updated_by)
                VALUES (?, ?, ?, ?, 'PROJECT', 'ACTIVE', ?, ?, ?)
                """, id, businessId, code, name, baseCurrency, ownerFixture.user().getId(), ownerFixture.user().getId());
        return id;
    }

    private record BudgetLine(String category, String desc, BigDecimal plannedCost) {}

    private void insertApprovedBudget(UUID businessId, UUID projectId, int revision, BigDecimal plannedRevenue, BigDecimal plannedCost, List<BudgetLine> lines) {
        UUID versionId = UUID.randomUUID();
        BigDecimal plannedMargin = plannedRevenue.subtract(plannedCost);
        BigDecimal plannedMarginPct = plannedMargin.divide(plannedRevenue, 6, java.math.RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

        // 1. Insert as DRAFT first so lines can be added without triggering enforce_project_planning_immutability
        jdbc.update("""
                INSERT INTO project_budget_versions (
                    id, business_id, project_id, revision, status, base_currency,
                    planned_revenue, planned_cost, planned_margin, planned_margin_pct,
                    calculation_version, created_by, updated_by
                ) VALUES (?, ?, ?, ?, 'DRAFT', 'PKR', ?, ?, ?, ?, 'f05-plan-v1', ?, ?)
                """, versionId, businessId, projectId, revision, plannedRevenue, plannedCost, plannedMargin, plannedMarginPct,
                ownerFixture.user().getId(), ownerFixture.user().getId());

        int lineNum = 1;
        for (BudgetLine l : lines) {
            jdbc.update("""
                    INSERT INTO project_budget_lines (
                        id, business_id, project_id, budget_version_id, line_number,
                        category, description, quantity, unit, unit_cost, planned_line_cost
                    ) VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, 1.000000, 'JOB', ?, ?)
                    """, businessId, projectId, versionId, lineNum++, l.category(), l.desc(), l.plannedCost(), l.plannedCost());
        }

        // 2. Transition from DRAFT to APPROVED
        jdbc.update("""
                UPDATE project_budget_versions
                SET status = 'APPROVED',
                    approved_by = ?,
                    approved_at = now()
                WHERE id = ? AND business_id = ?
                """, ownerFixture.user().getId(), versionId, businessId);
    }

    private UUID postPayment(UUID businessId, UUID accountId, BigDecimal amount, String desc) {
        BusinessAccessContext context = new BusinessAccessContext(ownerFixture.user().getId(), businessId, MembershipRole.OWNER);
        TransactionPostRequest req = new TransactionPostRequest(
                accountId,
                TransactionEventType.OPERATING_EXPENSE_PAYMENT.name(),
                amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                LocalDate.now().toString(),
                desc,
                null
        );
        TransactionDetailResponse res = postService.postTransaction(context, "post-" + UUID.randomUUID(), req);
        return res.id();
    }

    private void attributeSourceViaApi(UserBusinessFixture fixture, UUID projectId, UUID sourceId, AttributionSourceType type, BigDecimal amount, AttributionCostCategory category) throws Exception {
        AttributionCreateRequest req = new AttributionCreateRequest(
                type,
                sourceId,
                amount,
                category,
                "API attribution test",
                LocalDate.now(),
                null
        );
        mvc.perform(post("/api/finance/projects/" + projectId + "/attributions")
                        .cookie(fixture.session())
                        .with(realCsrf(fixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    private FinancialAccount createAccount(
            UUID businessId,
            String name,
            AccountType type,
            String currency,
            int decimals,
            String openingBalance
    ) {
        FinancialAccount acc = new FinancialAccount(
                businessId,
                name,
                type,
                currency,
                decimals,
                new BigDecimal(openingBalance),
                LocalDate.of(2026, 1, 1),
                AccountStatus.ACTIVE,
                "Test Bank",
                "ACC-" + UUID.randomUUID(),
                "Testing notes"
        );
        return accounts.saveAndFlush(acc);
    }

    private UserBusinessFixture createFixture(String emailPrefix, MembershipRole role) throws Exception {
        return createFixture(emailPrefix, role, null);
    }

    private UserBusinessFixture createFixture(String emailPrefix, MembershipRole role, UUID existingBizId) throws Exception {
        AppUser user = new AppUser();
        user.setEmail(emailPrefix + "@test.finsight.local");
        user.setFullName("C05 Test User");
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setAccountStatus(com.app.sme_health_backend.identity.model.AccountStatus.ACTIVE);
        user.setMustChangePassword(false);
        user = users.saveAndFlush(user);

        UUID businessId = existingBizId != null ? existingBizId : UUID.randomUUID();
        if (existingBizId == null) {
            businesses.saveAndFlush(new Business(businessId));
        }
        memberships.saveAndFlush(new BusinessMembership(user.getId(), businessId, role, MembershipStatus.ACTIVE));

        Cookie session = loginAndSelect(user, businessId);
        String xsrfToken = getXsrfToken(session);
        return new UserBusinessFixture(user, businessId, session, xsrfToken);
    }

    private Cookie loginAndSelect(AppUser user, UUID businessId) throws Exception {
        var csrfResponse = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        Cookie initialSession = csrfResponse.getCookie("FINSIGHT_SESSION");
        Cookie xsrf = csrfResponse.getCookie("XSRF-TOKEN");
        assertNotNull(xsrf);

        var login = post("/api/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + DEFAULT_PASSWORD + "\"}")
                .cookie(xsrf)
                .header("X-XSRF-TOKEN", xsrf.getValue());
        if (initialSession != null) {
            login.cookie(initialSession);
        }

        Cookie session = mvc.perform(login).andExpect(status().isOk()).andReturn().getResponse().getCookie("FINSIGHT_SESSION");
        assertNotNull(session, "Login must establish a session cookie");

        mvc.perform(post("/api/businesses/active")
                        .cookie(session)
                        .with(realCsrf(session))
                        .contentType("application/json")
                        .content("{\"businessId\":\"" + businessId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(businessId.toString()));

        return session;
    }

    private RequestPostProcessor realCsrf(Cookie session) throws Exception {
        var response = mvc.perform(get("/api/auth/csrf").cookie(session)).andExpect(status().isOk()).andReturn().getResponse();
        Cookie xsrf = response.getCookie("XSRF-TOKEN");
        assertNotNull(xsrf);
        CsrfResponse token = objectMapper.readValue(response.getContentAsString(), CsrfResponse.class);
        return req -> {
            var cookies = new ArrayList<Cookie>(Arrays.asList(req.getCookies() == null ? new Cookie[0] : req.getCookies()));
            cookies.removeIf(c -> c.getName().equals(xsrf.getName()));
            cookies.add(xsrf);
            req.setCookies(cookies.toArray(Cookie[]::new));
            req.addHeader(token.headerName(), token.token());
            return req;
        };
    }

    private String getXsrfToken(Cookie session) throws Exception {
        var res = mvc.perform(get("/api/auth/csrf").cookie(session)).andExpect(status().isOk()).andReturn().getResponse();
        Cookie xsrf = res.getCookie("XSRF-TOKEN");
        return xsrf != null ? xsrf.getValue() : "";
    }

    private record UserBusinessFixture(AppUser user, UUID businessId, Cookie session, String csrfToken) {}
}
