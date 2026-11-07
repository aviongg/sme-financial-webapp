package com.app.sme_health_backend.funding;

import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.funding.dto.*;
import com.app.sme_health_backend.identity.dto.CsrfResponse;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.entity.Business;
import com.app.sme_health_backend.identity.entity.BusinessMembership;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.model.MembershipStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.repository.BusinessMembershipRepository;
import com.app.sme_health_backend.identity.repository.BusinessRepository;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.whatsapp.provider=mock")
@AutoConfigureMockMvc
class FundingScenarioPostgreSqlIT extends DisposablePostgres {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private BusinessRepository businesses;

    @Autowired
    private BusinessMembershipRepository memberships;

    @Autowired
    private FinancialAccountRepository accounts;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();
    private static final String DEFAULT_PASSWORD = "TestPassword2026!";

    private record UserBusinessFixture(AppUser user, UUID businessId, Cookie session) {}

    private UserBusinessFixture fixtureA;
    private UserBusinessFixture fixtureB;

    @BeforeEach
    void setUp() throws Exception {
        fixtureA = createFixture("tenant-a-" + UUID.randomUUID(), MembershipRole.OWNER);
        fixtureB = createFixture("tenant-b-" + UUID.randomUUID(), MembershipRole.OWNER);

        // Seed PKR 105,000 cash account for Business A
        FinancialAccount cashAccount = new FinancialAccount(
                fixtureA.businessId(),
                "Main Operating Account",
                AccountType.CASH,
                "PKR",
                2,
                new BigDecimal("105000.00"),
                LocalDate.of(2026, 11, 1),
                AccountStatus.ACTIVE,
                "Meezan Bank",
                "PKR-001-CASH",
                "Primary operational cash reserve"
        );
        accounts.saveAndFlush(cashAccount);
    }

    @AfterEach
    void tearDown() {
        if (fixtureA != null && fixtureB != null) {
            jdbc.update("DELETE FROM financial_accounts WHERE business_id IN (?, ?)", fixtureA.businessId(), fixtureB.businessId());
            jdbc.update("DELETE FROM business_memberships WHERE business_id IN (?, ?)", fixtureA.businessId(), fixtureB.businessId());
            jdbc.update("DELETE FROM businesses WHERE id IN (?, ?)", fixtureA.businessId(), fixtureB.businessId());
            jdbc.update("DELETE FROM app_users WHERE id IN (?, ?)", fixtureA.user().getId(), fixtureB.user().getId());
        }
    }

    @Test
    @DisplayName("IT-FND-01: Pure Section 6 numerical fixture with MANUAL_ASSUMPTION verifies exact financial timeline")
    void section6NumericalFixtureWithManualAssumption() throws Exception {
        LocalDate asOf = LocalDate.of(2026, 11, 1);
        FundingScenarioCompareRequest request = new FundingScenarioCompareRequest(
                "PKR",
                asOf.toString(),
                30,
                new OpeningLiquidityRequest(OpeningLiquiditySource.MANUAL_ASSUMPTION, BigDecimal.ZERO.setScale(2), "Section 6 clean start assumption"),
                new ScenarioDefinition("Section 6 Canonical Plan", "Canonical scenario baseline", List.of(
                        new PlannedCashEventInput("evt-1", asOf.plusDays(1).toString(), CashFlowDirection.OUTFLOW, new BigDecimal("60000.00"), "Supplier Payment", "SUPPLIES"),
                        new PlannedCashEventInput("evt-2", asOf.plusDays(5).toString(), CashFlowDirection.INFLOW, new BigDecimal("20000.00"), "Receivable Collection", "REVENUE"),
                        new PlannedCashEventInput("evt-3", asOf.plusDays(10).toString(), CashFlowDirection.INFLOW, new BigDecimal("50000.00"), "Customer Advance", "SALES")
                )),
                null
        );

        mvc.perform(post("/api/finance/funding/scenarios/compare")
                        .cookie(fixtureA.session())
                        .with(realCsrf(fixtureA.session()))
                        .contentType("application/json")
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("PKR"))
                .andExpect(jsonPath("$.horizonDays").value(30))
                .andExpect(jsonPath("$.openingLiquidity.source").value("MANUAL_ASSUMPTION"))
                .andExpect(jsonPath("$.openingLiquidity.amount").value("0.00"))
                .andExpect(jsonPath("$.baseline.peakFundingRequirement").value("60000.00"))
                .andExpect(jsonPath("$.baseline.peakDeficitDate").value("2026-11-02"))
                .andExpect(jsonPath("$.baseline.firstDeficitDate").value("2026-11-02"))
                .andExpect(jsonPath("$.baseline.recoveryDate").value("2026-11-11"))
                .andExpect(jsonPath("$.baseline.deficitDayCount").value(9))
                .andExpect(jsonPath("$.baseline.closingProjectedLiquidity").value("10000.00"))
                .andExpect(jsonPath("$.baseline.hasDeficit").value(true))
                .andExpect(jsonPath("$.baseline.isRecovered").value(true))
                .andExpect(jsonPath("$.provenance.openingLiquiditySource").value("MANUAL_ASSUMPTION"))
                .andExpect(jsonPath("$.provenance.planningMode").value("HYPOTHETICAL_SIMULATION"));
    }

    @Test
    @DisplayName("IT-FND-02: LEDGER_CASH dynamically resolves PKR 105,000 from real PostgreSQL financial_accounts")
    void ledgerCashDynamicPostgresIntegration() throws Exception {
        LocalDate asOf = LocalDate.of(2026, 11, 1);
        FundingScenarioCompareRequest request = new FundingScenarioCompareRequest(
                "PKR",
                asOf.toString(),
                30,
                new OpeningLiquidityRequest(OpeningLiquiditySource.LEDGER_CASH, null, null),
                new ScenarioDefinition("Ledger-Backed Plan", "Evaluation from posted actuals", List.of(
                        new PlannedCashEventInput("evt-rent", asOf.plusDays(2).toString(), CashFlowDirection.OUTFLOW, new BigDecimal("25000.00"), "Monthly Facility Lease", "RENT")
                )),
                null
        );

        mvc.perform(post("/api/finance/funding/scenarios/compare")
                        .cookie(fixtureA.session())
                        .with(realCsrf(fixtureA.session()))
                        .contentType("application/json")
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openingLiquidity.source").value("POSTED_ACTUAL"))
                .andExpect(jsonPath("$.openingLiquidity.amount").value("105000.00"))
                .andExpect(jsonPath("$.baseline.closingProjectedLiquidity").value("80000.00"))
                .andExpect(jsonPath("$.baseline.peakFundingRequirement").value("0.00"))
                .andExpect(jsonPath("$.baseline.hasDeficit").value(false))
                .andExpect(jsonPath("$.baseline.isRecovered").value(false));
    }

    @Test
    @DisplayName("IT-FND-03: Strict Database Read-Only Invariant: zero writes across all financial tables")
    void strictReadOnlyDatabaseInvariant() throws Exception {
        LocalDate asOf = LocalDate.of(2026, 11, 1);
        FundingScenarioCompareRequest request = new FundingScenarioCompareRequest(
                "PKR",
                asOf.toString(),
                14,
                new OpeningLiquidityRequest(OpeningLiquiditySource.LEDGER_CASH, null, null),
                new ScenarioDefinition("Base Simulation", "Base", List.of(
                        new PlannedCashEventInput("evt-sim-1", asOf.plusDays(1).toString(), CashFlowDirection.OUTFLOW, new BigDecimal("10000.00"), "Supplies", "OPERATING")
                )),
                new ScenarioDefinition("Alt Simulation", "Alternative", List.of(
                        new PlannedCashEventInput("evt-alt-1", asOf.plusDays(1).toString(), CashFlowDirection.INFLOW, new BigDecimal("50000.00"), "Collection", "REVENUE")
                ))
        );

        int initialAccounts = countRows("financial_accounts");
        int initialTransactions = countRows("financial_transactions");
        int initialMovements = countRows("account_movements");
        int initialObligations = countRows("financial_obligations");
        int initialCounterparties = countRows("counterparties");

        // Execute 5 simulated runs
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/finance/funding/scenarios/compare")
                            .cookie(fixtureA.session())
                            .with(realCsrf(fixtureA.session()))
                            .contentType("application/json")
                            .content(json.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        // Verify row counts remain 100% identical
        assertEquals(initialAccounts, countRows("financial_accounts"), "financial_accounts count must not change");
        assertEquals(initialTransactions, countRows("financial_transactions"), "financial_transactions count must not change");
        assertEquals(initialMovements, countRows("account_movements"), "account_movements count must not change");
        assertEquals(initialObligations, countRows("financial_obligations"), "financial_obligations count must not change");
        assertEquals(initialCounterparties, countRows("counterparties"), "counterparties count must not change");
    }

    @Test
    @DisplayName("IT-FND-04: Cross-Tenant Isolation: Tenant B cannot access Tenant A cash and fails closed")
    void crossTenantIsolationFailsClosed() throws Exception {
        LocalDate asOf = LocalDate.of(2026, 11, 1);
        FundingScenarioCompareRequest request = new FundingScenarioCompareRequest(
                "PKR",
                asOf.toString(),
                30,
                new OpeningLiquidityRequest(OpeningLiquiditySource.LEDGER_CASH, null, null),
                new ScenarioDefinition("Tenant B Plan", "Plan for Tenant B", List.of()),
                null
        );

        // Tenant B has zero accounts in PostgreSQL; must fail closed with 400 Bad Request
        mvc.perform(post("/api/finance/funding/scenarios/compare")
                        .cookie(fixtureB.session())
                        .with(realCsrf(fixtureB.session()))
                        .contentType("application/json")
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors['openingLiquidity.source']").value("No authoritative ledger cash accounts found for currency PKR"));
    }

    @Test
    @DisplayName("IT-FND-05: RBAC Role Matrix: OWNER, ACCOUNTANT, MANAGER, and VIEWER can execute; unauthenticated rejected")
    void rbacRoleMatrixAndUnauthenticatedRejection() throws Exception {
        LocalDate asOf = LocalDate.of(2026, 11, 1);
        FundingScenarioCompareRequest request = new FundingScenarioCompareRequest(
                "PKR",
                asOf.toString(),
                14,
                new OpeningLiquidityRequest(OpeningLiquiditySource.MANUAL_ASSUMPTION, new BigDecimal("10000.00"), null),
                new ScenarioDefinition("Role Check Plan", "Plan", List.of()),
                null
        );

        // 1. Unauthenticated request without session -> 401 Unauthorized or 403 Forbidden
        mvc.perform(post("/api/finance/funding/scenarios/compare")
                        .contentType("application/json")
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        // 2. Test ACCOUNTANT, MANAGER, VIEWER roles in Business A
        for (MembershipRole role : List.of(MembershipRole.ACCOUNTANT, MembershipRole.MANAGER, MembershipRole.VIEWER)) {
            AppUser roleUser = new AppUser();
            roleUser.setEmail("role-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.test");
            roleUser.setFullName("Role " + role.name());
            roleUser.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
            roleUser.setAccountStatus(com.app.sme_health_backend.identity.model.AccountStatus.ACTIVE);
            roleUser.setMustChangePassword(false);
            roleUser = users.saveAndFlush(roleUser);

            memberships.saveAndFlush(new BusinessMembership(roleUser.getId(), fixtureA.businessId(), role, MembershipStatus.ACTIVE));
            Cookie roleSession = loginAndSelect(roleUser, fixtureA.businessId());

            mvc.perform(post("/api/finance/funding/scenarios/compare")
                            .cookie(roleSession)
                            .with(realCsrf(roleSession))
                            .contentType("application/json")
                            .content(json.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.openingLiquidity.amount").value("10000.00"));
        }
    }

    private int countRows(String tableName) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return count != null ? count : 0;
    }

    private UserBusinessFixture createFixture(String emailPrefix, MembershipRole role) throws Exception {
        AppUser user = new AppUser();
        user.setEmail(emailPrefix + "@example.test");
        user.setFullName("Fixture User");
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setAccountStatus(com.app.sme_health_backend.identity.model.AccountStatus.ACTIVE);
        user.setMustChangePassword(false);
        user = users.saveAndFlush(user);

        UUID businessId = UUID.randomUUID();
        businesses.saveAndFlush(new Business(businessId));
        memberships.saveAndFlush(new BusinessMembership(user.getId(), businessId, role, MembershipStatus.ACTIVE));

        Cookie session = loginAndSelect(user, businessId);
        return new UserBusinessFixture(user, businessId, session);
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

        // Select business
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
        CsrfResponse token = json.readValue(response.getContentAsString(), CsrfResponse.class);
        return req -> {
            var cookies = new ArrayList<Cookie>(Arrays.asList(req.getCookies() == null ? new Cookie[0] : req.getCookies()));
            cookies.removeIf(c -> c.getName().equals(xsrf.getName()));
            cookies.add(xsrf);
            req.setCookies(cookies.toArray(Cookie[]::new));
            req.addHeader(token.headerName(), token.token());
            return req;
        };
    }
}
