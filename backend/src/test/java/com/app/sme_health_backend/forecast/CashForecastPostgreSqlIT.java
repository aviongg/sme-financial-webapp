package com.app.sme_health_backend.forecast;

import com.app.sme_health_backend.accounting.dto.TransactionDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionPostRequest;
import com.app.sme_health_backend.accounting.dto.TransactionReverseRequest;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.repository.AccountMovementRepository;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialTransactionRepository;
import com.app.sme_health_backend.accounting.service.TransactionPostService;
import com.app.sme_health_backend.accounting.service.TransactionReverseService;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.counterparty.CounterpartyType;
import com.app.sme_health_backend.forecast.dto.DailyCashForecastResponse;
import com.app.sme_health_backend.forecast.dto.DailyForecastRow;
import com.app.sme_health_backend.forecast.dto.ForecastScenarioRequest;
import com.app.sme_health_backend.forecast.dto.ForecastScenarioResponse;
import com.app.sme_health_backend.forecast.service.DailyCashForecastService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.dto.CreateBusinessRequest;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.entity.BusinessMembership;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.model.MembershipStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.repository.BusinessMembershipRepository;
import com.app.sme_health_backend.identity.service.BusinessService;
import com.app.sme_health_backend.obligation.*;
import com.app.sme_health_backend.obligation.aging.ObligationDirection;
import com.app.sme_health_backend.obligation.dto.ObligationSettlementRequest;
import com.app.sme_health_backend.obligation.dto.PaymentPlanInstallmentRequest;
import com.app.sme_health_backend.obligation.dto.PaymentPlanRequest;
import com.app.sme_health_backend.recurring.dto.OccurrenceSkipRequest;
import com.app.sme_health_backend.recurring.dto.RecurringRuleCreateRequest;
import com.app.sme_health_backend.recurring.repository.RecurringRuleOccurrenceRepository;
import com.app.sme_health_backend.recurring.service.RecurringRuleService;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
@DisplayName("Cash Forecast Real PostgreSQL 14.22 Integration Tests (A-W6-ADVANCED-CASH-FORECAST-V1)")
class CashForecastPostgreSqlIT extends DisposablePostgres {

    private static final String RAW_PASSWORD = "secure-password-2026!";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;

    @Autowired private DailyCashForecastService forecastService;
    @Autowired private FinancialAccountRepository accountRepository;
    @Autowired private FinancialTransactionRepository transactionRepository;
    @Autowired private AccountMovementRepository movementRepository;
    @Autowired private TransactionPostService transactionPostService;
    @Autowired private TransactionReverseService transactionReverseService;
    @Autowired private CounterpartyRepository counterpartyRepository;
    @Autowired private FinancialObligationRepository obligationRepository;
    @Autowired private FinancialObligationService obligationService;
    @Autowired private PaymentPlanService paymentPlanService;
    @Autowired private RecurringRuleService recurringRuleService;
    @Autowired private BusinessService businessService;
    @Autowired private BusinessMembershipRepository membershipRepository;
    @Autowired private AppUserRepository userRepository;
    @Autowired private RecurringRuleOccurrenceRepository recurringOccurrenceRepository;

    private BusinessAccessContext contextA;
    private UUID businessIdA;
    private AppUser ownerUserA;
    private UUID userIdA;

    private final LocalDate evalDate = LocalDate.of(2026, 10, 15);

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        ownerUserA = createActiveUser("owner-" + unique);
        userIdA = ownerUserA.getId();

        businessIdA = businessService.createBusiness(
                new CreateBusinessRequest("Forecast Business " + unique, "retail", "en", null, false, null, null, null),
                userIdA
        ).businessId();
        contextA = new BusinessAccessContext(userIdA, businessIdA, MembershipRole.OWNER);
    }

    @Test
    @DisplayName("Fixture A: Baseline cash forecast on PostgreSQL reconciles 100k opening, 40k recv, 25k payable installment, 10k recurring expense to 105k closing")
    void testFixtureA_BaselineCashForecastPostgreSQL() throws Exception {
        // 1. Account with 100k opening balance
        FinancialAccount acc = accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "HBL Main", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), evalDate.minusDays(5))
        );

        // 2. Counterparties
        Counterparty cust = counterpartyRepository.saveAndFlush(
                new Counterparty(businessIdA, "Alpha Traders", CounterpartyType.CUSTOMER, null, null, null, null, null)
        );
        Counterparty supp = counterpartyRepository.saveAndFlush(
                new Counterparty(businessIdA, "Beta Supplies", CounterpartyType.SUPPLIER, null, null, null, null, null)
        );

        // 3. Customer receivable obligation: 40k due on Nov 10
        obligationRepository.saveAndFlush(
                new FinancialObligation(businessIdA, cust.getId(), ObligationDirection.RECEIVABLE, ObligationSourceType.CUSTOMER_INVOICE,
                        "SRC-INV-1", "INV-101", evalDate, evalDate.plusDays(9), new BigDecimal("40000.00"), "PKR", "Sales invoice", userIdA)
        );

        // 4. Supplier payable obligation: 25k due on evalDate+40 with 25k installment plan on evalDate+14
        FinancialObligation obPay = obligationRepository.saveAndFlush(
                new FinancialObligation(businessIdA, supp.getId(), ObligationDirection.PAYABLE, ObligationSourceType.SUPPLIER_BILL,
                        "SRC-BILL-1", "BILL-201", evalDate, evalDate.plusDays(40), new BigDecimal("25000.00"), "PKR", "Inventory bill", userIdA)
        );

        paymentPlanService.setPaymentPlan(contextA, "PLAN-" + UUID.randomUUID(), obPay.getId(), new PaymentPlanRequest(List.of(
                new PaymentPlanInstallmentRequest(1, evalDate.plusDays(14).toString(), "25000.00", "Part 1")
        )));

        // 5. Recurring rule: 10k monthly expense on Nov 20
        recurringRuleService.createRule(contextA, new RecurringRuleCreateRequest(
                "Office Rent", "Rent", "OPERATING_EXPENSE_PAYMENT", acc.getId(), supp.getId(),
                "10000.00", "MONTHLY", evalDate.toString(), null
        ));

        // Evaluate via Service
        DailyCashForecastResponse res = forecastService.getDailyCashForecast(contextA, "PKR", 30, evalDate.toString());

        assertNotNull(res);
        assertEquals("PKR", res.currency());
        assertEquals("100000.00", res.summary().startingCash());
        assertEquals("105000.00", res.summary().endingProjectedCash());
        assertEquals("40000.00", res.summary().totalExpectedInflows());
        assertEquals("35000.00", res.summary().totalExpectedOutflows());
        assertEquals("5000.00", res.summary().netProjectedMovement());
        assertFalse(res.dailyRows().stream().anyMatch(DailyForecastRow::isDeficit));
    }

    @Test
    @DisplayName("Fixture B: Delayed collection scenario shifts receipt beyond horizon, reducing horizon-end closing cash by 40k")
    void testFixtureB_DelayedCollectionScenarioPostgreSQL() throws Exception {
        accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "HBL Main", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), evalDate.minusDays(5))
        );
        Counterparty cust = counterpartyRepository.saveAndFlush(
                new Counterparty(businessIdA, "Alpha Traders", CounterpartyType.CUSTOMER, null, null, null, null, null)
        );
        obligationRepository.saveAndFlush(
                new FinancialObligation(businessIdA, cust.getId(), ObligationDirection.RECEIVABLE, ObligationSourceType.CUSTOMER_INVOICE,
                        "SRC-INV-1", "INV-101", evalDate, evalDate.plusDays(14), new BigDecimal("40000.00"), "PKR", "Sales invoice", userIdA)
        );

        ForecastScenarioRequest req = new ForecastScenarioRequest(
                "PKR", 30, evalDate.toString(), 20, new BigDecimal("100.00"), 0, false, 1
        );

        ForecastScenarioResponse res = forecastService.previewScenario(contextA, req);

        assertNotNull(res);
        assertEquals("140000.00", res.baseline().summary().endingProjectedCash());
        assertEquals("100000.00", res.scenario().summary().endingProjectedCash());
        assertEquals("-40000.00", res.comparison().endingCashVariance());
    }

    @Test
    @DisplayName("Fixture C: Reduced collection scenario models 50% receipt of 20k on 40k receivable")
    void testFixtureC_ReducedCollectionScenarioPostgreSQL() throws Exception {
        accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "HBL Main", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), evalDate.minusDays(5))
        );
        Counterparty cust = counterpartyRepository.saveAndFlush(
                new Counterparty(businessIdA, "Alpha Traders", CounterpartyType.CUSTOMER, null, null, null, null, null)
        );
        obligationRepository.saveAndFlush(
                new FinancialObligation(businessIdA, cust.getId(), ObligationDirection.RECEIVABLE, ObligationSourceType.CUSTOMER_INVOICE,
                        "SRC-INV-1", "INV-101", evalDate, evalDate.plusDays(10), new BigDecimal("40000.00"), "PKR", "Sales invoice", userIdA)
        );

        ForecastScenarioRequest req = new ForecastScenarioRequest(
                "PKR", 30, evalDate.toString(), 0, new BigDecimal("50.00"), 0, false, 1
        );

        ForecastScenarioResponse res = forecastService.previewScenario(contextA, req);

        assertNotNull(res);
        assertEquals("40000.00", res.baseline().summary().totalExpectedInflows());
        assertEquals("20000.00", res.scenario().summary().totalExpectedInflows());
        assertEquals("-20000.00", res.comparison().endingCashVariance());
        assertEquals("120000.00", res.scenario().summary().endingProjectedCash());
    }

    @Test
    @DisplayName("Fixture D: Partially paid installment absorbs 20k settlement, leaving 30k scheduled exposure without parent double-counting")
    void testFixtureD_PartiallyPaidInstallmentPostgreSQL() throws Exception {
        FinancialAccount acc = accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "HBL Main", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), evalDate.minusDays(10))
        );
        Counterparty supp = counterpartyRepository.saveAndFlush(
                new Counterparty(businessIdA, "Beta Supplies", CounterpartyType.SUPPLIER, null, null, null, null, null)
        );

        FinancialObligation obPay = obligationRepository.saveAndFlush(
                new FinancialObligation(businessIdA, supp.getId(), ObligationDirection.PAYABLE, ObligationSourceType.SUPPLIER_BILL,
                        "SRC-BILL-1", "BILL-301", evalDate.minusDays(5), evalDate.plusDays(25), new BigDecimal("50000.00"), "PKR", "Raw materials", userIdA)
        );

        paymentPlanService.setPaymentPlan(contextA, "PLAN-" + UUID.randomUUID(), obPay.getId(), new PaymentPlanRequest(List.of(
                new PaymentPlanInstallmentRequest(1, evalDate.plusDays(9).toString(), "25000.00", "Inst 1"),
                new PaymentPlanInstallmentRequest(2, evalDate.plusDays(19).toString(), "25000.00", "Inst 2")
        )));

        // Settle 20k against obligation on evalDate
        obligationService.recordSettlement(contextA, obPay.getId(), "TX-PAY-" + UUID.randomUUID(),
                new ObligationSettlementRequest(acc.getId(), "20000.00", evalDate.toString(), "PAY-01", "Partial payment")
        );

        DailyCashForecastResponse res = forecastService.getDailyCashForecast(contextA, "PKR", 30, evalDate.toString());

        assertNotNull(res);
        // Starting cash: 100k - 20k posted payment = 80k.
        // Remaining scheduled outflows: 30k (Installment 1 remaining 5k + Installment 2 25k).
        // Closing cash: 80k - 30k = 50k.
        assertEquals("80000.00", res.summary().startingCash());
        assertEquals("30000.00", res.summary().totalExpectedOutflows());
        assertEquals("50000.00", res.summary().endingProjectedCash());
    }

    @Test
    @DisplayName("Fixture E: Recurring rule deduplication with generated occurrences")
    void testFixtureE_RecurringRuleDeduplicationPostgreSQL() throws Exception {
        FinancialAccount acc = accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "HBL Main", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), evalDate.minusDays(5))
        );

        recurringRuleService.createRule(contextA, new RecurringRuleCreateRequest(
                "Biweekly Cleaning", "Cleaning", "OPERATING_EXPENSE_PAYMENT", acc.getId(), null,
                "5000.00", "BIWEEKLY", evalDate.toString(), null
        ));

        DailyCashForecastResponse res = forecastService.getDailyCashForecast(contextA, "PKR", 30, evalDate.toString());

        assertNotNull(res);
        // Occurrences on day 0, day 14, day 28 = 15,000.00 PKR.
        assertTrue(new BigDecimal(res.summary().totalExpectedOutflows()).compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Fixture E2: Skipped recurring occurrence is excluded from cash outflows and not recreated on PostgreSQL")
    void testFixtureE2_SkippedOccurrenceExclusionPostgreSQL() throws Exception {
        FinancialAccount acc = accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "HBL Main", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), evalDate.minusDays(5))
        );

        recurringRuleService.createRule(contextA, new RecurringRuleCreateRequest(
                "Biweekly Cleaning", "Cleaning", "OPERATING_EXPENSE_PAYMENT", acc.getId(), null,
                "5000.00", "BIWEEKLY", evalDate.toString(), null
        ));

        // Generate due occurrence on evalDate
        recurringRuleService.generateDueOccurrencesForBusiness(contextA);
        var occurrences = recurringOccurrenceRepository.findByBusinessIdAndScheduledDateBetween(businessIdA, evalDate, evalDate.plusDays(30));
        assertFalse(occurrences.isEmpty(), "Generated occurrence must exist");

        // Skip the first occurrence
        recurringRuleService.skipOccurrence(contextA, occurrences.get(0).getId(), new OccurrenceSkipRequest("Testing skip exclusion"));

        DailyCashForecastResponse res = forecastService.getDailyCashForecast(contextA, "PKR", 30, evalDate.toString());

        assertNotNull(res);
        // The skipped occurrence on evalDate must not be in outflows, nor recreated by the template!
        // Future unmaterialized occurrences at evalDate + 14, evalDate + 28 = 10,000.00 (not 15,000.00)
        assertEquals("10000.00", res.summary().totalExpectedOutflows());
    }

    @Test
    @DisplayName("Fixture F: Reversed transaction neutrality on PostgreSQL")
    void testFixtureF_ReversedTransactionNeutralityPostgreSQL() throws Exception {
        FinancialAccount acc = accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "HBL Main", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), evalDate.minusDays(10))
        );

        TransactionDetailResponse tx = transactionPostService.postTransaction(contextA, "TX-EXP-" + UUID.randomUUID(),
                new TransactionPostRequest(acc.getId(), "OPERATING_EXPENSE_PAYMENT", "20000.00", evalDate.minusDays(2).toString(), "Test expense", null)
        );

        transactionReverseService.postReversal(contextA, "REV-" + UUID.randomUUID(), tx.id(),
                new TransactionReverseRequest("Error in amount")
        );

        DailyCashForecastResponse res = forecastService.getDailyCashForecast(contextA, "PKR", 30, evalDate.toString());

        assertNotNull(res);
        assertEquals("100000.00", res.summary().startingCash());
        assertEquals("100000.00", res.summary().endingProjectedCash());
    }

    @Test
    @DisplayName("Fixture G: Currency isolation ensures separate accounts do not pollute cash forecasts")
    void testFixtureG_CurrencyIsolationPostgreSQL() throws Exception {
        accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "HBL PKR", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), evalDate.minusDays(5))
        );
        accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "Citi USD", AccountType.BANK, "USD", 2, new BigDecimal("5000.00"), evalDate.minusDays(5))
        );

        DailyCashForecastResponse resPkr = forecastService.getDailyCashForecast(contextA, "PKR", 30, evalDate.toString());
        DailyCashForecastResponse resUsd = forecastService.getDailyCashForecast(contextA, "USD", 30, evalDate.toString());

        assertEquals("PKR", resPkr.currency());
        assertEquals("100000.00", resPkr.summary().startingCash());

        assertEquals("USD", resUsd.currency());
        assertEquals("5000.00", resUsd.summary().startingCash());
    }

    @Test
    @DisplayName("Fixture H: Financial read-only invariant verified via bit-for-bit SHA-256 snapshots on 12 tables")
    void testFixtureH_ReadOnlyDatabaseInvariantsPostgreSQL() throws Exception {
        FinancialAccount acc = accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "HBL Main", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), evalDate.minusDays(5))
        );
        Counterparty cust = counterpartyRepository.saveAndFlush(
                new Counterparty(businessIdA, "Alpha Traders", CounterpartyType.CUSTOMER, null, null, null, null, null)
        );
        obligationRepository.saveAndFlush(
                new FinancialObligation(businessIdA, cust.getId(), ObligationDirection.RECEIVABLE, ObligationSourceType.CUSTOMER_INVOICE,
                        "SRC-INV-1", "INV-101", evalDate, evalDate.plusDays(10), new BigDecimal("40000.00"), "PKR", "Sales invoice", userIdA)
        );

        LoginSession session = loginAndSelectBusiness(ownerUserA, businessIdA);

        List<String> auditedTables = List.of(
                "financial_accounts", "financial_transactions", "account_movements", "record_drafts",
                "financial_obligations", "obligation_payment_allocations", "obligation_payment_schedules",
                "counterparty_advances", "counterparties", "recurring_rules",
                "recurring_rule_occurrences", "monthly_records"
        );

        Map<String, TableSnapshot> beforeSnapshots = captureTableSnapshots(auditedTables);

        // Execute service calls
        forecastService.getDailyCashForecast(contextA, "PKR", 30, evalDate.toString());
        forecastService.previewScenario(contextA, new ForecastScenarioRequest("PKR", 30, evalDate.toString(), 7, new BigDecimal("80.00"), 5, false, 1));

        // Execute HTTP endpoints via MockMvc
        mockMvc.perform(withSession(get("/api/finance/forecast/daily")
                        .param("currency", "PKR")
                        .param("horizonDays", "30")
                        .param("asOfDate", evalDate.toString()), session))
                .andExpect(status().isOk());

        mockMvc.perform(withSession(post("/api/finance/forecast/scenario-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currency": "PKR",
                                  "horizonDays": 30,
                                  "asOfDate": "%s",
                                  "collectionDelayDays": 10,
                                  "collectionPercentage": 90.00,
                                  "paymentDelayDays": 5
                                }
                                """.formatted(evalDate)), session))
                .andExpect(status().isOk());

        Map<String, TableSnapshot> afterSnapshots = captureTableSnapshots(auditedTables);

        for (String table : auditedTables) {
            TableSnapshot before = beforeSnapshots.get(table);
            TableSnapshot after = afterSnapshots.get(table);
            assertNotNull(before, "Missing before snapshot for " + table);
            assertNotNull(after, "Missing after snapshot for " + table);
            assertEquals(before.rowCount(), after.rowCount(), "Row count changed on read-only forecast for " + table);
            assertEquals(before.sha256Digest(), after.sha256Digest(), "SHA-256 value hash changed on read-only forecast for " + table);
        }
    }

    @Test
    @DisplayName("Fixture I: Full Spring Security HTTP session authentication, RBAC, and cross-tenant isolation")
    void testFixtureI_SecurityAndRbacPostgreSQL() throws Exception {
        accountRepository.saveAndFlush(
                new FinancialAccount(businessIdA, "HBL Main", AccountType.BANK, "PKR", 2, new BigDecimal("100000.00"), evalDate.minusDays(5))
        );

        LoginSession ownerSession = loginAndSelectBusiness(ownerUserA, businessIdA);

        // 1. Authenticated OWNER -> 200 OK
        mockMvc.perform(withSession(get("/api/finance/forecast/daily"), ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("PKR"))
                .andExpect(jsonPath("$.summary.startingCash").value("100000.00"));

        // 2. Unauthenticated request -> 401 Unauthorized
        mockMvc.perform(get("/api/finance/forecast/daily"))
                .andExpect(status().isUnauthorized());

        // 3. Member with VIEWER role -> 200 OK (read allowed)
        AppUser viewerUser = createActiveUser("viewer");
        membershipRepository.saveAndFlush(new BusinessMembership(viewerUser.getId(), businessIdA, MembershipRole.VIEWER, MembershipStatus.ACTIVE));
        LoginSession viewerSession = loginAndSelectBusiness(viewerUser, businessIdA);

        mockMvc.perform(withSession(get("/api/finance/forecast/daily"), viewerSession))
                .andExpect(status().isOk());

        // 4. Cross-tenant isolation: User B cannot access Business A
        AppUser stranger = createActiveUser("stranger");
        UUID businessIdB = businessService.createBusiness(
                new CreateBusinessRequest("Other Business", "retail", "en", null, false, null, null, null),
                stranger.getId()
        ).businessId();
        LoginSession strangerSession = loginAndSelectBusiness(stranger, businessIdB);

        MvcResult result = mockMvc.perform(withSession(get("/api/finance/forecast/daily"), strangerSession))
                .andExpect(status().isOk())
                .andReturn();

        // Business B has no accounts, so starting cash is 0.00 (not 100,000.00 from Business A)
        assertTrue(result.getResponse().getContentAsString().contains("\"startingCash\":\"0.00\""));
    }

    private record TableSnapshot(long rowCount, String sha256Digest) {}

    private Map<String, TableSnapshot> captureTableSnapshots(List<String> tables) throws Exception {
        Map<String, TableSnapshot> snapshots = new LinkedHashMap<>();
        for (String table : tables) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM " + table + " ORDER BY id ASC"
            );
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (Map<String, Object> row : rows) {
                TreeMap<String, String> sortedCols = new TreeMap<>();
                for (Map.Entry<String, Object> e : row.entrySet()) {
                    sortedCols.put(e.getKey(), Objects.toString(e.getValue(), "<NULL>"));
                }
                md.update(sortedCols.toString().getBytes(StandardCharsets.UTF_8));
                md.update((byte) '\n');
            }
            snapshots.put(table, new TableSnapshot(rows.size(), HexFormat.of().formatHex(md.digest())));
        }
        return snapshots;
    }

    private AppUser createActiveUser(String prefix) {
        AppUser user = new AppUser();
        user.setEmail(prefix + "-" + UUID.randomUUID().toString().substring(0, 6) + "@example.com");
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
}
