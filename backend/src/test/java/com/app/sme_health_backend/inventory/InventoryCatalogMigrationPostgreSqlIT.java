package com.app.sme_health_backend.inventory;

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
 * Verifies V37__inventory_catalog_foundation.sql on real disposable PostgreSQL: migration history, populated
 * V34 -> V35 -> V36 -> V37 upgrade, preservation of financial/score/project/commercial rows, constraints, lifecycle trigger,
 * optimistic-version semantics and least-privilege grants.
 *
 * <p>History note: in the local integrated Developer-B candidate V35 (F09) and V36 (F06) precede V37, so the
 * complete history is exactly V1-V17, V30-V37.
 */
class InventoryCatalogMigrationPostgreSqlIT extends DisposablePostgres {
    private static final List<String> BASE_HISTORY = java.util.stream.IntStream.rangeClosed(1, 34)
            .mapToObj(String::valueOf).toList();
    private static final List<String> PRESERVED_TABLES = List.of("monthly_records", "score_results",
            "business_profiles", "document_financial_contributions", "projects", "project_budget_versions",
            "project_budget_lines", "commercial_documents", "commercial_document_lines", "commercial_number_sequences");

    @Test
    void freshHistoryEndsWithV37AndCreatesOnlyTheCatalogTable() throws Exception {
        try (var connection = POSTGRES.getPostgresDatabase().getConnection(); var sql = connection.createStatement()) {
            List<String> versions = new ArrayList<>();
            try (var rs = sql.executeQuery("SELECT version FROM flyway_schema_history WHERE success AND type='SQL' ORDER BY installed_rank")) {
                while (rs.next()) versions.add(rs.getString(1));
            }
            assertEquals(com.app.sme_health_backend.testsupport.MigrationHistory.expected(), versions,
                    "Expected the complete contiguous history");
            try (var rs = sql.executeQuery("SELECT to_regclass('public.inventory_items') IS NOT NULL")) {
                assertTrue(rs.next());
                assertTrue(rs.getBoolean(1));
            }
            // V37 created the catalog; V46 (product completion) adds the authoritative movement ledger and positions.
            try (var rs = sql.executeQuery("SELECT string_agg(table_name, ',' ORDER BY table_name) FROM information_schema.tables "
                    + "WHERE table_schema='public' AND table_name LIKE 'inventory%'")) {
                assertTrue(rs.next());
                assertEquals("inventory_items,inventory_movements,inventory_positions", rs.getString(1));
            }
            // No quantity-on-hand, stock value or average-cost column exists.
            List<String> columns = new ArrayList<>();
            try (var rs = sql.executeQuery("SELECT column_name FROM information_schema.columns "
                    + "WHERE table_schema='public' AND table_name='inventory_items' ORDER BY ordinal_position")) {
                while (rs.next()) columns.add(rs.getString(1));
            }
            assertEquals(List.of("id", "business_id", "sku", "name", "unit", "item_kind", "category", "reorder_level",
                    "status", "version", "created_by", "updated_by", "created_at", "updated_at"), columns);
            assertPrivileges(sql, "public");
        }
    }

    @Test
    void upgradesPopulatedV34SchemaWithoutTouchingExistingRowsAndEnforcesCatalogRules() throws Exception {
        String schema = "f08_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        String adminUrl = POSTGRES.getJdbcUrl("postgres", "postgres");
        migrate(adminUrl, schema, "34");
        try (var connection = POSTGRES.getPostgresDatabase().getConnection(); var sql = connection.createStatement()) {
            sql.execute("SET search_path TO " + schema);
            UUID businessA = UUID.randomUUID(), businessB = UUID.randomUUID(), user = UUID.randomUUID(), project = UUID.randomUUID();
            sql.executeUpdate("INSERT INTO businesses(id) VALUES ('" + businessA + "'), ('" + businessB + "')");
            sql.executeUpdate("INSERT INTO app_users(id,email,password_hash,full_name) VALUES ('" + user
                    + "','f08-migration@example.com','fixture-only','F08 Migration Fixture')");
            sql.executeUpdate("INSERT INTO business_profiles(user_id,business_type,language_preference) VALUES ('" + businessA + "','retail','en')");
            sql.executeUpdate("INSERT INTO monthly_records(user_id,month,revenue,cogs,cash_balance_eom,receivables_outstanding,payables_outstanding,inventory_value) VALUES ('"
                    + businessA + "','2026-10',250000.00,120000.00,85000.00,15000.00,5000.00,40000.00)");
            sql.executeUpdate("INSERT INTO score_results(user_id,month,composite_score,band,component_scores,weakest_component,data_completeness,methodology_version) VALUES ('"
                    + businessA + "','2026-10',74.20,'Healthy','{\"cashflow\":70,\"profitability\":80,\"repayment\":75,\"trend\":70,\"compliance\":75}','cashflow',1.00,'health-score-v1')");
            sql.executeUpdate("INSERT INTO projects(id,business_id,code,name,kind,status,version,created_by,updated_by) VALUES ('"
                    + project + "','" + businessA + "','PRJ-F08','Upgrade fixture','PROJECT','ACTIVE',1,'" + user + "','" + user + "')");
            sql.executeUpdate("INSERT INTO commercial_documents(business_id,kind,created_by_user_id,updated_by_user_id) VALUES ('"
                    + businessA + "','INVOICE','" + user + "','" + user + "')");
            Map<String, String> before = snapshot(sql, PRESERVED_TABLES);

            migrate(adminUrl, schema, "37");
            assertEquals(before, snapshot(sql, PRESERVED_TABLES),
                    "V35-V37 must not change MonthlyRecord, score, profile, contribution, project or commercial rows");
            try (var rs = sql.executeQuery("SELECT string_agg(version, ',' ORDER BY installed_rank) FROM flyway_schema_history"
                    + " WHERE success AND type='SQL' AND version IN ('34','35','36','37')")) {
                assertTrue(rs.next());
                assertEquals("34,35,36,37", rs.getString(1), "The upgrade applies V35, V36 and V37 in order after V34");
            }
            assertPrivileges(sql, schema);

            UUID item = item(sql, businessA, user, "RM-001", "RAW_MATERIAL", "12.5");
            item(sql, businessA, user, "FG-001", "FINISHED_GOOD", null);
            item(sql, businessB, user, "RM-001", "RESALE_GOOD", "0");
            try (var rs = sql.executeQuery("SELECT status, version, reorder_level FROM inventory_items WHERE id='" + item + "'")) {
                assertTrue(rs.next());
                assertEquals("ACTIVE", rs.getString(1), "Items are born ACTIVE");
                assertEquals(0, rs.getLong(2));
                assertEquals("12.500000", rs.getBigDecimal(3).toPlainString());
            }

            // SKU uniqueness is per business; stored SKUs are upper-case so it is case-insensitive.
            assertEquals("23505", assertThrows(SQLException.class,
                    () -> item(sql, businessA, user, "RM-001", "CONSUMABLE", null)).getSQLState());
            for (String bad : List.of(
                    insert(businessA, user, "rm-002", "CONSUMABLE", null),
                    insert(businessA, user, " RM-003", "CONSUMABLE", null),
                    insert(businessA, user, "-RM", "CONSUMABLE", null),
                    insert(businessA, user, "RM 004", "CONSUMABLE", null),
                    insert(businessA, user, "RM-005", "WORK_IN_PROGRESS", null),
                    insert(businessA, user, "RM-006", "CONSUMABLE", "-0.000001"),
                    "INSERT INTO inventory_items(business_id,sku,name,unit,item_kind,status,created_by,updated_by) VALUES ('"
                            + businessA + "','RM-007','Name','kg','CONSUMABLE','DELETED','" + user + "','" + user + "')",
                    "INSERT INTO inventory_items(business_id,sku,name,unit,item_kind,created_by,updated_by) VALUES ('"
                            + businessA + "','RM-008','  ','kg','CONSUMABLE','" + user + "','" + user + "')",
                    "INSERT INTO inventory_items(business_id,sku,name,unit,item_kind,category,created_by,updated_by) VALUES ('"
                            + businessA + "','RM-009','Name','kg','CONSUMABLE','',  '" + user + "','" + user + "')",
                    "INSERT INTO inventory_items(business_id,sku,name,unit,item_kind,version,created_by,updated_by) VALUES ('"
                            + businessA + "','RM-010','Name','kg','CONSUMABLE',-1,'" + user + "','" + user + "')")) {
                assertEquals("23514", assertThrows(SQLException.class, () -> sql.executeUpdate(bad), bad).getSQLState(), bad);
            }
            assertEquals("23503", assertThrows(SQLException.class, () -> sql.executeUpdate(
                    insert(UUID.randomUUID(), user, "RM-011", "CONSUMABLE", null))).getSQLState(), "Tenant must exist");

            // Optimistic versioning: every update strictly increases the version.
            assertEquals(1, sql.executeUpdate("UPDATE inventory_items SET name='Cotton yarn',version=version+1 WHERE id='" + item + "'"));
            for (String forbidden : List.of(
                    "UPDATE inventory_items SET name='Same version' WHERE id='" + item + "'",
                    "UPDATE inventory_items SET name='Older',version=0 WHERE id='" + item + "'",
                    "UPDATE inventory_items SET business_id='" + businessB + "',version=version+1 WHERE id='" + item + "'",
                    "UPDATE inventory_items SET id=gen_random_uuid(),version=version+1 WHERE id='" + item + "'",
                    "UPDATE inventory_items SET created_at=now() - interval '1 day',version=version+1 WHERE id='" + item + "'",
                    "DELETE FROM inventory_items WHERE id='" + item + "'")) {
                assertEquals("55000", assertThrows(SQLException.class, () -> sql.executeUpdate(forbidden), forbidden).getSQLState(), forbidden);
            }
            // Archive is terminal and the archived row is immutable.
            assertEquals(1, sql.executeUpdate("UPDATE inventory_items SET status='ARCHIVED',version=version+1 WHERE id='" + item + "'"));
            for (String forbidden : List.of(
                    "UPDATE inventory_items SET status='ACTIVE',version=version+1 WHERE id='" + item + "'",
                    "UPDATE inventory_items SET name='Edited after archive',version=version+1 WHERE id='" + item + "'",
                    "DELETE FROM inventory_items WHERE id='" + item + "'")) {
                assertEquals("55000", assertThrows(SQLException.class, () -> sql.executeUpdate(forbidden), forbidden).getSQLState(), forbidden);
            }
            assertEquals("23505", assertThrows(SQLException.class,
                    () -> item(sql, businessA, user, "RM-001", "CONSUMABLE", null)).getSQLState(),
                    "An archived SKU is retained and cannot be reused");
            assertEquals("55000", assertThrows(SQLException.class, () -> sql.execute("TRUNCATE TABLE inventory_items")).getSQLState());

            assertEquals(before, snapshot(sql, PRESERVED_TABLES), "Catalog writes never touch other domain tables");
        } finally {
            try (var connection = POSTGRES.getPostgresDatabase().getConnection(); var sql = connection.createStatement()) {
                sql.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    private static void migrate(String url, String schema, String target) {
        Flyway.configure().dataSource(url, "postgres", "").schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(target).load().migrate();
    }

    private static void assertPrivileges(Statement sql, String schema) throws SQLException {
        String table = schema + ".inventory_items";
        try (var rs = sql.executeQuery("SELECT has_table_privilege('finsight_app','" + table + "','SELECT'),"
                + "has_table_privilege('finsight_app','" + table + "','INSERT'),"
                + "has_table_privilege('finsight_app','" + table + "','UPDATE'),"
                + "has_table_privilege('finsight_app','" + table + "','DELETE'),"
                + "has_table_privilege('finsight_app','" + table + "','TRUNCATE'),"
                + "has_table_privilege('finsight_app','" + table + "','REFERENCES'),"
                + "has_table_privilege('finsight_app','" + table + "','TRIGGER'),"
                + "has_table_privilege('finsight_backup','" + table + "','SELECT'),"
                + "has_table_privilege('finsight_backup','" + table + "','INSERT'),"
                + "has_table_privilege('finsight_backup','" + table + "','UPDATE'),"
                + "has_table_privilege('finsight_backup','" + table + "','DELETE'),"
                + "has_table_privilege('finsight_backup','" + table + "','TRUNCATE')")) {
            assertTrue(rs.next());
            for (int column = 1; column <= 3; column++) assertTrue(rs.getBoolean(column), "finsight_app SELECT/INSERT/UPDATE");
            for (int column = 4; column <= 7; column++) assertFalse(rs.getBoolean(column), "finsight_app has no DELETE/TRUNCATE/REFERENCES/TRIGGER");
            assertTrue(rs.getBoolean(8), "finsight_backup SELECT");
            for (int column = 9; column <= 12; column++) assertFalse(rs.getBoolean(column), "finsight_backup is read-only");
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

    private static UUID item(Statement sql, UUID business, UUID user, String sku, String kind, String reorder) throws SQLException {
        UUID id = UUID.randomUUID();
        sql.executeUpdate(insert(business, user, sku, kind, reorder)
                .replace("(business_id", "(id,business_id").replace("VALUES (", "VALUES ('" + id + "',"));
        return id;
    }

    private static String insert(UUID business, UUID user, String sku, String kind, String reorder) {
        return "INSERT INTO inventory_items(business_id,sku,name,unit,item_kind,reorder_level,created_by,updated_by) VALUES ('"
                + business + "','" + sku + "','Fixture item','kg','" + kind + "'," + (reorder == null ? "NULL" : reorder)
                + ",'" + user + "','" + user + "')";
    }
}
