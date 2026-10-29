package com.app.sme_health_backend.risk;

import com.app.sme_health_backend.accounting.draft.DraftStatus;
import com.app.sme_health_backend.accounting.draft.RecordDraft;
import com.app.sme_health_backend.accounting.draft.RecordDraftRepository;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.AccountMovementRepository;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialTransactionRepository;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.counterparty.CounterpartyType;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.dto.CreateBusinessRequest;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.entity.BusinessMembership;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.model.MembershipStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.repository.BusinessMembershipRepository;
import com.app.sme_health_backend.identity.service.BusinessService;
import com.app.sme_health_backend.obligation.FinancialObligation;
import com.app.sme_health_backend.obligation.FinancialObligationRepository;
import com.app.sme_health_backend.obligation.ObligationSourceType;
import com.app.sme_health_backend.obligation.ObligationStatus;
import com.app.sme_health_backend.obligation.aging.ObligationDirection;
import com.app.sme_health_backend.recurring.entity.RecurringRule;
import com.app.sme_health_backend.recurring.model.RecurringFrequency;
import com.app.sme_health_backend.recurring.model.RecurringRuleStatus;
import com.app.sme_health_backend.recurring.repository.RecurringRuleOccurrenceRepository;
import com.app.sme_health_backend.recurring.repository.RecurringRuleRepository;
import com.app.sme_health_backend.risk.dto.FinancialRiskInsight;
import com.app.sme_health_backend.risk.dto.RiskAssessmentResponse;
import com.app.sme_health_backend.risk.model.RiskCategory;
import com.app.sme_health_backend.risk.model.RiskSeverity;
import com.app.sme_health_backend.risk.service.FinancialRiskAssessmentService;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("FinancialRiskPostgreSqlIT — Real PostgreSQL 14.22 Integration Tests (A-W6-FINANCIAL-RISK-INTELLIGENCE-V1)")
class FinancialRiskPostgreSqlIT extends DisposablePostgres {

    private static final String RAW_PASSWORD = "secure-password-2026!";

    @Autowired private MockMvc mockMvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private FinancialRiskAssessmentService riskService;
    @Autowired private FinancialAccountRepository accountRepository;
    @Autowired private AccountMovementRepository movementRepository;
    @Autowired private FinancialTransactionRepository transactionRepository;
    @Autowired private FinancialObligationRepository obligationRepository;
    @Autowired private CounterpartyRepository counterpartyRepository;
    @Autowired private RecurringRuleRepository recurringRuleRepository;
    @Autowired private RecurringRuleOccurrenceRepository recurringOccurrenceRepository;
    @Autowired private RecordDraftRepository recordDraftRepository;
    @Autowired private BusinessService businessService;
    @Autowired private AppUserRepository userRepository;
    @Autowired private BusinessMembershipRepository membershipRepository;
    @Autowired private DataSource dataSource;

    private UUID businessIdA;
    private UUID userIdA;
    private AppUser ownerUserA;
    private BusinessAccessContext contextA;
    private final LocalDate evalDate = LocalDate.of(2026, 10, 15);

    @BeforeEach
    void setUp() {
        ownerUserA = createActiveUser("ownera");
        userIdA = ownerUserA.getId();

        businessIdA = businessService.createBusiness(
                new CreateBusinessRequest("Risk Test Business A", "retail", "en", null, false, null, null, null),
                userIdA
        ).businessId();

        contextA = new BusinessAccessContext(userIdA, businessIdA, MembershipRole.OWNER);
    }

    @Test
    @DisplayName("Fixture A: Forecast liquidity shortage detected on real PostgreSQL with CRITICAL severity")
    void testFixtureA_ForecastLiquidityDeficitPostgreSQL() {
        accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "HBL Main", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), evalDate.minusDays(5))
        );
        Counterparty supplier = counterpartyRepository.saveAndFlush(
                new Counterparty(businessIdA, "Major Supplier", CounterpartyType.SUPPLIER, null, null, null, null, null)
        );
        Counterparty customer = counterpartyRepository.saveAndFlush(
                new Counterparty(businessIdA, "Customer Co", CounterpartyType.CUSTOMER, null, null, null, null, null)
        );

        obligationRepository.saveAndFlush(
                new FinancialObligation(businessIdA, supplier.getId(), ObligationDirection.PAYABLE, ObligationSourceType.SUPPLIER_BILL,
                        "SRC-BILL-1", "BILL-101", evalDate, evalDate.plusDays(10), new BigDecimal("150000.00"), "PKR", "Inventory purchase", userIdA)
        );
        obligationRepository.saveAndFlush(
                new FinancialObligation(businessIdA, customer.getId(), ObligationDirection.RECEIVABLE, ObligationSourceType.CUSTOMER_INVOICE,
                        "SRC-INV-1", "INV-101", evalDate, evalDate.plusDays(5), new BigDecimal("20000.00"), "PKR", "Customer sale", userIdA)
        );

        RiskAssessmentResponse response = riskService.getAssessment(contextA, evalDate.toString(), 30, "PKR", null, null);

        assertNotNull(response);
        assertEquals(RiskSeverity.CRITICAL, response.summary().highestSeverity());
        Optional<FinancialRiskInsight> r01 = response.insights().stream()
                .filter(i -> "R01_PROJECTED_CASH_DEFICIT".equals(i.riskCode()))
                .findFirst();
        assertTrue(r01.isPresent(), "R01 deficit insight must be present on real PostgreSQL");
        assertEquals(new BigDecimal("30000.00"), r01.get().affectedAmount());
        assertEquals(evalDate.plusDays(10), r01.get().startDate());
    }

    @Test
    @DisplayName("Fixture B: Overdue customer receivables accurately aged on real PostgreSQL")
    void testFixtureB_OverdueReceivablesPostgreSQL() {
        Counterparty customer = counterpartyRepository.saveAndFlush(
                new Counterparty(businessIdA, "Overdue Client", CounterpartyType.CUSTOMER, null, null, null, null, null)
        );
        obligationRepository.saveAndFlush(
                new FinancialObligation(businessIdA, customer.getId(), ObligationDirection.RECEIVABLE, ObligationSourceType.CUSTOMER_INVOICE,
                        "SRC-INV-OD", "INV-OVERDUE", evalDate.minusDays(45), evalDate.minusDays(35), new BigDecimal("60000.00"), "PKR", "Overdue invoice", userIdA)
        );
        obligationRepository.saveAndFlush(
                new FinancialObligation(businessIdA, customer.getId(), ObligationDirection.RECEIVABLE, ObligationSourceType.CUSTOMER_INVOICE,
                        "SRC-INV-CURR", "INV-CURRENT", evalDate.minusDays(5), evalDate.plusDays(25), new BigDecimal("40000.00"), "PKR", "Current invoice", userIdA)
        );

        RiskAssessmentResponse response = riskService.getAssessment(contextA, evalDate.toString(), 30, "PKR", null, null);

        Optional<FinancialRiskInsight> r03 = response.insights().stream()
                .filter(i -> "R03_OVERDUE_RECEIVABLES".equals(i.riskCode()))
                .findFirst();
        assertTrue(r03.isPresent(), "R03 overdue receivables insight must be generated");
        assertEquals(new BigDecimal("60000.00"), r03.get().affectedAmount());
        assertEquals(new BigDecimal("60.00"), r03.get().affectedPercentage());
    }

    @Test
    @DisplayName("Fixture C: Customer concentration identifies 75% debtor on real PostgreSQL")
    void testFixtureC_CustomerConcentrationPostgreSQL() {
        Counterparty alpha = counterpartyRepository.saveAndFlush(
                new Counterparty(businessIdA, "Alpha Heavy Corp", CounterpartyType.CUSTOMER, null, null, null, null, null)
        );
        Counterparty beta = counterpartyRepository.saveAndFlush(
                new Counterparty(businessIdA, "Beta Minor Corp", CounterpartyType.CUSTOMER, null, null, null, null, null)
        );

        obligationRepository.saveAndFlush(
                new FinancialObligation(businessIdA, alpha.getId(), ObligationDirection.RECEIVABLE, ObligationSourceType.CUSTOMER_INVOICE,
                        "SRC-A", "INV-A", evalDate.minusDays(5), evalDate.plusDays(15), new BigDecimal("75000.00"), "PKR", "Alpha billing", userIdA)
        );
        obligationRepository.saveAndFlush(
                new FinancialObligation(businessIdA, beta.getId(), ObligationDirection.RECEIVABLE, ObligationSourceType.CUSTOMER_INVOICE,
                        "SRC-B", "INV-B", evalDate.minusDays(5), evalDate.plusDays(15), new BigDecimal("25000.00"), "PKR", "Beta billing", userIdA)
        );

        RiskAssessmentResponse response = riskService.getAssessment(contextA, evalDate.toString(), 30, "PKR", null, null);

        Optional<FinancialRiskInsight> r04 = response.insights().stream()
                .filter(i -> "R04_CUSTOMER_CONCENTRATION".equals(i.riskCode()))
                .findFirst();
        assertTrue(r04.isPresent(), "R04 customer concentration insight must be generated");
        assertEquals(new BigDecimal("75.00"), r04.get().affectedPercentage());
        assertEquals(RiskSeverity.CRITICAL, r04.get().severity());
        assertTrue(r04.get().title().contains("Alpha Heavy Corp"));
    }

    @Test
    @DisplayName("Fixture D: Payment pressure in 7 days produces CRITICAL gap of 45,000 PKR")
    void testFixtureD_PaymentPressurePostgreSQL() {
        accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "Cash Till", AccountType.CASH, "PKR", 2, new BigDecimal("30000.00"), evalDate.minusDays(5))
        );
        Counterparty supp = counterpartyRepository.saveAndFlush(
                new Counterparty(businessIdA, "Key Supplier", CounterpartyType.SUPPLIER, null, null, null, null, null)
        );
        obligationRepository.saveAndFlush(
                new FinancialObligation(businessIdA, supp.getId(), ObligationDirection.PAYABLE, ObligationSourceType.SUPPLIER_BILL,
                        "SRC-BILL-D", "BILL-75K", evalDate.minusDays(2), evalDate.plusDays(4), new BigDecimal("75000.00"), "PKR", "Urgent supplier bill", userIdA)
        );

        RiskAssessmentResponse response = riskService.getAssessment(contextA, evalDate.toString(), 30, "PKR", null, null);

        Optional<FinancialRiskInsight> r02 = response.insights().stream()
                .filter(i -> "R02_UPCOMING_PAYMENT_PRESSURE".equals(i.riskCode()))
                .findFirst();
        assertTrue(r02.isPresent(), "R02 payment pressure insight must be generated");
        assertEquals(RiskSeverity.CRITICAL, r02.get().severity());
        assertEquals(new BigDecimal("45000.00"), r02.get().affectedAmount());
    }

    @Test
    @DisplayName("Fixture E: Recurring expense commitments include active rule and exclude paused rule")
    void testFixtureE_RecurringCommitmentsActiveVsPausedPostgreSQL() {
        FinancialAccount acc = accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "Main Bank", AccountType.BANK, "PKR", 2, new BigDecimal("15000.00"), evalDate.minusDays(5))
        );

        RecurringRule activeRent = new RecurringRule(
                businessIdA, "Warehouse Lease", "Monthly lease", TransactionEventType.OPERATING_EXPENSE_PAYMENT,
                acc.getId(), null, new BigDecimal("20000.00"), RecurringFrequency.MONTHLY,
                18, 10, evalDate.minusMonths(1), null, evalDate.plusDays(3), userIdA
        );
        activeRent.setStatus(RecurringRuleStatus.ACTIVE);
        recurringRuleRepository.saveAndFlush(activeRent);

        RecurringRule pausedSub = new RecurringRule(
                businessIdA, "ERP Cloud License", "SaaS subscription", TransactionEventType.OPERATING_EXPENSE_PAYMENT,
                acc.getId(), null, new BigDecimal("5000.00"), RecurringFrequency.MONTHLY,
                20, 10, evalDate.minusMonths(1), null, evalDate.plusDays(5), userIdA
        );
        pausedSub.setStatus(RecurringRuleStatus.PAUSED);
        recurringRuleRepository.saveAndFlush(pausedSub);

        RiskAssessmentResponse response = riskService.getAssessment(contextA, evalDate.toString(), 30, "PKR", null, null);

        Optional<FinancialRiskInsight> r05 = response.insights().stream()
                .filter(i -> "R05_RECURRING_CASH_PRESSURE".equals(i.riskCode()))
                .findFirst();
        assertTrue(r05.isPresent(), "R05 recurring pressure insight must be generated");
        assertEquals(new BigDecimal("20000.00"), r05.get().affectedAmount());
    }

    @Test
    @DisplayName("Fixture F: Currency isolation preserves independent risk evaluation for PKR vs USD")
    void testFixtureF_CurrencySeparationPostgreSQL() {
        accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "PKR Checking", AccountType.BANK, "PKR", 2, new BigDecimal("50000.00"), evalDate.minusDays(5))
        );
        accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "USD Overdrawn", AccountType.BANK, "USD", 2, new BigDecimal("-500.00"), evalDate.minusDays(5))
        );

        // PKR assessment must not report negative cash
        RiskAssessmentResponse pkrRes = riskService.getAssessment(contextA, evalDate.toString(), 30, "PKR", null, null);
        assertFalse(pkrRes.insights().stream().anyMatch(i -> "R06_NEGATIVE_ACTUAL_CASH".equals(i.riskCode())));

        // USD assessment must report negative cash on USD account
        RiskAssessmentResponse usdRes = riskService.getAssessment(contextA, evalDate.toString(), 30, "USD", null, null);
        Optional<FinancialRiskInsight> usdR06 = usdRes.insights().stream()
                .filter(i -> "R06_NEGATIVE_ACTUAL_CASH".equals(i.riskCode()))
                .findFirst();
        assertTrue(usdR06.isPresent(), "USD evaluation must report negative cash on the USD account");
        assertEquals(new BigDecimal("500.00"), usdR06.get().affectedAmount());
    }

    @Test
    @DisplayName("Fixture G: Incomplete data generates explicit DATA_LIMITED warning, not false LOW RISK")
    void testFixtureG_IncompleteDataPostgreSQL() {
        Counterparty cp = counterpartyRepository.saveAndFlush(
                new Counterparty(businessIdA, "Pending Supplier", CounterpartyType.SUPPLIER, null, null, null, null, null)
        );
        FinancialObligation unscheduled = new FinancialObligation();
        unscheduled.setBusinessId(businessIdA);
        unscheduled.setCounterpartyId(cp.getId());
        unscheduled.setDirection(ObligationDirection.PAYABLE);
        unscheduled.setCurrency("PKR");
        unscheduled.setOriginalAmount(new BigDecimal("12000.00"));
        unscheduled.setStatus(ObligationStatus.OPEN);
        unscheduled.setIssueDate(evalDate);
        unscheduled.setDueDate(null);
        unscheduled.setCreatedByUserId(userIdA);
        obligationRepository.saveAndFlush(unscheduled);

        RecordDraft draft = new RecordDraft();
        draft.setBusinessId(businessIdA);
        draft.setCreatedById(userIdA);
        draft.setUpdatedById(userIdA);
        draft.setStatus(DraftStatus.DRAFT);
        draft.setAmountInput("7500.00");
        draft.setDescription("Pending receipt review");
        recordDraftRepository.saveAndFlush(draft);

        RiskAssessmentResponse response = riskService.getAssessment(contextA, evalDate.toString(), 30, "PKR", null, null);

        assertTrue(response.summary().hasDataCompletenessIssues(), "Completeness flag must be true");
        Optional<FinancialRiskInsight> r07 = response.insights().stream()
                .filter(i -> "R07_DATA_COMPLETENESS".equals(i.riskCode()))
                .findFirst();
        assertTrue(r07.isPresent(), "R07 data completeness insight must exist");
        assertEquals(RiskSeverity.HIGH, r07.get().severity());
    }

    @Test
    @DisplayName("Fixture H: Bit-for-bit SHA-256 snapshots on 12 tables prove zero financial database mutations")
    void testFixtureH_ReadOnlyDatabaseInvariantsPostgreSQL() throws Exception {
        accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "Main Bank", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), evalDate.minusDays(5))
        );
        Counterparty cp = counterpartyRepository.saveAndFlush(
                new Counterparty(businessIdA, "General Client", CounterpartyType.CUSTOMER, null, null, null, null, null)
        );
        obligationRepository.saveAndFlush(
                new FinancialObligation(businessIdA, cp.getId(), ObligationDirection.RECEIVABLE, ObligationSourceType.CUSTOMER_INVOICE,
                        "SRC-INV-H", "INV-H", evalDate.minusDays(2), evalDate.plusDays(8), new BigDecimal("50000.00"), "PKR", "Sales invoice", userIdA)
        );

        LoginSession session = loginAndSelectBusiness(ownerUserA, businessIdA);

        List<String> auditedTables = List.of(
                "financial_accounts", "financial_transactions", "account_movements", "record_drafts",
                "financial_obligations", "obligation_payment_allocations", "obligation_payment_schedules",
                "counterparty_advances", "counterparties", "recurring_rules",
                "recurring_rule_occurrences", "monthly_records"
        );

        Map<String, TableSnapshot> beforeSnapshots = captureTableSnapshots(auditedTables);

        // Execute service evaluations across multiple horizon days and categories
        riskService.getAssessment(contextA, evalDate.toString(), 30, "PKR", null, null);
        riskService.getAssessment(contextA, evalDate.toString(), 90, "PKR", "LIQUIDITY_FORECAST", null);
        riskService.getInsights(contextA, evalDate.toString(), 30, "PKR", null, "CRITICAL");
        riskService.getSummary(contextA, evalDate.toString(), 30, "PKR");

        // Execute HTTP GET endpoints via MockMvc
        mockMvc.perform(withSession(get("/api/finance/risk/assessment")
                        .param("asOfDate", evalDate.toString())
                        .param("horizonDays", "30")
                        .param("currency", "PKR"), session))
                .andExpect(status().isOk());

        mockMvc.perform(withSession(get("/api/finance/risk/insights")
                        .param("asOfDate", evalDate.toString()), session))
                .andExpect(status().isOk());

        mockMvc.perform(withSession(get("/api/finance/risk/summary")
                        .param("asOfDate", evalDate.toString()), session))
                .andExpect(status().isOk());

        Map<String, TableSnapshot> afterSnapshots = captureTableSnapshots(auditedTables);

        for (String table : auditedTables) {
            TableSnapshot before = beforeSnapshots.get(table);
            TableSnapshot after = afterSnapshots.get(table);
            assertNotNull(before, "Missing before snapshot for " + table);
            assertNotNull(after, "Missing after snapshot for " + table);
            assertEquals(before.rowCount(), after.rowCount(), "Row count changed on read-only risk query for " + table);
            assertEquals(before.sha256Digest(), after.sha256Digest(), "SHA-256 hash changed on read-only risk query for " + table);
        }
    }

    @Test
    @DisplayName("Fixture I: Full Spring Security HTTP session authentication, RBAC, and cross-tenant isolation")
    void testFixtureI_SecurityAndTenantIsolationPostgreSQL() throws Exception {
        accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "HBL Main", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), evalDate.minusDays(5))
        );

        LoginSession ownerSession = loginAndSelectBusiness(ownerUserA, businessIdA);

        // 1. Authenticated OWNER -> 200 OK
        mockMvc.perform(withSession(get("/api/finance/risk/assessment"), ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("PKR"))
                .andExpect(jsonPath("$.summary.totalCategories").value(8));

        // 2. Unauthenticated request -> 401 Unauthorized
        mockMvc.perform(get("/api/finance/risk/assessment"))
                .andExpect(status().isUnauthorized());

        // 3. Member with VIEWER role -> 200 OK (read allowed)
        AppUser viewerUser = createActiveUser("riskviewer");
        membershipRepository.saveAndFlush(new BusinessMembership(viewerUser.getId(), businessIdA, MembershipRole.VIEWER, MembershipStatus.ACTIVE));
        LoginSession viewerSession = loginAndSelectBusiness(viewerUser, businessIdA);

        mockMvc.perform(withSession(get("/api/finance/risk/assessment"), viewerSession))
                .andExpect(status().isOk());

        // 4. Cross-tenant isolation: User B in Business B cannot see Business A's risks
        AppUser stranger = createActiveUser("riskstranger");
        UUID businessIdB = businessService.createBusiness(
                new CreateBusinessRequest("Other Business B", "retail", "en", null, false, null, null, null),
                stranger.getId()
        ).businessId();
        LoginSession strangerSession = loginAndSelectBusiness(stranger, businessIdB);

        MvcResult result = mockMvc.perform(withSession(get("/api/finance/risk/assessment"), strangerSession))
                .andExpect(status().isOk())
                .andReturn();

        // Business B has no records from Business A
        assertTrue(result.getResponse().getContentAsString().contains("\"businessId\":\"" + businessIdB + "\""));
        assertFalse(result.getResponse().getContentAsString().contains("Risk Test Business A"));
    }

    private AppUser createActiveUser(String prefix) {
        AppUser user = new AppUser();
        user.setEmail((prefix + "-" + UUID.randomUUID().toString().substring(0, 6) + "@example.com").toLowerCase(Locale.ROOT));
        user.setPasswordHash(passwordEncoder.encode(RAW_PASSWORD));
        user.setFullName("Test " + prefix);
        user.setAccountStatus(com.app.sme_health_backend.identity.model.AccountStatus.ACTIVE);
        user.setMustChangePassword(false);
        return userRepository.saveAndFlush(user);
    }

    private record LoginSession(Cookie sessionCookie, Cookie xsrfCookie) {}

    private LoginSession loginOnly(AppUser user) throws Exception {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie preLoginCookie = csrfResult.getResponse().getCookie("FINSIGHT_SESSION");
        Cookie xsrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

        MockHttpServletRequestBuilder loginReq = post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + RAW_PASSWORD + "\"}");

        if (preLoginCookie != null) {
            loginReq.cookie(preLoginCookie);
        }
        if (xsrfCookie != null) {
            loginReq.cookie(xsrfCookie).header("X-XSRF-TOKEN", xsrfCookie.getValue());
        } else {
            loginReq.with(csrf());
        }

        MvcResult loginResult = mockMvc.perform(loginReq)
                .andExpect(status().isOk())
                .andReturn();

        Cookie postLoginCookie = loginResult.getResponse().getCookie("FINSIGHT_SESSION");
        assertNotNull(postLoginCookie, "FINSIGHT_SESSION cookie must be issued upon login");
        return new LoginSession(postLoginCookie, xsrfCookie);
    }

    private LoginSession loginAndSelectBusiness(AppUser user, UUID businessId) throws Exception {
        LoginSession session = loginOnly(user);
        mockMvc.perform(withSession(post("/api/businesses/active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessId\":\"" + businessId + "\"}"), session))
                .andExpect(status().isOk());
        return session;
    }

    private MockHttpServletRequestBuilder withSession(MockHttpServletRequestBuilder builder, LoginSession session) {
        builder.cookie(session.sessionCookie());
        if (session.xsrfCookie() != null) {
            builder.cookie(session.xsrfCookie()).header("X-XSRF-TOKEN", session.xsrfCookie().getValue());
        }
        builder.with(csrf());
        return builder;
    }

    private record TableSnapshot(long rowCount, String sha256Digest) {}

    private Map<String, TableSnapshot> captureTableSnapshots(List<String> tables) throws Exception {
        Map<String, TableSnapshot> map = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String table : tables) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM " + table + " ORDER BY 1 ASC")) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int cols = meta.getColumnCount();
                    long rowCount = 0;
                    md.reset();
                    while (rs.next()) {
                        rowCount++;
                        for (int i = 1; i <= cols; i++) {
                            String val = rs.getString(i);
                            if (val != null) {
                                md.update(val.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            }
                            md.update((byte) 0x1F);
                        }
                    }
                    byte[] digest = md.digest();
                    StringBuilder sb = new StringBuilder();
                    for (byte b : digest) {
                        sb.append(String.format("%02x", b));
                    }
                    map.put(table, new TableSnapshot(rowCount, sb.toString()));
                }
            }
        }
        return map;
    }
}
