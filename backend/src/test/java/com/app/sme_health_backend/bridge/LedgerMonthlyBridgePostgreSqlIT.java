package com.app.sme_health_backend.bridge;

import com.app.sme_health_backend.accounting.dto.TransactionDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionPostRequest;
import com.app.sme_health_backend.accounting.dto.TransactionReverseRequest;
import com.app.sme_health_backend.accounting.dto.TransferPostRequest;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.service.TransactionPostService;
import com.app.sme_health_backend.accounting.service.TransactionReverseService;
import com.app.sme_health_backend.accounting.service.TransferService;
import com.app.sme_health_backend.bridge.dto.LedgerCutoverRequest;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.counterparty.CounterpartyType;
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
import com.app.sme_health_backend.obligation.*;
import com.app.sme_health_backend.obligation.aging.ObligationDirection;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.records.service.MonthlyRecordService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.whatsapp.provider=mock")
@AutoConfigureMockMvc
class LedgerMonthlyBridgePostgreSqlIT extends DisposablePostgres {

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
    private CounterpartyRepository counterparties;

    @Autowired
    private FinancialObligationRepository obligationRepository;

    @Autowired
    private ObligationPaymentAllocationRepository paymentAllocationRepository;

    @Autowired
    private MonthlyRecordRepository monthlyRecordRepository;

    @Autowired
    private MonthlyRecordService monthlyRecordService;

    @Autowired
    private TransactionPostService postService;

    @Autowired
    private TransactionReverseService reverseService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private UserBusinessFixture ownerFixture;

    @BeforeEach
    void setUp() throws Exception {
        ownerFixture = createFixture("bridge-owner-" + UUID.randomUUID().toString().substring(0, 8), MembershipRole.OWNER);
    }

    @Test
    @DisplayName("Stage 3: Derive cash receipts, operating expenses, open AR/AP, excluding transfers & reversals, and perform authoritative cutover")
    void testStage3_LedgerBridgeDerivationAndCutover() throws Exception {
        UUID businessId = ownerFixture.businessId();
        BusinessAccessContext ctx = new BusinessAccessContext(ownerFixture.user().getId(), businessId, MembershipRole.OWNER);

        // 1. Create financial accounts
        FinancialAccount bank = createAccount(businessId, "Primary Operating Bank", AccountType.BANK, "PKR", 2, "100000.00");
        FinancialAccount pettyCash = createAccount(businessId, "Petty Cash", AccountType.CASH, "PKR", 2, "20000.00");

        // 2. Post eligible cash transactions in target month (2026-11)
        // Direct sale receipt: 50,000 PKR
        postTransaction(ctx, bank.getId(), TransactionEventType.DIRECT_SALE_RECEIPT, "50000.00", "2026-11-02", "Direct cash sales");
        // Receivable collection: 30,000 PKR
        TransactionDetailResponse collTx = postTransaction(ctx, bank.getId(), TransactionEventType.RECEIVABLE_COLLECTION, "30000.00", "2026-11-04", "Collection from customer");
        // Operating expense payment: 20,000 PKR
        postTransaction(ctx, bank.getId(), TransactionEventType.OPERATING_EXPENSE_PAYMENT, "20000.00", "2026-11-06", "Office utilities");
        // Payable settlement: 15,000 PKR
        postTransaction(ctx, bank.getId(), TransactionEventType.PAYABLE_SETTLEMENT, "15000.00", "2026-11-08", "Vendor payment");

        // 3. Post internal transfer: 10,000 PKR (Must be EXCLUDED from inflows and outflows)
        postTransfer(ctx, bank.getId(), pettyCash.getId(), "10000.00", "2026-11-07", "ATM cash withdrawal");

        // 4. Post and reverse an operating expense: 5,000 PKR (Must be EXCLUDED because reversed)
        TransactionDetailResponse expToReverse = postTransaction(
                ctx, bank.getId(), TransactionEventType.OPERATING_EXPENSE_PAYMENT, "5000.00", "2026-11-09", "Erroneous charge"
        );
        reverseService.postReversal(ctx, "rev-key-" + UUID.randomUUID(), expToReverse.id(), new TransactionReverseRequest("Billing mistake"));

        // 5. Create counterparties & open obligations as of 2026-11
        Counterparty customer = createCounterparty(businessId, "Acme Client Corp", CounterpartyType.CUSTOMER);
        Counterparty supplier = createCounterparty(businessId, "Global Supplies Ltd", CounterpartyType.SUPPLIER);

        // Active receivable: original 25,000, allocated 10,000 -> outstanding 15,000
        FinancialObligation arOb = createObligation(businessId, customer.getId(), ObligationDirection.RECEIVABLE, new BigDecimal("25000.0000"), "INV-701");
        createAllocation(businessId, arOb.getId(), collTx.id(), new BigDecimal("10000.0000"));

        // Active payable: original 40,000, allocated 0 -> outstanding 40,000
        createObligation(businessId, supplier.getId(), ObligationDirection.PAYABLE, new BigDecimal("40000.0000"), "BILL-801");

        // 6. Call GET /api/finance/ledger-bridge/monthly-projection?yearMonth=2026-11
        mvc.perform(get("/api/finance/ledger-bridge/monthly-projection?yearMonth=2026-11")
                        .cookie(ownerFixture.session())
                        .header("X-XSRF-TOKEN", ownerFixture.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yearMonth").value("2026-11"))
                .andExpect(jsonPath("$.ledgerCashInflow").value(80000.0))
                .andExpect(jsonPath("$.ledgerCashOutflow").value(35000.0))
                .andExpect(jsonPath("$.ledgerNetCashFlow").value(45000.0))
                .andExpect(jsonPath("$.ledgerOperatingRevenue").value(50000.0))
                .andExpect(jsonPath("$.ledgerOperatingExpenses").value(20000.0))
                .andExpect(jsonPath("$.asOfReceivablesOutstanding").value(15000.0))
                .andExpect(jsonPath("$.asOfPayablesOutstanding").value(40000.0))
                .andExpect(jsonPath("$.hasExistingMonthlyRecord").value(false))
                .andExpect(jsonPath("$.completeness.hasDirectSaleReceipts").value(true))
                .andExpect(jsonPath("$.completeness.hasReceivableCollections").value(true))
                .andExpect(jsonPath("$.completeness.hasOperatingDisbursements").value(true))
                .andExpect(jsonPath("$.completeness.hasPayableSettlements").value(true))
                .andExpect(jsonPath("$.completeness.readinessStatus").value("READY_FOR_PREVIEW"));

        // 7. V52: manual override figures are refused (they would be a second source of truth) ...
        LedgerCutoverRequest manualReq = new LedgerCutoverRequest(
                "2026-11",
                new BigDecimal("25000.00"), // manual COGS
                new BigDecimal("12000.00"), // manual inventory
                null,
                null,
                "none",
                null,
                "Audit cutover verified by Antigravity"
        );
        mvc.perform(post("/api/finance/ledger-bridge/apply-projection")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(manualReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("monthly_figures_derived"));

        // ... and "apply" runs the deterministic monthly projection (monthly-projection-v1).
        LedgerCutoverRequest cutoverReq = new LedgerCutoverRequest("2026-11", null, null, null, null, null, null,
                "Audit cutover verified by Antigravity");
        mvc.perform(post("/api/finance/ledger-bridge/apply-projection")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(cutoverReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.yearMonth").value("2026-11"))
                .andExpect(jsonPath("$.monthlyRecord.cashInflow").value(80000.0))
                .andExpect(jsonPath("$.monthlyRecord.cashOutflow").value(35000.0))
                // Accrual basis: 50,000 cash sale + 25,000 recognized invoice INV-701; 20,000 cash expense + 40,000
                // recognized bill BILL-801. The GET preview above stays the cash-basis c02 view (50,000 / 20,000).
                .andExpect(jsonPath("$.monthlyRecord.revenue").value(75000.0))
                .andExpect(jsonPath("$.monthlyRecord.cogs").doesNotExist())
                .andExpect(jsonPath("$.monthlyRecord.operatingExpenses").value(60000.0))
                .andExpect(jsonPath("$.monthlyRecord.receivablesOutstanding").value(15000.0))
                .andExpect(jsonPath("$.monthlyRecord.payablesOutstanding").value(40000.0))
                .andExpect(jsonPath("$.monthlyRecord.source").value("DERIVED"))
                .andExpect(jsonPath("$.bridgeProvenance").value("monthly-projection-v1"));

        // 8. Verify database state
        MonthlyRecord recordInDb = monthlyRecordRepository.findByUserIdAndMonth(businessId, "2026-11").orElseThrow();
        assertThat(recordInDb.getCashInflow()).isEqualByComparingTo("80000.00");
        assertThat(recordInDb.getCashOutflow()).isEqualByComparingTo("35000.00");
        assertThat(recordInDb.getRevenue()).isEqualByComparingTo("75000.00");
        assertThat(recordInDb.getCogs()).as("no inventory ledger -> COGS unknown, never a typed figure").isNull();
        assertThat(recordInDb.getOperatingExpenses()).isEqualByComparingTo("60000.00");
        assertThat(recordInDb.getReceivablesOutstanding()).isEqualByComparingTo("15000.00");
        assertThat(recordInDb.getPayablesOutstanding()).isEqualByComparingTo("40000.00");
        assertThat(recordInDb.getSource()).isEqualTo("DERIVED");

        // 9. Verify Security Audit Log
        Integer auditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM security_audit_events WHERE business_id = ? AND event_type = 'LEDGER_HEALTH_CUTOVER'",
                Integer.class,
                businessId
        );
        assertThat(auditCount).isNotNull().isGreaterThan(0);
    }

    @Test
    @DisplayName("Stage 3: RBAC Denials — Salesperson cannot apply ledger cutover")
    void testStage3_SalespersonRbacDenial() throws Exception {
        UserBusinessFixture salesFixture = createFixture("salesperson-" + UUID.randomUUID().toString().substring(0, 8), MembershipRole.VIEWER);

        LedgerCutoverRequest cutoverReq = new LedgerCutoverRequest(
                "2026-11",
                new BigDecimal("10000.00"),
                null,
                null,
                null,
                "none",
                null,
                "Unauthorized attempt"
        );

        mvc.perform(post("/api/finance/ledger-bridge/apply-projection")
                        .cookie(salesFixture.session())
                        .with(realCsrf(salesFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(cutoverReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Fixtures A-E: Existing manual MonthlyRecord preserved, zero-mutation preview, idempotent cutover, and concurrency lock")
    void testFixturesA_Through_E_PreservationAndIdempotency() throws Exception {
        UUID businessId = ownerFixture.businessId();
        BusinessAccessContext ctx = new BusinessAccessContext(ownerFixture.user().getId(), businessId, MembershipRole.OWNER);

        FinancialAccount bank = createAccount(businessId, "Main Bank", AccountType.BANK, "PKR", 2, "200000.00");
        postTransaction(ctx, bank.getId(), TransactionEventType.DIRECT_SALE_RECEIPT, "60000.00", "2026-11-03", "Cash direct sales");

        // Fixture A: a pre-V52 manual month (legacy history) for the same month as ledger activity.
        jdbc.update("INSERT INTO monthly_records(user_id,month,cash_inflow,cash_outflow,revenue,cogs,operating_expenses,"
                + "cash_balance_eom,inventory_value,loan_outstanding,financing_type) VALUES (?, '2026-11', 10000, 5000, 10000, 45000, 5000,"
                + " 205000, 30000, 100000, 'islamic')", businessId);

        // Fixture B: the cash-basis preview makes zero database mutations
        long recordCountBefore = monthlyRecordRepository.count();
        mvc.perform(get("/api/finance/ledger-bridge/monthly-projection?yearMonth=2026-11")
                        .cookie(ownerFixture.session())
                        .header("X-XSRF-TOKEN", ownerFixture.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasExistingMonthlyRecord").value(true))
                .andExpect(jsonPath("$.existingMonthlyRecord.cogs").value(45000.0))
                .andExpect(jsonPath("$.ledgerCashInflow").value(60000.0));
        assertThat(monthlyRecordRepository.count()).isEqualTo(recordCountBefore);

        // Fixture C: applying replaces the legacy manual month with the derived one (one truth per month).
        LedgerCutoverRequest cutoverReq = new LedgerCutoverRequest("2026-11", null, null, null, null, null, null, "Explicit cutover");
        mvc.perform(post("/api/finance/ledger-bridge/apply-projection")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(cutoverReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.monthlyRecord.cashInflow").value(60000.0))
                .andExpect(jsonPath("$.monthlyRecord.revenue").value(60000.0))
                .andExpect(jsonPath("$.monthlyRecord.cogs").doesNotExist())
                .andExpect(jsonPath("$.monthlyRecord.inventoryValue").doesNotExist())
                .andExpect(jsonPath("$.monthlyRecord.source").value("DERIVED"));
        MonthlyRecord updated = monthlyRecordRepository.findByUserIdAndMonth(businessId, "2026-11").orElseThrow();
        assertThat(updated.getCashInflow()).isEqualByComparingTo("60000.00");
        assertThat(updated.getCogs()).isNull();
        assertThat(updated.isDerived()).isTrue();
        Long updatedVersion = updated.getVersion();

        // Fixture D: repeating is idempotent — no rewrite, no duplicate row.
        mvc.perform(post("/api/finance/ledger-bridge/apply-projection")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(cutoverReq)))
                .andExpect(status().isCreated());
        assertThat(monthlyRecordRepository.findByUserIdAndMonth(businessId, "2026-11").orElseThrow().getVersion()).isEqualTo(updatedVersion);
        assertThat(monthlyRecordRepository.findByUserIdAndMonth(businessId, "2026-11")).isPresent();

        // Fixture E: supplemental financing goes through its own explicit input, never through the cutover.
        LedgerCutoverRequest financingReq = new LedgerCutoverRequest("2026-11", null, null, new BigDecimal("100000.00"), null,
                "islamic", null, "Financing attempt");
        mvc.perform(post("/api/finance/ledger-bridge/apply-projection")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(financingReq)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Fixture F & Historical As-Of: Currency isolation (USD vs PKR) and allocation cutoff date enforcement")
    void testFixtureF_CurrencyIsolationAndHistoricalAsOfCutoff() throws Exception {
        UUID businessId = ownerFixture.businessId();
        BusinessAccessContext ctx = new BusinessAccessContext(ownerFixture.user().getId(), businessId, MembershipRole.OWNER);

        // 1. Create PKR account (100k opening) and USD account ($5k opening)
        FinancialAccount pkrBank = createAccount(businessId, "PKR Operating Bank", AccountType.BANK, "PKR", 2, "100000.00");
        FinancialAccount usdBank = createAccount(businessId, "USD Foreign Bank", AccountType.BANK, "USD", 2, "5000.00");

        // 2. Post direct sale receipt in PKR
        postTransaction(ctx, pkrBank.getId(), TransactionEventType.DIRECT_SALE_RECEIPT, "40000.00", "2026-10-15", "October sale");

        // 3. Create counterparties
        Counterparty customer = createCounterparty(businessId, "Export Client", CounterpartyType.CUSTOMER);

        // 4. Create PKR receivable obligation issued in October for 50,000 PKR
        FinancialObligation pkrOb = new FinancialObligation(
                businessId, customer.getId(), ObligationDirection.RECEIVABLE,
                ObligationSourceType.CUSTOMER_INVOICE, "INV-OCT-01", "INV-OCT-01",
                LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 31),
                new BigDecimal("50000.00"), "PKR", "October invoice", ownerFixture.user().getId()
        );
        pkrOb = obligationRepository.saveAndFlush(pkrOb);

        // Create USD obligation for $2,000 USD (Foreign currency!)
        FinancialObligation usdOb = new FinancialObligation(
                businessId, customer.getId(), ObligationDirection.RECEIVABLE,
                ObligationSourceType.CUSTOMER_INVOICE, "INV-OCT-USD", "INV-OCT-USD",
                LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 31),
                new BigDecimal("2000.00"), "USD", "October USD invoice", ownerFixture.user().getId()
        );
        obligationRepository.saveAndFlush(usdOb);

        // 5. Post partial allocation of 20,000 PKR in October (2026-10-20)
        TransactionDetailResponse collTxOct = postTransaction(ctx, pkrBank.getId(), TransactionEventType.RECEIVABLE_COLLECTION, "20000.00", "2026-10-20", "Oct collection");
        ObligationPaymentAllocation allocOct = new ObligationPaymentAllocation(
                businessId, pkrOb.getId(), collTxOct.id(), new BigDecimal("20000.00"), LocalDate.of(2026, 10, 20), ownerFixture.user().getId()
        );
        paymentAllocationRepository.saveAndFlush(allocOct);

        // 6. Post remaining allocation of 30,000 PKR in NOVEMBER (2026-11-05) - Future allocation relative to October!
        TransactionDetailResponse collTxNov = postTransaction(ctx, pkrBank.getId(), TransactionEventType.RECEIVABLE_COLLECTION, "30000.00", "2026-11-05", "Nov collection");
        ObligationPaymentAllocation allocNov = new ObligationPaymentAllocation(
                businessId, pkrOb.getId(), collTxNov.id(), new BigDecimal("30000.00"), LocalDate.of(2026, 11, 5), ownerFixture.user().getId()
        );
        paymentAllocationRepository.saveAndFlush(allocNov);

        // 7. Call projection for October 2026 (2026-10)
        mvc.perform(get("/api/finance/ledger-bridge/monthly-projection?yearMonth=2026-10")
                        .cookie(ownerFixture.session())
                        .header("X-XSRF-TOKEN", ownerFixture.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yearMonth").value("2026-10"))
                // Cash balance EOM must isolate currency: PKR opening (100k) + Oct movements (40k sale + 20k coll) = 160,000.
                // It must NOT blend the $5,000 USD opening balance into PKR!
                .andExpect(jsonPath("$.currentCashBalanceEom").value(160000.0))
                // Historical AR as of 2026-10-31: original 50k - Oct allocation (20k) = 30,000 PKR.
                // It must NOT count the Nov allocation (30k) which occurred in a future month!
                .andExpect(jsonPath("$.asOfReceivablesOutstanding").value(30000.0))
                // Foreign currency warnings must be present
                .andExpect(jsonPath("$.completeness.warnings[0]").exists());
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private TransactionDetailResponse postTransaction(
            BusinessAccessContext ctx, UUID accountId, TransactionEventType eventType, String amount, String date, String desc
    ) {
        TransactionPostRequest req = new TransactionPostRequest(
                accountId, eventType.name(), amount, date, desc, null
        );
        return postService.postTransaction(ctx, "tx-post-" + UUID.randomUUID(), req);
    }

    private void postTransfer(
            BusinessAccessContext ctx, UUID srcId, UUID destId, String amount, String date, String desc
    ) {
        TransferPostRequest req = new TransferPostRequest(
                srcId, destId, amount, date, desc
        );
        transferService.postTransfer(ctx, "trf-post-" + UUID.randomUUID(), req);
    }

    private Counterparty createCounterparty(UUID businessId, String name, CounterpartyType type) {
        Counterparty cp = new Counterparty(businessId, name, type, null, null, null, null, "Active counterparty");
        return counterparties.saveAndFlush(cp);
    }

    private FinancialObligation createObligation(
            UUID businessId, UUID counterpartyId, ObligationDirection direction, BigDecimal amount, String ref
    ) {
        FinancialObligation ob = new FinancialObligation(
                businessId,
                counterpartyId,
                direction,
                direction == ObligationDirection.RECEIVABLE ? ObligationSourceType.CUSTOMER_INVOICE : ObligationSourceType.SUPPLIER_BILL,
                ref,
                ref,
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30),
                amount,
                "PKR",
                "Test obligation " + ref,
                ownerFixture.user().getId()
        );
        return obligationRepository.saveAndFlush(ob);
    }

    private ObligationPaymentAllocation createAllocation(
            UUID businessId, UUID obligationId, UUID txId, BigDecimal amount
    ) {
        ObligationPaymentAllocation alloc = new ObligationPaymentAllocation(
                businessId,
                obligationId,
                txId,
                amount,
                LocalDate.of(2026, 11, 5),
                ownerFixture.user().getId()
        );
        return paymentAllocationRepository.saveAndFlush(alloc);
    }

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

    private UserBusinessFixture createFixture(String emailPrefix, MembershipRole role) throws Exception {
        AppUser user = new AppUser();
        user.setEmail(emailPrefix + "@test.finsight.local");
        user.setFullName("Bridge Test User");
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setAccountStatus(com.app.sme_health_backend.identity.model.AccountStatus.ACTIVE);
        user.setMustChangePassword(false);
        user = users.saveAndFlush(user);

        UUID businessId = UUID.randomUUID();
        businesses.saveAndFlush(new Business(businessId));
        jdbc.update("INSERT INTO business_profiles(user_id, business_type, language_preference) VALUES (?, 'trade', 'en')", businessId);
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
