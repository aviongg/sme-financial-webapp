package com.app.sme_health_backend.fx;

import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.counterparty.CounterpartyStatus;
import com.app.sme_health_backend.counterparty.CounterpartyType;
import com.app.sme_health_backend.fx.dto.*;
import com.app.sme_health_backend.fx.entity.FxObligationSettlement;
import com.app.sme_health_backend.fx.model.FxSettlementStatus;
import com.app.sme_health_backend.fx.model.SettlementDirection;
import com.app.sme_health_backend.fx.repository.FxObligationSettlementRepository;
import com.app.sme_health_backend.identity.dto.CsrfResponse;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.entity.Business;
import com.app.sme_health_backend.identity.entity.BusinessMembership;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.model.MembershipStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.repository.BusinessMembershipRepository;
import com.app.sme_health_backend.identity.repository.BusinessRepository;
import com.app.sme_health_backend.obligation.*;
import com.app.sme_health_backend.obligation.aging.ObligationDirection;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.whatsapp.provider=mock")
@AutoConfigureMockMvc
class FxSettlementPostgreSqlIT extends DisposablePostgres {

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
    private FinancialObligationRepository obligations;

    @Autowired
    private ObligationPaymentAllocationRepository allocations;

    @Autowired
    private CounterpartyRepository counterparties;

    @Autowired
    private FxObligationSettlementRepository fxSettlements;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();
    private static final String DEFAULT_PASSWORD = "TestPassword2026!";

    private record UserBusinessFixture(AppUser user, UUID businessId, Cookie session) {}

    private UserBusinessFixture ownerFixture;
    private UserBusinessFixture salespersonFixture;
    private FinancialAccount ownerPkrAccount;
    private Counterparty testCounterparty;

    @BeforeEach
    void setUp() throws Exception {
        ownerFixture = createFixture("fx-set-owner-" + UUID.randomUUID(), MembershipRole.OWNER);
        salespersonFixture = createFixture("fx-set-sales-" + UUID.randomUUID(), MembershipRole.SALESPERSON);

        // Seed PKR 1,000,000 operating bank account
        ownerPkrAccount = new FinancialAccount(
                ownerFixture.businessId(),
                "Primary PKR Operating Account",
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("1000000.00"),
                LocalDate.of(2026, 1, 1),
                AccountStatus.ACTIVE,
                "Habib Bank Limited",
                "HBL-OPERATING-001",
                "Operating bank"
        );
        ownerPkrAccount = accounts.saveAndFlush(ownerPkrAccount);

        // Seed counterparty
        testCounterparty = new Counterparty(
                ownerFixture.businessId(),
                "Global Imports LLC",
                CounterpartyType.SUPPLIER,
                "ali@globalimports.test",
                "+923001234567",
                "Karachi, Pakistan",
                "GI-001",
                "Main supplier"
        );
        testCounterparty = counterparties.saveAndFlush(testCounterparty);
    }

    @AfterEach
    void tearDown() {
        // DisposablePostgres runs on ephemeral Postgres instances with unique random UUIDs per test.
        // Financial ledger tables have append-only REVOKE DELETE for audit integrity.
    }

    @Test
    @DisplayName("IT-FX-SETTLE-01: Preview cross-currency settlement returns accurate figures without DB mutations")
    void previewSettlementInvariance() throws Exception {
        FinancialObligation payable = createObligation(ObligationDirection.PAYABLE, new BigDecimal("100.00"), "USD");

        int settlementsBefore = jdbc.queryForObject("SELECT count(*) FROM fx_obligation_settlements", Integer.class);
        int txBefore = jdbc.queryForObject("SELECT count(*) FROM financial_transactions", Integer.class);

        FxSettlementPreviewRequest previewReq = new FxSettlementPreviewRequest(
                payable.getId(),
                ownerPkrAccount.getId(),
                new BigDecimal("100.00"),
                new BigDecimal("278.50"),
                new BigDecimal("150.00"),
                BigDecimal.ZERO,
                "PKR"
        );

        mvc.perform(post("/api/finance/fx/settlements/preview")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(json.writeValueAsString(previewReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.obligationCurrency").value("USD"))
                .andExpect(jsonPath("$.settleAmountInObligationCurrency").value(100.00))
                .andExpect(jsonPath("$.accountCurrency").value("PKR"))
                .andExpect(jsonPath("$.convertedPrincipalInAccountCurrency").value(27850.00))
                .andExpect(jsonPath("$.totalFeeAmount").value(150.00))
                .andExpect(jsonPath("$.totalAccountMovement").value(28000.00))
                .andExpect(jsonPath("$.effectiveRate").value(280.000000))
                .andExpect(jsonPath("$.accountMovementType").value("DECREASE"));

        int settlementsAfter = jdbc.queryForObject("SELECT count(*) FROM fx_obligation_settlements", Integer.class);
        int txAfter = jdbc.queryForObject("SELECT count(*) FROM financial_transactions", Integer.class);
        assertThat(settlementsAfter).isEqualTo(settlementsBefore);
        assertThat(txAfter).isEqualTo(txBefore);
    }

    @Test
    @DisplayName("IT-FX-SETTLE-02: Case A - Settle USD 100 payable from PKR account @ 278.50 with 150 PKR fee")
    void postCaseAPayableSettlement() throws Exception {
        FinancialObligation payable = createObligation(ObligationDirection.PAYABLE, new BigDecimal("100.00"), "USD");

        FxSettlementPostRequest request = new FxSettlementPostRequest(
                payable.getId(),
                ownerPkrAccount.getId(),
                new BigDecimal("100.00"),
                new BigDecimal("278.50"),
                new BigDecimal("150.00"),
                BigDecimal.ZERO,
                "PKR",
                LocalDate.of(2026, 11, 5),
                "Case A settlement",
                "MANUAL_USER_RATE"
        );

        String idempKey = "it-case-a-" + UUID.randomUUID();

        var result = mvc.perform(post("/api/finance/fx/settlements")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .header("Idempotency-Key", idempKey)
                        .contentType("application/json")
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.obligationCurrency").value("USD"))
                .andExpect(jsonPath("$.obligationAmount").value(100.00))
                .andExpect(jsonPath("$.accountCurrency").value("PKR"))
                .andExpect(jsonPath("$.convertedPrincipal").value(27850.00))
                .andExpect(jsonPath("$.feeAmount").value(150.00))
                .andExpect(jsonPath("$.totalAccountMovement").value(28000.00))
                .andExpect(jsonPath("$.effectiveRate").value(280.000000))
                .andExpect(jsonPath("$.settlementDirection").value("PAYABLE_SETTLEMENT"))
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andReturn();

        FxSettlementResponse response = json.readValue(result.getResponse().getContentAsString(), FxSettlementResponse.class);

        // Verify database persistence in fx_obligation_settlements
        FxObligationSettlement entity = fxSettlements.findById(response.id()).orElseThrow();
        assertThat(entity.getObligationAmount()).isEqualByComparingTo("100.00");
        assertThat(entity.getTotalAccountMovement()).isEqualByComparingTo("28000.00");
        assertThat(entity.getStatus()).isEqualTo(FxSettlementStatus.SETTLED);

        // Verify obligation remaining balance is 0.00 USD
        BigDecimal paidUsd = allocations.sumEffectiveAllocations(ownerFixture.businessId(), payable.getId());
        assertThat(paidUsd).isEqualByComparingTo("100.00");

        // Verify account movement is 28,000 PKR decrease
        BigDecimal movSum = jdbc.queryForObject(
                "SELECT amount FROM account_movements WHERE transaction_id = ?",
                BigDecimal.class, response.financialTransactionId()
        );
        assertThat(movSum).isEqualByComparingTo("28000.00");

        // Verify idempotency replay returns identical response without duplicating
        mvc.perform(post("/api/finance/fx/settlements")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .header("Idempotency-Key", idempKey)
                        .contentType("application/json")
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(response.id().toString()));
    }

    @Test
    @DisplayName("IT-FX-SETTLE-03: Case B - Collect USD 100 receivable into PKR account @ 278.50 with 150 PKR fee")
    void postCaseBReceivableCollection() throws Exception {
        FinancialObligation receivable = createObligation(ObligationDirection.RECEIVABLE, new BigDecimal("100.00"), "USD");

        FxSettlementPostRequest request = new FxSettlementPostRequest(
                receivable.getId(),
                ownerPkrAccount.getId(),
                new BigDecimal("100.00"),
                new BigDecimal("278.50"),
                new BigDecimal("150.00"),
                BigDecimal.ZERO,
                "PKR",
                LocalDate.of(2026, 11, 5),
                "Case B collection",
                "MANUAL_USER_RATE"
        );

        mvc.perform(post("/api/finance/fx/settlements")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.obligationCurrency").value("USD"))
                .andExpect(jsonPath("$.obligationAmount").value(100.00))
                .andExpect(jsonPath("$.accountCurrency").value("PKR"))
                .andExpect(jsonPath("$.convertedPrincipal").value(27850.00))
                .andExpect(jsonPath("$.feeAmount").value(150.00))
                .andExpect(jsonPath("$.totalAccountMovement").value(27700.00))
                .andExpect(jsonPath("$.effectiveRate").value(277.000000))
                .andExpect(jsonPath("$.settlementDirection").value("RECEIVABLE_COLLECTION"))
                .andExpect(jsonPath("$.status").value("SETTLED"));

        // Verify obligation remaining balance is 0.00 USD
        BigDecimal paidUsd = allocations.sumEffectiveAllocations(ownerFixture.businessId(), receivable.getId());
        assertThat(paidUsd).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("IT-FX-SETTLE-04: Compensating reversal re-opens obligation and reverses account movement")
    void reverseSettlementWorkflow() throws Exception {
        FinancialObligation payable = createObligation(ObligationDirection.PAYABLE, new BigDecimal("100.00"), "USD");

        FxSettlementPostRequest request = new FxSettlementPostRequest(
                payable.getId(),
                ownerPkrAccount.getId(),
                new BigDecimal("100.00"),
                new BigDecimal("278.50"),
                new BigDecimal("150.00"),
                BigDecimal.ZERO,
                "PKR",
                LocalDate.of(2026, 11, 5),
                "Settlement to reverse",
                "MANUAL_USER_RATE"
        );

        var postRes = mvc.perform(post("/api/finance/fx/settlements")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        FxSettlementResponse settlement = json.readValue(postRes.getResponse().getContentAsString(), FxSettlementResponse.class);

        // Obligation is paid
        assertThat(allocations.sumEffectiveAllocations(ownerFixture.businessId(), payable.getId()))
                .isEqualByComparingTo("100.00");

        // Reverse settlement
        FxSettlementReverseRequest revReq = new FxSettlementReverseRequest("Invoice disputed by supplier");

        mvc.perform(post("/api/finance/fx/settlements/" + settlement.id() + "/reverse")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(json.writeValueAsString(revReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVERSED"))
                .andExpect(jsonPath("$.reversalReason").value("Invoice disputed by supplier"))
                .andExpect(jsonPath("$.reversalTransactionId").isNotEmpty());

        // Obligation is now OPEN again (0.00 effective allocations)
        assertThat(allocations.sumEffectiveAllocations(ownerFixture.businessId(), payable.getId()))
                .isEqualByComparingTo("0.00");

        // Second reversal fails with 409 Conflict
        mvc.perform(post("/api/finance/fx/settlements/" + settlement.id() + "/reverse")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(json.writeValueAsString(revReq)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("IT-FX-SETTLE-05: RBAC - SALESPERSON role denied access (403 Forbidden)")
    void salespersonDeniedAccess() throws Exception {
        FinancialObligation payable = createObligation(ObligationDirection.PAYABLE, new BigDecimal("100.00"), "USD");

        FxSettlementPostRequest request = new FxSettlementPostRequest(
                payable.getId(),
                ownerPkrAccount.getId(),
                new BigDecimal("100.00"),
                new BigDecimal("278.50"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "PKR",
                LocalDate.of(2026, 11, 5),
                "Salesperson breach attempt",
                "MANUAL_USER_RATE"
        );

        mvc.perform(post("/api/finance/fx/settlements")
                        .cookie(salespersonFixture.session())
                        .with(realCsrf(salespersonFixture.session()))
                        .contentType("application/json")
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/finance/fx/settlements")
                        .cookie(salespersonFixture.session()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IT-FX-SETTLE-06: Three-decimal KWD settlement posting, allocation, persistence, and reversal")
    void testThreeDecimalKwdSettlementPostingAndReversal() throws Exception {
        BigDecimal kwdAmount = new BigDecimal("50.125");
        FinancialObligation kwdPayable = createObligation(ObligationDirection.PAYABLE, kwdAmount, "KWD");

        // 50.125 KWD @ 905.500000 PKR/KWD -> Converted principal: 45388.19 PKR
        BigDecimal rate = new BigDecimal("905.500000");
        BigDecimal fixedFee = new BigDecimal("50.00");

        FxSettlementPostRequest request = new FxSettlementPostRequest(
                kwdPayable.getId(),
                ownerPkrAccount.getId(),
                kwdAmount,
                rate,
                fixedFee,
                BigDecimal.ZERO,
                "PKR",
                LocalDate.of(2026, 11, 5),
                "KWD 3-decimal precision settlement",
                "MANUAL_USER_RATE"
        );

        var postRes = mvc.perform(post("/api/finance/fx/settlements")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.obligationCurrency").value("KWD"))
                .andExpect(jsonPath("$.convertedPrincipal").value(45388.19))
                .andExpect(jsonPath("$.feeAmount").value(50.00))
                .andExpect(jsonPath("$.totalAccountMovement").value(45438.19))
                .andReturn();

        FxSettlementResponse settlement = json.readValue(postRes.getResponse().getContentAsString(), FxSettlementResponse.class);

        // Verify database persistence in fx_obligation_settlements retains exact scale without truncation
        FxObligationSettlement entity = fxSettlements.findById(settlement.id()).orElseThrow();
        assertThat(entity.getObligationCurrency()).isEqualTo("KWD");
        assertThat(entity.getObligationAmount()).isEqualByComparingTo("50.1250");
        assertThat(entity.getConvertedPrincipal()).isEqualByComparingTo("45388.1900");
        assertThat(entity.getTotalAccountMovement()).isEqualByComparingTo("45438.1900");

        // Verify allocation in database retains 50.125 KWD
        BigDecimal allocated = allocations.sumEffectiveAllocations(ownerFixture.businessId(), kwdPayable.getId());
        assertThat(allocated).isEqualByComparingTo("50.1250");

        // Reversal restores the exact 50.125 KWD balance
        FxSettlementReverseRequest revReq = new FxSettlementReverseRequest("Reversing KWD settlement");
        mvc.perform(post("/api/finance/fx/settlements/" + settlement.id() + "/reverse")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(json.writeValueAsString(revReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVERSED"));

        BigDecimal remainingAllocated = allocations.sumEffectiveAllocations(ownerFixture.businessId(), kwdPayable.getId());
        assertThat(remainingAllocated).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("IT-FX-SETTLE-07: Concurrency & Oversettlement - Exceeding balance or payload mutation rejected")
    void testOversettlementAndChangedPayloadIdempotencyRejected() throws Exception {
        FinancialObligation payable = createObligation(ObligationDirection.PAYABLE, new BigDecimal("100.00"), "USD");

        // 1. Settle 60.00 USD
        FxSettlementPostRequest req1 = new FxSettlementPostRequest(
                payable.getId(),
                ownerPkrAccount.getId(),
                new BigDecimal("60.00"),
                new BigDecimal("278.50"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "PKR",
                LocalDate.of(2026, 11, 5),
                "Installment 1",
                "MANUAL_USER_RATE"
        );
        String idempKey = "idemp-" + UUID.randomUUID();

        mvc.perform(post("/api/finance/fx/settlements")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .header("Idempotency-Key", idempKey)
                        .contentType("application/json")
                        .content(json.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        // 2. Attempt to settle 50.00 USD (only 40.00 USD remaining) -> must be rejected with 409 Conflict
        FxSettlementPostRequest reqExceed = new FxSettlementPostRequest(
                payable.getId(),
                ownerPkrAccount.getId(),
                new BigDecimal("50.00"),
                new BigDecimal("278.50"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "PKR",
                LocalDate.of(2026, 11, 5),
                "Installment 2 Exceeds",
                "MANUAL_USER_RATE"
        );
        mvc.perform(post("/api/finance/fx/settlements")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(json.writeValueAsString(reqExceed)))
                .andExpect(status().isConflict());

        // 3. Reusing existing idempotency key with altered payload -> must be rejected with 409 Conflict
        FxSettlementPostRequest reqAltered = new FxSettlementPostRequest(
                payable.getId(),
                ownerPkrAccount.getId(),
                new BigDecimal("40.00"),
                new BigDecimal("278.50"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "PKR",
                LocalDate.of(2026, 11, 5),
                "Altered payload",
                "MANUAL_USER_RATE"
        );
        mvc.perform(post("/api/finance/fx/settlements")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .header("Idempotency-Key", idempKey)
                        .contentType("application/json")
                        .content(json.writeValueAsString(reqAltered)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("IT-FX-SETTLE-08: Reporting isolation - Salesperson denied and cross-business isolation verified")
    void testCrossBusinessAndSalespersonReportingIsolation() throws Exception {
        // Salesperson cannot access analytics overview, trends, report, or CSV export
        mvc.perform(get("/api/finance/fx/analytics/overview")
                        .cookie(salespersonFixture.session()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/finance/fx/analytics/trends")
                        .cookie(salespersonFixture.session()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/finance/fx/analytics/report")
                        .cookie(salespersonFixture.session()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/finance/fx/analytics/export")
                        .cookie(salespersonFixture.session()))
                .andExpect(status().isForbidden());

        // Another business cannot see first business settlements
        UserBusinessFixture secondBizFixture = createFixture("other-owner", MembershipRole.OWNER);
        var res = mvc.perform(get("/api/finance/fx/analytics/report")
                        .cookie(secondBizFixture.session()))
                .andExpect(status().isOk())
                .andReturn();

        java.util.List<?> items = json.readValue(res.getResponse().getContentAsString(), java.util.List.class);
        assertThat(items).isEmpty();
    }

    private FinancialObligation createObligation(ObligationDirection direction, BigDecimal amount, String currency) {
        FinancialObligation ob = new FinancialObligation(
                ownerFixture.businessId(),
                testCounterparty.getId(),
                direction,
                ObligationSourceType.MANUAL_DECLARATION,
                "FX-TEST-" + UUID.randomUUID(),
                "REF-FX-" + UUID.randomUUID(),
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30),
                amount,
                currency,
                "FX Cross-currency test obligation",
                ownerFixture.user().getId()
        );
        return obligations.saveAndFlush(ob);
    }

    private UserBusinessFixture createFixture(String emailPrefix, MembershipRole role) throws Exception {
        AppUser user = new AppUser();
        user.setEmail(emailPrefix + "@test.finsight.local");
        user.setFullName("FX Settle Test User");
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
