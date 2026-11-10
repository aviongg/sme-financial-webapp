package com.app.sme_health_backend.database;

import com.app.sme_health_backend.testsupport.DisposablePostgres;
import com.app.sme_health_backend.testsupport.MigrationHistory;
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
 * V45-V50 product-completion migrations on real PostgreSQL: fresh contiguous history, every new table present with
 * least-privilege runtime grants, and a POPULATED V44 schema upgraded to the latest version without changing any
 * pre-existing financial, score, commercial, project or inventory row.
 */
class ProductCompletionMigrationPostgreSqlIT extends DisposablePostgres {
    private static final List<String> NEW_TABLES = List.of("commercial_adjustments", "commercial_adjustment_sequences",
            "inventory_movements", "inventory_positions", "sample_actual_costs", "sample_recovery_links",
            "financial_classifications", "category_cost_behaviors", "business_budgets", "business_budget_revisions",
            "business_budget_lines", "financial_goals", "estimate_commercial_links", "whatsapp_inbound_links",
            "whatsapp_conversations", "whatsapp_inbound_messages", "commercial_document_evidence_links");
    /** Append-only tables: the runtime role may never delete (and for movements, never update). */
    private static final List<String> NO_DELETE = List.of("commercial_adjustments", "inventory_movements", "inventory_positions",
            "sample_actual_costs", "sample_recovery_links", "whatsapp_inbound_messages", "commercial_document_evidence_links");
    private static final List<String> PRESERVED = List.of("monthly_records", "score_results", "business_profiles", "projects",
            "inventory_items", "commercial_documents", "financial_obligations", "counterparties");

    @Test
    void freshHistoryIsContiguousThroughV50WithEveryTableAndLeastPrivilege() throws Exception {
        try (var connection = POSTGRES.getPostgresDatabase().getConnection(); var sql = connection.createStatement()) {
            List<String> versions = new ArrayList<>();
            try (var rs = sql.executeQuery("SELECT version FROM flyway_schema_history WHERE success AND type='SQL' ORDER BY installed_rank")) {
                while (rs.next()) versions.add(rs.getString(1));
            }
            assertEquals(MigrationHistory.expected(), versions);
            assertTrue(Integer.parseInt(MigrationHistory.latest()) >= 50);
            assertGrants(sql, "public");
        }
    }

    @Test
    void populatedV44SchemaUpgradesWithoutChangingExistingRows() throws Exception {
        String schema = "pc_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        String adminUrl = POSTGRES.getJdbcUrl("postgres", "postgres");
        migrate(adminUrl, schema, "44");
        try (var connection = POSTGRES.getPostgresDatabase().getConnection(); var sql = connection.createStatement()) {
            sql.execute("SET search_path TO " + schema);
            UUID business = UUID.randomUUID(), user = UUID.randomUUID(), project = UUID.randomUUID(), item = UUID.randomUUID(),
                    doc = UUID.randomUUID(), counterparty = UUID.randomUUID();
            sql.executeUpdate("INSERT INTO businesses(id) VALUES ('" + business + "')");
            sql.executeUpdate("INSERT INTO app_users(id,email,password_hash,full_name) VALUES ('" + user + "','pc-mig@example.com','x','PC Migration')");
            sql.executeUpdate("INSERT INTO business_profiles(user_id,business_type,language_preference) VALUES ('" + business + "','retail','en')");
            sql.executeUpdate("INSERT INTO monthly_records(user_id,month,revenue,cogs,cash_balance_eom,receivables_outstanding,payables_outstanding,inventory_value) VALUES ('"
                    + business + "','2026-10',250000.00,120000.00,85000.00,15000.00,5000.00,40000.00)");
            sql.executeUpdate("INSERT INTO score_results(user_id,month,composite_score,band,component_scores,weakest_component,data_completeness,methodology_version) VALUES ('"
                    + business + "','2026-10',74.20,'Healthy','{\"cashflow\":70,\"profitability\":80,\"repayment\":75,\"trend\":70,\"compliance\":75}','cashflow',1.00,'health-score-v1')");
            sql.executeUpdate("INSERT INTO projects(id,business_id,code,name,kind,status,version,created_by,updated_by) VALUES ('"
                    + project + "','" + business + "','PRJ-UP','Upgrade','PROJECT','ACTIVE',1,'" + user + "','" + user + "')");
            sql.executeUpdate("INSERT INTO inventory_items(id,business_id,sku,name,unit,item_kind,created_by,updated_by) VALUES ('"
                    + item + "','" + business + "','UP-1','Upgrade item','pcs','RESALE_GOOD','" + user + "','" + user + "')");
            sql.executeUpdate("INSERT INTO commercial_documents(id,business_id,kind,created_by_user_id,updated_by_user_id) VALUES ('"
                    + doc + "','" + business + "','INVOICE','" + user + "','" + user + "')");
            sql.executeUpdate("INSERT INTO commercial_document_lines(business_id,document_id,line_number,description,quantity,unit_price,line_amount) VALUES ('"
                    + business + "','" + doc + "',1,'Line',2,10.00,20.00)");
            sql.executeUpdate("INSERT INTO counterparties(id,business_id,display_name,type,status) VALUES ('"
                    + counterparty + "','" + business + "','Upgrade Customer','CUSTOMER','ACTIVE')");
            sql.executeUpdate("INSERT INTO financial_obligations(business_id,counterparty_id,direction,source_type,issue_date,original_amount,currency,status,created_by_user_id) VALUES ('"
                    + business + "','" + counterparty + "','RECEIVABLE','MANUAL_DECLARATION',current_date,500.0000,'PKR','OPEN','" + user + "')");
            Map<String, String> before = snapshot(sql);

            migrate(adminUrl, schema, MigrationHistory.latest());
            assertEquals(before, snapshot(sql), "V45-V50 never change pre-existing rows");
            try (var rs = sql.executeQuery("SELECT count(*), count(inventory_item_id) FROM commercial_document_lines")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
                assertEquals(0, rs.getInt(2), "Existing lines gain a NULL item link only");
            }
            try (var rs = sql.executeQuery("SELECT string_agg(version, ',' ORDER BY installed_rank) FROM flyway_schema_history "
                    + "WHERE success AND type='SQL' AND version::int > 44")) {
                assertTrue(rs.next());
                assertEquals(String.join(",", MigrationHistory.expected().subList(44, MigrationHistory.expected().size())), rs.getString(1));
                assertTrue(rs.getString(1).startsWith("45,46,47,48,49,50"));
            }
            try (var rs = sql.executeQuery("SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace "
                    + "WHERE n.nspname = '" + schema + "' AND c.conname = 'ck_obligation_allocations_type'")) {
                assertTrue(rs.next());
                assertTrue(rs.getString(1).contains("CREDIT_NOTE"));
                assertTrue(rs.getString(1).contains("CASH") && rs.getString(1).contains("ADVANCE"), "Existing allocation types remain valid");
            }
            assertGrants(sql, schema);
            // Immutability triggers are active on the upgraded schema.
            sql.executeUpdate("INSERT INTO inventory_movements(business_id,item_id,sequence_no,movement_type,quantity_delta,value_delta,"
                    + "quantity_after,value_after,movement_date,source_type,idempotency_key,payload_hash,policy_version,created_by) VALUES ('"
                    + business + "','" + item + "',1,'RECEIPT',1,10,1,10,current_date,'MANUAL','k','h','f08-wac-v1','" + user + "')");
            assertEquals("55000", assertThrows(SQLException.class, () -> sql.executeUpdate("UPDATE inventory_movements SET value_delta = 0")).getSQLState());
            assertEquals("23514", assertThrows(SQLException.class, () -> sql.executeUpdate("INSERT INTO inventory_movements(business_id,item_id,sequence_no,"
                    + "movement_type,quantity_delta,value_delta,quantity_after,value_after,movement_date,source_type,idempotency_key,payload_hash,"
                    + "policy_version,created_by) VALUES ('" + business + "','" + item + "',2,'ISSUE',-1,-10,0,5,current_date,'MANUAL','k2','h','f08-wac-v1','"
                    + user + "')")).getSQLState(), "An emptied item cannot keep value");
        }
    }

    private static void assertGrants(Statement sql, String schema) throws SQLException {
        for (String table : NEW_TABLES) {
            String t = schema + "." + table;
            try (var rs = sql.executeQuery("SELECT to_regclass('" + t + "') IS NOT NULL, has_table_privilege('finsight_app','" + t + "','SELECT'),"
                    + "has_table_privilege('finsight_app','" + t + "','DELETE'), has_table_privilege('finsight_app','" + t + "','TRUNCATE'),"
                    + "has_table_privilege('finsight_backup','" + t + "','SELECT'), has_table_privilege('finsight_backup','" + t + "','INSERT')")) {
                assertTrue(rs.next());
                assertTrue(rs.getBoolean(1), table + " exists");
                assertTrue(rs.getBoolean(2), table + " readable by runtime");
                if (NO_DELETE.contains(table)) assertFalse(rs.getBoolean(3), table + " is not deletable by runtime");
                assertFalse(rs.getBoolean(4), table + " is not truncatable by runtime");
                assertTrue(rs.getBoolean(5), table + " readable by backup");
                assertFalse(rs.getBoolean(6), table + " read-only for backup");
            }
        }
        try (var rs = sql.executeQuery("SELECT has_table_privilege('finsight_app','" + schema + ".inventory_movements','UPDATE')")) {
            assertTrue(rs.next());
            assertFalse(rs.getBoolean(1), "Movements are insert-only for the runtime role");
        }
    }

    private static Map<String, String> snapshot(Statement sql) throws SQLException {
        Map<String, String> out = new LinkedHashMap<>();
        for (String table : PRESERVED) {
            try (var rs = sql.executeQuery("SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text), '[]')::text FROM " + table + " t")) {
                rs.next();
                out.put(table, rs.getString(1));
            }
        }
        return out;
    }

    private static void migrate(String url, String schema, String target) {
        Flyway.configure().dataSource(url, "postgres", "").schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(target).load().migrate();
    }
}
