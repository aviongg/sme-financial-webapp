package com.app.sme_health_backend.configuration;

import com.app.sme_health_backend.testsupport.DisposablePostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationMigrationPostgreSqlIT extends DisposablePostgres {
    @Test void upgradesVersion17WithoutChangingMoneyNullsScoresOrExistingProfile() throws Exception {
        String schema = "f02_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        String adminUrl = POSTGRES.getJdbcUrl("postgres", "postgres");
        Flyway.configure().dataSource(adminUrl, "postgres", "").schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("17").load().migrate();
        try (var connection = POSTGRES.getPostgresDatabase().getConnection(); var sql = connection.createStatement()) {
            sql.execute("SET search_path TO " + schema);
            UUID business = UUID.randomUUID();
            sql.executeUpdate("INSERT INTO businesses(id) VALUES ('" + business + "')");
            sql.executeUpdate("INSERT INTO business_profiles(user_id,business_type,language_preference) VALUES ('" + business + "','retail','ur')");
            sql.executeUpdate("INSERT INTO monthly_records(user_id,month,revenue,cogs,cash_balance_eom,receivables_outstanding,payables_outstanding) VALUES ('"
                    + business + "','2026-09',12345.67,NULL,-23.45,NULL,0)");
            sql.executeUpdate("INSERT INTO score_results(user_id,month,composite_score,band,component_scores,weakest_component,data_completeness,methodology_version) VALUES ('"
                    + business + "','2026-09',63.53,'Stable','{\"cashflow\":10,\"profitability\":100,\"repayment\":80,\"trend\":null,\"compliance\":100}','cashflow',0.85,'health-score-v1')");
            String recordBefore, scoreBefore, profileBefore;
            try (var rows = sql.executeQuery("SELECT row_to_json(m)::text FROM monthly_records m")) { assertTrue(rows.next()); recordBefore = rows.getString(1); }
            try (var rows = sql.executeQuery("SELECT row_to_json(s)::text FROM score_results s")) { assertTrue(rows.next()); scoreBefore = rows.getString(1); }
            try (var rows = sql.executeQuery("SELECT row_to_json(p)::text FROM business_profiles p")) { assertTrue(rows.next()); profileBefore = rows.getString(1); }
            Flyway.configure().dataSource(adminUrl, "postgres", "").schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").target("30").load().migrate();
            try (var rows = sql.executeQuery("SELECT row_to_json(m)::text FROM monthly_records m")) { assertTrue(rows.next()); assertEquals(recordBefore, rows.getString(1)); }
            try (var rows = sql.executeQuery("SELECT row_to_json(s)::text FROM score_results s")) { assertTrue(rows.next()); assertEquals(scoreBefore, rows.getString(1)); }
            try (var rows = sql.executeQuery("SELECT row_to_json(p)::text FROM business_profiles p")) { assertTrue(rows.next()); assertEquals(profileBefore, rows.getString(1)); }
            try (var rows = sql.executeQuery("SELECT revenue,cogs,cash_balance_eom,receivables_outstanding,payables_outstanding FROM monthly_records")) {
                assertTrue(rows.next()); assertEquals(new BigDecimal("12345.67"), rows.getBigDecimal(1)); assertNull(rows.getBigDecimal(2));
                assertEquals(new BigDecimal("-23.45"), rows.getBigDecimal(3)); assertNull(rows.getBigDecimal(4)); assertEquals(new BigDecimal("0.00"), rows.getBigDecimal(5));
            }
            try (var rows = sql.executeQuery("SELECT operating_profile,configured,version,requested_modules @> '[\"core_finance\",\"documents\",\"whatsapp\",\"ai\"]'::jsonb FROM business_configuration")) {
                assertTrue(rows.next()); assertEquals("RETAIL_TRADE", rows.getString(1)); assertFalse(rows.getBoolean(2));
                assertEquals(0, rows.getLong(3)); assertTrue(rows.getBoolean(4));
            }
            try (var rows = sql.executeQuery("SELECT has_table_privilege('finsight_app','" + schema + ".business_configuration','SELECT,INSERT,UPDATE,DELETE'), has_table_privilege('finsight_backup','" + schema + ".business_configuration','SELECT')")) {
                assertTrue(rows.next()); assertTrue(rows.getBoolean(1)); assertTrue(rows.getBoolean(2));
            }
        } finally {
            // This random schema belongs only to the process's disposable PostgreSQL instance.
            try (var connection = POSTGRES.getPostgresDatabase().getConnection(); var sql = connection.createStatement()) {
                sql.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
}
