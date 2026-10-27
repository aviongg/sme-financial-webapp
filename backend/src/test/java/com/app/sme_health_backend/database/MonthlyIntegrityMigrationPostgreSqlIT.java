package com.app.sme_health_backend.database;

import com.app.sme_health_backend.testsupport.DisposablePostgres;
import com.app.sme_health_backend.crypto.AesGcmSensitiveDataCipher;
import com.app.sme_health_backend.crypto.CryptoProperties;
import com.app.sme_health_backend.crypto.LocalKeyringProvider;
import com.app.sme_health_backend.crypto.converter.EncryptedFullNameConverter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real PostgreSQL upgrade, source constraints and least-privilege checks. */
class MonthlyIntegrityMigrationPostgreSqlIT extends DisposablePostgres {
    @Test
    void upgradesV19WithoutInventingLegacyProvenanceOrChangingFinancialValues() throws Exception {
        String schema = "pilot_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        String adminUrl = POSTGRES.getJdbcUrl("postgres", "postgres");
        Flyway.configure().dataSource(adminUrl, "postgres", "").schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("19").load().migrate();
        try (var connection = POSTGRES.getPostgresDatabase().getConnection(); var sql = connection.createStatement()) {
            sql.execute("SET search_path TO " + schema);
            UUID business = UUID.randomUUID(), doc = UUID.randomUUID();
            sql.executeUpdate("INSERT INTO businesses(id) VALUES ('" + business + "')");
            sql.executeUpdate("INSERT INTO business_profiles(user_id,business_type,language_preference) VALUES ('" + business + "','retail','ur')");
            sql.executeUpdate("INSERT INTO monthly_records(user_id,month,revenue,cogs,cash_balance_eom,receivables_outstanding,payables_outstanding)"
                    + " VALUES ('" + business + "','2026-09',12345.67,NULL,-23.45,NULL,0)");
            sql.executeUpdate("INSERT INTO uploaded_documents(id,user_id,file_url,processing_status,linked_month,confirmed_data) VALUES ('"
                    + doc + "','" + business + "','legacy-fixture','confirmed','2026-09',"
                    + "'{\"target_month\":\"2026-09\",\"confirmed_amount\":200,\"target_classification\":\"revenue\",\"cash_flow_impact\":\"none\"}')");
            sql.executeUpdate("INSERT INTO score_results(user_id,month,composite_score,band,component_scores,weakest_component,data_completeness,methodology_version)"
                    + " VALUES ('" + business + "','2026-09',63.53,'Stable','{\"cashflow\":10,\"profitability\":100,\"repayment\":80,\"trend\":null,\"compliance\":100}',"
                    + "'cashflow',0.85,'health-score-v1')");
            Map<String, String> before = new LinkedHashMap<>();
            for (String table : List.of("monthly_records", "uploaded_documents", "score_results", "business_profiles")) {
                try (var rs = sql.executeQuery("SELECT to_jsonb(t)::text FROM " + table + " t")) {
                    assertTrue(rs.next()); before.put(table, rs.getString(1));
                }
            }
            var result = Flyway.configure().dataSource(adminUrl, "postgres", "").schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").target("29").load().migrate();
            assertEquals(1, result.migrationsExecuted);
            for (var entry : before.entrySet()) {
                String expression = entry.getKey().equals("monthly_records") ? "(to_jsonb(t)-'version')" : "to_jsonb(t)";
                try (var rs = sql.executeQuery("SELECT " + expression + "::text FROM " + entry.getKey() + " t")) {
                    assertTrue(rs.next()); assertEquals(entry.getValue(), rs.getString(1));
                }
            }
            try (var rs = sql.executeQuery("SELECT version FROM monthly_records")) {
                assertTrue(rs.next()); assertEquals(0L, rs.getLong(1)); assertFalse(rs.wasNull());
            }
            try (var rs = sql.executeQuery("SELECT count(*) FROM document_financial_contributions")) {
                assertTrue(rs.next()); assertEquals(0, rs.getInt(1), "Legacy source attribution cannot be fabricated");
            }
            assertEquals("23502", assertThrows(SQLException.class,
                    () -> sql.executeUpdate("UPDATE monthly_records SET version=NULL")).getSQLState());
            assertPrivileges(connection, schema);
        } finally {
            // Only this test-created schema in the process-owned disposable database.
            try (var connection = POSTGRES.getPostgresDatabase().getConnection(); var sql = connection.createStatement()) {
                sql.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    @Test
    void freshV1ThroughV19PlusV29V30AndV31AllowsAppendOnlyRuntimeSourcesAndReadOnlyBackup() throws Exception {
        UUID business = UUID.randomUUID(), otherBusiness = UUID.randomUUID(), actor = UUID.randomUUID();
        UUID doc = UUID.randomUUID(), doc2 = UUID.randomUUID(), record = UUID.randomUUID(), otherRecord = UUID.randomUUID();
        UUID contribution = UUID.randomUUID();
        try (var admin = POSTGRES.getPostgresDatabase().getConnection(); var sql = admin.createStatement()) {
            try (var rs = sql.executeQuery("SELECT version FROM flyway_schema_history WHERE success AND type='SQL' ORDER BY installed_rank")) {
                List<String> versions = new ArrayList<>();
                while (rs.next()) versions.add(rs.getString(1));
                assertEquals(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12",
                        "13", "14", "15", "16", "17", "18", "19", "29", "30", "31"), versions,
                        "The integrated schema contains only V1-V19, V29, F05 V30 and F04 V31");
            }
            assertPrivileges(admin, "public");
        }
        try (var app = connect("finsight_app"); var sql = app.createStatement()) {
            try (var insertActor = app.prepareStatement("INSERT INTO app_users(id,email,password_hash,full_name) VALUES (?,?,?,?)")) {
                insertActor.setObject(1, actor);
                insertActor.setString(2, "pilot-migration-" + actor + "@example.com");
                insertActor.setString(3, "fixture-only");
                insertActor.setString(4, encryptedFixtureName());
                assertEquals(1, insertActor.executeUpdate());
            }
            for (UUID id : List.of(business, otherBusiness)) {
                sql.executeUpdate("INSERT INTO businesses(id) VALUES ('" + id + "')");
                sql.executeUpdate("INSERT INTO business_profiles(user_id,business_type) VALUES ('" + id + "','retail')");
            }
            sql.executeUpdate("INSERT INTO monthly_records(id,user_id,month) VALUES ('" + record + "','" + business + "','2026-10'),"
                    + "('" + otherRecord + "','" + otherBusiness + "','2026-10')");
            sql.executeUpdate("INSERT INTO uploaded_documents(id,user_id,file_url,processing_status) VALUES ('" + doc + "','" + business
                    + "','fixture','confirmed'),('" + doc2 + "','" + otherBusiness + "','fixture','confirmed')");
            assertEquals(1, sql.executeUpdate(insert(contribution, business, doc, record, actor, "2026-10")));
            assertEquals("23505", assertThrows(SQLException.class,
                    () -> sql.executeUpdate(insert(UUID.randomUUID(), business, doc, record, actor, "2026-10"))).getSQLState(),
                    "One document cannot contribute twice even with a fresh contribution id");
            assertEquals("23503", assertThrows(SQLException.class,
                    () -> sql.executeUpdate(insert(UUID.randomUUID(), business, doc2, record, actor, "2026-10"))).getSQLState(),
                    "A business cannot reference another business's document");
            assertEquals("23503", assertThrows(SQLException.class,
                    () -> sql.executeUpdate(insert(UUID.randomUUID(), otherBusiness, doc2, record, actor, "2026-10"))).getSQLState(),
                    "A business cannot reference another business's monthly record");
            assertEquals("23503", assertThrows(SQLException.class,
                    () -> sql.executeUpdate(insert(UUID.randomUUID(), otherBusiness, doc2, otherRecord, actor, "2026-11"))).getSQLState(),
                    "The source month must match its referenced monthly record");
            for (String command : List.of("UPDATE document_financial_contributions SET confirmed_amount=999",
                    "DELETE FROM document_financial_contributions", "TRUNCATE document_financial_contributions",
                    "ALTER TABLE document_financial_contributions ADD COLUMN forbidden integer", "DROP TABLE document_financial_contributions")) {
                assertEquals("42501", assertThrows(SQLException.class, () -> sql.execute(command)).getSQLState());
            }
            assertEquals(1, sql.executeUpdate("UPDATE monthly_records SET version=version+1 WHERE id='" + record + "'"));
        }
        try (var backup = connect("finsight_backup"); var sql = backup.createStatement()) {
            try (var rs = sql.executeQuery("SELECT business_id,document_id,monthly_record_id,target_month,confirmed_amount,actor_user_id,"
                    + "source_fingerprint,duplicate_override FROM document_financial_contributions WHERE id='" + contribution + "'")) {
                assertTrue(rs.next());
                assertEquals(business, rs.getObject(1)); assertEquals(doc, rs.getObject(2)); assertEquals(record, rs.getObject(3));
                assertEquals("2026-10", rs.getString(4)); assertEquals("200.00", rs.getBigDecimal(5).toPlainString());
                assertEquals(actor, rs.getObject(6)); assertEquals("a".repeat(64), rs.getString(7)); assertFalse(rs.getBoolean(8));
            }
            for (String command : List.of(insert(UUID.randomUUID(), business, doc, record, actor, "2026-10"),
                    "UPDATE document_financial_contributions SET confirmed_amount=999",
                    "DELETE FROM document_financial_contributions", "TRUNCATE document_financial_contributions",
                    "UPDATE monthly_records SET version=99")) {
                assertEquals("42501", assertThrows(SQLException.class, () -> sql.execute(command)).getSQLState());
            }
        }
        try (var owner = connect("finsight_migrator"); var sql = owner.createStatement()) {
            for (String command : List.of("UPDATE document_financial_contributions SET confirmed_amount=999 WHERE id='" + contribution + "'",
                    "DELETE FROM document_financial_contributions WHERE id='" + contribution + "'", "TRUNCATE document_financial_contributions")) {
                assertEquals("55000", assertThrows(SQLException.class, () -> sql.execute(command)).getSQLState(),
                        "Append-only triggers defend against accidental owner DML as well");
            }
        }
    }

    private static String encryptedFixtureName() {
        String key = Objects.requireNonNull(System.getProperty("FINSIGHT_CRYPTO_KEY_K1"), "Maven fixture key is required");
        CryptoProperties properties = new CryptoProperties();
        properties.setActiveKeyId("k1");
        properties.setKeys(Map.of("k1", key));
        LocalKeyringProvider keyring = new LocalKeyringProvider(properties);
        keyring.initialize();
        return new AesGcmSensitiveDataCipher(keyring, properties).encrypt("Fixture", EncryptedFullNameConverter.AAD);
    }

    private static Connection connect(String role) throws SQLException {
        Connection result = DriverManager.getConnection(URL, role, PASSWORD);
        try (var sql = result.createStatement(); var rs = sql.executeQuery("SELECT current_user")) {
            assertTrue(rs.next()); assertEquals(role, rs.getString(1));
        }
        return result;
    }

    private static String insert(UUID id, UUID business, UUID doc, UUID record, UUID actor, String month) {
        return "INSERT INTO document_financial_contributions(id,business_id,document_id,monthly_record_id,target_month,"
                + "target_classification,cash_flow_impact,confirmed_amount,actor_user_id,source_fingerprint,duplicate_override)"
                + " VALUES ('" + id + "','" + business + "','" + doc + "','" + record + "','" + month
                + "','revenue','cash_inflow',200,'" + actor + "','" + "a".repeat(64) + "',false)";
    }

    private static void assertPrivileges(Connection connection, String schema) throws SQLException {
        try (var sql = connection.createStatement()) {
            for (String role : List.of("finsight_app", "finsight_backup")) {
                for (String privilege : List.of("SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER")) {
                    boolean allowed = privilege.equals("SELECT") || role.equals("finsight_app") && privilege.equals("INSERT");
                    try (var rs = sql.executeQuery("SELECT has_table_privilege('" + role + "','" + schema
                            + ".document_financial_contributions','" + privilege + "')")) {
                        assertTrue(rs.next()); assertEquals(allowed, rs.getBoolean(1), role + " " + privilege);
                    }
                }
            }
        }
    }
}
