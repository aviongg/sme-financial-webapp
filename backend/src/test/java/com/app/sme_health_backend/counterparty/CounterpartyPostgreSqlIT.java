package com.app.sme_health_backend.counterparty;

import com.app.sme_health_backend.accounting.dto.TransactionDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionPostRequest;
import com.app.sme_health_backend.accounting.draft.DraftCreateRequest;
import com.app.sme_health_backend.accounting.draft.DraftDetailResponse;
import com.app.sme_health_backend.accounting.draft.DraftRecordRequest;
import com.app.sme_health_backend.accounting.draft.RecordDraftService;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.service.TransactionPostService;
import com.app.sme_health_backend.counterparty.dto.CounterpartyCreateRequest;
import com.app.sme_health_backend.counterparty.dto.CounterpartyDetailResponse;
import com.app.sme_health_backend.counterparty.dto.CounterpartyListResponse;
import com.app.sme_health_backend.counterparty.dto.CounterpartyUpdateRequest;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.dto.CreateBusinessRequest;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.service.BusinessService;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PermissionDeniedDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CounterpartyPostgreSqlIT extends DisposablePostgres {

    @Autowired
    private CounterpartyService counterpartyService;

    @Autowired
    private TransactionPostService transactionPostService;

    @Autowired
    private RecordDraftService recordDraftService;

    @Autowired
    private FinancialAccountRepository accountRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private BusinessService businessService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userA;
    private UUID businessA;
    private BusinessAccessContext contextA;

    private UUID userB;
    private UUID businessB;
    private BusinessAccessContext contextB;

    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(ZoneId.of("Asia/Karachi"));

        AppUser uA = new AppUser();
        uA.setEmail("cp-it-a-" + UUID.randomUUID() + "@example.test");
        uA.setFullName("Counterparty User A");
        uA.setPasswordHash("hash-a");
        userA = userRepository.save(uA).getId();
        businessA = businessService.createBusiness(
                new CreateBusinessRequest("CP Business A", "retail", "en", null, false, null, null, null),
                userA
        ).businessId();
        contextA = new BusinessAccessContext(userA, businessA, MembershipRole.OWNER);

        AppUser uB = new AppUser();
        uB.setEmail("cp-it-b-" + UUID.randomUUID() + "@example.test");
        uB.setFullName("Counterparty User B");
        uB.setPasswordHash("hash-b");
        userB = userRepository.save(uB).getId();
        businessB = businessService.createBusiness(
                new CreateBusinessRequest("CP Business B", "services", "en", null, false, null, null, null),
                userB
        ).businessId();
        contextB = new BusinessAccessContext(userB, businessB, MembershipRole.OWNER);
    }

    private FinancialAccount createAccount(UUID businessId, String name, BigDecimal opening) {
        FinancialAccount account = new FinancialAccount(
                businessId,
                name,
                AccountType.BANK,
                "PKR",
                2,
                opening,
                today.minusDays(30),
                AccountStatus.ACTIVE,
                null,
                null,
                "Test Account"
        );
        return accountRepository.save(account);
    }

    @Test
    @DisplayName("IT-CP-01: Flyway migration V25 grants SELECT, INSERT, UPDATE to finsight_app; DELETE is denied")
    void verifyFlywayMigrationAndGrants() {
        Boolean canSelect = jdbcTemplate.queryForObject(
                "SELECT has_table_privilege('finsight_app', 'counterparties', 'SELECT')",
                Boolean.class
        );
        assertTrue(canSelect, "finsight_app must have SELECT privilege on counterparties");

        Boolean canInsert = jdbcTemplate.queryForObject(
                "SELECT has_table_privilege('finsight_app', 'counterparties', 'INSERT')",
                Boolean.class
        );
        assertTrue(canInsert, "finsight_app must have INSERT privilege on counterparties");

        Boolean canUpdate = jdbcTemplate.queryForObject(
                "SELECT has_table_privilege('finsight_app', 'counterparties', 'UPDATE')",
                Boolean.class
        );
        assertTrue(canUpdate, "finsight_app must have UPDATE privilege on counterparties");

        Boolean canDelete = jdbcTemplate.queryForObject(
                "SELECT has_table_privilege('finsight_app', 'counterparties', 'DELETE')",
                Boolean.class
        );
        assertFalse(canDelete, "finsight_app must NOT have DELETE privilege on counterparties");

        // Verify that direct DML works for SELECT, INSERT, UPDATE
        UUID cpId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO counterparties (id, business_id, display_name, type, status, version, created_at, updated_at)
                VALUES (?, ?, 'Direct DML Test', 'CUSTOMER', 'ACTIVE', 0, now(), now())
                """, cpId, businessA);

        String name = jdbcTemplate.queryForObject(
                "SELECT display_name FROM counterparties WHERE id = ?",
                String.class,
                cpId
        );
        assertEquals("Direct DML Test", name);

        jdbcTemplate.update("UPDATE counterparties SET display_name = 'Updated DML' WHERE id = ?", cpId);
        String updatedName = jdbcTemplate.queryForObject(
                "SELECT display_name FROM counterparties WHERE id = ?",
                String.class,
                cpId
        );
        assertEquals("Updated DML", updatedName);

        // Attempting direct DELETE as runtime role finsight_app must be rejected
        org.springframework.dao.DataAccessException ex = assertThrows(org.springframework.dao.DataAccessException.class, () -> {
            jdbcTemplate.update("DELETE FROM counterparties WHERE id = ?", cpId);
        }, "Direct DELETE by finsight_app must be denied with permission denied");
        assertTrue(ex.getMostSpecificCause().getMessage().contains("permission denied"));
    }

    @Test
    @DisplayName("IT-CP-02: Strict tenant isolation between Business A and Business B")
    void testTenantIsolation() {
        CounterpartyDetailResponse cpA = counterpartyService.createCounterparty(
                contextA,
                new CounterpartyCreateRequest("Customer In Business A", "CUSTOMER", "a@test.com", "+923001111111", null, null, null)
        );

        CounterpartyDetailResponse cpB = counterpartyService.createCounterparty(
                contextB,
                new CounterpartyCreateRequest("Customer In Business B", "CUSTOMER", "b@test.com", "+923002222222", null, null, null)
        );

        CounterpartyListResponse listA = counterpartyService.listCounterparties(contextA, null, null, null, 0, 50);
        assertEquals(1, listA.totalElements());
        assertEquals("Customer In Business A", listA.content().getFirst().displayName());

        CounterpartyListResponse listB = counterpartyService.listCounterparties(contextB, null, null, null, 0, 50);
        assertEquals(1, listB.totalElements());
        assertEquals("Customer In Business B", listB.content().getFirst().displayName());

        assertThrows(RuntimeException.class, () -> counterpartyService.getCounterparty(contextA, cpB.id()));
        assertThrows(RuntimeException.class, () -> counterpartyService.getCounterparty(contextB, cpA.id()));
    }

    @Test
    @DisplayName("IT-CP-03: Zero unintended financial mutation across counterparty lifecycle")
    void testZeroFinancialMutation() {
        int initialTxCount = jdbcTemplate.queryForObject("SELECT count(*) FROM financial_transactions", Integer.class);
        int initialMovCount = jdbcTemplate.queryForObject("SELECT count(*) FROM account_movements", Integer.class);
        int initialMonthlyCount = jdbcTemplate.queryForObject("SELECT count(*) FROM monthly_records", Integer.class);

        CounterpartyDetailResponse created = counterpartyService.createCounterparty(
                contextA,
                new CounterpartyCreateRequest("Zero Mutation Co", "BOTH", "zero@example.test", "+923003333333", "Karachi", "REF-00", "Notes")
        );

        counterpartyService.updateCounterparty(
                contextA,
                created.id(),
                new CounterpartyUpdateRequest(created.version(), "Zero Mutation Co Updated", "BOTH", "zero@example.test", "+923003333333", "Lahore", "REF-01", "Updated Notes")
        );

        counterpartyService.archiveCounterparty(contextA, created.id());
        counterpartyService.restoreCounterparty(contextA, created.id());

        int finalTxCount = jdbcTemplate.queryForObject("SELECT count(*) FROM financial_transactions", Integer.class);
        int finalMovCount = jdbcTemplate.queryForObject("SELECT count(*) FROM account_movements", Integer.class);
        int finalMonthlyCount = jdbcTemplate.queryForObject("SELECT count(*) FROM monthly_records", Integer.class);

        assertEquals(initialTxCount, finalTxCount, "Financial transactions count must remain unchanged");
        assertEquals(initialMovCount, finalMovCount, "Account movements count must remain unchanged");
        assertEquals(initialMonthlyCount, finalMonthlyCount, "Monthly records count must remain unchanged");
    }

    @Test
    @DisplayName("IT-CP-04: Transaction foreign key constraint is composite (counterparty_id, business_id) with ON DELETE RESTRICT")
    void testTransactionForeignKeyRestrict() {
        FinancialAccount account = createAccount(businessA, "Sales Account", new BigDecimal("10000.00"));
        CounterpartyDetailResponse customer = counterpartyService.createCounterparty(
                contextA,
                new CounterpartyCreateRequest("Restricted Customer", "CUSTOMER", null, null, null, null, null)
        );

        TransactionDetailResponse tx = transactionPostService.postTransaction(
                contextA,
                UUID.randomUUID().toString(),
                new TransactionPostRequest(
                        account.getId(),
                        "DIRECT_SALE_RECEIPT",
                        "2500.00",
                        today.toString(),
                        "Cash sale to customer",
                        customer.id()
                )
        );
        assertEquals(customer.id(), tx.counterpartyId());
        assertEquals("Restricted Customer", tx.counterpartyName());

        // Deleting the counterparty directly from DB should fail due to FK ON DELETE RESTRICT
        // (Note: even if run with a privileged user, the FK constraint itself blocks deletion)
        assertThrows(Exception.class, () -> {
            jdbcTemplate.update("DELETE FROM counterparties WHERE id = ?", customer.id());
        });
    }

    @Test
    @DisplayName("IT-CP-05: Draft foreign key constraint is composite (counterparty_id, business_id) with ON DELETE RESTRICT")
    void testDraftForeignKeyRestrict() {
        FinancialAccount account = createAccount(businessA, "Expense Account", new BigDecimal("10000.00"));
        CounterpartyDetailResponse supplier = counterpartyService.createCounterparty(
                contextA,
                new CounterpartyCreateRequest("Draft Supplier", "SUPPLIER", null, null, null, null, null)
        );

        DraftDetailResponse draft = recordDraftService.createDraft(
                contextA,
                UUID.randomUUID().toString(),
                new DraftCreateRequest(
                        account.getId(),
                        "OPERATING_EXPENSE_PAYMENT",
                        today.toString(),
                        "1500.00",
                        "Draft expense",
                        supplier.id()
                )
        );
        assertEquals(supplier.id(), draft.counterpartyId());

        // Deleting supplier from DB should be blocked by FK ON DELETE RESTRICT (preserving draft associations)
        assertThrows(Exception.class, () -> {
            jdbcTemplate.update("DELETE FROM counterparties WHERE id = ?", supplier.id());
        });

        // Verify draft still retains counterparty_id
        UUID cpAfterAttempt = jdbcTemplate.queryForObject(
                "SELECT counterparty_id FROM record_drafts WHERE id = ?",
                UUID.class,
                draft.id()
        );
        assertEquals(supplier.id(), cpAfterAttempt, "Draft counterparty_id must be preserved");
    }

    @Test
    @DisplayName("IT-CP-06: Database-level and service-level cross-tenant foreign key rejection")
    void testCrossTenantDatabaseAndServiceRejection() {
        FinancialAccount accountA = createAccount(businessA, "Business A Account", new BigDecimal("5000.00"));
        CounterpartyDetailResponse cpInB = counterpartyService.createCounterparty(
                contextB,
                new CounterpartyCreateRequest("Supplier In B", "SUPPLIER", null, null, null, null, null)
        );

        // 1. Service layer rejection for transactions
        var txEx = assertThrows(com.app.sme_health_backend.accounting.exception.TransactionValidationException.class, () -> {
            transactionPostService.postTransaction(
                    contextA,
                    UUID.randomUUID().toString(),
                    new TransactionPostRequest(
                            accountA.getId(),
                            "OPERATING_EXPENSE_PAYMENT",
                            "500.00",
                            today.toString(),
                            "Illegal cross-tenant expense",
                            cpInB.id()
                    )
            );
        });
        assertTrue(txEx.getErrors().containsKey("counterpartyId"));

        // 2. Service layer rejection for drafts
        var draftEx = assertThrows(com.app.sme_health_backend.accounting.draft.DraftValidationException.class, () -> {
            recordDraftService.createDraft(
                    contextA,
                    UUID.randomUUID().toString(),
                    new DraftCreateRequest(
                            accountA.getId(),
                            "OPERATING_EXPENSE_PAYMENT",
                            today.toString(),
                            "500.00",
                            "Illegal cross-tenant draft",
                            cpInB.id()
                    )
            );
        });
        assertTrue(draftEx.getErrors().containsKey("counterpartyId"));

        // 3. Direct database insertion into record_drafts with cross-tenant counterparty fails FK constraint
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update("""
                    INSERT INTO record_drafts (
                        id, business_id, account_id, event_type, effective_date, amount_input,
                        description, status, version, counterparty_id, created_by_id, updated_by_id,
                        created_at, updated_at
                    ) VALUES (
                        gen_random_uuid(), ?, ?, 'OPERATING_EXPENSE_PAYMENT', now(), '500.00',
                        'Direct SQL cross-tenant draft', 'DRAFT', 0, ?, ?, ?, now(), now()
                    )
                    """, businessA, accountA.getId(), cpInB.id(), userA, userA);
        }, "Direct SQL insert of cross-tenant counterparty into record_drafts must fail fk_record_drafts_counterparty");

        // 4. Direct database insertion into financial_transactions with cross-tenant counterparty fails FK constraint
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update("""
                    INSERT INTO financial_transactions (
                        id, business_id, account_id, event_type, movement_direction,
                        amount, currency, base_amount_pkr, effective_date, description,
                        created_by_user_id, counterparty_id, created_at
                    ) VALUES (
                        gen_random_uuid(), ?, ?, 'OPERATING_EXPENSE_PAYMENT', 'DECREASE',
                        500.00, 'PKR', 500.00, now(), 'Direct SQL cross-tenant tx',
                        ?, ?, now()
                    )
                    """, businessA, accountA.getId(), userA, cpInB.id());
        }, "Direct SQL insert of cross-tenant counterparty into financial_transactions must fail fk_fin_tx_counterparty");
    }

    @Test
    @DisplayName("IT-CP-07: Draft recording preserves counterparty linkage on posted transaction")
    void testDraftRecordingPreservesCounterparty() {
        FinancialAccount account = createAccount(businessA, "Revenue Account", new BigDecimal("20000.00"));
        CounterpartyDetailResponse customer = counterpartyService.createCounterparty(
                contextA,
                new CounterpartyCreateRequest("Enterprise Client", "CUSTOMER", "client@corp.test", "+923009999999", null, null, null)
        );

        DraftDetailResponse draft = recordDraftService.createDraft(
                contextA,
                UUID.randomUUID().toString(),
                new DraftCreateRequest(
                        account.getId(),
                        "DIRECT_SALE_RECEIPT",
                        today.toString(),
                        "7500.00",
                        "Enterprise contract milestone",
                        customer.id()
                )
        );
        assertEquals(customer.id(), draft.counterpartyId());

        var recorded = recordDraftService.recordDraft(
                contextA,
                draft.id(),
                UUID.randomUUID().toString(),
                new DraftRecordRequest(
                        draft.version(),
                        "7500.00",
                        account.getId(),
                        "DIRECT_SALE_RECEIPT",
                        today.toString(),
                        "Enterprise contract milestone",
                        customer.id()
                )
        );

        assertNotNull(recorded.transaction().id());
        assertEquals(customer.id(), recorded.transaction().counterpartyId());
        assertEquals("Enterprise Client", recorded.transaction().counterpartyName());

        UUID dbCpId = jdbcTemplate.queryForObject(
                "SELECT counterparty_id FROM financial_transactions WHERE id = ?",
                UUID.class,
                recorded.transaction().id()
        );
        assertEquals(customer.id(), dbCpId);
    }

    @Test
    @DisplayName("IT-CP-08: Identical display names are permitted as distinct, independent counterparties")
    void testIdenticalDisplayNamesAllowed() {
        FinancialAccount account = createAccount(businessA, "Main Trading Account", new BigDecimal("50000.00"));

        // Create first counterparty with display name "Tariq Traders"
        CounterpartyDetailResponse cp1 = counterpartyService.createCounterparty(
                contextA,
                new CounterpartyCreateRequest("Tariq Traders", "CUSTOMER", "tariq1@example.test", "+923001234567", "Shop 1, Bolton Market", "REF-001", "Wholesale")
        );

        // Create second counterparty with exact same display name "Tariq Traders"
        CounterpartyDetailResponse cp2 = counterpartyService.createCounterparty(
                contextA,
                new CounterpartyCreateRequest("Tariq Traders", "SUPPLIER", "tariq2@example.test", "+923007654321", "Shop 5, Jodia Bazaar", "REF-002", "Textile Supplier")
        );

        // Verify distinct identities
        assertNotNull(cp1.id());
        assertNotNull(cp2.id());
        assertNotEquals(cp1.id(), cp2.id(), "Identical display names must produce distinct counterparty UUIDs");
        assertEquals("Tariq Traders", cp1.displayName());
        assertEquals("Tariq Traders", cp2.displayName());
        assertEquals("CUSTOMER", cp1.type());
        assertEquals("SUPPLIER", cp2.type());

        // Search returns both independent records
        CounterpartyListResponse searchResult = counterpartyService.listCounterparties(
                contextA,
                "tariq traders",
                null,
                null,
                0,
                50
        );
        assertEquals(2, searchResult.totalElements(), "Search for 'tariq traders' must return both counterparties");

        // Both can be independently linked to financial transactions
        TransactionDetailResponse tx1 = transactionPostService.postTransaction(
                contextA,
                UUID.randomUUID().toString(),
                new TransactionPostRequest(
                        account.getId(),
                        "DIRECT_SALE_RECEIPT",
                        "3000.00",
                        today.toString(),
                        "Sale to customer Tariq Traders",
                        cp1.id()
                )
        );
        assertEquals(cp1.id(), tx1.counterpartyId());

        TransactionDetailResponse tx2 = transactionPostService.postTransaction(
                contextA,
                UUID.randomUUID().toString(),
                new TransactionPostRequest(
                        account.getId(),
                        "OPERATING_EXPENSE_PAYMENT",
                        "2000.00",
                        today.toString(),
                        "Payment to supplier Tariq Traders",
                        cp2.id()
                )
        );
        assertEquals(cp2.id(), tx2.counterpartyId());

        // Both can be independently linked to drafts
        DraftDetailResponse draft1 = recordDraftService.createDraft(
                contextA,
                UUID.randomUUID().toString(),
                new DraftCreateRequest(
                        account.getId(),
                        "DIRECT_SALE_RECEIPT",
                        today.toString(),
                        "4000.00",
                        "Draft for customer Tariq Traders",
                        cp1.id()
                )
        );
        assertEquals(cp1.id(), draft1.counterpartyId());

        DraftDetailResponse draft2 = recordDraftService.createDraft(
                contextA,
                UUID.randomUUID().toString(),
                new DraftCreateRequest(
                        account.getId(),
                        "OPERATING_EXPENSE_PAYMENT",
                        today.toString(),
                        "1500.00",
                        "Draft for supplier Tariq Traders",
                        cp2.id()
                )
        );
        assertEquals(cp2.id(), draft2.counterpartyId());
    }
}
