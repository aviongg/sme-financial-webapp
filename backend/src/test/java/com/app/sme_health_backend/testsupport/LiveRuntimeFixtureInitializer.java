package com.app.sme_health_backend.testsupport;

import com.app.sme_health_backend.SmeHealthBackendApplication;
import com.app.sme_health_backend.accounting.dto.*;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.service.*;
import com.app.sme_health_backend.commercial.dto.CommercialDocumentDraftRequest;
import com.app.sme_health_backend.commercial.dto.CommercialLineRequest;
import com.app.sme_health_backend.commercial.model.CommercialDocumentKind;
import com.app.sme_health_backend.commercial.service.CommercialDocumentService;
import com.app.sme_health_backend.counterparty.dto.CounterpartyCreateRequest;
import com.app.sme_health_backend.counterparty.dto.CounterpartyDetailResponse;
import com.app.sme_health_backend.counterparty.CounterpartyService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.dto.CreateBusinessRequest;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.service.BusinessService;
import com.app.sme_health_backend.inventory.dto.InventoryItemRequest;
import com.app.sme_health_backend.inventory.model.InventoryItemKind;
import com.app.sme_health_backend.inventory.service.InventoryCatalogService;
import com.app.sme_health_backend.obligation.AdvanceService;
import com.app.sme_health_backend.obligation.FinancialObligationService;
import com.app.sme_health_backend.obligation.dto.*;
import com.app.sme_health_backend.projects.dto.ProjectCreateRequest;
import com.app.sme_health_backend.projects.model.ProjectKind;
import com.app.sme_health_backend.projects.service.ProjectPlanningService;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.recurring.dto.RecurringRuleCreateRequest;
import com.app.sme_health_backend.recurring.service.RecurringRuleService;
import com.app.sme_health_backend.scoring.service.ScoringService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * STANDALONE FIXTURE INITIALIZER FOR ISOLATED DISPOSABLE TEST ENVIRONMENTS ONLY.
 *
 * Safety invariants:
 * 1. NOT a test class. Zero @Test annotations. Never discoverable by Surefire or automated regression.
 * 2. Requires explicit authorization: FINSIGHT_DISPOSABLE_INITIALIZE=true.
 * 3. Enforces target database host (127.0.0.1) and port (explicitly rejects 5432, 5433).
 * 4. Requires an EMPTY database (count == 0). Fails closed if data exists; ZERO DELETE statements.
 * 5. Exports manifest to dedicated tmp/finsight-live-fixture-s4b.json without overwriting incident evidence.
 */
public final class LiveRuntimeFixtureInitializer {

    private static final String OWNER_EMAIL = "live.owner@finsight.local";
    private static final String OWNER_RAW_PASSWORD = "Synthetic-Owner-2026!";
    private static final String BIZ_A_NAME = "Apex Industrial Ltd";
    private static final String BIZ_B_NAME = "Zenith Commercial Ltd";

    private LiveRuntimeFixtureInitializer() {}

    public static void main(String[] args) throws Exception {
        System.out.println("=== FINSIGHT S4B SAFE FIXTURE INITIALIZER ===");

        // 1. Validate authorization
        String authFlag = System.getenv("FINSIGHT_DISPOSABLE_INITIALIZE");
        if (!"true".equalsIgnoreCase(authFlag)) {
            throw new SecurityException("AUTHORIZATION_REQUIRED: FINSIGHT_DISPOSABLE_INITIALIZE=true must be set explicitly.");
        }

        // 2. Resolve and validate database connection target
        String dbHost = System.getenv().getOrDefault("POSTGRES_HOST", "127.0.0.1");
        String dbPortStr = System.getenv().getOrDefault("POSTGRES_PORT", "5434");
        String dbName = System.getenv().getOrDefault("POSTGRES_DB", "sme_health_s4b");
        String dbUser = System.getenv().getOrDefault("POSTGRES_APP_USER", "finsight_app");
        String dbPassword = System.getenv().getOrDefault("POSTGRES_APP_PASSWORD", "TestAppPassword2026!");

        int dbPort = Integer.parseInt(dbPortStr);

        // Strict target validation
        if (!"127.0.0.1".equals(dbHost) && !"localhost".equalsIgnoreCase(dbHost)) {
            throw new SecurityException("PROHIBITED_HOST: Only local disposable database targets are permitted.");
        }
        if (dbPort == 5432) {
            throw new SecurityException("PROHIBITED_PORT: Default PostgreSQL port 5432 is prohibited.");
        }
        if (dbPort == 5433) {
            throw new SecurityException("PROHIBITED_PORT: Port 5433 is reserved for the preserved incident environment and is strictly prohibited.");
        }
        if (!dbName.contains("disposable") && !dbName.contains("s4b") && !dbName.contains("test")) {
            throw new SecurityException("PROHIBITED_DATABASE_NAME: Target database name must indicate test/disposable scope.");
        }

        System.out.println("Target: postgresql://" + dbHost + ":" + dbPort + "/" + dbName + " as " + dbUser);

        String jdbcUrl = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;

        // 3. Direct JDBC Pre-Flight Check: Fail-closed before Spring Boot starts!
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
             java.sql.Statement stmt = conn.createStatement()) {
            try (java.sql.ResultSet rs = stmt.executeQuery("SELECT count(*) FROM app_users")) {
                if (rs.next() && rs.getInt(1) > 0) {
                    throw new IllegalStateException("TARGET_NOT_EMPTY: Database must be pristine empty. Found "
                            + rs.getInt(1) + " users in app_users. Destructive seeding is prohibited.");
                }
            } catch (java.sql.SQLException ex) {
                // Table does not exist yet if unmigrated
            }

            try (java.sql.ResultSet rs = stmt.executeQuery("SELECT count(*) FROM financial_accounts")) {
                if (rs.next() && rs.getInt(1) > 0) {
                    throw new IllegalStateException("TARGET_NOT_EMPTY: Database must be pristine empty. Found "
                            + rs.getInt(1) + " accounts in financial_accounts. Destructive seeding is prohibited.");
                }
            } catch (java.sql.SQLException ex) {
                // Table does not exist yet
            }
        }

        // 4. Configure Spring environment
        System.setProperty("spring.datasource.url", jdbcUrl);
        System.setProperty("spring.datasource.username", dbUser);
        System.setProperty("spring.datasource.password", dbPassword);
        System.setProperty("spring.flyway.enabled", "false"); // Migrations already verified
        System.setProperty("finsight.crypto.keys.k1", "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=");
        System.setProperty("FINSIGHT_CRYPTO_KEY_K1", "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=");
        System.setProperty("app.security.internal-service-secret", "devb-browser-fixture-only-not-production");
        System.setProperty("app.ai.enabled", "true");
        System.setProperty("app.ai.provider", "mock");
        System.setProperty("app.ai.demo-enabled", "true");
        System.setProperty("app.ai.requests-per-minute", "60");

        SpringApplication app = new SpringApplication(SmeHealthBackendApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setAdditionalProfiles("dev");

        try (ConfigurableApplicationContext ctx = app.run()) {
            JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);

            // 4. Validate EMPTY database: ZERO DELETE statements permitted!
            Integer userCount = jdbc.queryForObject("SELECT count(*) FROM app_users", Integer.class);
            Integer accountCount = jdbc.queryForObject("SELECT count(*) FROM financial_accounts", Integer.class);
            if ((userCount != null && userCount > 0) || (accountCount != null && accountCount > 0)) {
                throw new IllegalStateException("TARGET_NOT_EMPTY: Database must be pristine empty. Found "
                        + userCount + " users, " + accountCount + " accounts. Destructive seeding is prohibited.");
            }
            System.out.println("Verified target database is empty (0 users, 0 accounts). Proceeding with safe initialization.");

            // Resolve required services
            AppUserRepository userRepo = ctx.getBean(AppUserRepository.class);
            PasswordEncoder encoder = ctx.getBean(PasswordEncoder.class);
            BusinessService businessService = ctx.getBean(BusinessService.class);
            FinancialAccountRepository accountRepo = ctx.getBean(FinancialAccountRepository.class);
            AccountReadService accountReadService = ctx.getBean(AccountReadService.class);
            TransactionPostService txPostService = ctx.getBean(TransactionPostService.class);
            TransferService transferService = ctx.getBean(TransferService.class);
            TransactionReverseService txReverseService = ctx.getBean(TransactionReverseService.class);
            CounterpartyService counterpartyService = ctx.getBean(CounterpartyService.class);
            FinancialObligationService obligationService = ctx.getBean(FinancialObligationService.class);
            AdvanceService advanceService = ctx.getBean(AdvanceService.class);
            MonthlyRecordRepository recordRepo = ctx.getBean(MonthlyRecordRepository.class);
            ScoringService scoringService = ctx.getBean(ScoringService.class);
            CommercialDocumentService commercialDocService = ctx.getBean(CommercialDocumentService.class);
            ProjectPlanningService projectService = ctx.getBean(ProjectPlanningService.class);
            InventoryCatalogService inventoryService = ctx.getBean(InventoryCatalogService.class);
            RecurringRuleService recurringRuleService = ctx.getBean(RecurringRuleService.class);

            ZoneId zone = ZoneId.of("Asia/Karachi");
            LocalDate today = LocalDate.now(zone);

            // 5. Create Owner User
            AppUser owner = new AppUser();
            owner.setEmail(OWNER_EMAIL);
            owner.setPasswordHash(encoder.encode(OWNER_RAW_PASSWORD));
            owner.setFullName("Synthetic Live Owner");
            owner.setAccountStatus(com.app.sme_health_backend.identity.model.AccountStatus.ACTIVE);
            owner.setMustChangePassword(false);
            owner.setAuthVersion(0L);
            owner = userRepo.saveAndFlush(owner);
            UUID userId = owner.getId();

            // 6. Create Business A & B
            UUID businessAId = businessService.createBusiness(
                    new CreateBusinessRequest(BIZ_A_NAME, "trade", "en", null, false, null, null, null),
                    userId
            ).businessId();

            UUID businessBId = businessService.createBusiness(
                    new CreateBusinessRequest(BIZ_B_NAME, "services", "en", null, false, null, null, null),
                    userId
            ).businessId();

            BusinessAccessContext contextA = new BusinessAccessContext(userId, businessAId, MembershipRole.OWNER);
            BusinessAccessContext contextB = new BusinessAccessContext(userId, businessBId, MembershipRole.OWNER);

            // 7. Seed Accounts for Business A
            FinancialAccount acc1 = new FinancialAccount(
                    businessAId, "Apex Main Operations", AccountType.BANK, "PKR", 2,
                    new BigDecimal("100000.00"), today.minusDays(30), AccountStatus.ACTIVE,
                    "Habib Bank Limited", "0123456789", "Primary operating checking account"
            );
            acc1 = accountRepo.saveAndFlush(acc1);

            FinancialAccount acc2 = new FinancialAccount(
                    businessAId, "Apex Reserve Account", AccountType.BANK, "PKR", 2,
                    BigDecimal.ZERO.setScale(2), today.minusDays(30), AccountStatus.ACTIVE,
                    "Meezan Bank", "9876543210", "Liquidity reserve account"
            );
            acc2 = accountRepo.saveAndFlush(acc2);

            FinancialAccount acc3 = new FinancialAccount(
                    businessAId, "Apex Trade Collections", AccountType.BANK, "PKR", 2,
                    BigDecimal.ZERO.setScale(2), today.minusDays(30), AccountStatus.ACTIVE,
                    "Standard Chartered", "5555444433", "Customer collections account"
            );
            acc3 = accountRepo.saveAndFlush(acc3);

            // 8. Transactions on Account 1: Receipt +20,000, Expense -5,000, Transfer -10,000 to Acc 2
            txPostService.postTransaction(
                    contextA, "seed-s4b-tx-01",
                    new TransactionPostRequest(acc1.getId(), "DIRECT_SALE_RECEIPT", "20000.00", today.minusDays(10).toString(), "Direct store receipts batch 1")
            );
            txPostService.postTransaction(
                    contextA, "seed-s4b-tx-02",
                    new TransactionPostRequest(acc1.getId(), "OPERATING_EXPENSE_PAYMENT", "5000.00", today.minusDays(8).toString(), "Factory consumable supplies")
            );
            transferService.postTransfer(
                    contextA, "seed-s4b-transfer-01",
                    new TransferPostRequest(acc1.getId(), acc2.getId(), "10000.00", today.minusDays(3).toString(), "Transfer to reserve liquidity")
            );

            // Verify Account 1 = 105,000, Account 2 = 10,000
            String bal1 = accountReadService.getAccountDetail(businessAId, acc1.getId()).currentBalance();
            String bal2 = accountReadService.getAccountDetail(businessAId, acc2.getId()).currentBalance();
            if (!"105000.00".equals(bal1) || !"10000.00".equals(bal2)) {
                throw new IllegalStateException("Balance mismatch: acc1=" + bal1 + ", acc2=" + bal2);
            }

            // 9. Counterparties
            CounterpartyDetailResponse customer = counterpartyService.createCounterparty(
                    contextA, new CounterpartyCreateRequest("Alpha Wholesale Buyers", "CUSTOMER", "billing@alphawholesale.local", "+923001234567", "Karachi Industrial Area", "CUST-001", "Preferred wholesale customer")
            );
            CounterpartyDetailResponse supplier = counterpartyService.createCounterparty(
                    contextA, new CounterpartyCreateRequest("Apex Raw Materials", "SUPPLIER", "supply@apexraw.local", "+923009876543", "Lahore Industrial Estate", "SUPP-001", "Primary raw materials supplier")
            );

            // 10. Receivable Obligation: 100,000 PKR
            ObligationDetailResponse ob = obligationService.createObligation(
                    contextA, new ObligationCreateRequest(
                            customer.id(), "RECEIVABLE", "100000.00",
                            today.minusDays(15).toString(), today.plusDays(15).toString(),
                            "INV-2026-001", "Bulk supply shipment invoice 001", null, null
                    )
            );

            // Customer Advance: 30,000 PKR into Account 3
            AdvanceDetailResponse adv = advanceService.createAdvance(
                    contextA, "seed-s4b-adv-01",
                    new AdvanceCreateRequest(customer.id(), "RECEIVABLE", acc3.getId(), today.minusDays(14).toString(), "30000.00", "ADV-CUST-001", "Advance deposit for order 001")
            );
            advanceService.applyAdvance(
                    contextA, "seed-s4b-apply-01", adv.id(),
                    new AdvanceApplyRequest(ob.id(), "30000.00", today.minusDays(12).toString(), "Application of advance deposit to INV-2026-001")
            );

            // Settlement: 50,000 PKR into Account 3
            obligationService.recordSettlement(
                    contextA, ob.id(), "seed-s4b-settle-01",
                    new ObligationSettlementRequest(acc3.getId(), "50000.00", today.minusDays(5).toString(), "CHK-55441", "Cheque payment installment 1")
            );

            // Reversal demo on Account 3 (net zero: +1000 and -1000)
            TransactionDetailResponse txToRev = txPostService.postTransaction(
                    contextA, "seed-s4b-torev-01",
                    new TransactionPostRequest(acc3.getId(), "DIRECT_SALE_RECEIPT", "1000.00", today.minusDays(2).toString(), "Customer test deposit to be reversed")
            );
            txReverseService.postReversal(
                    contextA, "seed-s4b-rev-01", txToRev.id(),
                    new TransactionReverseRequest("Customer deposit correction refund")
            );

            // Verify Account 3 = 80,000
            String bal3 = accountReadService.getAccountDetail(businessAId, acc3.getId()).currentBalance();
            if (!"80000.00".equals(bal3)) {
                throw new IllegalStateException("Balance mismatch for Account 3: " + bal3 + ", expected: 80000.00");
            }

            // Verify Obligation Outstanding = 20,000
            ObligationDetailResponse obFinal = obligationService.getObligation(contextA, ob.id());
            if (!"20000.00".equals(obFinal.outstandingAmount()) || !"PARTIALLY_PAID".equals(obFinal.settlementStatus())) {
                throw new IllegalStateException("Obligation mismatch: outstanding=" + obFinal.outstandingAmount() + ", status=" + obFinal.settlementStatus());
            }

            // 11. Recurring Rule
            recurringRuleService.createRule(
                    contextA, new RecurringRuleCreateRequest(
                            "Monthly Factory Rent", "Fixed lease payment", "OPERATING_EXPENSE_PAYMENT",
                            acc1.getId(), supplier.id(), "15000.00", "MONTHLY",
                            today.toString(), today.plusMonths(12).toString()
                    )
            );

            // 12. Historical Monthly Records & Scores
            for (String month : List.of("2026-07", "2026-08", "2026-09")) {
                MonthlyRecord mr = new MonthlyRecord();
                mr.setUserId(businessAId);
                mr.setMonth(month);
                mr.setRevenue(new BigDecimal("12000.00"));
                mr.setOperatingExpenses(new BigDecimal("10000.00"));
                mr.setCashInflow(new BigDecimal("8000.00"));
                mr.setCashOutflow(new BigDecimal("3000.00"));
                mr.setCashBalanceEom(new BigDecimal("25000.00"));
                mr.setReceivablesOutstanding(new BigDecimal("5000.00"));
                mr.setPayablesOutstanding(new BigDecimal("2000.00"));
                mr.setFinancingType("none");
                mr.setUpdatedAt(LocalDateTime.now());
                recordRepo.saveAndFlush(mr);
                scoringService.calculateAndSaveScore(businessAId, month);
            }

            // 13. Draft Commercial Invoice & Bill (F04 Phase A)
            commercialDocService.createDraft(
                    contextA,
                    CommercialDocumentKind.INVOICE,
                    new CommercialDocumentDraftRequest(
                            "Alpha Wholesale Buyers",
                            today.toString(),
                            today.plusDays(30).toString(),
                            "Draft commercial invoice for baseline",
                            List.of(new CommercialLineRequest("Custom Steel Beams", "5", "piece", "10000.00")),
                            null
                    )
            );
            commercialDocService.createDraft(
                    contextA,
                    CommercialDocumentKind.BILL,
                    new CommercialDocumentDraftRequest(
                            "Apex Raw Materials",
                            today.toString(),
                            today.plusDays(30).toString(),
                            "Draft commercial bill for baseline",
                            List.of(new CommercialLineRequest("Raw steel sheets", "10", "sheet", "5000.00")),
                            null
                    )
            );

            // 14. Project (F05)
            projectService.createProject(
                    contextA,
                    new ProjectCreateRequest(
                            "PRJ-S4B-01",
                            "Factory Automation Phase 1",
                            ProjectKind.PROJECT,
                            "Planning-only project for baseline",
                            today.toString(),
                            today.plusMonths(6).toString()
                    )
            );

            // 15. Inventory Item (F08)
            inventoryService.create(
                    contextA,
                    new InventoryItemRequest(
                            "SKU-STEEL-01",
                            "Industrial Steel Sheets",
                            "sheet",
                            InventoryItemKind.RAW_MATERIAL,
                            "Raw Materials",
                            "10",
                            null
                    )
            );

            // 16. Business B: Zenith Operations account = 88,888 PKR
            FinancialAccount accB = new FinancialAccount(
                    businessBId, "Zenith Operations", AccountType.BANK, "PKR", 2,
                    new BigDecimal("88888.00"), today.minusDays(20), AccountStatus.ACTIVE,
                    "Faysal Bank", "1122334455", "Zenith primary operational account"
            );
            accB = accountRepo.saveAndFlush(accB);

            // 17. Export NEW Fixture Manifest (tmp/finsight-live-fixture-s4b.json)
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("disposable", true);
            manifest.put("migrationHead", 37);
            manifest.put("frontendUrl", "http://127.0.0.1:3100");
            manifest.put("backendUrl", "http://127.0.0.1:8081");
            manifest.put("email", OWNER_EMAIL);
            manifest.put("password", OWNER_RAW_PASSWORD);
            manifest.put("businessAId", businessAId.toString());
            manifest.put("businessAName", BIZ_A_NAME);
            manifest.put("businessBId", businessBId.toString());
            manifest.put("businessBName", BIZ_B_NAME);
            manifest.put("account1Id", acc1.getId().toString());
            manifest.put("account2Id", acc2.getId().toString());
            manifest.put("account3Id", acc3.getId().toString());
            manifest.put("obligationId", ob.id().toString());
            manifest.put("customerCounterpartyId", customer.id().toString());
            manifest.put("supplierCounterpartyId", supplier.id().toString());
            manifest.put("zenithAccountId", accB.getId().toString());

            Path tmpDir = Path.of("tmp").toAbsolutePath().normalize();
            if (!Files.exists(tmpDir)) {
                Files.createDirectories(tmpDir);
            }
            Path manifestPath = tmpDir.resolve("finsight-live-fixture-s4b.json");
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), manifest);

            System.out.println("\nSUCCESS: S4B Disposable Fixtures Seeded Cleanly!");
            System.out.println("Manifest written to: " + manifestPath);
            System.out.println("Seeded Account 1 (Main Operations): " + bal1 + " PKR");
            System.out.println("Seeded Account 2 (Reserve Account): " + bal2 + " PKR");
            System.out.println("Seeded Account 3 (Trade Collections): " + bal3 + " PKR");
            System.out.println("Seeded 3-Account Subtotal: 195000.00 PKR");
            System.out.println("Seeded Obligation Outstanding: " + obFinal.outstandingAmount() + " PKR");
            System.out.println("Zenith Account: 88888.00 PKR");
        }
    }
}
