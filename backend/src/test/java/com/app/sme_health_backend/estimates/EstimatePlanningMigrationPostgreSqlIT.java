package com.app.sme_health_backend.estimates;

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
 * Verifies V36__persisted_estimates_and_conversion.sql on real disposable PostgreSQL: fresh history, a populated
 * V35 -> V36 upgrade (F04 V34 and F09 V35 present), full Phase A numeric capacity, tenant-scoped composite keys, one-DRAFT/one-APPROVED rules,
 * immutable history, safe draft deletion, insert-only lineage, TRUNCATE protection and role privileges.
 * V36 was authored in isolation as V33 and renumbered for the local integrated Developer-B candidate.
 */
class EstimatePlanningMigrationPostgreSqlIT extends DisposablePostgres {
    private static final List<String> TABLES = List.of("estimates", "estimate_versions", "estimate_lines", "estimate_project_conversions");
    private static final List<String> PRESERVED = List.of("monthly_records", "score_results", "business_profiles", "projects",
            "project_budget_versions", "project_budget_lines", "commercial_documents", "commercial_document_lines");
    private static final String MAX = "999999999999.999999999999";

    @Test
    void freshMigrationContainsV1ToV17AndV30ToV37() throws Exception {
        try (var connection = POSTGRES.getPostgresDatabase().getConnection(); var sql = connection.createStatement()) {
            List<String> versions = new ArrayList<>();
            try (var rs = sql.executeQuery("SELECT version FROM flyway_schema_history WHERE success AND type='SQL' ORDER BY installed_rank")) {
                while (rs.next()) versions.add(rs.getString(1));
            }
            assertEquals(com.app.sme_health_backend.testsupport.MigrationHistory.expected(), versions);
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
    void upgradesPopulatedV35SchemaAndEnforcesEveryInvariant() throws Exception {
        String schema = "f06b_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        String adminUrl = POSTGRES.getJdbcUrl("postgres", "postgres");
        migrate(adminUrl, schema, "35");
        try (var connection = POSTGRES.getPostgresDatabase().getConnection(); var sql = connection.createStatement()) {
            sql.execute("SET search_path TO " + schema);
            UUID a = UUID.randomUUID(), b = UUID.randomUUID(), user = UUID.randomUUID(), project = UUID.randomUUID();
            UUID approvedBudget = UUID.randomUUID(), draftBudget = UUID.randomUUID();
            sql.executeUpdate("INSERT INTO businesses(id) VALUES ('" + a + "'), ('" + b + "')");
            sql.executeUpdate("INSERT INTO app_users(id,email,password_hash,full_name) VALUES ('" + user
                    + "','f06b-migration@example.com','fixture-only','F06B Migration Fixture')");
            sql.executeUpdate("INSERT INTO business_profiles(user_id,business_type,language_preference) VALUES ('" + a + "','services','en')");
            sql.executeUpdate("INSERT INTO monthly_records(user_id,month,revenue,cogs,cash_balance_eom,receivables_outstanding,payables_outstanding) VALUES ('"
                    + a + "','2026-10',250000.00,120000.00,85000.00,15000.00,5000.00)");
            sql.executeUpdate("INSERT INTO score_results(user_id,month,composite_score,band,component_scores,weakest_component,data_completeness,methodology_version) VALUES ('"
                    + a + "','2026-10',74.20,'Healthy','{\"cashflow\":70,\"profitability\":80,\"repayment\":75,\"trend\":70,\"compliance\":75}','cashflow',1.00,'health-score-v1')");
            sql.executeUpdate("INSERT INTO projects(id,business_id,code,name,kind,status,version,created_by,updated_by) VALUES ('"
                    + project + "','" + a + "','PRJ-F06B','Upgrade fixture','PROJECT','ACTIVE',3,'" + user + "','" + user + "')");
            sql.executeUpdate("INSERT INTO project_budget_versions(id,business_id,project_id,revision,status,planned_revenue,planned_cost,planned_margin,planned_margin_pct,created_by,updated_by,approved_by,approved_at) VALUES ('"
                    + approvedBudget + "','" + a + "','" + project + "',1,'DRAFT',1000,800,200,0.2,'" + user + "','" + user + "',NULL,NULL)");
            sql.executeUpdate("INSERT INTO project_budget_lines(business_id,project_id,budget_version_id,line_number,category,description,quantity,unit,unit_cost,planned_line_cost) VALUES ('"
                    + a + "','" + project + "','" + approvedBudget + "',1,'MATERIAL','Steel',1,'batch',800,800)");
            sql.executeUpdate("UPDATE project_budget_versions SET status='APPROVED',approved_by='" + user + "',approved_at=now(),version=version+1 WHERE id='" + approvedBudget + "'");
            sql.executeUpdate("INSERT INTO commercial_documents(business_id,kind,created_by_user_id,updated_by_user_id) VALUES ('"
                    + a + "','INVOICE','" + user + "','" + user + "')");
            Map<String, String> before = snapshot(sql, PRESERVED);

            migrate(adminUrl, schema, "36");
            assertEquals(before, snapshot(sql, PRESERVED), "V36 must not change monthly, score, profile, project or commercial rows");
            try (var rs = sql.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE version IN ('34','35','36') AND success")) {
                assertTrue(rs.next());
                assertEquals(3, rs.getInt(1), "F04 V34 and F09 V35 precede F06 V36");
            }
            assertPrivileges(sql, schema);

            // ---- estimates ----
            UUID estimate = estimate(sql, a, user, "EST-1");
            estimate(sql, b, user, "EST-1"); // the same code in another business is allowed
            assertState("23505", sql, insertEstimate(a, user, "EST-1"));
            for (String bad : List.of(insertEstimate(a, user, "est-lower"), insertEstimate(a, user, " "),
                    "INSERT INTO estimates(business_id,code,name,base_currency,created_by_user_id,updated_by_user_id) VALUES ('"
                            + a + "','EST-USD','n','USD','" + user + "','" + user + "')",
                    "INSERT INTO estimates(business_id,code,name,created_by_user_id,updated_by_user_id) VALUES ('"
                            + a + "','EST-BLANK',' ','" + user + "','" + user + "')")) {
                assertState("23514", sql, bad);
            }
            assertState("55000", sql, "INSERT INTO estimates(business_id,code,name,status,archived_by_user_id,archived_at,created_by_user_id,updated_by_user_id) VALUES ('"
                    + a + "','EST-ARCH','n','ARCHIVED','" + user + "',now(),'" + user + "','" + user + "')");

            // ---- revision CHECKs: f06-preview-v1 identities and availability reasons ----
            UUID draft = revision(sql, a, estimate, user, 1, "PROPOSED_PRICE", "1000", "800", "1000", "200", "0.2", "0.25");
            line(sql, a, estimate, draft, 1, "1", "800", "800");
            for (String bad : List.of(
                    revisionSql(a, estimate, user, 2, "PROPOSED_PRICE", "1000", "800", "999", "199", "0.2", "0.25"),
                    revisionSql(a, estimate, user, 2, "PROPOSED_PRICE", "1000", "800", "1000", "199", "0.2", "0.25"),
                    revisionSql(a, estimate, user, 2, "TARGET_MARKUP", "0.2", "800", "961", "161", "0.1", "0.2"),
                    revisionSql(a, estimate, user, 2, "TARGET_MARGIN", "1", "800", "1000", "200", "0.2", "0.25"),
                    revisionSql(a, estimate, user, 2, "PROPOSED_PRICE", "0", "800", "0", "-800", "0", "-1"),
                    revisionSql(a, estimate, user, 2, "PROPOSED_PRICE", "1000", "0", "1000", "1000", "1", "1"),
                    revisionSql(a, estimate, user, 2, "QUOTE", "1000", "800", "1000", "200", "0.2", "0.25"),
                    revisionSql(a, estimate, user, 2, "PROPOSED_PRICE", "1000", "800", "1000", "200", "0.2", "0.25")
                            .replace("'f06-preview-v1'", "'f06-preview-v2'"))) {
                assertState("23514", sql, bad);
            }
            // One open DRAFT per estimate.
            assertState("23505", sql, revisionSql(a, estimate, user, 2, "PROPOSED_PRICE", "1", "0", "1", "1", "1", null));
            // Cross-business rows cannot attach to another business's estimate/revision: the BEFORE trigger finds no
            // same-business parent and rejects first; the composite (id, business_id) foreign keys are the backstop.
            assertState("55000", sql, revisionSql(b, estimate, user, 1, "PROPOSED_PRICE", "1", "0", "1", "1", "1", null));
            assertState("55000", sql, lineSql(b, estimate, draft, 2, "1", "1", "1"));
            assertState("55000", sql, lineSql(a, UUID.randomUUID(), draft, 2, "1", "1", "1"));
            assertState("55000", sql, revisionSql(a, estimate, user, 2, "PROPOSED_PRICE", "1", "0", "1", "1", "1", null)
                    .replace("'DRAFT'", "'APPROVED'"));

            // ---- lines: exact product, stripped text, Phase A capacity ----
            for (String bad : List.of(lineSql(a, estimate, draft, 2, "1.5", "333.33", "499.99"),
                    lineSql(a, estimate, draft, 2, "-1", "1", "-1"),
                    lineSql(a, estimate, draft, 101, "1", "1", "1"),
                    lineSql(a, estimate, draft, 2, "1", "1", "1").replace("'Line'", "' Line'"),
                    lineSql(a, estimate, draft, 2, "1", "1", "1").replace("'Line'", "E'Li\\x07ne'"),
                    lineSql(a, estimate, draft, 2, "1", "1", "1").replace("'Line'", "E'Li\\u0085ne'"),
                    lineSql(a, estimate, draft, 2, "1", "1", "1").replace("'MATERIAL'", "'TAX'"))) {
                assertState("23514", sql, bad);
            }
            assertState("23505", sql, lineSql(a, estimate, draft, 1, "1", "800", "800"));
            // The full Phase A domain fits without narrowing; one more integer digit overflows instead of rounding.
            UUID wide = estimate(sql, a, user, "EST-WIDE");
            UUID wideRevision = revision(sql, a, wide, user, 1, "PROPOSED_PRICE", MAX, "0", MAX, MAX, "1", null);
            line(sql, a, wide, wideRevision, 1, MAX, "0", "0");
            sql.executeUpdate("UPDATE estimate_versions SET saleable_quantity = " + MAX + ", planned_per_unit_price = 1, "
                    + "planned_per_unit_price_unavailable_reason = NULL, version = version + 1 WHERE id = '" + wideRevision + "'");
            try (var rs = sql.executeQuery("SELECT pricing_value::text, saleable_quantity::text FROM estimate_versions WHERE id='" + wideRevision + "'")) {
                assertTrue(rs.next());
                assertEquals(MAX, rs.getString(1));
                assertEquals(MAX, rs.getString(2));
            }
            assertState("22003", sql, lineSql(a, wide, wideRevision, 2, "1000000000000", "0", "0"));
            sql.executeUpdate("UPDATE estimate_versions SET planned_markup = 10::numeric ^ 62, planned_markup_unavailable_reason = NULL, "
                    + "total_planned_cost = 0.000000000000000000000001, planned_contribution = " + MAX + " - 0.000000000000000000000001, "
                    + "version = version + 1 WHERE id = '" + wideRevision + "'");

            // ---- approval gate and immutable history ----
            UUID empty = estimate(sql, a, user, "EST-EMPTY");
            UUID noLines = revision(sql, a, empty, user, 1, "PROPOSED_PRICE", "1", "0", "1", "1", "1", null);
            assertState("55000", sql, approve(noLines, user));
            UUID mismatch = estimate(sql, a, user, "EST-SUM");
            UUID mismatched = revision(sql, a, mismatch, user, 1, "PROPOSED_PRICE", "10", "8", "10", "2", "0.2", "0.25");
            line(sql, a, mismatch, mismatched, 1, "1", "7", "7");
            assertState("55000", sql, approve(mismatched, user));
            sql.executeUpdate(approve(draft, user));
            UUID second = revision(sql, a, estimate, user, 2, "PROPOSED_PRICE", "960", "800", "960", "160", "0.1666", "0.2");
            line(sql, a, estimate, second, 1, "1", "800", "800");
            assertState("23505", sql, approve(second, user)); // a second APPROVED without supersession
            for (String bad : List.of(
                    "UPDATE estimate_versions SET planned_batch_price = 1001, planned_contribution = 201, version = version + 1 WHERE id = '" + draft + "'",
                    "UPDATE estimate_versions SET status = 'DRAFT', approved_by_user_id = NULL, approved_at = NULL, version = version + 1 WHERE id = '" + draft + "'",
                    "UPDATE estimate_versions SET status = 'SUPERSEDED', superseded_by_version_id = '" + second + "', superseded_at = now() WHERE id = '" + draft + "'",
                    "DELETE FROM estimate_versions WHERE id = '" + draft + "'",
                    lineSql(a, estimate, draft, 2, "1", "1", "1"),
                    "UPDATE estimate_lines SET description = 'Changed' WHERE estimate_version_id = '" + draft + "'",
                    "UPDATE estimate_lines SET estimate_version_id = '" + second + "', line_number = 2 WHERE estimate_version_id = '" + draft + "'",
                    "DELETE FROM estimate_lines WHERE estimate_version_id = '" + draft + "'",
                    "UPDATE estimate_versions SET status = 'SUPERSEDED', superseded_by_version_id = '" + second
                            + "', superseded_at = now(), version = version + 1 WHERE id = '" + second + "'")) {
                assertState("55000", sql, bad);
            }
            sql.executeUpdate("UPDATE estimate_versions SET status = 'SUPERSEDED', superseded_by_version_id = '" + second
                    + "', superseded_at = now(), version = version + 1 WHERE id = '" + draft + "'");
            sql.executeUpdate(approve(second, user));
            assertState("55000", sql, "UPDATE estimate_versions SET updated_at = now(), version = version + 1 WHERE id = '" + draft + "'");
            assertState("55000", sql, "DELETE FROM estimate_versions WHERE id = '" + draft + "'");

            // ---- safe draft deletion, archive and estimate deletion ----
            UUID third = revision(sql, a, estimate, user, 3, "PROPOSED_PRICE", "1", "0", "1", "1", "1", null);
            line(sql, a, estimate, third, 1, "1", "0", "0");
            assertState("55000", sql, archive(estimate, user));
            assertEquals(1, sql.executeUpdate("DELETE FROM estimate_versions WHERE id = '" + third + "'"));
            assertCount(sql, "SELECT count(*) FROM estimate_lines WHERE estimate_version_id = '" + third + "'", 0);
            assertState("55000", sql, "DELETE FROM estimates WHERE id = '" + estimate + "'");
            sql.executeUpdate(archive(estimate, user));
            assertState("55000", sql, "UPDATE estimates SET name = 'Changed', version = version + 1 WHERE id = '" + estimate + "'");
            assertState("55000", sql, "DELETE FROM estimates WHERE id = '" + estimate + "'");
            assertState("55000", sql, revisionSql(a, estimate, user, 4, "PROPOSED_PRICE", "1", "0", "1", "1", "1", null));
            assertState("55000", sql, "UPDATE estimates SET version = version WHERE id = '" + wide + "'");
            assertState("55000", sql, "UPDATE estimates SET business_id = '" + b + "', version = version + 1 WHERE id = '" + wide + "'");
            // Draft-only estimates can be deleted once their drafts are removed (RESTRICT protects any history).
            assertState("23503", sql, "DELETE FROM estimates WHERE id = '" + empty + "'");
            sql.executeUpdate("DELETE FROM estimate_versions WHERE estimate_id = '" + empty + "'");
            assertEquals(1, sql.executeUpdate("DELETE FROM estimates WHERE id = '" + empty + "'"));

            // ---- insert-only conversion lineage into an F05 DRAFT budget ----
            UUID convertible = estimate(sql, a, user, "EST-CONV");
            UUID source = revision(sql, a, convertible, user, 1, "PROPOSED_PRICE", "1000", "800", "1000", "200", "0.2", "0.25");
            line(sql, a, convertible, source, 1, "1", "800", "800");
            assertState("55000", sql, conversion(a, convertible, source, project, draftBudget, user)); // source not approved
            sql.executeUpdate(approve(source, user));
            assertState("55000", sql, conversion(a, convertible, source, project, draftBudget, user)); // budget absent
            assertState("55000", sql, conversion(a, convertible, source, project, approvedBudget, user)); // budget not DRAFT
            sql.executeUpdate("INSERT INTO project_budget_versions(id,business_id,project_id,revision,status,planned_revenue,planned_cost,planned_margin,planned_margin_pct,source_estimate_ref,created_by,updated_by) VALUES ('"
                    + draftBudget + "','" + a + "','" + project + "',2,'DRAFT',1000,800,200,0.2,'F06/EST-CONV/R1','" + user + "','" + user + "')");
            assertState("55000", sql, conversion(b, convertible, source, project, draftBudget, user)); // cross-business
            sql.executeUpdate(conversion(a, convertible, source, project, draftBudget, user));
            assertState("23505", sql, conversion(a, convertible, source, project, draftBudget, user));
            for (String bad : List.of("UPDATE estimate_project_conversions SET converted_at = now() WHERE estimate_version_id = '" + source + "'",
                    "DELETE FROM estimate_project_conversions WHERE estimate_version_id = '" + source + "'")) {
                assertState("55000", sql, bad);
            }
            assertState("23514", sql, conversion(a, convertible, source, project, draftBudget, user).replace("'f06-f05-exact-v1'", "'rounded-v1'"));
            // F05 keeps authority over its own draft: discarding it is not blocked by lineage.
            assertEquals(1, sql.executeUpdate("DELETE FROM project_budget_versions WHERE id = '" + draftBudget + "'"));
            assertCount(sql, "SELECT count(*) FROM estimate_project_conversions WHERE estimate_version_id = '" + source + "'", 1);

            for (String table : TABLES) assertState("55000", sql, "TRUNCATE TABLE " + table + " CASCADE");
        }
    }

    private static void migrate(String url, String schema, String target) {
        Flyway.configure().dataSource(url, "postgres", "").schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(target).load().migrate();
    }

    private static void assertPrivileges(Statement sql, String schema) throws SQLException {
        for (String table : TABLES) {
            String qualified = schema + "." + table;
            boolean lineage = table.equals("estimate_project_conversions");
            try (var rs = sql.executeQuery("SELECT has_table_privilege('finsight_app','" + qualified + "','SELECT,INSERT'),"
                    + "has_table_privilege('finsight_app','" + qualified + "','UPDATE'),"
                    + "has_table_privilege('finsight_app','" + qualified + "','DELETE'),"
                    + "has_table_privilege('finsight_app','" + qualified + "','TRUNCATE'),"
                    + "has_table_privilege('finsight_app','" + qualified + "','REFERENCES,TRIGGER'),"
                    + "has_table_privilege('finsight_backup','" + qualified + "','SELECT'),"
                    + "has_table_privilege('finsight_backup','" + qualified + "','INSERT'),"
                    + "has_table_privilege('finsight_backup','" + qualified + "','UPDATE'),"
                    + "has_table_privilege('finsight_backup','" + qualified + "','DELETE'),"
                    + "has_table_privilege('finsight_backup','" + qualified + "','TRUNCATE')")) {
                assertTrue(rs.next());
                assertTrue(rs.getBoolean(1), "finsight_app SELECT/INSERT on " + qualified);
                assertEquals(!lineage, rs.getBoolean(2), "finsight_app UPDATE except on insert-only lineage: " + qualified);
                assertEquals(!lineage, rs.getBoolean(3), "finsight_app DELETE except on insert-only lineage: " + qualified);
                assertFalse(rs.getBoolean(4), "finsight_app has no TRUNCATE on " + qualified);
                assertFalse(rs.getBoolean(5), "finsight_app has no REFERENCES/TRIGGER on " + qualified);
                assertTrue(rs.getBoolean(6), "finsight_backup SELECT on " + qualified);
                for (int column = 7; column <= 10; column++) assertFalse(rs.getBoolean(column), "finsight_backup is read-only on " + qualified);
            }
        }
    }

    private static UUID estimate(Statement sql, UUID business, UUID user, String code) throws SQLException {
        UUID id = UUID.randomUUID();
        sql.executeUpdate(insertEstimate(business, user, code).replace("(business_id,", "(id,business_id,").replace("VALUES ('", "VALUES ('" + id + "','"));
        return id;
    }
    private static String insertEstimate(UUID business, UUID user, String code) {
        return "INSERT INTO estimates(business_id,code,name,created_by_user_id,updated_by_user_id) VALUES ('"
                + business + "','" + code + "','Estimate " + code + "','" + user + "','" + user + "')";
    }
    private static UUID revision(Statement sql, UUID business, UUID estimate, UUID user, int revision, String mode, String value,
                                 String total, String price, String contribution, String margin, String markup) throws SQLException {
        String insert = revisionSql(business, estimate, user, revision, mode, value, total, price, contribution, margin, markup);
        try (var rs = sql.executeQuery(insert + " RETURNING id")) {
            assertTrue(rs.next());
            return rs.getObject(1, UUID.class);
        }
    }
    /** Saleable quantity unknown; margin/markup reasons follow the f06-preview-v1 availability rules. */
    private static String revisionSql(UUID business, UUID estimate, UUID user, int revision, String mode, String value, String total,
                                      String price, String contribution, String margin, String markup) {
        return "INSERT INTO estimate_versions(business_id,estimate_id,revision,status,pricing_mode,pricing_value,saleable_quantity,"
                + "total_planned_cost,planned_batch_price,planned_contribution,planned_margin,planned_markup,planned_per_unit_price,"
                + "planned_margin_unavailable_reason,planned_markup_unavailable_reason,planned_per_unit_price_unavailable_reason,"
                + "calculation_version,created_by_user_id,updated_by_user_id) VALUES ('" + business + "','" + estimate + "'," + revision
                + ",'DRAFT','" + mode + "'," + value + ",NULL," + total + "," + price + "," + contribution + ","
                + (margin == null ? "NULL" : margin) + "," + (markup == null ? "NULL" : markup) + ",NULL,"
                + (margin == null ? "'ZERO_PRICE'" : "NULL") + "," + (markup == null ? "'ZERO_COST'" : "NULL")
                + ",'SALEABLE_QUANTITY_UNKNOWN','f06-preview-v1','" + user + "','" + user + "')";
    }
    private static void line(Statement sql, UUID business, UUID estimate, UUID revision, int number, String quantity, String unitCost,
                             String plannedCost) throws SQLException {
        sql.executeUpdate(lineSql(business, estimate, revision, number, quantity, unitCost, plannedCost));
    }
    private static String lineSql(UUID business, UUID estimate, UUID revision, int number, String quantity, String unitCost, String plannedCost) {
        return "INSERT INTO estimate_lines(business_id,estimate_id,estimate_version_id,line_number,cost_group,description,quantity,unit,unit_cost,planned_cost) VALUES ('"
                + business + "','" + estimate + "','" + revision + "'," + number + ",'MATERIAL','Line'," + quantity + ",'batch'," + unitCost + "," + plannedCost + ")";
    }
    private static String approve(UUID revision, UUID user) {
        return "UPDATE estimate_versions SET status='APPROVED',approved_by_user_id='" + user + "',approved_at=now(),version=version+1 WHERE id='" + revision + "'";
    }
    private static String archive(UUID estimate, UUID user) {
        return "UPDATE estimates SET status='ARCHIVED',archived_by_user_id='" + user + "',archived_at=now(),version=version+1 WHERE id='" + estimate + "'";
    }
    private static String conversion(UUID business, UUID estimate, UUID revision, UUID project, UUID budget, UUID user) {
        return "INSERT INTO estimate_project_conversions(business_id,estimate_id,estimate_version_id,project_id,project_budget_version_id,"
                + "conversion_contract_version,source_calculation_version,target_calculation_version,converted_by_user_id) VALUES ('"
                + business + "','" + estimate + "','" + revision + "','" + project + "','" + budget + "','f06-f05-exact-v1','f06-preview-v1','f05-plan-v1','" + user + "')";
    }
    private static void assertState(String state, Statement sql, String statement) {
        assertEquals(state, assertThrows(SQLException.class, () -> sql.execute(statement), statement).getSQLState(), statement);
    }
    private static void assertCount(Statement sql, String query, int expected) throws SQLException {
        try (var rs = sql.executeQuery(query)) {
            assertTrue(rs.next());
            assertEquals(expected, rs.getInt(1), query);
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
}
