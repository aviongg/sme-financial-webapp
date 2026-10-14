package com.app.sme_health_backend.accounting;

import com.app.sme_health_backend.accounting.dto.AccountCreateRequest;
import com.app.sme_health_backend.accounting.dto.AccountDetailResponse;
import com.app.sme_health_backend.accounting.dto.AccountListResponse;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialIdempotencyRecord;
import com.app.sme_health_backend.accounting.exception.AccountValidationException;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.BalanceStatus;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialIdempotencyRepository;
import com.app.sme_health_backend.accounting.service.AccountCreateService;
import com.app.sme_health_backend.accounting.service.AccountReadService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.dto.CreateBusinessRequest;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.service.BusinessService;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FinancialAccountPostgreSqlIT extends DisposablePostgres {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FinancialAccountRepository accountRepository;

    @Autowired
    private AccountReadService accountReadService;

    @Autowired
    private AccountCreateService accountCreateService;

    @Autowired
    private FinancialIdempotencyRepository idempotencyRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private BusinessService businessService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userA;
    private UUID businessA;

    private UUID userB;
    private UUID businessB;

    @BeforeEach
    void setUp() {
        AppUser uA = new AppUser();
        uA.setEmail("test-acct-a-" + UUID.randomUUID() + "@example.test");
        uA.setFullName("User A");
        uA.setPasswordHash("hash-a");
        userA = userRepository.save(uA).getId();
        businessA = businessService.createBusiness(
                new CreateBusinessRequest("Acct Test Business A", "retail", "en", null, false, null, null, null),
                userA
        ).businessId();

        AppUser uB = new AppUser();
        uB.setEmail("test-acct-b-" + UUID.randomUUID() + "@example.test");
        uB.setFullName("User B");
        uB.setPasswordHash("hash-b");
        userB = userRepository.save(uB).getId();
        businessB = businessService.createBusiness(
                new CreateBusinessRequest("Acct Test Business B", "services", "en", null, false, null, null, null),
                userB
        ).businessId();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM financial_idempotency_records WHERE business_id IN (?, ?)", businessA, businessB);
        jdbcTemplate.update("DELETE FROM financial_accounts WHERE business_id IN (?, ?)", businessA, businessB);
        jdbcTemplate.update("DELETE FROM business_memberships WHERE business_id IN (?, ?)", businessA, businessB);
        jdbcTemplate.update("DELETE FROM business_profiles WHERE user_id IN (?, ?)", businessA, businessB);
        jdbcTemplate.update("DELETE FROM businesses WHERE id IN (?, ?)", businessA, businessB);
        jdbcTemplate.update("DELETE FROM app_users WHERE id IN (?, ?)", userA, userB);
    }

    @Test
    @DisplayName("V18 schema persists and reads financial account with correct metadata and masking")
    void persistsAndReadsAccountViaV18Schema() {
        FinancialAccount account = new FinancialAccount(
                businessA,
                "Meezan Main Operations",
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("100000.00"),
                LocalDate.of(2026, 10, 1),
                AccountStatus.ACTIVE,
                "Meezan Bank Ltd",
                "PK12MEZN0001234567894821",
                "Primary operational disbursements"
        );
        FinancialAccount saved = accountRepository.save(account);
        assertNotNull(saved.getId());

        AccountDetailResponse detail = accountReadService.getAccountDetail(businessA, saved.getId());

        assertEquals(saved.getId(), detail.id());
        assertEquals("Meezan Main Operations", detail.accountName());
        assertEquals(AccountType.BANK, detail.accountType());
        assertEquals("PKR", detail.currency());
        assertEquals(2, detail.currencyDecimals());
        assertEquals("100000.00", detail.openingBalance());
        assertEquals("100000.00", detail.currentBalance());
        assertEquals(BalanceStatus.AVAILABLE, detail.balanceStatus());
        assertNull(detail.balanceUnavailableReason());
        assertEquals("Meezan Bank Ltd", detail.institutionName());
        assertEquals("****4821", detail.accountIdentifier()); // Masked!
        assertEquals("Primary operational disbursements", detail.notes());
        assertEquals(AccountStatus.ACTIVE, detail.status());
        assertNotNull(detail.observationAt());
        assertNotNull(detail.effectiveCutoffDate());
    }

    @Test
    @DisplayName("Strict tenant isolation: Cross-business detail returns 404, list returns empty")
    void strictTenantIsolation() {
        FinancialAccount accountA = new FinancialAccount(
                businessA,
                "Business A Vault",
                AccountType.CASH,
                "PKR",
                2,
                new BigDecimal("50000.00"),
                LocalDate.of(2026, 10, 1),
                AccountStatus.ACTIVE,
                null,
                null,
                "Confidential A"
        );
        FinancialAccount savedA = accountRepository.save(accountA);

        // Business B attempting to read Business A's account returns non-disclosing 404
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> accountReadService.getAccountDetail(businessB, savedA.getId()));
        assertEquals("Financial account not found", ex.getMessage());

        // Business B listing should have 0 items
        AccountListResponse listB = accountReadService.listAccounts(
                businessB, AccountStatus.ACTIVE, null, null, 0, 50
        );
        assertEquals(0, listB.totalElements());
        assertEquals(0, listB.totalPages());
        assertTrue(listB.items().isEmpty());
    }

    @Test
    @DisplayName("Enforces unique constraint on (business_id, account_name)")
    void uniqueAccountNamePerBusiness() {
        FinancialAccount a1 = new FinancialAccount(
                businessA,
                "Operating Account",
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("10000.00"),
                LocalDate.of(2026, 10, 1),
                AccountStatus.ACTIVE,
                null,
                null,
                null
        );
        accountRepository.saveAndFlush(a1);

        FinancialAccount a2Duplicate = new FinancialAccount(
                businessA,
                "Operating Account", // Same name for same business
                AccountType.CASH,
                "PKR",
                2,
                new BigDecimal("5000.00"),
                LocalDate.of(2026, 10, 1),
                AccountStatus.ACTIVE,
                null,
                null,
                null
        );
        assertThrows(DataAccessException.class, () -> accountRepository.saveAndFlush(a2Duplicate));

        // Same account name for different business succeeds
        FinancialAccount b1 = new FinancialAccount(
                businessB,
                "Operating Account",
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("20000.00"),
                LocalDate.of(2026, 10, 1),
                AccountStatus.ACTIVE,
                null,
                null,
                null
        );
        assertDoesNotThrow(() -> accountRepository.saveAndFlush(b1));
    }

    @Test
    @DisplayName("Enforces foreign key constraint to businesses(id)")
    void foreignKeyToBusinessesEnforced() {
        UUID nonExistentBusiness = UUID.randomUUID();
        FinancialAccount account = new FinancialAccount(
                nonExistentBusiness,
                "Ghost Account",
                AccountType.CASH,
                "PKR",
                2,
                new BigDecimal("1000.00"),
                LocalDate.of(2026, 10, 1),
                AccountStatus.ACTIVE,
                null,
                null,
                null
        );
        assertThrows(DataAccessException.class, () -> accountRepository.saveAndFlush(account));
    }

    @Test
    @DisplayName("Filtering and deterministic ordering by createdAt ASC, id ASC")
    void filteringAndDeterministicOrdering() throws Exception {
        FinancialAccount a1 = new FinancialAccount(
                businessA,
                "Account Alpha",
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("10000.00"),
                LocalDate.of(2026, 10, 1),
                AccountStatus.ACTIVE,
                null,
                null,
                null
        );
        a1.setCreatedAt(Instant.now().minusSeconds(100));
        accountRepository.save(a1);

        FinancialAccount a2 = new FinancialAccount(
                businessA,
                "Account Beta",
                AccountType.CASH,
                "USD",
                2,
                new BigDecimal("2000.00"),
                LocalDate.of(2026, 10, 1),
                AccountStatus.ACTIVE,
                null,
                null,
                null
        );
        a2.setCreatedAt(Instant.now().minusSeconds(50));
        accountRepository.save(a2);

        FinancialAccount a3 = new FinancialAccount(
                businessA,
                "Account Gamma",
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("30000.00"),
                LocalDate.of(2026, 10, 1),
                AccountStatus.INACTIVE,
                null,
                null,
                null
        );
        a3.setCreatedAt(Instant.now().minusSeconds(10));
        accountRepository.save(a3);

        // Filter status=ACTIVE
        AccountListResponse activeList = accountReadService.listAccounts(
                businessA, AccountStatus.ACTIVE, null, null, 0, 50
        );
        assertEquals(2, activeList.totalElements());
        assertEquals("Account Alpha", activeList.items().get(0).accountName());
        assertEquals("Account Beta", activeList.items().get(1).accountName());

        // Filter accountType=BANK
        AccountListResponse bankList = accountReadService.listAccounts(
                businessA, null, AccountType.BANK, null, 0, 50
        );
        assertEquals(2, bankList.totalElements());
        assertEquals("Account Alpha", bankList.items().get(0).accountName());
        assertEquals("Account Gamma", bankList.items().get(1).accountName());

        // Filter currency=usd (case-insensitive)
        AccountListResponse usdList = accountReadService.listAccounts(
                businessA, null, null, "USD", 0, 50
        );
        assertEquals(1, usdList.totalElements());
        assertEquals("Account Beta", usdList.items().get(0).accountName());
    }

    @Test
    @DisplayName("Runtime role finsight_app has SELECT, INSERT, UPDATE, DELETE privileges on financial_accounts")
    void runtimeRolePrivilegesOnFinancialAccounts() throws Exception {
        try (Connection connection = DriverManager.getConnection(URL, "finsight_app", PASSWORD);
             Statement statement = connection.createStatement()) {

            // Check current user is finsight_app
            try (ResultSet rs = statement.executeQuery("SELECT current_user")) {
                assertTrue(rs.next());
                assertEquals("finsight_app", rs.getString(1));
            }

            // SELECT query
            try (ResultSet rs = statement.executeQuery("SELECT count(*) FROM financial_accounts")) {
                assertTrue(rs.next());
            }

            // INSERT query
            UUID tempId = UUID.randomUUID();
            int inserted = statement.executeUpdate(
                    "INSERT INTO financial_accounts (id, business_id, account_name, account_type, currency, currency_decimals, opening_balance, opening_date) " +
                            "VALUES ('" + tempId + "', '" + businessA + "', 'Privilege Test Acct', 'CASH', 'PKR', 2, 100.00, '2026-10-01')"
            );
            assertEquals(1, inserted);

            // UPDATE query
            int updated = statement.executeUpdate(
                    "UPDATE financial_accounts SET account_name = 'Privilege Updated' WHERE id = '" + tempId + "'"
            );
            assertEquals(1, updated);

            // DELETE query
            int deleted = statement.executeUpdate(
                    "DELETE FROM financial_accounts WHERE id = '" + tempId + "'"
            );
            assertEquals(1, deleted);
        }
    }

    @Test
    @DisplayName("Persisted PKR account with defective scale 3 returns UNAVAILABLE with UNSUPPORTED_CURRENCY")
    void persistedDefectiveMetadataReturnsUnavailableWithUnsupportedCurrency() {
        FinancialAccount defective = new FinancialAccount(
                businessA,
                "Live Invalid Metadata Replica",
                AccountType.BANK,
                "PKR",
                3, // Defective scale: PKR is scale 2
                new BigDecimal("100.00"),
                LocalDate.of(2026, 10, 1),
                AccountStatus.INACTIVE,
                "Synthetic institution",
                "SYNTHETIC-1234567890",
                "Synthetic integration fixture only"
        );
        FinancialAccount saved = accountRepository.saveAndFlush(defective);

        // Detail endpoint
        AccountDetailResponse detail = accountReadService.getAccountDetail(businessA, saved.getId());
        assertEquals("PKR", detail.currency());
        assertEquals(3, detail.currencyDecimals());
        assertNull(detail.openingBalance());
        assertNull(detail.currentBalance());
        assertEquals(BalanceStatus.UNAVAILABLE, detail.balanceStatus());
        assertEquals("UNSUPPORTED_CURRENCY", detail.balanceUnavailableReason());

        // List endpoint
        AccountListResponse list = accountReadService.listAccounts(
                businessA, AccountStatus.INACTIVE, null, null, 0, 50
        );
        assertEquals(1, list.totalElements());
        assertEquals(saved.getId(), list.items().get(0).id());
        assertEquals(3, list.items().get(0).currencyDecimals());
        assertNull(list.items().get(0).openingBalance());
        assertNull(list.items().get(0).currentBalance());
        assertEquals(BalanceStatus.UNAVAILABLE, list.items().get(0).balanceStatus());
        assertEquals("UNSUPPORTED_CURRENCY", list.items().get(0).balanceUnavailableReason());
    }

    @Test
    @DisplayName("PostgreSQL preserves excess-precision 1.0001 and service reports PRECISION_OVERFLOW")
    void persistedExcessPrecisionPreservedAndEvaluatedAsPrecisionOverflow() {
        FinancialAccount precisionAccount = new FinancialAccount(
                businessA,
                "Live Invalid Precision Replica",
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("1.0001"), // 4 decimals
                LocalDate.of(2026, 10, 1),
                AccountStatus.INACTIVE,
                "Synthetic institution",
                "SYNTHETIC-1234567890",
                "Synthetic integration fixture only"
        );
        FinancialAccount saved = accountRepository.saveAndFlush(precisionAccount);

        // Verify PostgreSQL NUMERIC(19,4) actually preserved 1.0001 without rounding
        BigDecimal persistedRaw = jdbcTemplate.queryForObject(
                "SELECT opening_balance FROM financial_accounts WHERE id = ?",
                BigDecimal.class,
                saved.getId()
        );
        assertNotNull(persistedRaw);
        assertEquals(new BigDecimal("1.0001"), persistedRaw);

        // Read service evaluation under Option A: openingBalance=null when precision overflows
        AccountDetailResponse detail = accountReadService.getAccountDetail(businessA, saved.getId());
        assertNull(detail.openingBalance());
        assertNull(detail.currentBalance());
        assertEquals(BalanceStatus.UNAVAILABLE, detail.balanceStatus());
        assertEquals("PRECISION_OVERFLOW", detail.balanceUnavailableReason());
    }

    @Test
    @DisplayName("PostgreSQL preserves capacity-overflow opening balance and service reports CAPACITY_OVERFLOW")
    void persistedCapacityOverflowEvaluatedAsCapacityOverflow() {
        FinancialAccount capacityAccount = new FinancialAccount(
                businessA,
                "Capacity Overflow Replica",
                AccountType.BANK,
                "PKR",
                2,
                new BigDecimal("1000000000000.00"), // 13 integer digits (10^12)
                LocalDate.of(2026, 10, 1),
                AccountStatus.INACTIVE,
                null,
                null,
                null
        );
        FinancialAccount saved = accountRepository.saveAndFlush(capacityAccount);

        AccountDetailResponse detail = accountReadService.getAccountDetail(businessA, saved.getId());
        assertNull(detail.openingBalance());
        assertNull(detail.currentBalance());
        assertEquals(BalanceStatus.UNAVAILABLE, detail.balanceStatus());
        assertEquals("CAPACITY_OVERFLOW", detail.balanceUnavailableReason());
    }

    @Test
    @DisplayName("Exact zero-, two-, three-decimal, negative, and future-opening values in PostgreSQL")
    void persistedExactZeroTwoThreeDecimalsAndNegativeValues() {
        LocalDate todayKarachi = LocalDate.now(AccountReadService.BUSINESS_ZONE);

        // 1. JPY scale 0
        FinancialAccount jpy = new FinancialAccount(
                businessA, "Live JPY Wallet Replica", AccountType.WALLET, "JPY", 0,
                new BigDecimal("999999999999"), todayKarachi.minusDays(1), AccountStatus.ACTIVE,
                "Synthetic institution", "SYNTHETIC-1234567890", null
        );
        // 2. BHD scale 3
        FinancialAccount bhd = new FinancialAccount(
                businessA, "Live BHD Bank Replica", AccountType.BANK, "BHD", 3,
                new BigDecimal("1250.375"), todayKarachi.minusDays(1), AccountStatus.ACTIVE,
                "Synthetic institution", "SYNTHETIC-1234567890", null
        );
        // 3. Negative PKR scale 2
        FinancialAccount negPkr = new FinancialAccount(
                businessA, "Live PKR Bank Replica", AccountType.BANK, "PKR", 2,
                new BigDecimal("-2500.50"), todayKarachi.minusDays(1), AccountStatus.ACTIVE,
                "Synthetic institution", "SYNTHETIC-1234567890", null
        );
        // 4. Future opening date PKR scale 2
        FinancialAccount futurePkr = new FinancialAccount(
                businessA, "Live Future Opening Replica", AccountType.CASH, "PKR", 2,
                new BigDecimal("500.00"), todayKarachi.plusDays(1), AccountStatus.ACTIVE,
                null, null, null
        );

        accountRepository.save(jpy);
        accountRepository.save(bhd);
        accountRepository.save(negPkr);
        accountRepository.save(futurePkr);
        accountRepository.flush();

        // Query list and verify all DTO invariants
        AccountListResponse list = accountReadService.listAccounts(
                businessA, AccountStatus.ACTIVE, null, null, 0, 50
        );
        assertEquals(4, list.totalElements());

        for (var item : list.items()) {
            // Documented DTO invariants:
            if (item.balanceStatus() == BalanceStatus.AVAILABLE) {
                assertNotNull(item.currentBalance());
                assertNull(item.balanceUnavailableReason());
            } else {
                assertNull(item.currentBalance());
                assertNotNull(item.balanceUnavailableReason());
            }

            if (item.accountName().equals("Live JPY Wallet Replica")) {
                assertEquals("999999999999", item.openingBalance());
                assertEquals("999999999999", item.currentBalance());
                assertEquals(0, item.currencyDecimals());
            } else if (item.accountName().equals("Live BHD Bank Replica")) {
                assertEquals("1250.375", item.openingBalance());
                assertEquals("1250.375", item.currentBalance());
                assertEquals(3, item.currencyDecimals());
            } else if (item.accountName().equals("Live PKR Bank Replica")) {
                assertEquals("-2500.50", item.openingBalance());
                assertEquals("-2500.50", item.currentBalance());
                assertEquals(2, item.currencyDecimals());
            } else if (item.accountName().equals("Live Future Opening Replica")) {
                assertEquals("500.00", item.openingBalance());
                assertEquals("0.00", item.currentBalance()); // Canonical zero before opening date
                assertEquals(2, item.currencyDecimals());
            }
        }
    }

    @Test
    @DisplayName("IT-CREATE-01: Account creation persists in PostgreSQL with durable idempotency and replay")
    void testCreateAccountAndDurableIdempotencyInPostgreSql() {
        BusinessAccessContext contextA = new BusinessAccessContext(userA, businessA, MembershipRole.OWNER);

        AccountCreateRequest request = new AccountCreateRequest(
                "PostgreSQL Verified Bank",
                "BANK",
                "PKR",
                "75000.50",
                "2026-10-14",
                "Bank Alfalah",
                "Integration test account"
        );

        String idempKey = "it-idemp-" + UUID.randomUUID();

        // 1. Initial creation
        AccountDetailResponse created = accountCreateService.createAccount(contextA, idempKey, request);
        assertNotNull(created.id());
        assertEquals("PostgreSQL Verified Bank", created.accountName());
        assertEquals("75000.50", created.openingBalance());
        assertEquals("75000.50", created.currentBalance());
        assertEquals(BalanceStatus.AVAILABLE, created.balanceStatus());
        assertEquals("Bank Alfalah", created.institutionName());

        // Verify direct database persistence in financial_accounts table
        FinancialAccount dbAccount = accountRepository.findById(created.id()).orElseThrow();
        assertEquals("PostgreSQL Verified Bank", dbAccount.getAccountName());
        assertEquals(businessA, dbAccount.getBusinessId());
        assertEquals(AccountType.BANK, dbAccount.getAccountType());
        assertEquals("PKR", dbAccount.getCurrency());
        assertEquals(2, dbAccount.getCurrencyDecimals());
        assertEquals(0, new BigDecimal("75000.50").compareTo(dbAccount.getOpeningBalance()));

        // Verify durable idempotency record in financial_idempotency_records table
        var idempRecord = idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(
                businessA, "ACCOUNT_CREATE", idempKey
        ).orElseThrow();
        assertEquals(201, idempRecord.getResponseStatus());
        assertTrue(idempRecord.getResponseBody().contains("PostgreSQL Verified Bank"));
        assertEquals(created.id(), idempRecord.getResourceId());

        long initialCount = accountRepository.count();

        // 2. Authorized replay with identical key and identical payload returns stored result without creating duplicate
        AccountDetailResponse replay = accountCreateService.createAccount(contextA, idempKey, request);
        assertEquals(created.id(), replay.id());
        assertEquals(created.accountName(), replay.accountName());
        assertEquals(initialCount, accountRepository.count(), "Replay must not insert additional accounts");

        // 3. Same key with different payload throws 409 conflict
        AccountCreateRequest conflictingRequest = new AccountCreateRequest(
                "Different Name",
                "BANK",
                "PKR",
                "99999.00",
                "2026-10-14",
                null,
                null
        );
        assertThrows(DuplicateResourceException.class, () ->
                accountCreateService.createAccount(contextA, idempKey, conflictingRequest)
        );
    }

    @Test
    @DisplayName("IT-CREATE-02: Duplicate account name within business throws 409 conflict; distinct businesses isolated")
    void testDuplicateAccountNameInSameBusinessThrowsConflict() {
        BusinessAccessContext contextA = new BusinessAccessContext(userA, businessA, MembershipRole.OWNER);
        BusinessAccessContext contextB = new BusinessAccessContext(userB, businessB, MembershipRole.OWNER);

        String accountName = "Shared Branch Drawer " + UUID.randomUUID();

        AccountCreateRequest requestA = new AccountCreateRequest(
                accountName,
                "CASH",
                "PKR",
                "10000.00",
                "2026-10-14",
                null,
                null
        );

        // Business A creates account
        AccountDetailResponse createdA = accountCreateService.createAccount(contextA, "key-a-" + UUID.randomUUID(), requestA);
        assertNotNull(createdA.id());

        // Business A attempts to create duplicate name with new key -> 409 conflict
        AccountCreateRequest duplicateRequest = new AccountCreateRequest(
                accountName,
                "CASH",
                "PKR",
                "5000.00",
                "2026-10-14",
                null,
                null
        );
        assertThrows(DuplicateResourceException.class, () ->
                accountCreateService.createAccount(contextA, "key-a-new-" + UUID.randomUUID(), duplicateRequest)
        );

        // Business B can create account with same name (tenant isolation)
        AccountDetailResponse createdB = accountCreateService.createAccount(contextB, "key-b-" + UUID.randomUUID(), requestA);
        assertNotNull(createdB.id());
        assertNotEquals(createdA.id(), createdB.id());
    }

    @Test
    @DisplayName("IT-CREATE-03: Account creation changes no MonthlyRecord, financial health score, or advice")
    void testAccountCreationLeavesMonthlyRecordsAndScoresUntouched() {
        BusinessAccessContext contextA = new BusinessAccessContext(userA, businessA, MembershipRole.OWNER);

        Integer recordsBefore = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM monthly_records WHERE user_id = ?",
                Integer.class,
                businessA
        );
        assertEquals(0, recordsBefore);

        Integer scoresBefore = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM score_results WHERE user_id = ?",
                Integer.class,
                businessA
        );
        assertEquals(0, scoresBefore);

        AccountCreateRequest request = new AccountCreateRequest(
                "Untouched Baseline Test Account",
                "BANK",
                "USD",
                "25000.00",
                "2026-10-14",
                "Chase Bank",
                null
        );

        accountCreateService.createAccount(contextA, "key-untouched-" + UUID.randomUUID(), request);

        Integer recordsAfter = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM monthly_records WHERE user_id = ?",
                Integer.class,
                businessA
        );
        assertEquals(0, recordsAfter, "Monthly records count must remain exactly 0");

        Integer scoresAfter = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM score_results WHERE user_id = ?",
                Integer.class,
                businessA
        );
        assertEquals(0, scoresAfter, "Score results must remain untouched");
    }

    @Test
    @DisplayName("IT-CREATE-04: Signed negative opening balance allowed for BANK; CASH negative rejected")
    void testSignedOpeningBalanceForBankAndCashNonNegative() {
        BusinessAccessContext contextA = new BusinessAccessContext(userA, businessA, MembershipRole.OWNER);

        // Negative opening balance for BANK is valid
        AccountCreateRequest bankReq = new AccountCreateRequest(
                "Overdraft Facility " + UUID.randomUUID(),
                "BANK",
                "PKR",
                "-150000.00",
                "2026-10-14",
                "HBL",
                "Approved revolving credit line"
        );
        AccountDetailResponse bankResp = accountCreateService.createAccount(
                contextA, "key-od-" + UUID.randomUUID(), bankReq
        );
        assertEquals("-150000.00", bankResp.openingBalance());
        assertEquals("-150000.00", bankResp.currentBalance());
        assertEquals(BalanceStatus.AVAILABLE, bankResp.balanceStatus());

        // Negative opening balance for CASH is rejected
        AccountCreateRequest cashReq = new AccountCreateRequest(
                "Negative Cash Invalid " + UUID.randomUUID(),
                "CASH",
                "PKR",
                "-100.00",
                "2026-10-14",
                null,
                null
        );
        assertThrows(AccountValidationException.class, () ->
                accountCreateService.createAccount(contextA, "key-cash-neg-" + UUID.randomUUID(), cashReq)
        );
    }

    @Test
    @DisplayName("IT-CREATE-05: Real HTTP mutation path enforces CSRF protection (missing CSRF -> 403 Forbidden)")
    void testRealHttpMutationPathEnforcesCsrf() throws Exception {
        mockMvc.perform(post("/api/finance/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountName\":\"CSRF Attack Test\",\"accountType\":\"CASH\",\"currency\":\"PKR\",\"openingBalance\":\"100.00\"}"))
                .andExpect(status().isForbidden());
    }
}
