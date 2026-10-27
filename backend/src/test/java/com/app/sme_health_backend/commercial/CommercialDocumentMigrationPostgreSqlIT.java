package com.app.sme_health_backend.commercial;

import com.app.sme_health_backend.testsupport.DisposablePostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies V31__commercial_documents_foundation.sql on real disposable PostgreSQL: exact fresh migration
 * history, populated V30 -> V31 upgrade, tenant-scoped composite keys, CHECK constraints, lifecycle and
 * immutability triggers, numbering uniqueness/monotonic sequences, TRUNCATE protection and role privileges.
 */
class CommercialDocumentMigrationPostgreSqlIT extends DisposablePostgres {
    private static final String CIPHERTEXT = "enc:v1:fixture:QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVowMTIz";
    private static final List<String> TABLES =
            List.of("commercial_documents", "commercial_document_lines", "commercial_number_sequences");

    @Test
    void freshMigrationContainsOnlyV1ToV19V29V30AndV31() throws Exception {
        try (var connection = POSTGRES.getPostgresDatabase().getConnection(); var sql = connection.createStatement()) {
            List<String> versions = new ArrayList<>();
            try (var rs = sql.executeQuery("SELECT version FROM flyway_schema_history WHERE success AND type='SQL' ORDER BY installed_rank")) {
                while (rs.next()) versions.add(rs.getString(1));
            }
            assertEquals(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15",
                    "16", "17", "18", "19", "29", "30", "31"), versions);
            for (String table : TABLES) {
                try (var rs = sql.executeQuery("SELECT to_regclass('public." + table + "') IS NOT NULL")) {
                    assertTrue(rs.next());
                    assertTrue(rs.getBoolean(1), table);
                }
            }
            assertPrivileges(sql, "public");
        }
    }

    @Test
    void upgradesPopulatedV30SchemaAndEnforcesConstraintsTriggersAndPrivileges() throws Exception {
        String schema = "f04_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        String adminUrl = POSTGRES.getJdbcUrl("postgres", "postgres");
        migrate(adminUrl, schema, "30");
        try (var connection = POSTGRES.getPostgresDatabase().getConnection(); var sql = connection.createStatement()) {
            sql.execute("SET search_path TO " + schema);
            UUID businessA = UUID.randomUUID(), businessB = UUID.randomUUID(), user = UUID.randomUUID(), project = UUID.randomUUID();
            sql.executeUpdate("INSERT INTO businesses(id) VALUES ('" + businessA + "'), ('" + businessB + "')");
            sql.executeUpdate("INSERT INTO app_users(id,email,password_hash,full_name) VALUES ('" + user
                    + "','f04-migration@example.com','fixture-only','F04 Migration Fixture')");
            sql.executeUpdate("INSERT INTO business_profiles(user_id,business_type,language_preference) VALUES ('" + businessA + "','services','en')");
            sql.executeUpdate("INSERT INTO monthly_records(user_id,month,revenue,cogs,cash_balance_eom,receivables_outstanding,payables_outstanding) VALUES ('"
                    + businessA + "','2026-10',250000.00,120000.00,85000.00,15000.00,5000.00)");
            sql.executeUpdate("INSERT INTO score_results(user_id,month,composite_score,band,component_scores,weakest_component,data_completeness,methodology_version) VALUES ('"
                    + businessA + "','2026-10',74.20,'Healthy','{\"cashflow\":70,\"profitability\":80,\"repayment\":75,\"trend\":70,\"compliance\":75}','cashflow',1.00,'health-score-v1')");
            sql.executeUpdate("INSERT INTO projects(id,business_id,code,name,kind,status,version,created_by,updated_by) VALUES ('"
                    + project + "','" + businessA + "','PRJ-F04','Upgrade fixture','PROJECT','ACTIVE',1,'" + user + "','" + user + "')");
            Map<String, String> before = snapshot(sql, List.of("monthly_records", "score_results", "business_profiles", "projects"));

            migrate(adminUrl, schema, "31");
            assertEquals(before, snapshot(sql, List.of("monthly_records", "score_results", "business_profiles", "projects")),
                    "V31 must not change existing financial, score, profile or project rows");
            assertPrivileges(sql, schema);

            // Draft creation with lines; explicit zero and null totals are both representable.
            UUID draft = document(sql, businessA, user, "INVOICE", "1000.00", CIPHERTEXT);
            line(sql, businessA, draft, 1, "2", "500.00", "1000.00");
            UUID empty = document(sql, businessA, user, "BILL", null, CIPHERTEXT);
            UUID zero = document(sql, businessA, user, "BILL", "0.00", CIPHERTEXT);
            line(sql, businessA, zero, 1, "1", "0", "0.00");

            // Documents are always born DRAFT; a direct FINALIZED/VOID insert would bypass the line-sum gate.
            for (String status : List.of("FINALIZED", "VOID")) {
                String direct = insertDocument(businessA, user, "INVOICE", status, "INV-000099", "10.00", CIPHERTEXT);
                assertEquals("55000", assertThrows(SQLException.class, () -> sql.executeUpdate(direct)).getSQLState(), status);
            }

            // Lifecycle CHECKs at finalization: number format per kind, number/counterparty/date required, total > 0.
            UUID noCounterparty = document(sql, businessA, user, "INVOICE", "10.00", null);
            line(sql, businessA, noCounterparty, 1, "1", "10.00", "10.00");
            UUID tenBill = document(sql, businessA, user, "BILL", "10.00", CIPHERTEXT);
            line(sql, businessA, tenBill, 1, "1", "10.00", "10.00");
            for (String bad : List.of(
                    finalize(tenBill, "INV-000001"),
                    finalize(tenBill, "BILL-1"),
                    finalize(tenBill, "BILL-0000001"),
                    "UPDATE commercial_documents SET status='FINALIZED',finalized_by_user_id=created_by_user_id,finalized_at=now(),version=version+1 WHERE id='" + tenBill + "'",
                    "UPDATE commercial_documents SET status='FINALIZED',document_number='BILL-000001',version=version+1 WHERE id='" + tenBill + "'",
                    finalize(zero, "BILL-000001"),
                    finalize(noCounterparty, "INV-000050"))) {
                assertEquals("23514", assertThrows(SQLException.class, () -> sql.executeUpdate(bad), bad).getSQLState(), bad);
            }

            // CHECK constraints on draft rows and lines.
            for (String bad : List.of(
                    insertDocument(businessA, user, "QUOTE", "DRAFT", null, null, CIPHERTEXT),
                    insertDocument(businessA, user, "INVOICE", "DRAFT", null, null, "Acme Plaintext Traders"),
                    insertDocument(businessA, user, "INVOICE", "DRAFT", "INV-000001", "10.00", CIPHERTEXT),
                    "UPDATE commercial_documents SET status='ISSUED',version=version+1 WHERE id='" + tenBill + "'",
                    "UPDATE commercial_documents SET status='APPROVED',version=version+1 WHERE id='" + tenBill + "'",
                    "INSERT INTO commercial_documents(business_id,kind,subtotal,total,created_by_user_id,updated_by_user_id) VALUES ('"
                            + businessA + "','INVOICE',10.00,11.00,'" + user + "','" + user + "')",
                    "INSERT INTO commercial_documents(business_id,kind,base_currency,created_by_user_id,updated_by_user_id) VALUES ('"
                            + businessA + "','INVOICE','USD','" + user + "','" + user + "')",
                    "INSERT INTO commercial_documents(business_id,kind,financial_effect,created_by_user_id,updated_by_user_id) VALUES ('"
                            + businessA + "','INVOICE','POSTED','" + user + "','" + user + "')",
                    "INSERT INTO commercial_documents(business_id,kind,document_date,due_date,created_by_user_id,updated_by_user_id) VALUES ('"
                            + businessA + "','INVOICE','2026-10-28','2026-10-01','" + user + "','" + user + "')",
                    insertLine(businessA, draft, 2, "1.5", "333.33", "499.99"),
                    insertLine(businessA, draft, 2, "1.5", "333.33", "500.00"),
                    insertLine(businessA, draft, 2, "0", "10.00", "0.00"),
                    insertLine(businessA, draft, 2, "1", "-1.00", "-1.00"),
                    insertLine(businessA, draft, 101, "1", "1.00", "1.00"))) {
                assertEquals("23514", assertThrows(SQLException.class, () -> sql.executeUpdate(bad), bad).getSQLState(), bad);
            }
            // A 3-decimal product written into the 2-decimal column is silently rounded by NUMERIC; the CHECK still rejects it.
            assertEquals("23514", assertThrows(SQLException.class,
                    () -> sql.executeUpdate(insertLine(businessA, draft, 2, "1.5", "333.33", "499.995"))).getSQLState());
            assertEquals("23505", assertThrows(SQLException.class,
                    () -> line(sql, businessA, draft, 1, "1", "1.00", "1.00")).getSQLState(), "Line numbers are unique per document");
            // The trigger rejects a cross-tenant parent before the composite FK (an AFTER check) is reached.
            assertEquals("55000", assertThrows(SQLException.class,
                    () -> line(sql, businessB, draft, 5, "1", "1.00", "1.00")).getSQLState(), "Lines cannot cross tenants");
            try (var rs = sql.executeQuery("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname='fk_commercial_document_lines_document'"
                    + " AND connamespace='" + schema + "'::regnamespace")) {
                assertTrue(rs.next());
                assertEquals("FOREIGN KEY (document_id, business_id) REFERENCES commercial_documents(id, business_id) ON DELETE CASCADE",
                        rs.getString(1).replace(schema + ".", ""));
            }
            // With the trigger disabled in this disposable schema, the composite FK alone still blocks the tenant crossing.
            sql.execute("ALTER TABLE commercial_document_lines DISABLE TRIGGER trg_commercial_document_lines_immutability");
            assertEquals("23503", assertThrows(SQLException.class,
                    () -> line(sql, businessB, draft, 5, "1", "1.00", "1.00")).getSQLState(), "Composite tenant FK");
            sql.execute("ALTER TABLE commercial_document_lines ENABLE TRIGGER trg_commercial_document_lines_immutability");

            // Finalization requires stored lines whose exact sum equals the subtotal.
            assertEquals("55000", assertThrows(SQLException.class, () -> sql.executeUpdate(finalize(empty, "BILL-000001"))).getSQLState());
            sql.executeUpdate("UPDATE commercial_documents SET subtotal=999.00,total=999.00,version=version+1 WHERE id='" + draft + "'");
            assertEquals("55000", assertThrows(SQLException.class, () -> sql.executeUpdate(finalize(draft, "INV-000001"))).getSQLState());
            sql.executeUpdate("UPDATE commercial_documents SET subtotal=1000.00,total=1000.00,version=version+1 WHERE id='" + draft + "'");
            assertEquals("55000", assertThrows(SQLException.class,
                    () -> sql.executeUpdate("UPDATE commercial_documents SET void_reason='x',status='VOID',version=version+1 WHERE id='" + draft + "'")).getSQLState(),
                    "A draft cannot be voided");
            assertEquals(1, sql.executeUpdate(finalize(draft, "INV-000001")));

            // Numbers are unique per business + kind only.
            UUID other = document(sql, businessA, user, "INVOICE", "5.00", CIPHERTEXT);
            line(sql, businessA, other, 1, "1", "5.00", "5.00");
            assertEquals("23505", assertThrows(SQLException.class, () -> sql.executeUpdate(finalize(other, "INV-000001"))).getSQLState());
            UUID otherBusiness = document(sql, businessB, user, "INVOICE", "5.00", CIPHERTEXT);
            line(sql, businessB, otherBusiness, 1, "1", "5.00", "5.00");
            assertEquals(1, sql.executeUpdate(finalize(otherBusiness, "INV-000001")), "Another tenant has its own INV sequence");

            // Finalized content and lines are immutable; it cannot be deleted or returned to draft.
            for (String forbidden : List.of(
                    "UPDATE commercial_documents SET notes='changed',version=version+1 WHERE id='" + draft + "'",
                    "UPDATE commercial_documents SET total=1.00,subtotal=1.00,version=version+1 WHERE id='" + draft + "'",
                    "UPDATE commercial_documents SET document_number='INV-000777',version=version+1 WHERE id='" + draft + "'",
                    "UPDATE commercial_documents SET counterparty_name_ciphertext='" + CIPHERTEXT.replace("ABC", "XYZ") + "',version=version+1 WHERE id='" + draft + "'",
                    "UPDATE commercial_documents SET status='DRAFT',document_number=NULL,finalized_by_user_id=NULL,finalized_at=NULL,version=version+1 WHERE id='" + draft + "'",
                    "UPDATE commercial_documents SET kind='BILL',version=version+1 WHERE id='" + draft + "'",
                    "UPDATE commercial_documents SET status='VOID',void_reason='x',voided_by_user_id='" + user + "',voided_at=now() WHERE id='" + draft + "'",
                    "UPDATE commercial_document_lines SET description='changed' WHERE document_id='" + draft + "'",
                    "UPDATE commercial_document_lines SET document_id='" + other + "' WHERE document_id='" + draft + "'",
                    "DELETE FROM commercial_document_lines WHERE document_id='" + draft + "'",
                    insertLine(businessA, draft, 2, "1", "1.00", "1.00"),
                    "DELETE FROM commercial_documents WHERE id='" + draft + "'")) {
                assertEquals("55000", assertThrows(SQLException.class, () -> sql.executeUpdate(forbidden), forbidden).getSQLState(), forbidden);
            }
            assertEquals("23514", assertThrows(SQLException.class, () -> sql.executeUpdate(
                    "UPDATE commercial_documents SET status='VOID',voided_by_user_id='" + user + "',voided_at=now(),version=version+1 WHERE id='" + draft + "'")).getSQLState(),
                    "VOID requires a reason");
            assertEquals(1, sql.executeUpdate("UPDATE commercial_documents SET status='VOID',void_reason='Entered twice',voided_by_user_id='"
                    + user + "',voided_at=now(),version=version+1 WHERE id='" + draft + "'"));
            for (String forbidden : List.of(
                    "UPDATE commercial_documents SET void_reason='rewritten',version=version+1 WHERE id='" + draft + "'",
                    "UPDATE commercial_documents SET status='FINALIZED',void_reason=NULL,voided_by_user_id=NULL,voided_at=NULL,version=version+1 WHERE id='" + draft + "'",
                    "UPDATE commercial_document_lines SET description='changed' WHERE document_id='" + draft + "'",
                    "DELETE FROM commercial_documents WHERE id='" + draft + "'")) {
                assertEquals("55000", assertThrows(SQLException.class, () -> sql.executeUpdate(forbidden), forbidden).getSQLState(), forbidden);
            }
            try (var rs = sql.executeQuery("SELECT status, document_number, total, void_reason, (SELECT count(*) FROM commercial_document_lines WHERE document_id=d.id) FROM commercial_documents d WHERE id='" + draft + "'")) {
                assertTrue(rs.next());
                assertEquals("VOID", rs.getString(1));
                assertEquals("INV-000001", rs.getString(2), "A voided number is retained and never reused");
                assertEquals("1000.00", rs.getBigDecimal(3).toPlainString());
                assertEquals("Entered twice", rs.getString(4));
                assertEquals(1, rs.getInt(5));
            }

            // Drafts remain editable and deletable; deletion cascades only to their own lines.
            assertEquals(1, sql.executeUpdate("UPDATE commercial_document_lines SET description='Edited' WHERE document_id='" + zero + "'"));
            assertEquals("55000", assertThrows(SQLException.class, () -> sql.executeUpdate(
                    "UPDATE commercial_documents SET notes='same version' WHERE id='" + zero + "'")).getSQLState(), "Version must increase");
            assertEquals(1, sql.executeUpdate("DELETE FROM commercial_documents WHERE id='" + zero + "'"));
            try (var rs = sql.executeQuery("SELECT count(*) FROM commercial_document_lines WHERE document_id='" + zero + "'")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }

            // Sequences are monotonic and permanent.
            sql.executeUpdate("INSERT INTO commercial_number_sequences(business_id,kind,next_value) VALUES ('" + businessA + "','INVOICE',2)");
            assertEquals(1, sql.executeUpdate("UPDATE commercial_number_sequences SET next_value=3 WHERE business_id='" + businessA + "'"));
            for (String forbidden : List.of(
                    "UPDATE commercial_number_sequences SET next_value=1 WHERE business_id='" + businessA + "'",
                    "UPDATE commercial_number_sequences SET next_value=3 WHERE business_id='" + businessA + "'",
                    "UPDATE commercial_number_sequences SET kind='BILL',next_value=4 WHERE business_id='" + businessA + "'",
                    "DELETE FROM commercial_number_sequences WHERE business_id='" + businessA + "'")) {
                assertEquals("55000", assertThrows(SQLException.class, () -> sql.executeUpdate(forbidden), forbidden).getSQLState(), forbidden);
            }
            assertEquals("23514", assertThrows(SQLException.class, () -> sql.executeUpdate(
                    "UPDATE commercial_number_sequences SET next_value=1000001 WHERE business_id='" + businessA + "'")).getSQLState());

            for (String table : List.of("commercial_document_lines", "commercial_documents CASCADE", "commercial_number_sequences")) {
                assertEquals("55000", assertThrows(SQLException.class, () -> sql.execute("TRUNCATE TABLE " + table)).getSQLState(), table);
            }
        } finally {
            try (var connection = POSTGRES.getPostgresDatabase().getConnection(); var sql = connection.createStatement()) {
                sql.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    private static void migrate(String url, String schema, String target) {
        Flyway.configure().dataSource(url, "postgres", "").schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(target).load().migrate();
    }

    private static void assertPrivileges(Statement sql, String schema) throws SQLException {
        for (String table : TABLES) {
            String qualified = schema + "." + table;
            boolean sequence = table.equals("commercial_number_sequences");
            try (var rs = sql.executeQuery("SELECT has_table_privilege('finsight_app','" + qualified + "','SELECT,INSERT,UPDATE'),"
                    + "has_table_privilege('finsight_app','" + qualified + "','DELETE'),"
                    + "has_table_privilege('finsight_app','" + qualified + "','TRUNCATE'),"
                    + "has_table_privilege('finsight_app','" + qualified + "','REFERENCES,TRIGGER'),"
                    + "has_table_privilege('finsight_backup','" + qualified + "','SELECT'),"
                    + "has_table_privilege('finsight_backup','" + qualified + "','INSERT'),"
                    + "has_table_privilege('finsight_backup','" + qualified + "','UPDATE'),"
                    + "has_table_privilege('finsight_backup','" + qualified + "','DELETE'),"
                    + "has_table_privilege('finsight_backup','" + qualified + "','TRUNCATE')")) {
                assertTrue(rs.next());
                assertTrue(rs.getBoolean(1), "finsight_app SELECT/INSERT/UPDATE on " + qualified);
                assertEquals(!sequence, rs.getBoolean(2), "finsight_app DELETE only on documents/lines: " + qualified);
                assertFalse(rs.getBoolean(3), "finsight_app has no TRUNCATE on " + qualified);
                assertFalse(rs.getBoolean(4), "finsight_app has no REFERENCES/TRIGGER on " + qualified);
                assertTrue(rs.getBoolean(5), "finsight_backup SELECT on " + qualified);
                for (int column = 6; column <= 9; column++) assertFalse(rs.getBoolean(column), "finsight_backup is read-only on " + qualified);
            }
        }
    }

    private static Map<String, String> snapshot(Statement sql, List<String> tables) throws SQLException {
        Map<String, String> result = new LinkedHashMap<>();
        for (String table : tables) {
            try (var rs = sql.executeQuery("SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text), '[]')::text FROM " + table + " t")) {
                assertTrue(rs.next());
                result.put(table, rs.getString(1));
            }
        }
        return result;
    }

    private static UUID document(Statement sql, UUID business, UUID user, String kind, String total, String ciphertext) throws SQLException {
        UUID id = UUID.randomUUID();
        sql.executeUpdate(insertDocument(business, user, kind, "DRAFT", null, total, ciphertext)
                .replace("VALUES (", "VALUES ('" + id + "',").replace("(business_id", "(id,business_id"));
        return id;
    }

    private static String insertDocument(UUID business, UUID user, String kind, String status, String number, String total, String ciphertext) {
        String finalized = status.equals("DRAFT") ? "NULL,NULL" : "'" + user + "',now()";
        return "INSERT INTO commercial_documents (business_id,kind,status,document_number,counterparty_name_ciphertext,document_date,subtotal,total,"
                + "created_by_user_id,updated_by_user_id,finalized_by_user_id,finalized_at) VALUES ("
                + "'" + business + "','" + kind + "','" + status + "'," + literal(number) + "," + literal(ciphertext) + ",'2026-10-28',"
                + (total == null ? "NULL,NULL" : total + "," + total) + ",'" + user + "','" + user + "'," + finalized + ")";
    }

    private static void line(Statement sql, UUID business, UUID document, int number, String quantity, String price, String amount) throws SQLException {
        sql.executeUpdate(insertLine(business, document, number, quantity, price, amount));
    }

    private static String insertLine(UUID business, UUID document, int number, String quantity, String price, String amount) {
        return "INSERT INTO commercial_document_lines (business_id,document_id,line_number,description,quantity,unit,unit_price,line_amount) VALUES ('"
                + business + "','" + document + "'," + number + ",'Fixture line'," + quantity + ",'unit'," + price + "," + amount + ")";
    }

    private static String finalize(UUID document, String number) {
        return "UPDATE commercial_documents SET status='FINALIZED',document_number='" + number + "',finalized_by_user_id=created_by_user_id,"
                + "finalized_at=now(),version=version+1 WHERE id='" + document + "'";
    }

    private static String literal(String value) {
        return value == null ? "NULL" : "'" + value + "'";
    }
}
