package com.app.sme_health_backend.identity;

import com.app.sme_health_backend.testsupport.DisposablePostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MvpLegacyMigrationPostgreSqlIT extends DisposablePostgres {
    @Test void upgradesExistingBusinessAndCorrectedLegacyExtractionWithoutInventingProvenance() throws Exception {
        String schema = "mvp_migration_" + UUID.randomUUID().toString().replace("-", "");
        String adminUrl = POSTGRES.getJdbcUrl("postgres", "postgres");
        Flyway.configure().dataSource(adminUrl, "postgres", "").schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("15").load().migrate();
        try (var connection = POSTGRES.getPostgresDatabase().getConnection(); var sql = connection.createStatement()) {
            sql.execute("SET search_path TO " + schema);
            UUID business = UUID.randomUUID(), document = UUID.randomUUID();
            sql.executeUpdate("INSERT INTO businesses(id) VALUES ('" + business + "')");
            sql.executeUpdate("INSERT INTO business_profiles(user_id,business_type) VALUES ('" + business + "','retail')");
            sql.executeUpdate("INSERT INTO score_results(user_id,month,composite_score,band,component_scores,weakest_component,data_completeness) VALUES ('"
                    + business + "','2026-08',63.53,'Stable','{\"cashflow\":10,\"profitability\":100,\"repayment\":80,\"trend\":null,\"compliance\":100}','cashflow',0.85)");
            sql.executeUpdate("INSERT INTO recommendations(user_id,month,recommendation_text,category,priority) VALUES ('"
                    + business + "','2026-08','Legacy advice','cashflow','high')");
            sql.executeUpdate("INSERT INTO uploaded_documents(id,user_id,file_url,extracted_data) VALUES ('" + document + "','" + business + "','legacy-fixture','{\"amount\":100,\"confidence\":\"high\"}')");
            Flyway.configure().dataSource(adminUrl, "postgres", "").schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").load().migrate();
            try (var rows = sql.executeQuery("SELECT business_name FROM businesses")) {
                assertTrue(rows.next()); assertEquals("My business", rows.getString(1));
            }
            try (var rows = sql.executeQuery("SELECT extraction_provenance, extracted_data->>'amount', reviewed_data FROM uploaded_documents")) {
                assertTrue(rows.next()); assertEquals("LEGACY_UNKNOWN", rows.getString(1)); assertEquals("100", rows.getString(2)); assertNull(rows.getString(3));
            }
            try (var rows = sql.executeQuery("SELECT composite_score, methodology_version, explanation FROM score_results")) {
                assertTrue(rows.next()); assertEquals(new java.math.BigDecimal("63.53"), rows.getBigDecimal(1));
                assertNull(rows.getString(2)); assertNull(rows.getString(3));
            }
            try (var rows = sql.executeQuery("SELECT recommendation_text,status,status_updated_at FROM recommendations")) {
                assertTrue(rows.next()); assertEquals("Legacy advice", rows.getString(1));
                assertEquals("NEW", rows.getString(2)); assertNull(rows.getObject(3));
            }
        } finally {
            // Only this random schema in this process's disposable PostgreSQL instance is removed.
            try (var connection = POSTGRES.getPostgresDatabase().getConnection(); var sql = connection.createStatement()) {
                sql.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
}
