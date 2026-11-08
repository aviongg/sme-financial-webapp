package com.app.sme_health_backend.fx;

import com.app.sme_health_backend.accounting.dto.TransferPostRequest;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.fx.dto.*;
import com.app.sme_health_backend.fx.entity.FxInternalTransfer;
import com.app.sme_health_backend.fx.model.FxSettlementStatus;
import com.app.sme_health_backend.fx.repository.FxInternalTransferRepository;
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
class FxTransferPostgreSqlIT extends DisposablePostgres {

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
    private FxInternalTransferRepository fxTransfers;

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
        ownerFixture = createFixture("fx-tr-owner-" + UUID.randomUUID(), MembershipRole.OWNER);
        salespersonFixture = createFixture("fx-tr-sales-" + UUID.randomUUID(), MembershipRole.SALESPERSON);
    }

    @Test
    @DisplayName("IT-FX-TRANSFER-01: Preview cross-currency transfer returns accurate preview without DB mutations")
    void previewTransferInvariance() throws Exception {
        FinancialAccount usdAcc = createAccount(ownerFixture.businessId(), "USD Preview Account", AccountType.BANK, "USD", 2, "1000.00");
        FinancialAccount pkrAcc = createAccount(ownerFixture.businessId(), "PKR Preview Account", AccountType.BANK, "PKR", 2, "50000.00");

        int transfersBefore = jdbc.queryForObject("SELECT count(*) FROM fx_internal_transfers", Integer.class);
        int txBefore = jdbc.queryForObject("SELECT count(*) FROM financial_transactions", Integer.class);
        int movBefore = jdbc.queryForObject("SELECT count(*) FROM account_movements", Integer.class);

        FxTransferPreviewRequest req = new FxTransferPreviewRequest(
                usdAcc.getId(),
                pkrAcc.getId(),
                new BigDecimal("100.00"),
                new BigDecimal("278.50"),
                new BigDecimal("150.00"),
                "DESTINATION_DEDUCTED"
        );

        mvc.perform(post("/api/finance/fx/transfers/preview")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceCurrency").value("USD"))
                .andExpect(jsonPath("$.destinationCurrency").value("PKR"))
                .andExpect(jsonPath("$.sourceAmount").value(100.00))
                .andExpect(jsonPath("$.convertedDestinationPrincipal").value(27850.00))
                .andExpect(jsonPath("$.feeAmount").value(150.00))
                .andExpect(jsonPath("$.netDestinationAmount").value(27700.00))
                .andExpect(jsonPath("$.totalSourceDebit").value(100.00))
                .andExpect(jsonPath("$.effectiveRate").value(277.000000));

        int transfersAfter = jdbc.queryForObject("SELECT count(*) FROM fx_internal_transfers", Integer.class);
        int txAfter = jdbc.queryForObject("SELECT count(*) FROM financial_transactions", Integer.class);
        int movAfter = jdbc.queryForObject("SELECT count(*) FROM account_movements", Integer.class);

        assertThat(transfersAfter).isEqualTo(transfersBefore);
        assertThat(txAfter).isEqualTo(txBefore);
        assertThat(movAfter).isEqualTo(movBefore);
    }

    @Test
    @DisplayName("IT-FX-TRANSFER-02: Mandatory Case A & Case B: Dual-currency posting, fee deduction, and reversal restoration")
    void testCaseA_and_CaseB_DestinationDeductedFeeAndReversal() throws Exception {
        // Setup initial accounts per specification:
        // USD Account: USD 1,000.00
        // PKR Account: PKR 50,000.00
        FinancialAccount usdAcc = createAccount(ownerFixture.businessId(), "HBL USD Account", AccountType.BANK, "USD", 2, "1000.00");
        FinancialAccount pkrAcc = createAccount(ownerFixture.businessId(), "HBL PKR Account", AccountType.BANK, "PKR", 2, "50000.00");

        // 1. EXECUTE CASE A: Transfer USD 100 @ 278.50 with PKR 150 fee (DESTINATION_DEDUCTED)
        String idemKey = "idem-case-a-" + UUID.randomUUID();
        FxTransferPostRequest postReq = new FxTransferPostRequest(
                usdAcc.getId(),
                pkrAcc.getId(),
                "100.00",
                "278.50",
                "150.00",
                "DESTINATION_DEDUCTED",
                "2026-11-08",
                "Case A Transfer"
        );

        var res = mvc.perform(post("/api/finance/fx/transfers")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .header("Idempotency-Key", idemKey)
                        .contentType("application/json")
                        .content(json.writeValueAsString(postReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.sourceAmount").value(100.00))
                .andExpect(jsonPath("$.convertedDestinationPrincipal").value(27850.00))
                .andExpect(jsonPath("$.feeAmount").value(150.00))
                .andExpect(jsonPath("$.netDestinationAmount").value(27700.00))
                .andExpect(jsonPath("$.totalSourceAmount").value(100.00))
                .andReturn();

        FxTransferResponse transferA = json.readValue(res.getResponse().getContentAsString(), FxTransferResponse.class);
        UUID transferId = transferA.id();

        // Verify account balances via backend balance service:
        // USD Account balance must be USD 900.00
        // PKR Account balance must be PKR 77,700.00
        BigDecimal usdBalAfterA = getAccountBalance(usdAcc.getId());
        BigDecimal pkrBalAfterA = getAccountBalance(pkrAcc.getId());
        assertThat(usdBalAfterA).isEqualByComparingTo("900.00");
        assertThat(pkrBalAfterA).isEqualByComparingTo("77700.00");

        // Verify no commercial obligations or sales revenue were created
        int obCount = jdbc.queryForObject("SELECT count(*) FROM financial_obligations WHERE business_id = '" + ownerFixture.businessId() + "'", Integer.class);
        assertThat(obCount).isEqualTo(0);

        // 2. EXECUTE CASE B: Reverse the Case A transfer
        FxTransferReverseRequest revReq = new FxTransferReverseRequest("Reversal requested per Case B specification");
        mvc.perform(post("/api/finance/fx/transfers/" + transferId + "/reverse")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(json.writeValueAsString(revReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVERSED"))
                .andExpect(jsonPath("$.reversalReason").value("Reversal requested per Case B specification"));

        // Verify restored account balances:
        // USD Account balance must be USD 1,000.00
        // PKR Account balance must be PKR 50,000.00
        BigDecimal usdBalAfterB = getAccountBalance(usdAcc.getId());
        BigDecimal pkrBalAfterB = getAccountBalance(pkrAcc.getId());
        assertThat(usdBalAfterB).isEqualByComparingTo("1000.00");
        assertThat(pkrBalAfterB).isEqualByComparingTo("50000.00");

        // Verify second reversal is rejected with 409 Conflict
        mvc.perform(post("/api/finance/fx/transfers/" + transferId + "/reverse")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(json.writeValueAsString(revReq)))
                .andExpect(status().isConflict());

        // Account balances remain unchanged after second reversal attempt
        assertThat(getAccountBalance(usdAcc.getId())).isEqualByComparingTo("1000.00");
        assertThat(getAccountBalance(pkrAcc.getId())).isEqualByComparingTo("50000.00");
    }

    @Test
    @DisplayName("IT-FX-TRANSFER-03: Mandatory Case C: Same-currency PKR transfer regression remains 100% operational")
    void testCaseC_SameCurrencyTransferRegression() throws Exception {
        FinancialAccount pkr1 = createAccount(ownerFixture.businessId(), "PKR Source Account", AccountType.BANK, "PKR", 2, "100000.00");
        FinancialAccount pkr2 = createAccount(ownerFixture.businessId(), "PKR Dest Account", AccountType.BANK, "PKR", 2, "20000.00");

        int fxTransfersBefore = jdbc.queryForObject("SELECT count(*) FROM fx_internal_transfers", Integer.class);

        TransferPostRequest pkrReq = new TransferPostRequest(
                pkr1.getId(),
                pkr2.getId(),
                "10000.00",
                "2026-11-08",
                "Case C same-currency transfer"
        );

        mvc.perform(post("/api/finance/transfers")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .header("Idempotency-Key", "idem-same-curr-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content(json.writeValueAsString(pkrReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventType").value("INTERNAL_TRANSFER"))
                .andExpect(jsonPath("$.amount").value("10000.00"))
                .andExpect(jsonPath("$.currency").value("PKR"));

        // Verify balances: pkr1 = 90,000.00, pkr2 = 30,000.00
        assertThat(getAccountBalance(pkr1.getId())).isEqualByComparingTo("90000.00");
        assertThat(getAccountBalance(pkr2.getId())).isEqualByComparingTo("30000.00");

        // Verify zero FX internal transfers were created
        int fxTransfersAfter = jdbc.queryForObject("SELECT count(*) FROM fx_internal_transfers", Integer.class);
        assertThat(fxTransfersAfter).isEqualTo(fxTransfersBefore);
    }

    @Test
    @DisplayName("IT-FX-TRANSFER-04: Mandatory Case D: Currency precision with KWD (3 decimals) and JPY (0 decimals)")
    void testCaseD_CurrencyPrecision_KWD_and_JPY() throws Exception {
        // 1. KWD Transfer (3 decimal places)
        FinancialAccount kwdAcc = createAccount(ownerFixture.businessId(), "KWD Operating", AccountType.BANK, "KWD", 3, "100.000");
        FinancialAccount pkrAcc = createAccount(ownerFixture.businessId(), "PKR for KWD", AccountType.BANK, "PKR", 2, "0.00");

        // Transfer 10.125 KWD @ 910.250000 PKR/KWD with PKR 50.00 fee
        // Converted: 10.125 * 910.25 = 9216.28125 -> 9216.28 PKR
        // Net: 9216.28 - 50.00 = 9166.28 PKR
        FxTransferPostRequest kwdReq = new FxTransferPostRequest(
                kwdAcc.getId(),
                pkrAcc.getId(),
                "10.125",
                "910.250000",
                "50.00",
                "DESTINATION_DEDUCTED",
                "2026-11-08",
                "KWD 3-decimal transfer"
        );

        var kwdRes = mvc.perform(post("/api/finance/fx/transfers")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .header("Idempotency-Key", "idem-kwd-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content(json.writeValueAsString(kwdReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceAmount").value(10.125))
                .andExpect(jsonPath("$.convertedDestinationPrincipal").value(9216.28))
                .andExpect(jsonPath("$.netDestinationAmount").value(9166.28))
                .andReturn();

        FxTransferResponse transferKwd = json.readValue(kwdRes.getResponse().getContentAsString(), FxTransferResponse.class);

        // KWD balance = 100.000 - 10.125 = 89.875 KWD
        // PKR balance = 9,166.28 PKR
        assertThat(getAccountBalance(kwdAcc.getId())).isEqualByComparingTo("89.875");
        assertThat(getAccountBalance(pkrAcc.getId())).isEqualByComparingTo("9166.28");

        // Reverse KWD transfer
        mvc.perform(post("/api/finance/fx/transfers/" + transferKwd.id() + "/reverse")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content("{\"reason\":\"KWD Reversal\"}"))
                .andExpect(status().isOk());

        // KWD balance restored to exactly 100.000 KWD
        assertThat(getAccountBalance(kwdAcc.getId())).isEqualByComparingTo("100.000");
        assertThat(getAccountBalance(pkrAcc.getId())).isEqualByComparingTo("0.00");

        // 2. JPY Transfer (0 decimal places)
        FinancialAccount jpyAcc = createAccount(ownerFixture.businessId(), "JPY Treasury", AccountType.BANK, "JPY", 0, "100000");
        FinancialAccount usdAcc = createAccount(ownerFixture.businessId(), "USD for JPY", AccountType.BANK, "USD", 2, "0.00");

        // 15,535 JPY @ 0.006437 USD/JPY -> 100.00 USD
        FxTransferPostRequest jpyReq = new FxTransferPostRequest(
                jpyAcc.getId(),
                usdAcc.getId(),
                "15535",
                "0.006437",
                "0.00",
                "DESTINATION_DEDUCTED",
                "2026-11-08",
                "JPY 0-decimal transfer"
        );

        mvc.perform(post("/api/finance/fx/transfers")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .header("Idempotency-Key", "idem-jpy-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content(json.writeValueAsString(jpyReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceAmount").value(15535))
                .andExpect(jsonPath("$.convertedDestinationPrincipal").value(100.00))
                .andExpect(jsonPath("$.netDestinationAmount").value(100.00));

        // JPY balance = 100000 - 15535 = 84465 JPY
        assertThat(getAccountBalance(jpyAcc.getId())).isEqualByComparingTo("84465");
        assertThat(getAccountBalance(usdAcc.getId())).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("IT-FX-TRANSFER-05: Real multi-threaded overlapping concurrency test prevents account overspending")
    void testCaseE_RealConcurrentOverlappingTransfersPreventOverspend() throws Exception {
        // Setup CASH account with USD 100.00.
        // Cash accounts strictly prohibit negative balances per FinSight accounting invariants.
        FinancialAccount cashUsd = createAccount(ownerFixture.businessId(), "USD Retail Cash Box", AccountType.CASH, "USD", 2, "100.00");
        FinancialAccount pkrBank = createAccount(ownerFixture.businessId(), "PKR Bank Account", AccountType.BANK, "PKR", 2, "0.00");

        int concurrencyLevel = 2;
        ExecutorService executor = Executors.newFixedThreadPool(concurrencyLevel);
        CountDownLatch readyLatch = new CountDownLatch(concurrencyLevel);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // Two concurrent transfers of USD 60.00 each against USD 100.00 balance.
        // 60 + 60 = 120 > 100. Exactly one MUST succeed, and exactly one MUST fail.
        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < concurrencyLevel; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await(); // Simultaneous release

                FxTransferPostRequest req = new FxTransferPostRequest(
                        cashUsd.getId(),
                        pkrBank.getId(),
                        "60.00",
                        "278.50",
                        "0.00",
                        "DESTINATION_DEDUCTED",
                        "2026-11-08",
                        "Concurrent transfer " + index
                );

                try {
                    var result = mvc.perform(post("/api/finance/fx/transfers")
                                    .cookie(ownerFixture.session())
                                    .with(realCsrf(ownerFixture.session()))
                                    .header("Idempotency-Key", "idem-race-" + index + "-" + UUID.randomUUID())
                                    .contentType("application/json")
                                    .content(json.writeValueAsString(req)))
                            .andReturn();

                    int status = result.getResponse().getStatus();
                    if (status == 201) {
                        successCount.incrementAndGet();
                    } else if (status == 400 || status == 422) {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
                return null;
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // FIRE simultaneously

        for (Future<Void> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Exactly 1 succeeded, exactly 1 failed
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);

        // Final balance must be exactly USD 40.00, NEVER -USD 20.00
        BigDecimal finalCash = getAccountBalance(cashUsd.getId());
        assertThat(finalCash).isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("IT-FX-TRANSFER-06: Idempotent replay and changed-payload conflict")
    void testCaseF_IdempotencyConflict() throws Exception {
        FinancialAccount usdAcc = createAccount(ownerFixture.businessId(), "USD Idem", AccountType.BANK, "USD", 2, "1000.00");
        FinancialAccount pkrAcc = createAccount(ownerFixture.businessId(), "PKR Idem", AccountType.BANK, "PKR", 2, "0.00");

        String key = "idem-transfer-test-" + UUID.randomUUID();
        FxTransferPostRequest original = new FxTransferPostRequest(
                usdAcc.getId(),
                pkrAcc.getId(),
                "50.00",
                "278.50",
                "0.00",
                "DESTINATION_DEDUCTED",
                "2026-11-08",
                "Original transfer"
        );

        // 1. First execution succeeds (201)
        mvc.perform(post("/api/finance/fx/transfers")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(json.writeValueAsString(original)))
                .andExpect(status().isCreated());

        // 2. Replay with identical payload returns cached 201
        mvc.perform(post("/api/finance/fx/transfers")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(json.writeValueAsString(original)))
                .andExpect(status().isCreated());

        // 3. Replay with altered payload fails with 409 Conflict
        FxTransferPostRequest altered = new FxTransferPostRequest(
                usdAcc.getId(),
                pkrAcc.getId(),
                "75.00", // altered amount
                "278.50",
                "0.00",
                "DESTINATION_DEDUCTED",
                "2026-11-08",
                "Altered transfer"
        );

        mvc.perform(post("/api/finance/fx/transfers")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(json.writeValueAsString(altered)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("IT-FX-TRANSFER-07: RBAC and Tenant Isolation: Salesperson denied (403), Cross-business access denied")
    void testCaseG_RBAC_and_TenantIsolation() throws Exception {
        FinancialAccount usdAcc = createAccount(ownerFixture.businessId(), "USD Auth", AccountType.BANK, "USD", 2, "500.00");
        FinancialAccount pkrAcc = createAccount(ownerFixture.businessId(), "PKR Auth", AccountType.BANK, "PKR", 2, "0.00");

        FxTransferPostRequest req = new FxTransferPostRequest(
                usdAcc.getId(),
                pkrAcc.getId(),
                "50.00",
                "278.50",
                "0.00",
                "DESTINATION_DEDUCTED",
                "2026-11-08",
                "Auth test"
        );

        // Salesperson is denied (403 Forbidden)
        mvc.perform(post("/api/finance/fx/transfers")
                        .cookie(salespersonFixture.session())
                        .with(realCsrf(salespersonFixture.session()))
                        .header("Idempotency-Key", "idem-sales-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        // Other business tries to access owner's accounts -> 404 Not Found
        UserBusinessFixture secondBiz = createFixture("second-owner-" + UUID.randomUUID(), MembershipRole.OWNER);
        mvc.perform(post("/api/finance/fx/transfers")
                        .cookie(secondBiz.session())
                        .with(realCsrf(secondBiz.session()))
                        .header("Idempotency-Key", "idem-cross-biz-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // Helper utilities
    private FinancialAccount createAccount(
            UUID businessId, String name, AccountType type, String currency, int decimals, String openingBalance
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

    private BigDecimal getAccountBalance(UUID accountId) {
        FinancialAccount acc = accounts.findById(accountId).orElseThrow();
        BigDecimal opening = acc.getOpeningBalance();

        BigDecimal increases = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM account_movements WHERE account_id = '" + accountId + "' AND movement_direction = 'INCREASE'",
                BigDecimal.class
        );
        BigDecimal decreases = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM account_movements WHERE account_id = '" + accountId + "' AND movement_direction = 'DECREASE'",
                BigDecimal.class
        );

        return opening.add(increases != null ? increases : BigDecimal.ZERO)
                .subtract(decreases != null ? decreases : BigDecimal.ZERO);
    }

    private UserBusinessFixture createFixture(String emailPrefix, MembershipRole role) throws Exception {
        AppUser user = new AppUser();
        user.setEmail(emailPrefix + "@test.finsight.local");
        user.setFullName("FX Transfer Test User");
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
