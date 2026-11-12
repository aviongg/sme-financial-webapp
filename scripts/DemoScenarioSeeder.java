import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

public class DemoScenarioSeeder {

    private static final String DB_URL = "jdbc:postgresql://127.0.0.1:5433/finsight_demo";
    private static final String DB_USER = "finsight_app";
    private static final String DB_PASS = "DemoDbPassword2026!";
    private static final String CRYPTO_KEY_B64 = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=";
    private static final String COUNTERPARTY_AAD = "FinSight|CommercialDocument|counterpartyName";

    private final Connection conn;
    private final Argon2PasswordEncoder passwordEncoder;
    private final byte[] aesKeyBytes;
    private final SecureRandom secureRandom = new SecureRandom();

    public DemoScenarioSeeder(Connection conn) {
        this.conn = conn;
        this.passwordEncoder = new Argon2PasswordEncoder(16, 32, 1, 65536, 3);
        this.aesKeyBytes = Base64.getDecoder().decode(CRYPTO_KEY_B64);
    }

    private String encryptAesGcm(String plaintext, String aad) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(aesKeyBytes, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
            if (aad != null && !aad.isEmpty()) {
                cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            }
            byte[] cipherAndTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[12 + cipherAndTag.length];
            System.arraycopy(iv, 0, combined, 0, 12);
            System.arraycopy(cipherAndTag, 0, combined, 12, cipherAndTag.length);
            return "enc:v1:k1:" + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption error", e);
        }
    }

    private static final String DB_ADMIN_USER = "postgres";
    private static final String DB_ADMIN_PASS = "DemoDbPassword2026!";

    public static void main(String[] args) throws Exception {
        boolean resetOnlySandbox = args.length > 0 && "--reset-sandbox".equalsIgnoreCase(args[0]);
        System.out.println("Starting FinSight Demo Scenario Seeder (Mode: " + (resetOnlySandbox ? "RESET_SANDBOX_ONLY" : "FULL_SEED") + ")...");
        String user = resetOnlySandbox ? DB_ADMIN_USER : DB_USER;
        String pass = resetOnlySandbox ? DB_ADMIN_PASS : DB_PASS;
        try (Connection conn = DriverManager.getConnection(DB_URL, user, pass)) {
            conn.setAutoCommit(false);
            DemoScenarioSeeder seeder = new DemoScenarioSeeder(conn);
            if (resetOnlySandbox) {
                seeder.resetSandboxOnly();
            } else {
                seeder.seedAll();
            }
            conn.commit();
            System.out.println("Demo Scenario Seeding completed successfully!");
        }
    }

    public void resetSandboxOnly() throws Exception {
        System.out.println("1. Setting administrative replication role to bypass immutable audit triggers...");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SET session_replication_role = 'replica'");
        }

        System.out.println("2. Locating Sandbox Businesses...");
        List<UUID> sandboxBizIds = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT id, business_name FROM businesses WHERE business_name LIKE '%(Sandbox)'")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID id = (UUID) rs.getObject("id");
                    String bName = rs.getString("business_name");
                    sandboxBizIds.add(id);
                    System.out.println("   Found Sandbox Business: " + bName + " (" + id + ")");
                }
            }
        }

        System.out.println("3. Purging Sandbox Business Records...");
        for (UUID bizId : sandboxBizIds) {
            String[] deleteSqls = {
                "DELETE FROM fx_obligation_settlements WHERE business_id = ?",
                "DELETE FROM fx_obligation_recognition_evidence WHERE business_id = ?",
                "DELETE FROM obligation_payment_allocations WHERE business_id = ?",
                "DELETE FROM financial_obligations WHERE business_id = ?",
                "DELETE FROM commission_payouts WHERE business_id = ?",
                "DELETE FROM commission_earnings WHERE business_id = ?",
                "DELETE FROM commission_rules WHERE business_id = ?",
                "DELETE FROM salesperson_profiles WHERE business_id = ?",
                "DELETE FROM account_movements WHERE business_id = ?",
                "DELETE FROM financial_transactions WHERE business_id = ?",
                "DELETE FROM financial_accounts WHERE business_id = ?",
                "DELETE FROM inventory_positions WHERE business_id = ?",
                "DELETE FROM inventory_movements WHERE business_id = ?",
                "DELETE FROM commercial_document_lines WHERE business_id = ?",
                "DELETE FROM commercial_documents WHERE business_id = ?",
                "DELETE FROM commercial_number_sequences WHERE business_id = ?",
                "DELETE FROM inventory_items WHERE business_id = ?",
                "DELETE FROM project_budget_lines WHERE business_id = ?",
                "DELETE FROM project_budget_versions WHERE business_id = ?",
                "DELETE FROM projects WHERE business_id = ?",
                "DELETE FROM recommendations WHERE user_id = ?",
                "DELETE FROM insights WHERE user_id = ?",
                "DELETE FROM score_results WHERE user_id = ?",
                "DELETE FROM monthly_financing_inputs WHERE business_id = ?",
                "DELETE FROM monthly_records WHERE user_id = ?",
                "DELETE FROM monthly_projection_state WHERE business_id = ?",
                "DELETE FROM dashboard_preferences WHERE business_id = ?",
                "DELETE FROM business_configuration WHERE business_id = ?",
                "DELETE FROM business_memberships WHERE business_id = ?",
                "DELETE FROM business_profiles WHERE user_id = ?",
                "DELETE FROM businesses WHERE id = ?"
            };
            for (String sql : deleteSqls) {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, bizId);
                    ps.executeUpdate();
                }
            }
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SET session_replication_role = 'origin'");
        }

        System.out.println("4. Re-seeding Sandbox Businesses from Clean Master Template...");
        Map<String, UUID> users = seedUsers();
        seedBusinessA(users, true);
        seedBusinessB(users, true);
        seedBusinessC(users, true);
        seedBusinessD(users, true);
        System.out.println("Sandbox businesses successfully reset to pristine state!");
    }

    public void seedAll() throws Exception {
        System.out.println("1. Seeding Users...");
        Map<String, UUID> users = seedUsers();

        // Ensure all existing business_profiles have encrypted whatsapp_number
        String encPhoneFixed = encryptAesGcm("+923001234567", "FinSight|BusinessProfile|whatsappNumber");
        try (PreparedStatement upd = conn.prepareStatement("UPDATE business_profiles SET whatsapp_number = ? WHERE whatsapp_number IS NULL OR whatsapp_number NOT LIKE 'enc:v1:%'")) {
            upd.setString(1, encPhoneFixed);
            upd.executeUpdate();
        }

        System.out.println("2. Seeding Businesses and Scenarios...");
        // 4 Showcase businesses
        seedBusinessA(users, false);
        seedBusinessB(users, false);
        seedBusinessC(users, false);
        seedBusinessD(users, false);

        // 4 Sandbox businesses (parallel copy for user test playground)
        seedBusinessA(users, true);
        seedBusinessB(users, true);
        seedBusinessC(users, true);
        seedBusinessD(users, true);
    }

    private Map<String, UUID> seedUsers() throws Exception {
        Map<String, UUID> userMap = new HashMap<>();

        String[][] userData = {
            {"demo.owner@finsight.local", "Demo Business Owner", "OWNER", "FinSight@Owner2026!"},
            {"demo.accountant@finsight.local", "Demo Lead Accountant", "ACCOUNTANT", "FinSight@Account2026!"},
            {"demo.sales@finsight.local", "Demo Sales Executive", "SALESPERSON", "FinSight@Sales2026!"},
            {"demo.viewer@finsight.local", "Demo Auditor / Viewer", "VIEWER", "FinSight@Viewer2026!"}
        };

        for (String[] u : userData) {
            String email = u[0];
            String name = u[1];
            String rawPassword = u[3];
            String encodedHash = passwordEncoder.encode(rawPassword);
            String encName = encryptAesGcm(name, "FinSight|AppUser|fullName");

            // Check if user exists
            UUID userId;
            try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM app_users WHERE email = ?")) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        userId = (UUID) rs.getObject("id");
                        try (PreparedStatement upd = conn.prepareStatement("UPDATE app_users SET full_name = ?, password_hash = ? WHERE id = ?")) {
                            upd.setString(1, encName);
                            upd.setString(2, encodedHash);
                            upd.setObject(3, userId);
                            upd.executeUpdate();
                        }
                    } else {
                        userId = UUID.randomUUID();
                        try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO app_users (id, email, password_hash, full_name, account_status, must_change_password, auth_version, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, 'ACTIVE', false, 0, now(), now())"
                        )) {
                            ins.setObject(1, userId);
                            ins.setString(2, email);
                            ins.setString(3, encodedHash);
                            ins.setString(4, encName);
                            ins.executeUpdate();
                        }
                    }
                }
            }
            userMap.put(email, userId);
            System.out.println("   User created/verified: " + email + " (" + name + ")");
        }
        return userMap;
    }

    private UUID createBusiness(String name, String type, String operatingProfile, Map<String, UUID> users) throws Exception {
        UUID bizId = UUID.randomUUID();

        // 1. businesses
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO businesses (id, business_name, status, created_at, updated_at) VALUES (?, ?, 'ACTIVE', now(), now())"
        )) {
            ps.setObject(1, bizId);
            ps.setString(2, name);
            ps.executeUpdate();
        }

        // 2. business_profiles
        String encPhone = encryptAesGcm("+923001234567", "FinSight|BusinessProfile|whatsappNumber");
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO business_profiles (user_id, business_type, language_preference, whatsapp_number, whatsapp_opt_in, created_at) " +
            "VALUES (?, ?, 'en', ?, true, now())"
        )) {
            ps.setObject(1, bizId);
            ps.setString(2, type);
            ps.setString(3, encPhone);
            ps.executeUpdate();
        }

        // 3. business_configuration
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO business_configuration (business_id, operating_profile, operating_answers, requested_modules, version, configured, updated_at) " +
            "VALUES (?, ?, '{}'::jsonb, '[\"core_finance\",\"documents\",\"commercial\",\"inventory\",\"projects\",\"whatsapp\",\"ai\"]'::jsonb, 1, true, now())"
        )) {
            ps.setObject(1, bizId);
            ps.setString(2, operatingProfile);
            ps.executeUpdate();
        }

        // 4. business_memberships for each demo user
        String[] roles = {"OWNER", "ACCOUNTANT", "SALESPERSON", "VIEWER"};
        String[] emails = {
            "demo.owner@finsight.local",
            "demo.accountant@finsight.local",
            "demo.sales@finsight.local",
            "demo.viewer@finsight.local"
        };
        for (int i = 0; i < emails.length; i++) {
            UUID uid = users.get(emails[i]);
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO business_memberships (id, user_id, business_id, role, status, created_at, updated_at) " +
                "VALUES (gen_random_uuid(), ?, ?, ?, 'ACTIVE', now(), now()) ON CONFLICT DO NOTHING"
            )) {
                ps.setObject(1, uid);
                ps.setObject(2, bizId);
                ps.setString(3, roles[i]);
                ps.executeUpdate();
            }

            // dashboard_preferences
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO dashboard_preferences (business_id, user_id, overrides_json, registry_version, version, updated_at) " +
                "VALUES (?, ?, '[]'::jsonb, 'v1', 1, now()) ON CONFLICT DO NOTHING"
            )) {
                ps.setObject(1, bizId);
                ps.setObject(2, uid);
                ps.executeUpdate();
            }
        }

        // 5. commercial_number_sequences
        for (String kind : new String[]{"INVOICE", "BILL"}) {
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO commercial_number_sequences (business_id, kind, next_value, created_at, updated_at) " +
                "VALUES (?, ?, 10, now(), now()) ON CONFLICT DO NOTHING"
            )) {
                ps.setObject(1, bizId);
                ps.setString(2, kind);
                ps.executeUpdate();
            }
        }

        // 6. monthly_projection_state
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO monthly_projection_state (business_id, dirty, dirty_seq, dirty_since, projected_through, last_projected_at) " +
            "VALUES (?, false, 1, now(), '2026-10', now()) ON CONFLICT DO NOTHING"
        )) {
            ps.setObject(1, bizId);
            ps.executeUpdate();
        }

        return bizId;
    }

    private void seedHistoricalMonthlyAndScores(UUID bizId, double baseInflow, double baseOutflow, double baseRevenue, double baseCogs, double baseOpex) throws Exception {
        String[] months = {"2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10"};
        double cash = 1000000.0;

        for (int i = 0; i < months.length; i++) {
            String m = months[i];
            double growth = 1.0 + (i * 0.04);
            double inflow = Math.round(baseInflow * growth);
            double outflow = Math.round(baseOutflow * (growth * 0.98));
            double revenue = Math.round(baseRevenue * growth);
            double cogs = Math.round(baseCogs * growth);
            double opex = Math.round(baseOpex * (1.0 + (i * 0.02)));
            cash += (inflow - outflow);
            double ar = Math.round(revenue * 0.25);
            double ap = Math.round(cogs * 0.20);
            double inv = Math.round(cogs * 0.80);

            // Insert monthly_records
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO monthly_records (id, user_id, month, cash_inflow, cash_outflow, revenue, cogs, operating_expenses, " +
                "cash_balance_eom, receivables_outstanding, payables_outstanding, inventory_value, financing_type, source, version, updated_at) " +
                "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'none', 'LEGACY_MANUAL', 1, now()) " +
                "ON CONFLICT (user_id, month) DO UPDATE SET cash_inflow = EXCLUDED.cash_inflow, revenue = EXCLUDED.revenue"
            )) {
                ps.setObject(1, bizId);
                ps.setString(2, m);
                ps.setBigDecimal(3, BigDecimal.valueOf(inflow));
                ps.setBigDecimal(4, BigDecimal.valueOf(outflow));
                ps.setBigDecimal(5, BigDecimal.valueOf(revenue));
                ps.setBigDecimal(6, BigDecimal.valueOf(cogs));
                ps.setBigDecimal(7, BigDecimal.valueOf(opex));
                ps.setBigDecimal(8, BigDecimal.valueOf(cash));
                ps.setBigDecimal(9, BigDecimal.valueOf(ar));
                ps.setBigDecimal(10, BigDecimal.valueOf(ap));
                ps.setBigDecimal(11, BigDecimal.valueOf(inv));
                ps.executeUpdate();
            }

            // Insert score_results
            double compScore = Math.min(88.0, 72.0 + (i * 2.5));
            String band = compScore >= 80.0 ? "Strong" : "Good";
            String compJson = String.format(
                "{\"cashflow\": %.1f, \"profitability\": %.1f, \"repayment\": %.1f, \"trend\": %.1f, \"compliance\": 85.0}",
                Math.min(90.0, 75.0 + i * 2.0),
                Math.min(88.0, 70.0 + i * 2.5),
                85.0,
                Math.min(92.0, 68.0 + i * 3.5)
            );
            String explJson = "{\"historyMonthsAvailable\": 6, \"previousMonth\": \"2026-09\", \"previousScore\": 82.5, \"overallDelta\": 1.5, \"components\": {}, \"majorChanges\": []}";

            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO score_results (id, user_id, month, composite_score, band, component_scores, weakest_component, data_completeness, methodology_version, explanation, computed_at) " +
                "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?::jsonb, 'trend', 1.00, 'health-score-v1', ?::jsonb, now()) " +
                "ON CONFLICT (user_id, month) DO UPDATE SET composite_score = EXCLUDED.composite_score"
            )) {
                ps.setObject(1, bizId);
                ps.setString(2, m);
                ps.setBigDecimal(3, BigDecimal.valueOf(compScore));
                ps.setString(4, band);
                ps.setString(5, compJson);
                ps.setString(6, explJson);
                ps.executeUpdate();
            }

            // Recommendations (bilingual EN and UR)
            if (i == months.length - 1) { // Current month
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO recommendations (id, user_id, month, recommendation_text, category, priority, language, status, created_at) " +
                    "VALUES (gen_random_uuid(), ?, ?, ?, 'RECEIVABLES', 'medium', 'en', 'NEW', now()) " +
                    "ON CONFLICT (user_id, month, category) DO NOTHING"
                )) {
                    ps.setObject(1, bizId);
                    ps.setString(2, m);
                    ps.setString(3, "Follow up on customer receivables over 30 days old to maintain cash velocity.");
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO recommendations (id, user_id, month, recommendation_text, category, priority, language, status, created_at) " +
                    "VALUES (gen_random_uuid(), ?, ?, ?, 'CASH_BUFFER', 'low', 'ur', 'NEW', now()) " +
                    "ON CONFLICT (user_id, month, category) DO NOTHING"
                )) {
                    ps.setObject(1, bizId);
                    ps.setString(2, m);
                    ps.setString(3, "کاروبار کے استحکام کے لیے کیش بفر کو برقرار رکھیں۔");
                    ps.executeUpdate();
                }
            }
        }
    }

    // =========================================================================
    // Business A: Manufacturing (Alpha Garments & Packaging Ltd)
    // WAC scenario:
    // Receipt 1: 100 @ 500 = 50,000
    // Receipt 2: 50 @ 800 = 40,000 => 150 @ 600 WAC (90,000 value)
    // Issue: 40 @ 600 = 24,000 COGS => Remaining: 110 @ 600 = 66,000 value
    // =========================================================================
    private void seedBusinessA(Map<String, UUID> users, boolean isSandbox) throws Exception {
        String name = isSandbox ? "Alpha Garments & Packaging (Sandbox)" : "Alpha Garments & Packaging Ltd";
        System.out.println("   Seeding " + name + "...");
        UUID bizId = createBusiness(name, "manufacturing", "MANUFACTURING", users);
        UUID ownerId = users.get("demo.owner@finsight.local");

        // 1. Accounts
        UUID bankAccId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO financial_accounts (id, business_id, account_name, account_type, currency, currency_decimals, opening_balance, opening_date, status, institution_name, account_identifier, created_at, updated_at) " +
            "VALUES (?, ?, 'Meezan Bank Operating Account', 'BANK', 'PKR', 2, 2500000.0000, '2026-01-01', 'ACTIVE', 'Meezan Bank', 'PK92MEZN00123456789012', now(), now())"
        )) {
            ps.setObject(1, bankAccId);
            ps.setObject(2, bizId);
            ps.executeUpdate();
        }

        UUID cashAccId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO financial_accounts (id, business_id, account_name, account_type, currency, currency_decimals, opening_balance, opening_date, status, created_at, updated_at) " +
            "VALUES (?, ?, 'Factory Petty Cash', 'CASH', 'PKR', 2, 250000.0000, '2026-01-01', 'ACTIVE', now(), now())"
        )) {
            ps.setObject(1, cashAccId);
            ps.setObject(2, bizId);
            ps.executeUpdate();
        }

        // 2. Counterparties
        UUID supplier1 = UUID.randomUUID(); // Indus Cotton Mills
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO counterparties (id, business_id, display_name, type, status, email, phone, created_at, updated_at) " +
            "VALUES (?, ?, 'Indus Cotton Mills', 'SUPPLIER', 'ACTIVE', 'sales@induscotton.pk', '+923214567890', now(), now())"
        )) {
            ps.setObject(1, supplier1);
            ps.setObject(2, bizId);
            ps.executeUpdate();
        }

        UUID supplier2 = UUID.randomUUID(); // Premier Packaging Boxes
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO counterparties (id, business_id, display_name, type, status, email, phone, created_at, updated_at) " +
            "VALUES (?, ?, 'Premier Packaging Boxes', 'SUPPLIER', 'ACTIVE', 'orders@premierbox.pk', '+923007654321', now(), now())"
        )) {
            ps.setObject(1, supplier2);
            ps.setObject(2, bizId);
            ps.executeUpdate();
        }

        UUID customer1 = UUID.randomUUID(); // Gul Ahmed Retailers
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO counterparties (id, business_id, display_name, type, status, email, phone, created_at, updated_at) " +
            "VALUES (?, ?, 'Gul Ahmed Retailers', 'CUSTOMER', 'ACTIVE', 'procurement@gulahmedretail.com', '+923331122334', now(), now())"
        )) {
            ps.setObject(1, customer1);
            ps.setObject(2, bizId);
            ps.executeUpdate();
        }

        // 3. Inventory Items
        UUID cottonItem = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO inventory_items (id, business_id, sku, name, unit, item_kind, category, reorder_level, status, version, created_by, updated_by, created_at, updated_at) " +
            "VALUES (?, ?, 'COTTON-FABRIC-01', 'Cotton Fabric Roll 60in', 'meters', 'RAW_MATERIAL', 'Fabrics', 50.000000, 'ACTIVE', 0, ?, ?, now(), now())"
        )) {
            ps.setObject(1, cottonItem);
            ps.setObject(2, bizId);
            ps.setObject(3, ownerId);
            ps.setObject(4, ownerId);
            ps.executeUpdate();
        }

        UUID boxItem = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO inventory_items (id, business_id, sku, name, unit, item_kind, category, reorder_level, status, version, created_by, updated_by, created_at, updated_at) " +
            "VALUES (?, ?, 'PACKAGING-BOX-01', 'Carton Packaging Large', 'pieces', 'CONSUMABLE', 'Packaging', 100.000000, 'ACTIVE', 0, ?, ?, now(), now())"
        )) {
            ps.setObject(1, boxItem);
            ps.setObject(2, bizId);
            ps.setObject(3, ownerId);
            ps.setObject(4, ownerId);
            ps.executeUpdate();
        }

        // 4. Project
        UUID projectId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO projects (id, business_id, code, name, kind, description, status, start_date, target_end_date, base_currency, version, created_by, updated_by, created_at, updated_at) " +
            "VALUES (?, ?, 'PRJ-GARMENTS-2026', 'Summer 2026 Oxford Shirts Production', 'PROJECT', 'Bulk production run of premium Oxford formal shirts', 'ACTIVE', '2026-08-01', '2026-11-30', 'PKR', 0, ?, ?, now(), now())"
        )) {
            ps.setObject(1, projectId);
            ps.setObject(2, bizId);
            ps.setObject(3, ownerId);
            ps.setObject(4, ownerId);
            ps.executeUpdate();
        }

        // 5. Authoritative Perpetual Inventory Movements & WAC Policy f08-wac-v1:
        // Receipt 1: 100 units @ 500 = 50,000 PKR
        UUID mov1 = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO inventory_movements (id, business_id, item_id, sequence_no, movement_type, quantity_delta, value_delta, " +
            "quantity_after, value_after, movement_date, source_type, idempotency_key, payload_hash, policy_version, notes, created_by, created_at) " +
            "VALUES (?, ?, ?, 1, 'RECEIPT', 100.000000, 50000.0000, 100.000000, 50000.0000, '2026-09-05', 'SUPPLIER_BILL', ?, 'hash1', 'f08-wac-v1', 'Receipt 1: 100m @ 500 PKR', ?, now())"
        )) {
            ps.setObject(1, mov1);
            ps.setObject(2, bizId);
            ps.setObject(3, cottonItem);
            ps.setString(4, "rcpt1-" + bizId);
            ps.setObject(5, ownerId);
            ps.executeUpdate();
        }

        // Receipt 2: 50 units @ 800 = 40,000 PKR -> total 150 @ 600 WAC (90,000 PKR)
        UUID mov2 = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO inventory_movements (id, business_id, item_id, sequence_no, movement_type, quantity_delta, value_delta, " +
            "quantity_after, value_after, movement_date, source_type, idempotency_key, payload_hash, policy_version, notes, created_by, created_at) " +
            "VALUES (?, ?, ?, 2, 'RECEIPT', 50.000000, 40000.0000, 150.000000, 90000.0000, '2026-09-15', 'SUPPLIER_BILL', ?, 'hash2', 'f08-wac-v1', 'Receipt 2: 50m @ 800 PKR (WAC moves to 600 PKR/m)', ?, now())"
        )) {
            ps.setObject(1, mov2);
            ps.setObject(2, bizId);
            ps.setObject(3, cottonItem);
            ps.setString(4, "rcpt2-" + bizId);
            ps.setObject(5, ownerId);
            ps.executeUpdate();
        }

        // Issue: 40 units for production/sale -> COGS = 40 * 600 = 24,000 PKR -> Remaining 110 units @ 600 = 66,000 PKR
        UUID mov3 = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO inventory_movements (id, business_id, item_id, sequence_no, movement_type, purpose, project_id, quantity_delta, value_delta, " +
            "quantity_after, value_after, movement_date, source_type, idempotency_key, payload_hash, policy_version, notes, created_by, created_at) " +
            "VALUES (?, ?, ?, 3, 'ISSUE', 'PROJECT', ?, -40.000000, -24000.0000, 110.000000, 66000.0000, '2026-09-25', 'PROJECT', ?, 'hash3', 'f08-wac-v1', 'Issue 40m for Oxford Shirts (COGS 24,000 PKR @ WAC 600)', ?, now())"
        )) {
            ps.setObject(1, mov3);
            ps.setObject(2, bizId);
            ps.setObject(3, cottonItem);
            ps.setObject(4, projectId);
            ps.setString(5, "issue1-" + bizId);
            ps.setObject(6, ownerId);
            ps.executeUpdate();
        }

        // Position record for cottonItem (must match latest movement state: 110 units, 66000 value, seq 3)
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO inventory_positions (business_id, item_id, quantity, value, last_sequence_no, last_movement_date, version, updated_at) " +
            "VALUES (?, ?, 110.000000, 66000.0000, 3, '2026-09-25', 0, now())"
        )) {
            ps.setObject(1, bizId);
            ps.setObject(2, cottonItem);
            ps.executeUpdate();
        }

        // 6. Obligations (Supplier Bills and Customer Invoices)
        // Bill 1: 50,000 PKR for Indus Cotton Mills
        UUID ob1 = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO financial_obligations (id, business_id, counterparty_id, direction, source_type, reference_number, issue_date, due_date, original_amount, currency, status, created_by_user_id, created_at, updated_at) " +
            "VALUES (?, ?, ?, 'PAYABLE', 'SUPPLIER_BILL', 'BILL-000001', '2026-09-05', '2026-10-05', 50000.0000, 'PKR', 'OPEN', ?, now(), now())"
        )) {
            ps.setObject(1, ob1);
            ps.setObject(2, bizId);
            ps.setObject(3, supplier1);
            ps.setObject(4, ownerId);
            ps.executeUpdate();
        }

        // Transaction & Allocation for Bill 1 Payment
        UUID tx1 = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO financial_transactions (id, business_id, account_id, counterparty_id, event_type, movement_direction, amount, currency, base_amount_pkr, effective_date, description, created_by_user_id, created_at) " +
            "VALUES (?, ?, ?, ?, 'PAYABLE_SETTLEMENT', 'DECREASE', 50000.0000, 'PKR', 50000.0000, '2026-09-28', 'Payment for Bill-000001 (Indus Cotton Mills)', ?, now())"
        )) {
            ps.setObject(1, tx1);
            ps.setObject(2, bizId);
            ps.setObject(3, bankAccId);
            ps.setObject(4, supplier1);
            ps.setObject(5, ownerId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO account_movements (id, business_id, transaction_id, account_id, movement_direction, amount, currency, effective_date, created_at) " +
            "VALUES (gen_random_uuid(), ?, ?, ?, 'DECREASE', 50000.0000, 'PKR', '2026-09-28', now())"
        )) {
            ps.setObject(1, bizId);
            ps.setObject(2, tx1);
            ps.setObject(3, bankAccId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO obligation_payment_allocations (id, business_id, obligation_id, financial_transaction_id, amount, effective_date, status, allocation_type, created_by_user_id, created_at) " +
            "VALUES (gen_random_uuid(), ?, ?, ?, 50000.0000, '2026-09-28', 'EFFECTIVE', 'CASH', ?, now())"
        )) {
            ps.setObject(1, bizId);
            ps.setObject(2, ob1);
            ps.setObject(3, tx1);
            ps.setObject(4, ownerId);
            ps.executeUpdate();
        }

        // Bill 2: 40,000 PKR for Premier Packaging Boxes (Paid)
        UUID ob2 = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO financial_obligations (id, business_id, counterparty_id, direction, source_type, reference_number, issue_date, due_date, original_amount, currency, status, created_by_user_id, created_at, updated_at) " +
            "VALUES (?, ?, ?, 'PAYABLE', 'SUPPLIER_BILL', 'BILL-000002', '2026-09-15', '2026-10-15', 40000.0000, 'PKR', 'OPEN', ?, now(), now())"
        )) {
            ps.setObject(1, ob2);
            ps.setObject(2, bizId);
            ps.setObject(3, supplier2);
            ps.setObject(4, ownerId);
            ps.executeUpdate();
        }
        UUID tx2 = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO financial_transactions (id, business_id, account_id, counterparty_id, event_type, movement_direction, amount, currency, base_amount_pkr, effective_date, description, created_by_user_id, created_at) " +
            "VALUES (?, ?, ?, ?, 'PAYABLE_SETTLEMENT', 'DECREASE', 40000.0000, 'PKR', 40000.0000, '2026-10-10', 'Payment for Bill-000002 (Premier Packaging)', ?, now())"
        )) {
            ps.setObject(1, tx2);
            ps.setObject(2, bizId);
            ps.setObject(3, bankAccId);
            ps.setObject(4, supplier2);
            ps.setObject(5, ownerId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO account_movements (id, business_id, transaction_id, account_id, movement_direction, amount, currency, effective_date, created_at) " +
            "VALUES (gen_random_uuid(), ?, ?, ?, 'DECREASE', 40000.0000, 'PKR', '2026-10-10', now())"
        )) {
            ps.setObject(1, bizId);
            ps.setObject(2, tx2);
            ps.setObject(3, bankAccId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO obligation_payment_allocations (id, business_id, obligation_id, financial_transaction_id, amount, effective_date, status, allocation_type, created_by_user_id, created_at) " +
            "VALUES (gen_random_uuid(), ?, ?, ?, 40000.0000, '2026-10-10', 'EFFECTIVE', 'CASH', ?, now())"
        )) {
            ps.setObject(1, bizId);
            ps.setObject(2, ob2);
            ps.setObject(3, tx2);
            ps.setObject(4, ownerId);
            ps.executeUpdate();
        }

        // Invoice 1: 180,000 PKR to Gul Ahmed Retailers (120,000 paid, 60,000 open receivable)
        UUID ob3 = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO financial_obligations (id, business_id, counterparty_id, direction, source_type, reference_number, issue_date, due_date, original_amount, currency, status, created_by_user_id, created_at, updated_at) " +
            "VALUES (?, ?, ?, 'RECEIVABLE', 'CUSTOMER_INVOICE', 'INV-000001', '2026-09-20', '2026-10-20', 180000.0000, 'PKR', 'OPEN', ?, now(), now())"
        )) {
            ps.setObject(1, ob3);
            ps.setObject(2, bizId);
            ps.setObject(3, customer1);
            ps.setObject(4, ownerId);
            ps.executeUpdate();
        }
        UUID tx3 = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO financial_transactions (id, business_id, account_id, counterparty_id, event_type, movement_direction, amount, currency, base_amount_pkr, effective_date, description, created_by_user_id, created_at) " +
            "VALUES (?, ?, ?, ?, 'RECEIVABLE_COLLECTION', 'INCREASE', 120000.0000, 'PKR', 120000.0000, '2026-10-05', 'Partial collection on INV-000001 (Gul Ahmed)', ?, now())"
        )) {
            ps.setObject(1, tx3);
            ps.setObject(2, bizId);
            ps.setObject(3, bankAccId);
            ps.setObject(4, customer1);
            ps.setObject(5, ownerId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO account_movements (id, business_id, transaction_id, account_id, movement_direction, amount, currency, effective_date, created_at) " +
            "VALUES (gen_random_uuid(), ?, ?, ?, 'INCREASE', 120000.0000, 'PKR', '2026-10-05', now())"
        )) {
            ps.setObject(1, bizId);
            ps.setObject(2, tx3);
            ps.setObject(3, bankAccId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO obligation_payment_allocations (id, business_id, obligation_id, financial_transaction_id, amount, effective_date, status, allocation_type, created_by_user_id, created_at) " +
            "VALUES (gen_random_uuid(), ?, ?, ?, 120000.0000, '2026-10-05', 'EFFECTIVE', 'CASH', ?, now())"
        )) {
            ps.setObject(1, bizId);
            ps.setObject(2, ob3);
            ps.setObject(3, tx3);
            ps.setObject(4, ownerId);
            ps.executeUpdate();
        }

        // 7. Commercial Documents (Finalized Commercial Invoice & Bill using proper lifecycle)
        UUID docInvoice = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO commercial_documents (id, business_id, kind, status, created_by_user_id, updated_by_user_id, created_at, updated_at) " +
            "VALUES (?, ?, 'INVOICE', 'DRAFT', ?, ?, now(), now())"
        )) {
            ps.setObject(1, docInvoice);
            ps.setObject(2, bizId);
            ps.setObject(3, ownerId);
            ps.setObject(4, ownerId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO commercial_document_lines (id, business_id, document_id, line_number, description, quantity, unit, unit_price, line_amount, inventory_item_id) " +
            "VALUES (gen_random_uuid(), ?, ?, 1, 'Custom Oxford Shirts Batch A', 100.000000, 'pieces', 1800.00, 180000.00, ?)"
        )) {
            ps.setObject(1, bizId);
            ps.setObject(2, docInvoice);
            ps.setObject(3, cottonItem);
            ps.executeUpdate();
        }
        // Finalize document
        String encCpName = encryptAesGcm("Gul Ahmed Retailers", COUNTERPARTY_AAD);
        try (PreparedStatement ps = conn.prepareStatement(
            "UPDATE commercial_documents SET status = 'FINALIZED', document_number = 'INV-000001', counterparty_name_ciphertext = ?, " +
            "document_date = '2026-09-20', due_date = '2026-10-20', subtotal = 180000.00, total = 180000.00, " +
            "finalized_by_user_id = ?, finalized_at = now(), version = 1, updated_at = now() WHERE id = ?"
        )) {
            ps.setString(1, encCpName);
            ps.setObject(2, ownerId);
            ps.setObject(3, docInvoice);
            ps.executeUpdate();
        }

        // 8. 6-Month Historical Monthly Records and Health Scores
        seedHistoricalMonthlyAndScores(bizId, 850000.0, 720000.0, 920000.0, 480000.0, 240000.0);
    }

    // =========================================================================
    // Business B: Services (Apex Digital Solutions)
    // Invoices: 2,500,000 PKR; Cash collected: 1,600,000 PKR; AR: 900,000 PKR
    // Salesperson commissions: 5% on 1,600,000 = 80,000 PKR, Payout: 60,000 PKR
    // =========================================================================
    private void seedBusinessB(Map<String, UUID> users, boolean isSandbox) throws Exception {
        String name = isSandbox ? "Apex Digital Solutions (Sandbox)" : "Apex Digital Solutions";
        System.out.println("   Seeding " + name + "...");
        UUID bizId = createBusiness(name, "services", "SERVICES", users);
        UUID ownerId = users.get("demo.owner@finsight.local");
        UUID salesUserId = users.get("demo.sales@finsight.local");

        // Accounts
        UUID bankAccId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO financial_accounts (id, business_id, account_name, account_type, currency, currency_decimals, opening_balance, opening_date, status, institution_name, account_identifier, created_at, updated_at) " +
            "VALUES (?, ?, 'Habib Bank Corporate Account', 'BANK', 'PKR', 2, 4500000.0000, '2026-01-01', 'ACTIVE', 'Habib Bank Limited', 'PK12HABB00987654321098', now(), now())"
        )) {
            ps.setObject(1, bankAccId);
            ps.setObject(2, bizId);
            ps.executeUpdate();
        }

        // Counterparties
        UUID client1 = UUID.randomUUID(); // Pak Telecom Solutions
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO counterparties (id, business_id, display_name, type, status, email, phone, created_at, updated_at) " +
            "VALUES (?, ?, 'Pak Telecom Solutions', 'CUSTOMER', 'ACTIVE', 'it-procure@paktelecom.pk', '+92512345678', now(), now())"
        )) {
            ps.setObject(1, client1);
            ps.setObject(2, bizId);
            ps.executeUpdate();
        }

        UUID client2 = UUID.randomUUID(); // National Logistics Cell
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO counterparties (id, business_id, display_name, type, status, email, phone, created_at, updated_at) " +
            "VALUES (?, ?, 'National Logistics Cell', 'CUSTOMER', 'ACTIVE', 'digital@nlc.gov.pk', '+92519876543', now(), now())"
        )) {
            ps.setObject(1, client2);
            ps.setObject(2, bizId);
            ps.executeUpdate();
        }

        // Project
        UUID projectId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO projects (id, business_id, code, name, kind, description, status, start_date, target_end_date, base_currency, version, created_by, updated_by, created_at, updated_at) " +
            "VALUES (?, ?, 'PRJ-TELCO-CRM', 'Enterprise CRM Implementation', 'PROJECT', 'Multi-tenant cloud CRM migration and custom billing integration', 'ACTIVE', '2026-07-01', '2026-12-31', 'PKR', 0, ?, ?, now(), now())"
        )) {
            ps.setObject(1, projectId);
            ps.setObject(2, bizId);
            ps.setObject(3, ownerId);
            ps.setObject(4, ownerId);
            ps.executeUpdate();
        }

        // Salesperson Profile & Commission Rules
        UUID spProfileId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO salesperson_profiles (id, business_id, user_id, display_name, email, phone, status, version, created_at, updated_at) " +
            "VALUES (?, ?, ?, 'Zain Ahmed - Enterprise Sales', 'demo.sales@finsight.local', '+923009988776', 'ACTIVE', 0, now(), now())"
        )) {
            ps.setObject(1, spProfileId);
            ps.setObject(2, bizId);
            ps.setObject(3, salesUserId);
            ps.executeUpdate();
        }

        UUID ruleId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO commission_rules (id, business_id, salesperson_id, rule_name, calculation_type, earning_basis, rate_percentage, currency, effective_start_date, status, version, created_at, updated_at) " +
            "VALUES (?, ?, ?, '5% Collections Commission Rule', 'PERCENTAGE', 'COLLECTION_BASED', 5.0000, 'PKR', '2026-01-01', 'ACTIVE', 0, now(), now())"
        )) {
            ps.setObject(1, ruleId);
            ps.setObject(2, bizId);
            ps.setObject(3, spProfileId);
            ps.executeUpdate();
        }

        // Invoice 1: 1,500,000 PKR to Pak Telecom (900,000 collected, 600,000 outstanding AR)
        UUID ob1 = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO financial_obligations (id, business_id, counterparty_id, direction, source_type, reference_number, issue_date, due_date, original_amount, currency, status, created_by_user_id, created_at, updated_at) " +
            "VALUES (?, ?, ?, 'RECEIVABLE', 'CUSTOMER_INVOICE', 'INV-000001', '2026-09-01', '2026-10-01', 1500000.0000, 'PKR', 'OPEN', ?, now(), now())"
        )) {
            ps.setObject(1, ob1);
            ps.setObject(2, bizId);
            ps.setObject(3, client1);
            ps.setObject(4, ownerId);
            ps.executeUpdate();
        }
        UUID tx1 = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO financial_transactions (id, business_id, account_id, counterparty_id, event_type, movement_direction, amount, currency, base_amount_pkr, effective_date, description, created_by_user_id, created_at) " +
            "VALUES (?, ?, ?, ?, 'RECEIVABLE_COLLECTION', 'INCREASE', 900000.0000, 'PKR', 900000.0000, '2026-09-20', 'Milestone 1 Collection (Pak Telecom)', ?, now())"
        )) {
            ps.setObject(1, tx1);
            ps.setObject(2, bizId);
            ps.setObject(3, bankAccId);
            ps.setObject(4, client1);
            ps.setObject(5, ownerId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO account_movements (id, business_id, transaction_id, account_id, movement_direction, amount, currency, effective_date, created_at) " +
            "VALUES (gen_random_uuid(), ?, ?, ?, 'INCREASE', 900000.0000, 'PKR', '2026-09-20', now())"
        )) {
            ps.setObject(1, bizId);
            ps.setObject(2, tx1);
            ps.setObject(3, bankAccId);
            ps.executeUpdate();
        }
        UUID alloc1 = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO obligation_payment_allocations (id, business_id, obligation_id, financial_transaction_id, amount, effective_date, status, allocation_type, created_by_user_id, created_at) " +
            "VALUES (?, ?, ?, ?, 900000.0000, '2026-09-20', 'EFFECTIVE', 'CASH', ?, now())"
        )) {
            ps.setObject(1, alloc1);
            ps.setObject(2, bizId);
            ps.setObject(3, ob1);
            ps.setObject(4, tx1);
            ps.setObject(5, ownerId);
            ps.executeUpdate();
        }

        // Invoice 2: 1,000,000 PKR to NLC (700,000 collected, 300,000 outstanding AR)
        UUID ob2 = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO financial_obligations (id, business_id, counterparty_id, direction, source_type, reference_number, issue_date, due_date, original_amount, currency, status, created_by_user_id, created_at, updated_at) " +
            "VALUES (?, ?, ?, 'RECEIVABLE', 'CUSTOMER_INVOICE', 'INV-000002', '2026-10-01', '2026-11-01', 1000000.0000, 'PKR', 'OPEN', ?, now(), now())"
        )) {
            ps.setObject(1, ob2);
            ps.setObject(2, bizId);
            ps.setObject(3, client2);
            ps.setObject(4, ownerId);
            ps.executeUpdate();
        }
        UUID tx2 = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO financial_transactions (id, business_id, account_id, counterparty_id, event_type, movement_direction, amount, currency, base_amount_pkr, effective_date, description, created_by_user_id, created_at) " +
            "VALUES (?, ?, ?, ?, 'RECEIVABLE_COLLECTION', 'INCREASE', 700000.0000, 'PKR', 700000.0000, '2026-10-15', 'Advance Cloud Setup Collection (NLC)', ?, now())"
        )) {
            ps.setObject(1, tx2);
            ps.setObject(2, bizId);
            ps.setObject(3, bankAccId);
            ps.setObject(4, client2);
            ps.setObject(5, ownerId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO account_movements (id, business_id, transaction_id, account_id, movement_direction, amount, currency, effective_date, created_at) " +
            "VALUES (gen_random_uuid(), ?, ?, ?, 'INCREASE', 700000.0000, 'PKR', '2026-10-15', now())"
        )) {
            ps.setObject(1, bizId);
            ps.setObject(2, tx2);
            ps.setObject(3, bankAccId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO obligation_payment_allocations (id, business_id, obligation_id, financial_transaction_id, amount, effective_date, status, allocation_type, created_by_user_id, created_at) " +
            "VALUES (gen_random_uuid(), ?, ?, ?, 700000.0000, '2026-10-15', 'EFFECTIVE', 'CASH', ?, now())"
        )) {
            ps.setObject(1, bizId);
            ps.setObject(2, ob2);
            ps.setObject(3, tx2);
            ps.setObject(4, ownerId);
            ps.executeUpdate();
        }

        // Commission Earning: 5% of 1,600,000 = 80,000 PKR total earned
        UUID earningId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO commission_earnings (id, business_id, salesperson_id, rule_id, counterparty_id, source_type, source_id, source_fingerprint, sale_amount, commission_rate_snapshot, commission_amount, currency, status, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, 'OBLIGATION_ALLOCATION', ?, ?, 1600000.0000, 5.0000, 80000.0000, 'PKR', 'APPROVED', now(), now())"
        )) {
            ps.setObject(1, earningId);
            ps.setObject(2, bizId);
            ps.setObject(3, spProfileId);
            ps.setObject(4, ruleId);
            ps.setObject(5, client1);
            ps.setString(6, alloc1.toString());
            ps.setString(7, bizId.toString() + ":" + alloc1.toString());
            ps.executeUpdate();
        }

        // Commission Payout: 60,000 PKR paid out via HBL
        UUID payoutTx = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO financial_transactions (id, business_id, account_id, event_type, movement_direction, amount, currency, base_amount_pkr, effective_date, description, created_by_user_id, created_at) " +
            "VALUES (?, ?, ?, 'OPERATING_EXPENSE_PAYMENT', 'DECREASE', 60000.0000, 'PKR', 60000.0000, '2026-10-25', 'Sales Commission Payout to Zain Ahmed', ?, now())"
        )) {
            ps.setObject(1, payoutTx);
            ps.setObject(2, bizId);
            ps.setObject(3, bankAccId);
            ps.setObject(4, ownerId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO account_movements (id, business_id, transaction_id, account_id, movement_direction, amount, currency, effective_date, created_at) " +
            "VALUES (gen_random_uuid(), ?, ?, ?, 'DECREASE', 60000.0000, 'PKR', '2026-10-25', now())"
        )) {
            ps.setObject(1, bizId);
            ps.setObject(2, payoutTx);
            ps.setObject(3, bankAccId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO commission_payouts (id, business_id, salesperson_id, earning_id, payout_type, amount, currency, payment_date, financial_account_id, financial_transaction_id, status, created_by_user_id, created_at, updated_at) " +
            "VALUES (gen_random_uuid(), ?, ?, ?, 'PAYOUT', 60000.0000, 'PKR', '2026-10-25', ?, ?, 'SETTLED', ?, now(), now())"
        )) {
            ps.setObject(1, bizId);
            ps.setObject(2, spProfileId);
            ps.setObject(3, earningId);
            ps.setObject(4, bankAccId);
            ps.setObject(5, payoutTx);
            ps.setObject(6, ownerId);
            ps.executeUpdate();
        }

        // 6-Month Historical Monthly Records and Health Scores
        seedHistoricalMonthlyAndScores(bizId, 1600000.0, 1100000.0, 2200000.0, 500000.0, 600000.0);
    }

    // =========================================================================
    // Business C: Retail / Wholesale (Crescent Electronics Trading)
    // Overdue supplier bill (725,000 PKR past due) -> impact on health score
    // Zakat calculation baseline
    // =========================================================================
    private void seedBusinessC(Map<String, UUID> users, boolean isSandbox) throws Exception {
        String name = isSandbox ? "Crescent Electronics Trading (Sandbox)" : "Crescent Electronics Trading";
        System.out.println("   Seeding " + name + "...");
        UUID bizId = createBusiness(name, "retail", "RETAIL_TRADE", users);
        UUID ownerId = users.get("demo.owner@finsight.local");

        // Accounts
        UUID bankAccId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO financial_accounts (id, business_id, account_name, account_type, currency, currency_decimals, opening_balance, opening_date, status, institution_name, account_identifier, created_at, updated_at) " +
            "VALUES (?, ?, 'Bank Alfalah Trade Account', 'BANK', 'PKR', 2, 1200000.0000, '2026-01-01', 'ACTIVE', 'Bank Alfalah', 'PK33ALFH00554433221100', now(), now())"
        )) {
            ps.setObject(1, bankAccId);
            ps.setObject(2, bizId);
            ps.executeUpdate();
        }

        UUID cashAccId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO financial_accounts (id, business_id, account_name, account_type, currency, currency_decimals, opening_balance, opening_date, status, created_at, updated_at) " +
            "VALUES (?, ?, 'Retail Shop Petty Cash', 'CASH', 'PKR', 2, 150000.0000, '2026-01-01', 'ACTIVE', now(), now())"
        )) {
            ps.setObject(1, cashAccId);
            ps.setObject(2, bizId);
            ps.executeUpdate();
        }

        // Counterparties
        UUID supplier1 = UUID.randomUUID(); // Inverex Power Supplies
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO counterparties (id, business_id, display_name, type, status, email, phone, created_at, updated_at) " +
            "VALUES (?, ?, 'Inverex Power Supplies', 'SUPPLIER', 'ACTIVE', 'orders@inverex.pk', '+924235678901', now(), now())"
        )) {
            ps.setObject(1, supplier1);
            ps.setObject(2, bizId);
            ps.executeUpdate();
        }

        UUID customer1 = UUID.randomUUID(); // Lahore Electronics Mart
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO counterparties (id, business_id, display_name, type, status, email, phone, created_at, updated_at) " +
            "VALUES (?, ?, 'Lahore Electronics Mart', 'CUSTOMER', 'ACTIVE', 'sales@lahoremart.pk', '+923001122334', now(), now())"
        )) {
            ps.setObject(1, customer1);
            ps.setObject(2, bizId);
            ps.executeUpdate();
        }

        // Inventory Items (Retail products)
        UUID invItem = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO inventory_items (id, business_id, sku, name, unit, item_kind, category, reorder_level, status, version, created_by, updated_by, created_at, updated_at) " +
            "VALUES (?, ?, 'SOLAR-INV-5KW', 'Hybrid Solar Inverter 5kW', 'units', 'RESALE_GOOD', 'Inverters', 5.000000, 'ACTIVE', 0, ?, ?, now(), now())"
        )) {
            ps.setObject(1, invItem);
            ps.setObject(2, bizId);
            ps.setObject(3, ownerId);
            ps.setObject(4, ownerId);
            ps.executeUpdate();
        }

        // Movement for inverters: 15 units @ 145,000 = 2,175,000 PKR
        UUID movInv = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO inventory_movements (id, business_id, item_id, sequence_no, movement_type, quantity_delta, value_delta, " +
            "quantity_after, value_after, movement_date, source_type, idempotency_key, payload_hash, policy_version, notes, created_by, created_at) " +
            "VALUES (?, ?, ?, 1, 'RECEIPT', 15.000000, 2175000.0000, 15.000000, 2175000.0000, '2026-09-10', 'SUPPLIER_BILL', ?, 'hash_inv', 'f08-wac-v1', 'Stock in: 15 units @ 145,000 PKR', ?, now())"
        )) {
            ps.setObject(1, movInv);
            ps.setObject(2, bizId);
            ps.setObject(3, invItem);
            ps.setString(4, "rcpt-inv-" + bizId);
            ps.setObject(5, ownerId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO inventory_positions (business_id, item_id, quantity, value, last_sequence_no, last_movement_date, version, updated_at) " +
            "VALUES (?, ?, 15.000000, 2175000.0000, 1, '2026-09-10', 0, now())"
        )) {
            ps.setObject(1, bizId);
            ps.setObject(2, invItem);
            ps.executeUpdate();
        }

        // OVERDUE Supplier Bill (725,000 PKR past due from 2026-10-15)
        UUID overdueBill = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO financial_obligations (id, business_id, counterparty_id, direction, source_type, reference_number, issue_date, due_date, original_amount, currency, status, description, created_by_user_id, created_at, updated_at) " +
            "VALUES (?, ?, ?, 'PAYABLE', 'SUPPLIER_BILL', 'BILL-000101', '2026-09-15', '2026-10-15', 725000.0000, 'PKR', 'OPEN', 'Overdue supplier payment for solar stock', ?, now(), now())"
        )) {
            ps.setObject(1, overdueBill);
            ps.setObject(2, bizId);
            ps.setObject(3, supplier1);
            ps.setObject(4, ownerId);
            ps.executeUpdate();
        }

        // Customer retail sale
        UUID saleTx = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO financial_transactions (id, business_id, account_id, counterparty_id, event_type, movement_direction, amount, currency, base_amount_pkr, effective_date, description, created_by_user_id, created_at) " +
            "VALUES (?, ?, ?, ?, 'DIRECT_SALE_RECEIPT', 'INCREASE', 390000.0000, 'PKR', 390000.0000, '2026-10-18', 'Retail sale of 2 units 5kW Inverter (Cash)', ?, now())"
        )) {
            ps.setObject(1, saleTx);
            ps.setObject(2, bizId);
            ps.setObject(3, bankAccId);
            ps.setObject(4, customer1);
            ps.setObject(5, ownerId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO account_movements (id, business_id, transaction_id, account_id, movement_direction, amount, currency, effective_date, created_at) " +
            "VALUES (gen_random_uuid(), ?, ?, ?, 'INCREASE', 390000.0000, 'PKR', '2026-10-18', now())"
        )) {
            ps.setObject(1, bizId);
            ps.setObject(2, saleTx);
            ps.setObject(3, bankAccId);
            ps.executeUpdate();
        }

        // 6-Month Historical Monthly Records and Health Scores
        seedHistoricalMonthlyAndScores(bizId, 1200000.0, 950000.0, 1400000.0, 850000.0, 180000.0);
    }

    // =========================================================================
    // Business D: Exporter (Indus Textiles Export Corp)
    // Dual currency: PKR + USD accounts
    // Export sales invoice: $15,000 USD booked @ 278.00 PKR/USD (4,170,000 PKR)
    // Settlement: received @ 282.50 PKR/USD (4,237,500 PKR) -> Realized FX Gain +67,500 PKR
    // =========================================================================
    private void seedBusinessD(Map<String, UUID> users, boolean isSandbox) throws Exception {
        String name = isSandbox ? "Indus Textiles Export Corp (Sandbox)" : "Indus Textiles Export Corp";
        System.out.println("   Seeding " + name + "...");
        UUID bizId = createBusiness(name, "trade", "PROJECT_ORDER", users);
        UUID ownerId = users.get("demo.owner@finsight.local");

        // Dual-currency accounts
        UUID pkrAccId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO financial_accounts (id, business_id, account_name, account_type, currency, currency_decimals, opening_balance, opening_date, status, institution_name, account_identifier, created_at, updated_at) " +
            "VALUES (?, ?, 'Standard Chartered PKR Operating', 'BANK', 'PKR', 2, 5000000.0000, '2026-01-01', 'ACTIVE', 'Standard Chartered Bank', 'PK55SCBL00112233445566', now(), now())"
        )) {
            ps.setObject(1, pkrAccId);
            ps.setObject(2, bizId);
            ps.executeUpdate();
        }

        UUID usdAccId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO financial_accounts (id, business_id, account_name, account_type, currency, currency_decimals, opening_balance, opening_date, status, institution_name, account_identifier, created_at, updated_at) " +
            "VALUES (?, ?, 'Standard Chartered USD Export Settlement', 'BANK', 'USD', 2, 25000.0000, '2026-01-01', 'ACTIVE', 'Standard Chartered Bank', 'PK55SCBL00998877665544', now(), now())"
        )) {
            ps.setObject(1, usdAccId);
            ps.setObject(2, bizId);
            ps.executeUpdate();
        }

        // Counterparty
        UUID customerUSA = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO counterparties (id, business_id, display_name, type, status, email, phone, address, created_at, updated_at) " +
            "VALUES (?, ?, 'Global Garments USA Inc', 'CUSTOMER', 'ACTIVE', 'finance@globalgarmentsusa.com', '+12125550199', '742 Broadway, New York, NY 10003, USA', now(), now())"
        )) {
            ps.setObject(1, customerUSA);
            ps.setObject(2, bizId);
            ps.executeUpdate();
        }

        // USD Export Obligation (Invoice for $15,000 USD)
        UUID exportOb = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO financial_obligations (id, business_id, counterparty_id, direction, source_type, reference_number, issue_date, due_date, original_amount, currency, status, description, created_by_user_id, created_at, updated_at) " +
            "VALUES (?, ?, ?, 'RECEIVABLE', 'CUSTOMER_INVOICE', 'INV-US-2026-01', '2026-09-01', '2026-10-15', 15000.0000, 'USD', 'OPEN', 'Export consignment: 5,000 pcs Polo Shirts shipped to NY Port', ?, now(), now())"
        )) {
            ps.setObject(1, exportOb);
            ps.setObject(2, bizId);
            ps.setObject(3, customerUSA);
            ps.setObject(4, ownerId);
            ps.executeUpdate();
        }

        // FX Recognition Evidence (Booking rate: 278.000000 PKR/USD -> Book Value = 4,170,000 PKR)
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO fx_obligation_recognition_evidence (id, business_id, obligation_id, native_currency, native_amount, functional_currency, recognition_rate, rate_convention, book_value, recognition_date, recognition_source, evidence_provenance, created_by_user_id, created_at) " +
            "VALUES (gen_random_uuid(), ?, ?, 'USD', 15000.0000, 'PKR', 278.000000, 'BASE_TO_QUOTE', 4170000.0000, '2026-09-01', 'MANUAL_DECLARATION', 'VERIFIED_AT_CREATION', ?, now())"
        )) {
            ps.setObject(1, bizId);
            ps.setObject(2, exportOb);
            ps.setObject(3, ownerId);
            ps.executeUpdate();
        }

        // Settlement on 2026-10-15:
        // Converted at settlement exchange rate 282.500000 PKR/USD
        // Converted principal = 4,237,500 PKR
        // Realized FX Gain = 4,237,500 - 4,170,000 = +67,500 PKR
        // Bank wire fee = 2,500 PKR
        // Net received in PKR account = 4,235,000 PKR
        UUID fxTx = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO financial_transactions (id, business_id, account_id, counterparty_id, event_type, movement_direction, amount, currency, base_amount_pkr, effective_date, description, created_by_user_id, created_at) " +
            "VALUES (?, ?, ?, ?, 'RECEIVABLE_COLLECTION', 'INCREASE', 4235000.0000, 'PKR', 4235000.0000, '2026-10-15', 'Settlement of Export Invoice INV-US-2026-01 ($15,000 @ 282.50 less 2500 fee)', ?, now())"
        )) {
            ps.setObject(1, fxTx);
            ps.setObject(2, bizId);
            ps.setObject(3, pkrAccId);
            ps.setObject(4, customerUSA);
            ps.setObject(5, ownerId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO account_movements (id, business_id, transaction_id, account_id, movement_direction, amount, currency, effective_date, created_at) " +
            "VALUES (gen_random_uuid(), ?, ?, ?, 'INCREASE', 4235000.0000, 'PKR', '2026-10-15', now())"
        )) {
            ps.setObject(1, bizId);
            ps.setObject(2, fxTx);
            ps.setObject(3, pkrAccId);
            ps.executeUpdate();
        }
        UUID allocId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO obligation_payment_allocations (id, business_id, obligation_id, financial_transaction_id, amount, effective_date, status, allocation_type, created_by_user_id, created_at) " +
            "VALUES (?, ?, ?, ?, 15000.0000, '2026-10-15', 'EFFECTIVE', 'CASH', ?, now())"
        )) {
            ps.setObject(1, allocId);
            ps.setObject(2, bizId);
            ps.setObject(3, exportOb);
            ps.setObject(4, fxTx);
            ps.setObject(5, ownerId);
            ps.executeUpdate();
        }

        // FX Settlement Record
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO fx_obligation_settlements (id, business_id, obligation_id, financial_transaction_id, allocation_id, settlement_direction, " +
            "obligation_currency, obligation_amount, account_id, account_currency, converted_principal, fee_amount, fee_currency, total_account_movement, " +
            "exchange_rate, rate_convention, base_currency, quote_currency, rate_source, settlement_date, effective_rate, status, " +
            "allocated_book_value, realized_fx_difference, realized_fx_impact, recognition_evidence_status, created_by_user_id, created_at) " +
            "VALUES (gen_random_uuid(), ?, ?, ?, ?, 'RECEIVABLE_COLLECTION', 'USD', 15000.0000, ?, 'PKR', 4237500.0000, 2500.0000, 'PKR', 4235000.0000, " +
            "282.500000, 'BASE_TO_QUOTE', 'USD', 'PKR', 'INTERBANK_SETTLEMENT', '2026-10-15', 282.333333, 'SETTLED', " +
            "4170000.0000, 67500.0000, 'GAIN', 'AVAILABLE', ?, now())"
        )) {
            ps.setObject(1, bizId);
            ps.setObject(2, exportOb);
            ps.setObject(3, fxTx);
            ps.setObject(4, allocId);
            ps.setObject(5, pkrAccId);
            ps.setObject(6, ownerId);
            ps.executeUpdate();
        }

        // 6-Month Historical Monthly Records and Health Scores
        seedHistoricalMonthlyAndScores(bizId, 4500000.0, 3200000.0, 5200000.0, 2600000.0, 600000.0);
    }
}
