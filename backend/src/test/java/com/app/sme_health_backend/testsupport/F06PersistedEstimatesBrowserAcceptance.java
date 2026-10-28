package com.app.sme_health_backend.testsupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in production Next / real Spring / disposable PostgreSQL F06 Phase B journey: persisted estimates, approval,
 * supersession and exact-fit conversion into an existing F05 project. No shared database or paid provider.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.address=127.0.0.1", "server.port=8080",
        "app.security.internal-service-secret=f06b-browser-fixture-only-not-production",
        "app.ai.enabled=false", "app.ai.live-activation-approved=false", "app.ai.api-key=",
        "app.security.cors.allowed-origins=http://127.0.0.1:3100"
})
@ActiveProfiles("dev")
class F06PersistedEstimatesBrowserAcceptance extends DisposablePostgres {
    private static final List<String> UNCHANGED_TABLES = List.of(
            "monthly_records", "score_results", "insights", "recommendations", "uploaded_documents",
            "whatsapp_deliveries", "business_configuration", "dashboard_preferences", "document_financial_contributions",
            "commercial_documents", "commercial_document_lines", "commercial_number_sequences");
    private static final List<String> PROBED_TABLES = List.of(
            "monthly_records", "score_results", "document_financial_contributions",
            "commercial_documents", "commercial_document_lines", "commercial_number_sequences");

    @Autowired JdbcTemplate jdbc;

    @Test
    void persistedEstimateApprovalAndExactProjectConversionWithoutFinancialEffect() throws Exception {
        String node = System.getenv("FINSIGHT_BROWSER_NODE");
        assertTrue(node != null && !node.isBlank(), "Set FINSIGHT_BROWSER_NODE to a Node executable");
        Path script = Path.of("..", "scripts", "test_f06b_estimates_browser.mjs").toAbsolutePath().normalize();
        Path output = Path.of(System.getenv().getOrDefault("BROWSER_ARTIFACT_DIR", "../tmp/f06b/real-browser"))
                .toAbsolutePath().normalize();
        Files.createDirectories(output);
        String run = UUID.randomUUID().toString();
        Path ready = output.resolve("baseline-ready-" + run);
        Path proceed = output.resolve("baseline-captured-" + run);
        Path log = output.resolve("browser-process.log");
        ProcessBuilder builder = new ProcessBuilder(node, script.toString()).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("FINSIGHT_DB_BASELINE_READY", ready.toString());
        builder.environment().put("FINSIGHT_DB_BASELINE_CAPTURED", proceed.toString());
        builder.environment().put("BROWSER_ARTIFACT_DIR", output.toString());
        Process process = builder.start();
        try {
            long deadline = System.nanoTime() + Duration.ofMinutes(2).toNanos();
            while (!Files.exists(ready) && process.isAlive() && System.nanoTime() < deadline) Thread.sleep(100);
            assertTrue(Files.exists(ready), "Browser did not finish isolated fixture setup; inspect browser-process.log");
            Map<String, String> before = snapshot();
            assertFalse(before.get("monthly_records").equals("[]"), "Require existing actual record fixtures");
            assertFalse(before.get("score_results").equals("[]"), "Require existing stored score fixtures");
            assertFalse(before.get("commercial_documents").equals("[]"), "Require an existing F04 document fixture");
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM estimates", Integer.class), "No estimates before the journey");
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM project_budget_versions", Integer.class), "No F05 budgets before the journey");
            installWriteProbe();
            Files.writeString(proceed, "Disposable PostgreSQL financial baseline captured; begin F06 Phase B actions.");
            assertTrue(process.waitFor(10, TimeUnit.MINUTES), "F06 Phase B browser acceptance timed out");
            assertEquals(0, process.exitValue(), "F06 Phase B browser acceptance failed; inspect browser-process.log");
            assertEquals(before, snapshot(), "Every financial/score/commercial/document/configuration row must remain identical");
            int writes;
            try (Connection connection = POSTGRES.getPostgresDatabase().getConnection(); Statement sql = connection.createStatement()) {
                StringBuilder diagnostics = new StringBuilder();
                try (var rows = sql.executeQuery("SELECT table_name, operation, statement FROM f06b_browser_financial_writes")) {
                    while (rows.next()) diagnostics.append(rows.getString(1)).append(' ')
                            .append(rows.getString(2)).append(": ").append(rows.getString(3)).append('\n');
                }
                Files.writeString(output.resolve("financial-write-diagnostics.txt"), diagnostics.toString());
                try (var rows = sql.executeQuery("SELECT count(*) FROM f06b_browser_financial_writes")) {
                    assertTrue(rows.next());
                    writes = rows.getInt(1);
                }
            }
            assertEquals(0, writes, "F06 must issue zero INSERT/UPDATE/DELETE/TRUNCATE statements on records, scores, contributions or commercial documents");

            // The journey's own persisted facts: history, one exact conversion, one F05 DRAFT budget, nothing posted.
            assertEquals(List.of("F06B-ORDER|1|SUPERSEDED", "F06B-ORDER|2|APPROVED", "F06B-RESIN|1|APPROVED"), jdbc.queryForList("""
                    SELECT e.code || '|' || v.revision || '|' || v.status FROM estimate_versions v JOIN estimates e ON e.id = v.estimate_id
                    ORDER BY e.code, v.revision""", String.class));
            assertEquals(List.of("F06B-PRJ|DRAFT|1500.2500|F06/F06B-ORDER/R2"), jdbc.queryForList("""
                    SELECT p.code || '|' || b.status || '|' || b.planned_revenue || '|' || split_part(b.source_estimate_ref, '/', 1) || '/'
                        || split_part(b.source_estimate_ref, '/', 2) || '/' || split_part(b.source_estimate_ref, '/', 3)
                    FROM project_budget_versions b JOIN projects p ON p.id = b.project_id""", String.class));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM estimate_project_conversions", Integer.class));
            Files.writeString(output.resolve("database-boundaries.json"), """
                    {"wholeRowTablesCompared":12,"recordScoreContributionCommercialWriteStatements":0,
                    "estimateFacts":["F06B-ORDER R1 SUPERSEDED","F06B-ORDER R2 APPROVED","F06B-RESIN R1 APPROVED (conversion refused)"],
                    "f05Facts":["F06B-PRJ one DRAFT planning budget 1500.25 from F06B-ORDER R2"],"conversionLineageRows":1,
                    "permittedMutation":"estimates, the single F05 DRAFT planning budget, lineage, audit, and business_profiles language changes",
                    "result":"PASS","database":"real disposable PostgreSQL; synthetic preexisting actual records, scores, project and invoice"}
                    """);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            if (Files.exists(log)) System.out.println(Files.readString(log));
        }
    }

    private Map<String, String> snapshot() {
        // The required language-switch journey deliberately updates business_profiles; financial tables remain strict.
        Map<String, String> result = new LinkedHashMap<>();
        for (String table : UNCHANGED_TABLES) result.put(table, jdbc.queryForObject(
                "SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text), '[]'::jsonb)::text FROM " + table + " t", String.class));
        return result;
    }

    /** Test-only statement probes detect even writes that would leave identical final rows. */
    private void installWriteProbe() throws Exception {
        try (Connection connection = POSTGRES.getPostgresDatabase().getConnection(); Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE f06b_browser_financial_writes (table_name text NOT NULL, operation text NOT NULL, statement text NOT NULL)");
            sql.execute("""
                    CREATE FUNCTION f06b_browser_capture_write() RETURNS trigger LANGUAGE plpgsql
                    SECURITY DEFINER SET search_path = pg_catalog, public AS $$
                    BEGIN INSERT INTO public.f06b_browser_financial_writes VALUES (TG_TABLE_NAME, TG_OP, current_query()); RETURN NULL; END $$
                    """);
            for (String table : PROBED_TABLES) sql.execute(
                    "CREATE TRIGGER f06b_browser_write_probe AFTER INSERT OR UPDATE OR DELETE OR TRUNCATE ON " + table
                            + " FOR EACH STATEMENT EXECUTE FUNCTION f06b_browser_capture_write()");
        }
    }
}
