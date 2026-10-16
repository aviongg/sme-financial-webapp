package com.app.sme_health_backend.dashboard.workspace;

import com.app.sme_health_backend.testsupport.DisposablePostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceMigrationPostgreSqlIT extends DisposablePostgres {
    @Test void upgradesVersion18WithoutChangingConfigurationFinancialRecordsOrScoresAndGrantsRuntime() throws Exception {
        String schema = "f13_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        String adminUrl = POSTGRES.getJdbcUrl("postgres", "postgres");
        Flyway.configure().dataSource(adminUrl, "postgres", "").schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("18").load().migrate();
        try (var connection = POSTGRES.getPostgresDatabase().getConnection(); var sql = connection.createStatement()) {
            sql.execute("SET search_path TO " + schema);
            UUID business = UUID.randomUUID(), user = UUID.randomUUID();
            sql.executeUpdate("INSERT INTO businesses(id) VALUES ('" + business + "')");
            sql.executeUpdate("INSERT INTO app_users(id,email,password_hash,full_name) VALUES ('" + user + "','f13-migration@example.com','fixture-only','Fixture')");
            sql.executeUpdate("INSERT INTO business_profiles(user_id,business_type,language_preference) VALUES ('" + business + "','retail','ur')");
            sql.executeUpdate("INSERT INTO business_configuration(business_id,operating_profile,requested_modules,version,configured) VALUES ('"
                    + business + "','PROJECT_ORDER','[\"core_finance\"]',4,true)");
            sql.executeUpdate("INSERT INTO monthly_records(user_id,month,revenue,cogs,cash_balance_eom,receivables_outstanding,payables_outstanding) VALUES ('"
                    + business + "','2026-09',12345.67,NULL,-23.45,NULL,0)");
            sql.executeUpdate("INSERT INTO score_results(user_id,month,composite_score,band,component_scores,weakest_component,data_completeness,methodology_version) VALUES ('"
                    + business + "','2026-09',63.53,'Stable','{\"cashflow\":10,\"profitability\":100,\"repayment\":80,\"trend\":null,\"compliance\":100}','cashflow',0.85,'health-score-v1')");
            Map<String, String> before = new LinkedHashMap<>();
            for (String table : List.of("business_configuration", "monthly_records", "score_results", "business_profiles")) {
                try (var rows = sql.executeQuery("SELECT row_to_json(t)::text FROM " + table + " t")) { assertTrue(rows.next()); before.put(table, rows.getString(1)); }
            }
            Flyway.configure().dataSource(adminUrl, "postgres", "").schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").target("19").load().migrate();
            for (var entry : before.entrySet()) {
                try (var rows = sql.executeQuery("SELECT row_to_json(t)::text FROM " + entry.getKey() + " t")) {
                    assertTrue(rows.next()); assertEquals(entry.getValue(), rows.getString(1));
                }
            }
            try (var rows = sql.executeQuery("SELECT count(*) FROM dashboard_preferences")) { assertTrue(rows.next()); assertEquals(0, rows.getInt(1)); }
            sql.executeUpdate("INSERT INTO dashboard_preferences(business_id,user_id,registry_version,version) VALUES ('" + business + "','" + user + "','f13-v1',1)");
            assertThrows(java.sql.SQLException.class, () -> sql.executeUpdate("INSERT INTO dashboard_preferences(business_id,user_id,registry_version,version) VALUES ('"
                    + business + "','" + user + "','f13-v1',2)"));
            try (var rows = sql.executeQuery("SELECT has_table_privilege('finsight_app','" + schema + ".dashboard_preferences','SELECT,INSERT,UPDATE,DELETE'), has_table_privilege('finsight_backup','" + schema + ".dashboard_preferences','SELECT')")) {
                assertTrue(rows.next()); assertTrue(rows.getBoolean(1)); assertTrue(rows.getBoolean(2));
            }
            sql.executeUpdate("DELETE FROM app_users WHERE id='" + user + "'");
            try (var rows = sql.executeQuery("SELECT count(*) FROM dashboard_preferences")) { assertTrue(rows.next()); assertEquals(0, rows.getInt(1)); }
        } finally {
            // Only this randomly named schema in the process-owned disposable database.
            try (var connection = POSTGRES.getPostgresDatabase().getConnection(); var sql = connection.createStatement()) {
                sql.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
}
