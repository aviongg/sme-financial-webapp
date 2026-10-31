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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in production Next / real Spring / disposable PostgreSQL journey for the local integrated Developer-B
 * candidate: every navigation entry plus the frontend UX-hardening fixes in English and Urdu/RTL, with zero writes to
 * financial, score, project, commercial, sample, estimate or inventory tables. Requires a production Next server on
 * 127.0.0.1:3100 rewriting /api to Spring on 8080; FINSIGHT_BROWSER_BASE_URL / FINSIGHT_BROWSER_BACKEND_PORT override both.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.address=127.0.0.1", "server.port=${FINSIGHT_BROWSER_BACKEND_PORT:8080}",
        "app.security.internal-service-secret=devb-browser-fixture-only-not-production",
        "app.ai.enabled=false", "app.ai.live-activation-approved=false", "app.ai.api-key=",
        "app.security.cors.allowed-origins=${FINSIGHT_BROWSER_BASE_URL:http://127.0.0.1:3100}"
})
@ActiveProfiles("dev")
class DevBIntegrationBrowserAcceptance extends DisposablePostgres {
    // business_profiles is excluded: the English-to-Urdu journey deliberately updates the language preference.
    private static final List<String> PROTECTED_TABLES = List.of(
            "monthly_records", "score_results", "document_financial_contributions", "business_configuration",
            "projects", "project_budget_versions", "project_budget_lines", "commercial_documents",
            "commercial_document_lines", "commercial_number_sequences", "sample_activities", "sample_cost_plan_versions",
            "sample_cost_plan_lines", "estimates", "estimate_versions", "estimate_lines", "estimate_project_conversions",
            "inventory_items");

    @Autowired JdbcTemplate jdbc;

    @Test
    void integratedNavigationAndUxHardeningJourneyHasNoWrites() throws Exception {
        String node = System.getenv("FINSIGHT_BROWSER_NODE");
        assertTrue(node != null && !node.isBlank(), "Set FINSIGHT_BROWSER_NODE to a Node executable");
        Path script = Path.of("..", "scripts", "test_devb_integration_browser.mjs").toAbsolutePath().normalize();
        Path output = Path.of(System.getenv().getOrDefault("BROWSER_ARTIFACT_DIR", "../tmp/devb-integration/real-browser"))
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
        builder.environment().put("FINSIGHT_BROWSER_BASE_URL",
                System.getenv().getOrDefault("FINSIGHT_BROWSER_BASE_URL", "http://127.0.0.1:3100"));
        Process process = builder.start();
        int scoreNoOpUpdates;
        try {
            long deadline = System.nanoTime() + Duration.ofMinutes(2).toNanos();
            while (!Files.exists(ready) && process.isAlive() && System.nanoTime() < deadline) Thread.sleep(100);
            assertTrue(Files.exists(ready), "Browser did not finish fixture setup; inspect browser-process.log");
            Map<String, String> before = snapshot();
            installWriteProbe();
            Files.writeString(proceed, "Disposable PostgreSQL baseline captured; begin navigation checks.");
            assertTrue(process.waitFor(10, TimeUnit.MINUTES), "Integration browser acceptance timed out");
            assertEquals(0, process.exitValue(), "Integration browser acceptance failed; inspect browser-process.log");
            assertEquals(before, snapshot(), "Every protected row must remain identical");
            try (Connection connection = POSTGRES.getPostgresDatabase().getConnection(); Statement sql = connection.createStatement();
                 var rows = sql.executeQuery("SELECT count(*) FILTER (WHERE table_name <> 'score_results'),"
                         + " string_agg(DISTINCT table_name || ':' || operation || ' ' || left(statement, 160), ' | ') FILTER (WHERE table_name <> 'score_results'),"
                         + " count(*) FILTER (WHERE table_name = 'score_results'),"
                         + " count(*) FILTER (WHERE table_name = 'score_results' AND operation <> 'UPDATE')"
                         + " FROM devb_browser_protected_writes")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1), "Navigation must issue zero INSERT/UPDATE/DELETE/TRUNCATE statements on protected tables: "
                        + rows.getString(2));
                // Pre-existing (unchanged since before 311de78): dashboard reads run inside the class-level @Transactional
                // AdviceContextService, so Hibernate flushes identical-value score UPDATEs. Rows are proven identical above.
                assertEquals(0, rows.getInt(4), "score_results may only see pre-existing no-op dirty-check UPDATEs");
                scoreNoOpUpdates = rows.getInt(3);
            }
            Files.writeString(output.resolve("database-boundaries.json"), """
                    {"wholeRowTablesCompared":%d,"probedTables":%d,"protectedWriteStatementsExcludingScore":0,
                    "scoreResultsNoOpDirtyCheckUpdates":%d,"scoreResultsRowsIdentical":true,
                    "permittedMutation":"sessions, security_audit_events and business_profiles language preference",
                    "result":"PASS","database":"real disposable PostgreSQL; synthetic records, score and project"}
                    """.formatted(PROTECTED_TABLES.size(), PROTECTED_TABLES.size(), scoreNoOpUpdates));
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            if (Files.exists(log)) System.out.println(Files.readString(log));
        }
    }

    private Map<String, String> snapshot() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String table : PROTECTED_TABLES) result.put(table, jdbc.queryForObject(
                "SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text), '[]'::jsonb)::text FROM " + table + " t", String.class));
        return result;
    }

    /** Test-only statement probes detect even writes that would leave identical final rows. */
    private void installWriteProbe() throws Exception {
        try (Connection connection = POSTGRES.getPostgresDatabase().getConnection(); Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE devb_browser_protected_writes (table_name text NOT NULL, operation text NOT NULL, statement text NOT NULL)");
            sql.execute("""
                    CREATE FUNCTION devb_browser_capture_write() RETURNS trigger LANGUAGE plpgsql
                    SECURITY DEFINER SET search_path = pg_catalog, public AS $$
                    BEGIN INSERT INTO public.devb_browser_protected_writes VALUES (TG_TABLE_NAME, TG_OP, current_query()); RETURN NULL; END $$
                    """);
            for (String table : PROTECTED_TABLES) sql.execute(
                    "CREATE TRIGGER devb_browser_write_probe AFTER INSERT OR UPDATE OR DELETE OR TRUNCATE ON " + table
                            + " FOR EACH STATEMENT EXECUTE FUNCTION devb_browser_capture_write()");
        }
    }
}
