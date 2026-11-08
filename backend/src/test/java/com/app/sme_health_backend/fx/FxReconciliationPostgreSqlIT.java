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
import com.app.sme_health_backend.fx.facade.FxFinancialEvidenceFacade;
import com.app.sme_health_backend.fx.repository.FxObligationRecognitionEvidenceRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.whatsapp.provider=mock")
@AutoConfigureMockMvc
class FxReconciliationPostgreSqlIT extends DisposablePostgres {

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
    private CounterpartyRepository counterparties;

    @Autowired
    private FxObligationSettlementRepository fxSettlements;

    @Autowired
    private FxObligationRecognitionEvidenceRepository fxRecognitionEvidences;

    @Autowired
    private FxFinancialEvidenceFacade fxFacade;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();
    private static final String DEFAULT_PASSWORD = "TestPassword2026!";

    private record UserBusinessFixture(AppUser user, UUID businessId, Cookie session) {}

    private UserBusinessFixture ownerFixture;
    private FinancialAccount ownerPkrAccount;
    private Counterparty testCounterparty;

    @BeforeEach
    void setUp() throws Exception {
        ownerFixture = createFixture("fx-recon-owner-" + UUID.randomUUID(), MembershipRole.OWNER);

        testCounterparty = new Counterparty(
                ownerFixture.businessId(),
                "Global Forex Supplier Ltd",
                CounterpartyType.SUPPLIER,
                "forex@global.test",
                "+923001234567",
                "Karachi, Pakistan",
                "TAX-9988",
                "Main FX supplier"
        );
        testCounterparty = counterparties.saveAndFlush(testCounterparty);

        ownerPkrAccount = new FinancialAccount(
                ownerFixture.businessId(),
                "PKR Primary Bank Account",
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("10000000.00"),
                LocalDate.of(2026, 1, 1),
                AccountStatus.ACTIVE,
                "Standard Chartered Bank",
                "SCB-PKR-001",
                "Main operational checking account"
        );
        ownerPkrAccount = accounts.saveAndFlush(ownerPkrAccount);
    }

    @AfterEach
    void tearDown() {
        // DisposablePostgres runs on ephemeral Postgres instances with unique random UUIDs per test.
        // Financial ledger tables have append-only REVOKE DELETE for audit integrity.
    }

    @Test
    @DisplayName("IT: Full lifecycle - Record recognition, post settlement, verify reconciliation and period summary")
    void testFullReconciliationLifecycle() throws Exception {
        // 1. Create USD 100 export receivable
        FinancialObligation receivable = createObligation(ObligationDirection.RECEIVABLE, new BigDecimal("100.00"), "USD");

        // 2. Record recognition evidence @ 275.00 -> book value PKR 27,500.00
        FxRecognitionEvidenceRequest recReq = new FxRecognitionEvidenceRequest(
                new BigDecimal("275.00"), "PKR", LocalDate.of(2026, 11, 1),
                "SALES_INVOICE", "ORIGINAL_CONTRACT", "Invoice booking"
        );

        mvc.perform(post("/api/finance/fx/obligations/" + receivable.getId() + "/recognition")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(json.writeValueAsString(recReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nativeCurrency").value("USD"))
                .andExpect(jsonPath("$.nativeAmount").value(100.00))
                .andExpect(jsonPath("$.recognitionRate").value(275.00))
                .andExpect(jsonPath("$.bookValue").value(27500.00));

        // 3. Post collection @ 278.50 with 150 PKR fee -> 27,700 credited, 350 FX GAIN
        FxSettlementPostRequest settleReq = new FxSettlementPostRequest(
                receivable.getId(),
                ownerPkrAccount.getId(),
                new BigDecimal("100.00"),
                new BigDecimal("278.50"),
                new BigDecimal("150.00"),
                BigDecimal.ZERO,
                "PKR",
                LocalDate.of(2026, 11, 5),
                "Collection with FX gain",
                "MANUAL_USER_RATE"
        );

        String postRes = mvc.perform(post("/api/finance/fx/settlements")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(json.writeValueAsString(settleReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.convertedPrincipal").value(27850.00))
                .andExpect(jsonPath("$.feeAmount").value(150.00))
                .andExpect(jsonPath("$.totalAccountMovement").value(27700.00))
                .andExpect(jsonPath("$.allocatedBookValue").value(27500.00))
                .andExpect(jsonPath("$.realizedFxDifference").value(350.00))
                .andExpect(jsonPath("$.realizedFxImpact").value("GAIN"))
                .andExpect(jsonPath("$.recognitionEvidenceStatus").value("AVAILABLE"))
                .andReturn().getResponse().getContentAsString();

        FxSettlementResponse settleDto = json.readValue(postRes, FxSettlementResponse.class);

        // 4. Fetch reconciliation endpoint
        mvc.perform(get("/api/finance/fx/settlements/" + settleDto.id() + "/reconciliation")
                        .cookie(ownerFixture.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashReconciliationVerified").value(true))
                .andExpect(jsonPath("$.feeTreatment").value("FEE_DEDUCTED_FROM_CASH_CREDIT"))
                .andExpect(jsonPath("$.realizedFxDifference").value(350.00))
                .andExpect(jsonPath("$.realizedFxImpact").value("GAIN"))
                .andExpect(jsonPath("$.netEconomicVariance").value(200.00));

        // 5. Fetch period summary
        mvc.perform(get("/api/finance/fx/settlements/summary")
                        .cookie(ownerFixture.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSettlementsCount").value(1))
                .andExpect(jsonPath("$.activeSettlementsCount").value(1))
                .andExpect(jsonPath("$.totalRealizedGain").value(350.00))
                .andExpect(jsonPath("$.totalRealizedLoss").value(0))
                .andExpect(jsonPath("$.netRealizedFx").value(350.00))
                .andExpect(jsonPath("$.totalBankFees").value(150.00));

        // 6. Test Facade integration
        assertThat(fxFacade.getCumulativeRealizedFxForObligation(ownerFixture.businessId(), receivable.getId()))
                .isEqualByComparingTo("350.00");
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
        user.setFullName("FX Recon Test User");
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
