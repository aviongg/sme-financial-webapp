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

/** Opt-in production Next / real Spring / disposable PostgreSQL F04 journey. No shared database or paid provider. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.address=127.0.0.1", "server.port=8080",
        "app.security.internal-service-secret=f04-browser-fixture-only-not-production",
        "app.ai.enabled=false", "app.ai.live-activation-approved=false", "app.ai.api-key=",
        "app.security.cors.allowed-origins=http://127.0.0.1:3100"
})
@ActiveProfiles("dev")
class F04CommercialBrowserAcceptance extends DisposablePostgres {
    private static final List<String> UNCHANGED_TABLES = List.of(
            "monthly_records", "score_results", "insights", "recommendations", "uploaded_documents",
            "whatsapp_deliveries", "business_configuration", "dashboard_preferences",
            "document_financial_contributions", "projects", "project_budget_versions", "project_budget_lines");
    private static final List<String> PROBED_TABLES = List.of(
            "monthly_records", "score_results", "document_financial_contributions",
            "projects", "project_budget_versions", "project_budget_lines");

    @Autowired JdbcTemplate jdbc;

    @Test
    void authenticatedInvoiceAndBillJourneyRecordsDocumentsWithoutFinancialEffect() throws Exception {
        String node = System.getenv("FINSIGHT_BROWSER_NODE");
        assertTrue(node != null && !node.isBlank(), "Set FINSIGHT_BROWSER_NODE to a Node executable");
        Path script = Path.of("..", "scripts", "test_f04_commercial_browser.mjs").toAbsolutePath().normalize();
        Path output = Path.of(System.getenv().getOrDefault("BROWSER_ARTIFACT_DIR", "../tmp/f04/real-browser"))
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
            assertFalse(before.get("projects").equals("[]"), "Require an existing F05 project fixture");
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM commercial_documents", Integer.class), "No commercial rows before the journey");
            installWriteProbe();
            Files.writeString(proceed, "Disposable PostgreSQL financial baseline captured; begin F04 actions.");
            assertTrue(process.waitFor(10, TimeUnit.MINUTES), "F04 browser acceptance timed out");
            assertEquals(0, process.exitValue(), "F04 browser acceptance failed; inspect browser-process.log");
            assertEquals(before, snapshot(), "Every financial/score/project/document/configuration row must remain identical");
            int writes;
            try (Connection connection = POSTGRES.getPostgresDatabase().getConnection(); Statement sql = connection.createStatement()) {
                StringBuilder diagnostics = new StringBuilder();
                try (var rows = sql.executeQuery("SELECT table_name, operation, statement FROM f04_browser_financial_writes")) {
                    while (rows.next()) diagnostics.append(rows.getString(1)).append(' ')
                            .append(rows.getString(2)).append(": ").append(rows.getString(3)).append('\n');
                }
                Files.writeString(output.resolve("financial-write-diagnostics.txt"), diagnostics.toString());
                try (var rows = sql.executeQuery("SELECT count(*) FROM f04_browser_financial_writes")) {
                    assertTrue(rows.next());
                    writes = rows.getInt(1);
                }
            }
            assertEquals(0, writes, "F04 must issue zero INSERT/UPDATE/DELETE/TRUNCATE statements on records, scores, contributions or projects");

            // The journey's own persisted facts: numbering, lifecycle, encryption at rest.
            assertEquals(List.of("BILL|BILL-000001|FINALIZED|10750.50", "INVOICE|INV-000001|VOID|1921.40", "INVOICE|INV-000002|FINALIZED|99.99"),
                    jdbc.queryForList("SELECT kind || '|' || document_number || '|' || status || '|' || total FROM commercial_documents "
                            + "WHERE document_number IS NOT NULL ORDER BY kind, document_number", String.class));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM commercial_documents WHERE counterparty_name_ciphertext NOT LIKE 'enc:v1:%' "
                    + "OR counterparty_name_ciphertext LIKE '%Acme%' OR counterparty_name_ciphertext LIKE '%Steel%'", Integer.class));
            assertEquals(List.of("BILL|2", "INVOICE|3"), jdbc.queryForList(
                    "SELECT kind || '|' || next_value FROM commercial_number_sequences ORDER BY kind", String.class));
            Files.writeString(output.resolve("database-boundaries.json"), """
                    {"wholeRowTablesCompared":12,"monthlyRecordScoreContributionProjectWriteStatements":0,
                    "commercialFacts":["INV-000001 VOID 1921.40","INV-000002 FINALIZED 99.99","BILL-000001 FINALIZED 10750.50"],
                    "counterpartyNamesEncryptedAtRest":true,
                    "permittedMutation":"business_profiles language preference changes through the required English-to-Urdu journey",
                    "result":"PASS","database":"real disposable PostgreSQL; synthetic preexisting actual records, scores and project"}
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
            sql.execute("CREATE TABLE f04_browser_financial_writes (table_name text NOT NULL, operation text NOT NULL, statement text NOT NULL)");
            sql.execute("""
                    CREATE FUNCTION f04_browser_capture_write() RETURNS trigger LANGUAGE plpgsql
                    SECURITY DEFINER SET search_path = pg_catalog, public AS $$
                    BEGIN INSERT INTO public.f04_browser_financial_writes VALUES (TG_TABLE_NAME, TG_OP, current_query()); RETURN NULL; END $$
                    """);
            for (String table : PROBED_TABLES) sql.execute(
                    "CREATE TRIGGER f04_browser_write_probe AFTER INSERT OR UPDATE OR DELETE OR TRUNCATE ON " + table
                            + " FOR EACH STATEMENT EXECUTE FUNCTION f04_browser_capture_write()");
        }
    }
}
