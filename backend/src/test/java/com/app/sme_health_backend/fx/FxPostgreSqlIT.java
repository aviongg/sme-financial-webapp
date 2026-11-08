package com.app.sme_health_backend.fx;

import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.fx.dto.FxPreviewRequest;
import com.app.sme_health_backend.fx.model.FxDirection;
import com.app.sme_health_backend.fx.model.RateSourceType;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.whatsapp.provider=mock")
@AutoConfigureMockMvc
class FxPostgreSqlIT extends DisposablePostgres {

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

    private UserBusinessFixture ownerFixture;
    private UserBusinessFixture salespersonFixture;

    @BeforeEach
    void setUp() throws Exception {
        ownerFixture = createFixture("fx-owner-" + UUID.randomUUID(), MembershipRole.OWNER);
        salespersonFixture = createFixture("fx-sales-" + UUID.randomUUID(), MembershipRole.SALESPERSON);

        // Seed PKR 500,000 cash account for Owner Business
        FinancialAccount cashAccount = new FinancialAccount(
                ownerFixture.businessId(),
                "Primary Current Account",
                AccountType.CASH,
                "PKR",
                2,
                new BigDecimal("500000.00"),
                LocalDate.of(2026, 11, 1),
                AccountStatus.ACTIVE,
                "Standard Chartered",
                "SCB-PKR-001",
                "Operational account"
        );
        accounts.saveAndFlush(cashAccount);
    }

    @AfterEach
    void tearDown() {
        if (ownerFixture != null && salespersonFixture != null) {
            jdbc.update("DELETE FROM financial_accounts WHERE business_id IN (?, ?)",
                    ownerFixture.businessId(), salespersonFixture.businessId());
            jdbc.update("DELETE FROM business_memberships WHERE business_id IN (?, ?)",
                    ownerFixture.businessId(), salespersonFixture.businessId());
            jdbc.update("DELETE FROM businesses WHERE id IN (?, ?)",
                    ownerFixture.businessId(), salespersonFixture.businessId());
            jdbc.update("DELETE FROM app_users WHERE id IN (?, ?)",
                    ownerFixture.user().getId(), salespersonFixture.user().getId());
        }
    }

    @Test
    @DisplayName("IT-FX-01: GET /api/finance/fx/currencies returns 200 with full currency metadata")
    void getCurrenciesReturnsAuthoritativeMetadata() throws Exception {
        mvc.perform(get("/api/finance/fx/currencies")
                        .cookie(ownerFixture.session())
                        .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[?(@.code == 'PKR')].minorUnit").value(2))
                .andExpect(jsonPath("$[?(@.code == 'USD')].minorUnit").value(2))
                .andExpect(jsonPath("$[?(@.code == 'JPY')].minorUnit").value(0))
                .andExpect(jsonPath("$[?(@.code == 'KWD')].minorUnit").value(3))
                .andExpect(jsonPath("$[?(@.code == 'BHD')].minorUnit").value(3))
                .andExpect(jsonPath("$[?(@.code == 'OMR')].minorUnit").value(3));
    }

    @Test
    @DisplayName("IT-FX-02: POST /api/finance/fx/preview executes Case A and confirms zero DB mutations")
    void previewExecutionInvariance() throws Exception {
        Integer txCountBefore = jdbc.queryForObject("SELECT count(*) FROM financial_transactions", Integer.class);
        Integer accCountBefore = jdbc.queryForObject("SELECT count(*) FROM financial_accounts", Integer.class);
        Integer movCountBefore = jdbc.queryForObject("SELECT count(*) FROM account_movements", Integer.class);
        BigDecimal balanceBefore = jdbc.queryForObject(
                "SELECT opening_balance FROM financial_accounts WHERE business_id = ?",
                BigDecimal.class,
                ownerFixture.businessId()
        );

        FxPreviewRequest request = new FxPreviewRequest(
                "USD",
                "PKR",
                new BigDecimal("278.50"),
                FxDirection.OUTGOING,
                new BigDecimal("100.00"),
                "USD",
                new BigDecimal("150.00"),
                "PKR",
                BigDecimal.ZERO,
                new BigDecimal("282.00"),
                RateSourceType.MANUAL_USER_RATE
        );

        mvc.perform(post("/api/finance/fx/preview")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency").value("USD"))
                .andExpect(jsonPath("$.quoteCurrency").value("PKR"))
                .andExpect(jsonPath("$.grossSettlementAmount").value(27850.00))
                .andExpect(jsonPath("$.totalSettlementDebited").value(28000.00))
                .andExpect(jsonPath("$.effectiveRate").value(280.000000))
                .andExpect(jsonPath("$.scenarioVariance.impact").value("UNFAVORABLE"))
                .andExpect(jsonPath("$.provenance.calculationType").value("PURE_JAVA_DETERMINISTIC_PREVIEW"));

        // Verify strictly zero DB mutation
        Integer txCountAfter = jdbc.queryForObject("SELECT count(*) FROM financial_transactions", Integer.class);
        Integer accCountAfter = jdbc.queryForObject("SELECT count(*) FROM financial_accounts", Integer.class);
        Integer movCountAfter = jdbc.queryForObject("SELECT count(*) FROM account_movements", Integer.class);
        BigDecimal balanceAfter = jdbc.queryForObject(
                "SELECT opening_balance FROM financial_accounts WHERE business_id = ?",
                BigDecimal.class,
                ownerFixture.businessId()
        );

        assertThat(txCountAfter).isEqualTo(txCountBefore);
        assertThat(accCountAfter).isEqualTo(accCountBefore);
        assertThat(movCountAfter).isEqualTo(movCountBefore);
        assertThat(balanceAfter).isEqualByComparingTo(balanceBefore);
    }

    @Test
    @DisplayName("IT-FX-03: CSRF protection rejects preview without valid token")
    void previewWithoutCsrfRejected() throws Exception {
        FxPreviewRequest request = new FxPreviewRequest(
                "USD",
                "PKR",
                new BigDecimal("278.50"),
                FxDirection.OUTGOING,
                new BigDecimal("100.00"),
                "USD",
                BigDecimal.ZERO,
                "PKR",
                BigDecimal.ZERO,
                null,
                RateSourceType.MANUAL_USER_RATE
        );

        mvc.perform(post("/api/finance/fx/preview")
                        .cookie(ownerFixture.session())
                        .contentType("application/json")
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IT-FX-04: SALESPERSON role is rejected with 403 Forbidden (RBAC boundary)")
    void salespersonDeniedAccess() throws Exception {
        mvc.perform(get("/api/finance/fx/currencies")
                        .cookie(salespersonFixture.session())
                        .contentType("application/json"))
                .andExpect(status().isForbidden());

        FxPreviewRequest request = new FxPreviewRequest(
                "USD",
                "PKR",
                new BigDecimal("278.50"),
                FxDirection.OUTGOING,
                new BigDecimal("100.00"),
                "USD",
                BigDecimal.ZERO,
                "PKR",
                BigDecimal.ZERO,
                null,
                RateSourceType.MANUAL_USER_RATE
        );

        mvc.perform(post("/api/finance/fx/preview")
                        .cookie(salespersonFixture.session())
                        .with(realCsrf(salespersonFixture.session()))
                        .contentType("application/json")
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    private UserBusinessFixture createFixture(String emailPrefix, MembershipRole role) throws Exception {
        AppUser user = new AppUser();
        user.setEmail(emailPrefix + "@test.finsight.local");
        user.setFullName("FX Test User");
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
