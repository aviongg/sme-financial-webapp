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
 * Opt-in production Next / real Spring / disposable PostgreSQL journey for F08 Phase A (inventory catalog and
 * valuation preview). No shared database or paid provider. Requires a production Next server on 127.0.0.1:3100
 * rewriting /api to Spring on 8080; F08_BROWSER_WEB_PORT / F08_BROWSER_API_PORT override both when those ports are
 * owned by another task.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.address=127.0.0.1", "server.port=${F08_BROWSER_API_PORT:8080}",
        "app.security.internal-service-secret=f08-browser-fixture-only-not-production",
        "app.ai.enabled=false", "app.ai.live-activation-approved=false", "app.ai.api-key=",
        "app.security.cors.allowed-origins=http://127.0.0.1:${F08_BROWSER_WEB_PORT:3100}"
})
@ActiveProfiles("dev")
class F08InventoryBrowserAcceptance extends DisposablePostgres {
    // business_profiles is excluded: the required English-to-Urdu journey deliberately updates the language.
    private static final List<String> UNCHANGED_TABLES = List.of(
            "monthly_records", "score_results", "insights", "recommendations", "uploaded_documents",
            "document_corrections", "document_financial_contributions", "whatsapp_deliveries",
            "business_configuration", "dashboard_preferences", "projects", "project_budget_versions",
            "project_budget_lines", "commercial_documents", "commercial_document_lines", "commercial_number_sequences");
    private static final List<String> PROBED_TABLES = List.of(
            "monthly_records", "score_results", "document_financial_contributions", "projects",
            "project_budget_versions", "project_budget_lines", "commercial_documents", "commercial_document_lines");

    @Autowired JdbcTemplate jdbc;

    @Test
    void inventoryCatalogAndValuationPreviewJourneyHasNoFinancialEffect() throws Exception {
        String node = System.getenv("FINSIGHT_BROWSER_NODE");
        assertTrue(node != null && !node.isBlank(), "Set FINSIGHT_BROWSER_NODE to a Node executable");
        Path script = Path.of("..", "scripts", "test_f08_inventory_browser.mjs").toAbsolutePath().normalize();
        Path output = Path.of(System.getenv().getOrDefault("BROWSER_ARTIFACT_DIR", "../tmp/f08/real-browser"))
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
                "http://127.0.0.1:" + System.getenv().getOrDefault("F08_BROWSER_WEB_PORT", "3100"));
        Process process = builder.start();
        try {
            long deadline = System.nanoTime() + Duration.ofMinutes(2).toNanos();
            while (!Files.exists(ready) && process.isAlive() && System.nanoTime() < deadline) Thread.sleep(100);
            assertTrue(Files.exists(ready), "Browser did not finish isolated fixture setup; inspect browser-process.log");
            Map<String, String> before = snapshot();
            assertFalse(before.get("monthly_records").equals("[]"), "Require existing actual record fixtures");
            assertFalse(before.get("score_results").equals("[]"), "Require existing stored score fixtures");
            assertFalse(before.get("projects").equals("[]"), "Require an existing project fixture");
            assertFalse(before.get("commercial_documents").equals("[]"), "Require an existing commercial document fixture");
            int itemsBefore = jdbc.queryForObject("SELECT count(*) FROM inventory_items", Integer.class);
            installWriteProbe();
            Files.writeString(proceed, "Disposable PostgreSQL baseline captured; begin F08 actions.");
            assertTrue(process.waitFor(10, TimeUnit.MINUTES), "F08 browser acceptance timed out");
            assertEquals(0, process.exitValue(), "F08 browser acceptance failed; inspect browser-process.log");
            assertEquals(before, snapshot(), "Every financial/score/project/commercial/configuration row must remain identical");
            try (Connection connection = POSTGRES.getPostgresDatabase().getConnection(); Statement sql = connection.createStatement()) {
                try (var rows = sql.executeQuery("SELECT count(*) FROM f08_browser_protected_writes")) {
                    assertTrue(rows.next());
                    assertEquals(0, rows.getInt(1), "F08 must issue zero INSERT/UPDATE/DELETE/TRUNCATE statements on protected tables");
                }
            }
            int itemsAfter = jdbc.queryForObject("SELECT count(*) FROM inventory_items", Integer.class);
            int auditEvents = jdbc.queryForObject("SELECT count(*) FROM security_audit_events WHERE event_type LIKE 'INVENTORY_ITEM_%'", Integer.class);
            assertEquals(0, itemsBefore);
            assertTrue(itemsAfter >= 3, "The browser journey creates catalog items only");
            Files.writeString(output.resolve("database-boundaries.json"), """
                    {"wholeRowTablesCompared":%d,"probedTables":%d,"protectedWriteStatements":0,
                    "inventoryItemsCreated":%d,"inventoryAuditEvents":%d,
                    "permittedMutation":"inventory_items, security_audit_events, sessions, and business_profiles language preference through the English-to-Urdu journey",
                    "result":"PASS","database":"real disposable PostgreSQL; synthetic preexisting records, score, project and commercial document"}
                    """.formatted(UNCHANGED_TABLES.size(), PROBED_TABLES.size(), itemsAfter - itemsBefore, auditEvents));
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            if (Files.exists(log)) System.out.println(Files.readString(log));
        }
    }

    private Map<String, String> snapshot() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String table : UNCHANGED_TABLES) result.put(table, jdbc.queryForObject(
                "SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text), '[]'::jsonb)::text FROM " + table + " t", String.class));
        return result;
    }

    /** Test-only statement probes detect even writes that would leave identical final rows. */
    private void installWriteProbe() throws Exception {
        try (Connection connection = POSTGRES.getPostgresDatabase().getConnection(); Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE f08_browser_protected_writes (table_name text NOT NULL, operation text NOT NULL, statement text NOT NULL)");
            sql.execute("""
                    CREATE FUNCTION f08_browser_capture_write() RETURNS trigger LANGUAGE plpgsql
                    SECURITY DEFINER SET search_path = pg_catalog, public AS $$
                    BEGIN INSERT INTO public.f08_browser_protected_writes VALUES (TG_TABLE_NAME, TG_OP, current_query()); RETURN NULL; END $$
                    """);
            for (String table : PROBED_TABLES) sql.execute(
                    "CREATE TRIGGER f08_browser_write_probe AFTER INSERT OR UPDATE OR DELETE OR TRUNCATE ON " + table
                            + " FOR EACH STATEMENT EXECUTE FUNCTION f08_browser_capture_write()");
        }
    }
}
