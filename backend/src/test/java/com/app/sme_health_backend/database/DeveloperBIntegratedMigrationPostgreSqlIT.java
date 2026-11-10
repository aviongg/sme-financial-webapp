package com.app.sme_health_backend.database;

import com.app.sme_health_backend.testsupport.DisposablePostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Local integrated Developer-B candidate: verifies the complete V1-V17, V30-V37 history and a populated
 * V34 -> V35 (F09) -> V36 (F06) -> V37 (F08) upgrade on real disposable PostgreSQL. Every pre-existing row
 * (actuals, score, configuration, dashboard preferences, F05 projects/budgets and F04 documents) must survive each
 * step unchanged, and rows written through each new migration's tables must survive the later migrations.
 */
class DeveloperBIntegratedMigrationPostgreSqlIT extends DisposablePostgres {
    private static final List<String> EXPECTED_HISTORY = com.app.sme_health_backend.testsupport.MigrationHistory.expected();
    private static final List<String> PRE_EXISTING = List.of("monthly_records", "score_results", "business_profiles",
            "business_configuration", "dashboard_preferences", "projects", "project_budget_versions",
            "project_budget_lines", "commercial_documents", "commercial_document_lines", "commercial_number_sequences");

    @Test
    void freshHistoryIsExactlyV1ToV17AndV30ToV37WithEachFeatureTablePresent() throws Exception {
        try (var connection = POSTGRES.getPostgresDatabase().getConnection(); var sql = connection.createStatement()) {
            assertEquals(EXPECTED_HISTORY, history(sql));
            try (var rs = sql.executeQuery("SELECT count(*), count(DISTINCT version) FROM flyway_schema_history WHERE version IS NOT NULL")) {
                assertTrue(rs.next());
                assertEquals(rs.getInt(1), rs.getInt(2), "No Flyway version may be applied twice");
            }
            for (String table : List.of("business_configuration", "dashboard_preferences", "projects", "commercial_documents",
                    "sample_activities", "sample_cost_plan_versions", "sample_cost_plan_lines", "estimates", "estimate_versions",
                    "estimate_lines", "estimate_project_conversions", "inventory_items")) {
                try (var rs = sql.executeQuery("SELECT to_regclass('public." + table + "') IS NOT NULL")) {
                    assertTrue(rs.next());
                    assertTrue(rs.getBoolean(1), table);
                }
            }
        }
    }

    @Test
    void populatedV34UpgradesThroughV35V36V37PreservingEveryExistingRow() throws Exception {
        String schema = "devb_integrated_" + UUID.randomUUID().toString().replace("-", "");
        migrate(schema, "34");
        try (var connection = POSTGRES.getPostgresDatabase().getConnection(); var sql = connection.createStatement()) {
            sql.execute("SET search_path TO " + schema);
            UUID business = UUID.randomUUID(), user = UUID.randomUUID(), project = UUID.randomUUID(), budget = UUID.randomUUID();
            sql.executeUpdate("INSERT INTO businesses(id) VALUES ('" + business + "')");
            sql.executeUpdate("INSERT INTO app_users(id,email,password_hash,full_name) VALUES ('" + user
                    + "','devb-integration@example.com','fixture-only','Integration Fixture')");
            sql.executeUpdate("INSERT INTO business_profiles(user_id,business_type,language_preference) VALUES ('" + business + "','manufacturing','ur')");
            sql.executeUpdate("INSERT INTO business_configuration(business_id,operating_profile,requested_modules,configured,updated_by) VALUES ('"
                    + business + "','MANUFACTURING','[\"core_finance\",\"projects\"]',true,'" + user + "')");
            sql.executeUpdate("INSERT INTO dashboard_preferences(business_id,user_id,overrides_json,registry_version,version) VALUES ('"
                    + business + "','" + user + "','[]','fixture-registry',1)");
            sql.executeUpdate("INSERT INTO monthly_records(user_id,month,revenue,cogs,cash_balance_eom,receivables_outstanding,payables_outstanding,inventory_value) VALUES ('"
                    + business + "','2026-10',250000.00,120000.00,85000.00,15000.00,5000.00,40000.00)");
            sql.executeUpdate("INSERT INTO score_results(user_id,month,composite_score,band,component_scores,weakest_component,data_completeness,methodology_version) VALUES ('"
                    + business + "','2026-10',74.20,'Healthy','{\"cashflow\":70,\"profitability\":80,\"repayment\":75,\"trend\":70,\"compliance\":75}','cashflow',1.00,'health-score-v1')");
            sql.executeUpdate("INSERT INTO projects(id,business_id,code,name,kind,status,version,created_by,updated_by) VALUES ('"
                    + project + "','" + business + "','PRJ-INT','Integration fixture','PROJECT','ACTIVE',1,'" + user + "','" + user + "')");
            sql.executeUpdate("INSERT INTO project_budget_versions(id,business_id,project_id,revision,status,planned_revenue,planned_cost,planned_margin,planned_margin_pct,created_by,updated_by) VALUES ('"
                    + budget + "','" + business + "','" + project + "',1,'DRAFT',1000,800,200,0.2,'" + user + "','" + user + "')");
            sql.executeUpdate("INSERT INTO project_budget_lines(business_id,project_id,budget_version_id,line_number,category,description,quantity,unit,unit_cost,planned_line_cost) VALUES ('"
                    + business + "','" + project + "','" + budget + "',1,'MATERIAL','Steel',1,'batch',800,800)");
            sql.executeUpdate("INSERT INTO commercial_documents(business_id,kind,created_by_user_id,updated_by_user_id) VALUES ('"
                    + business + "','INVOICE','" + user + "','" + user + "')");
            Map<String, String> preExisting = snapshot(sql, PRE_EXISTING);
            for (String table : PRE_EXISTING) {
                if (!table.equals("commercial_document_lines") && !table.equals("commercial_number_sequences")) {
                    assertNotEquals("[]", preExisting.get(table), "Fixture must populate " + table);
                }
            }

            migrate(schema, "35");
            assertEquals(preExisting, snapshot(sql, PRE_EXISTING), "V35 must preserve every pre-existing row");
            sql.executeUpdate("INSERT INTO sample_activities(business_id,code,name,kind,recovery_intent,project_id,created_by,updated_by) VALUES ('"
                    + business + "','S-INT-1','Integration sample','SAMPLE','FREE','" + project + "','" + user + "','" + user + "')");
            List<String> carried = new ArrayList<>(PRE_EXISTING);
            carried.add("sample_activities");
            Map<String, String> afterV35 = snapshot(sql, carried);

            migrate(schema, "36");
            assertEquals(afterV35, snapshot(sql, carried), "V36 must preserve pre-existing and F09 rows");
            sql.executeUpdate("INSERT INTO estimates(business_id,code,name,created_by_user_id,updated_by_user_id) VALUES ('"
                    + business + "','EST-INT','Integration estimate','" + user + "','" + user + "')");
            carried.add("estimates");
            Map<String, String> afterV36 = snapshot(sql, carried);

            migrate(schema, "37");
            assertEquals(afterV36, snapshot(sql, carried), "V37 must preserve pre-existing, F09 and F06 rows");
            sql.executeUpdate("INSERT INTO inventory_items(business_id,sku,name,unit,item_kind,created_by,updated_by) VALUES ('"
                    + business + "','RM-INT','Integration item','kg','RAW_MATERIAL','" + user + "','" + user + "')");
            assertEquals(afterV36, snapshot(sql, carried), "Catalog writes leave every other table unchanged");
            assertEquals(EXPECTED_HISTORY, history(sql));
        }
    }

    private static List<String> history(Statement sql) throws Exception {
        List<String> versions = new ArrayList<>();
        try (var rs = sql.executeQuery("SELECT version FROM flyway_schema_history WHERE success AND type='SQL' ORDER BY installed_rank")) {
            while (rs.next()) versions.add(rs.getString(1));
        }
        return versions;
    }

    private static void migrate(String schema, String target) {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl("postgres", "postgres"), "postgres", "").schemas(schema)
                .defaultSchema(schema).locations("classpath:db/migration").target(target).load().migrate();
    }

    private static Map<String, String> snapshot(Statement sql, List<String> tables) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        for (String table : tables) {
            try (var rs = sql.executeQuery("SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text), '[]')::text FROM " + table + " t")) {
                assertTrue(rs.next());
                result.put(table, rs.getString(1));
            }
        }
        return result;
    }
}
