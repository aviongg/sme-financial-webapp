package com.app.sme_health_backend.projects;

import com.app.sme_health_backend.testsupport.DisposablePostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies V30__project_planning_foundation.sql on real disposable PostgreSQL:
 * - populated V29 -> V30 upgrade preserving monthly_records, score_results, business_configuration, dashboard_preferences
 * - UUID identity, composite tenant FKs, and partial unique indexes (one DRAFT, one APPROVED per project)
 * - enforce_project_planning_immutability() trigger protection against TRUNCATE, history deletion, and approved mutation
 * - finsight_app (SELECT, INSERT, UPDATE, DELETE) and finsight_backup (SELECT only) table privileges
 */
class ProjectPlanningMigrationPostgreSqlIT extends DisposablePostgres {

    @Test
    void upgradesPopulatedV29SchemaToV30AndEnforcesConstraintsTriggersAndPrivileges() throws Exception {
        String schema = "f05_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        String adminUrl = POSTGRES.getJdbcUrl("postgres", "postgres");

        Flyway.configure()
                .dataSource(adminUrl, "postgres", "")
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target("29")
                .load()
                .migrate();

        try (var connection = POSTGRES.getPostgresDatabase().getConnection();
             var sql = connection.createStatement()) {
            sql.execute("SET search_path TO " + schema);

            UUID businessA = UUID.randomUUID();
            UUID businessB = UUID.randomUUID();
            UUID user = UUID.randomUUID();

            sql.executeUpdate("INSERT INTO businesses(id) VALUES ('" + businessA + "'), ('" + businessB + "')");
            sql.executeUpdate("INSERT INTO app_users(id,email,password_hash,full_name) VALUES ('"
                    + user + "','f05-migration@example.com','fixture-only','F05 Migration Fixture')");
            sql.executeUpdate("INSERT INTO business_profiles(user_id,business_type,language_preference) VALUES ('"
                    + businessA + "','services','en')");
            sql.executeUpdate("INSERT INTO business_configuration(business_id,operating_profile,requested_modules,version,configured) VALUES ('"
                    + businessA + "','PROJECT_ORDER','[\"core_finance\",\"projects\"]',2,true)");
            sql.executeUpdate("INSERT INTO dashboard_preferences(business_id,user_id,registry_version,version) VALUES ('"
                    + businessA + "','" + user + "','f13-v1',1)");
            sql.executeUpdate("INSERT INTO monthly_records(user_id,month,revenue,cogs,cash_balance_eom,receivables_outstanding,payables_outstanding) VALUES ('"
                    + businessA + "','2026-10',250000.00,120000.00,85000.00,15000.00,5000.00)");
            sql.executeUpdate("INSERT INTO score_results(user_id,month,composite_score,band,component_scores,weakest_component,data_completeness,methodology_version) VALUES ('"
                    + businessA + "','2026-10',74.20,'Healthy','{\"cashflow\":70,\"profitability\":80,\"repayment\":75,\"trend\":70,\"compliance\":75}','cashflow',1.00,'health-score-v1')");

            Map<String, String> before = new LinkedHashMap<>();
            for (String table : List.of(
                    "business_configuration",
                    "dashboard_preferences",
                    "monthly_records",
                    "score_results",
                    "business_profiles"
            )) {
                try (var rows = sql.executeQuery("SELECT row_to_json(t)::text FROM " + table + " t")) {
                    assertTrue(rows.next());
                    before.put(table, rows.getString(1));
                }
            }

            Flyway.configure()
                    .dataSource(adminUrl, "postgres", "")
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations("classpath:db/migration")
                    .target("30")
                    .load()
                    .migrate();

            for (var entry : before.entrySet()) {
                try (var rows = sql.executeQuery("SELECT row_to_json(t)::text FROM " + entry.getKey() + " t")) {
                    assertTrue(rows.next());
                    assertEquals(entry.getValue(), rows.getString(1), "Unchanged row after V30: " + entry.getKey());
                }
            }

            // Verify privileges on projects, project_budget_versions, project_budget_lines
            for (String table : List.of("projects", "project_budget_versions", "project_budget_lines")) {
                try (var rows = sql.executeQuery(
                        "SELECT has_table_privilege('finsight_app','" + schema + "." + table + "','SELECT,INSERT,UPDATE,DELETE'), "
                                + "has_table_privilege('finsight_app','" + schema + "." + table + "','TRUNCATE'), "
                                + "has_table_privilege('finsight_backup','" + schema + "." + table + "','SELECT'), "
                                + "has_table_privilege('finsight_backup','" + schema + "." + table + "','INSERT,UPDATE,DELETE')"
                )) {
                    assertTrue(rows.next());
                    assertTrue(rows.getBoolean(1), "finsight_app DML on " + table);
                    assertFalse(rows.getBoolean(2), "finsight_app no TRUNCATE on " + table);
                    assertTrue(rows.getBoolean(3), "finsight_backup SELECT on " + table);
                    assertFalse(rows.getBoolean(4), "finsight_backup no DML on " + table);
                }
            }

            // Create pure DRAFT project and DRAFT budget -> verify deletion of draft budget and pure-draft project succeeds
            UUID draftProject = UUID.randomUUID();
            UUID draftBudget = UUID.randomUUID();
            sql.executeUpdate("INSERT INTO projects(id,business_id,code,name,kind,status,version,created_by,updated_by) VALUES ('"
                    + draftProject + "','" + businessA + "','PRJ-DRAFT','Draft Only','PROJECT','DRAFT',0,'" + user + "','" + user + "')");
            sql.executeUpdate("INSERT INTO project_budget_versions(id,business_id,project_id,revision,status,planned_revenue,planned_cost,planned_margin,planned_margin_pct,version,created_by,updated_by) VALUES ('"
                    + draftBudget + "','" + businessA + "','" + draftProject + "',1,'DRAFT',1000.0000,600.000000000000,400.000000000000,40.000000000000,0,'" + user + "','" + user + "')");
            sql.executeUpdate("INSERT INTO project_budget_lines(id,business_id,project_id,budget_version_id,line_number,category,description,quantity,unit,unit_cost,planned_line_cost) VALUES ('"
                    + UUID.randomUUID() + "','" + businessA + "','" + draftProject + "','" + draftBudget + "',1,'LABOUR','Tech',10.000000,'hour',60.000000,600.000000000000)");

            // Second open DRAFT on same project is rejected by partial unique index uq_project_budget_versions_one_draft
            SQLException secondDraftEx = assertThrows(SQLException.class, () ->
                    sql.executeUpdate("INSERT INTO project_budget_versions(id,business_id,project_id,revision,status,planned_revenue,planned_cost,planned_margin,planned_margin_pct,version,created_by,updated_by) VALUES ('"
                            + UUID.randomUUID() + "','" + businessA + "','" + draftProject + "',2,'DRAFT',1000,500,500,50,0,'" + user + "','" + user + "')")
            );
            assertEquals("23505", secondDraftEx.getSQLState());

            // Cross-tenant composite FK rejection on project_budget_versions
            SQLException crossTenantBudgetEx = assertThrows(SQLException.class, () ->
                    sql.executeUpdate("INSERT INTO project_budget_versions(id,business_id,project_id,revision,status,planned_revenue,planned_cost,planned_margin,planned_margin_pct,version,created_by,updated_by) VALUES ('"
                            + UUID.randomUUID() + "','" + businessB + "','" + draftProject + "',2,'DRAFT',1000,500,500,50,0,'" + user + "','" + user + "')")
            );
            assertEquals("23503", crossTenantBudgetEx.getSQLState());

            // Legitimate DRAFT line editing and cascading draft cleanup remain allowed.
            assertEquals(1, sql.executeUpdate("UPDATE project_budget_lines SET description='Updated draft work' WHERE budget_version_id='" + draftBudget + "'"));
            // Deleting DRAFT budget and pure-DRAFT project is allowed
            assertEquals(1, sql.executeUpdate("DELETE FROM project_budget_versions WHERE id='" + draftBudget + "'"));
            try (var rows = sql.executeQuery("SELECT count(*) FROM project_budget_lines WHERE budget_version_id='" + draftBudget + "'")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1), "Draft budget deletion cascades to its editable lines");
            }
            assertEquals(1, sql.executeUpdate("DELETE FROM projects WHERE id='" + draftProject + "'"));

            // Create project with DRAFT budget + line, then approve it and test immutability trigger
            UUID projectId = UUID.randomUUID();
            UUID approvedBudgetId = UUID.randomUUID();
            UUID replacementDraftId = UUID.randomUUID();
            UUID lineId = UUID.randomUUID();
            sql.executeUpdate("INSERT INTO projects(id,business_id,code,name,kind,status,version,created_by,updated_by) VALUES ('"
                    + projectId + "','" + businessA + "','PRJ-IMMUT','Immutable Check','JOB','DRAFT',1,'" + user + "','" + user + "')");
            sql.executeUpdate("INSERT INTO project_budget_versions(id,business_id,project_id,revision,status,planned_revenue,planned_cost,planned_margin,planned_margin_pct,version,created_by,updated_by) VALUES ('"
                    + approvedBudgetId + "','" + businessA + "','" + projectId + "',1,'DRAFT',5000.0000,3000.000000000000,2000.000000000000,40.000000000000,0,'" + user + "','" + user + "')");
            sql.executeUpdate("INSERT INTO project_budget_lines(id,business_id,project_id,budget_version_id,line_number,category,description,quantity,unit,unit_cost,planned_line_cost) VALUES ('"
                    + lineId + "','" + businessA + "','" + projectId + "','" + approvedBudgetId + "',1,'MATERIAL','Cable',100.000000,'meter',30.000000,3000.000000000000)");
            assertEquals(1, sql.executeUpdate("UPDATE project_budget_versions SET status='APPROVED', approved_by='" + user + "', approved_at=now(), version=1 WHERE id='" + approvedBudgetId + "'"));
            sql.executeUpdate("INSERT INTO project_budget_versions(id,business_id,project_id,revision,status,planned_revenue,planned_cost,planned_margin,planned_margin_pct,version,created_by,updated_by) VALUES ('"
                    + replacementDraftId + "','" + businessA + "','" + projectId + "',2,'DRAFT',6000.0000,3600.000000000000,2400.000000000000,40.000000000000,0,'" + user + "','" + user + "')");

            // Mutating an APPROVED budget's financials or lines, or deleting a project with approved history, is blocked with SQLSTATE 55000
            for (String forbiddenSql : List.of(
                    "UPDATE project_budget_versions SET planned_revenue = 9999.0000 WHERE id='" + approvedBudgetId + "'",
                    "DELETE FROM project_budget_versions WHERE id='" + approvedBudgetId + "'",
                    "UPDATE project_budget_lines SET unit_cost = 99.000000, planned_line_cost = 9900.000000000000 WHERE id='" + lineId + "'",
                    "UPDATE project_budget_lines SET budget_version_id='" + replacementDraftId + "' WHERE id='" + lineId + "'",
                    "DELETE FROM project_budget_lines WHERE id='" + lineId + "'",
                    "INSERT INTO project_budget_lines(id,business_id,project_id,budget_version_id,line_number,category,description,quantity,unit,unit_cost,planned_line_cost) VALUES ('"
                            + UUID.randomUUID() + "','" + businessA + "','" + projectId + "','" + approvedBudgetId + "',2,'OTHER','Extra',1,'item',10,10)",
                    "DELETE FROM projects WHERE id='" + projectId + "'",
                    "TRUNCATE TABLE project_budget_lines",
                    "TRUNCATE TABLE project_budget_versions CASCADE",
                    "TRUNCATE TABLE projects CASCADE"
            )) {
                SQLException immutEx = assertThrows(SQLException.class, () -> sql.execute(forbiddenSql),
                        "Expected immutability trigger to reject: " + forbiddenSql);
                assertEquals("55000", immutEx.getSQLState(), "Expected SQLSTATE 55000 for: " + forbiddenSql);
            }

            // Transitioning APPROVED -> SUPERSEDED is permitted by the trigger
            assertEquals(1, sql.executeUpdate(
                    "UPDATE project_budget_versions SET status='SUPERSEDED', superseded_by_budget_id='" + replacementDraftId + "', superseded_at=now(), version=version+1 WHERE id='" + approvedBudgetId + "'"
            ));

            // Once SUPERSEDED, further updates are blocked with SQLSTATE 55000
            SQLException supersededUpdateEx = assertThrows(SQLException.class, () ->
                    sql.executeUpdate("UPDATE project_budget_versions SET status='APPROVED', superseded_by_budget_id=NULL, superseded_at=NULL WHERE id='" + approvedBudgetId + "'")
            );
            assertEquals("55000", supersededUpdateEx.getSQLState());

            // Reparenting must never remove a line from immutable history, even when the destination is DRAFT.
            SQLException supersededLineMoveEx = assertThrows(SQLException.class, () ->
                    sql.executeUpdate("UPDATE project_budget_lines SET budget_version_id='" + replacementDraftId + "' WHERE id='" + lineId + "'")
            );
            assertEquals("55000", supersededLineMoveEx.getSQLState());
            try (var rows = sql.executeQuery("SELECT budget_version_id, description FROM project_budget_lines WHERE id='" + lineId + "'")) {
                assertTrue(rows.next());
                assertEquals(approvedBudgetId, rows.getObject(1, UUID.class), "Historical line stays with its approved/superseded budget");
                assertEquals("Cable", rows.getString(2));
            }
        } finally {
            try (var connection = POSTGRES.getPostgresDatabase().getConnection();
                 var sql = connection.createStatement()) {
                sql.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
}
