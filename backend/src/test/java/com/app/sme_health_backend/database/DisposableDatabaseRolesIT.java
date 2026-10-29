package com.app.sme_health_backend.database;

import com.app.sme_health_backend.testsupport.DisposablePostgres;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Privilege checks on real disposable PostgreSQL; Docker HBA/TLS acceptance stays in its own suites. */
class DisposableDatabaseRolesIT extends DisposablePostgres {
    private Connection connect(String role) throws SQLException {
        Connection connection = DriverManager.getConnection(URL, role, PASSWORD);
        try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT current_user")) {
            assertTrue(result.next());
            assertEquals(role, result.getString(1), "JDBC must not override the requested role");
        }
        return connection;
    }

    @Test
    void allServiceRolesHaveRestrictedAttributesAndScramCredentials() throws Exception {
        try (var connection = POSTGRES.getPostgresDatabase().getConnection();
             var statement = connection.createStatement();
             var roles = statement.executeQuery("SELECT rolname, rolsuper, rolcreatedb, rolcreaterole, rolbypassrls, "
                     + "rolpassword LIKE 'SCRAM-SHA-256$%' AS scram FROM pg_authid "
                     + "WHERE rolname IN ('finsight_app', 'finsight_migrator', 'finsight_backup')")) {
            int count = 0;
            while (roles.next()) {
                assertFalse(roles.getBoolean("rolsuper"));
                assertFalse(roles.getBoolean("rolcreatedb"));
                assertFalse(roles.getBoolean("rolcreaterole"));
                assertFalse(roles.getBoolean("rolbypassrls"));
                assertTrue(roles.getBoolean("scram"));
                count++;
            }
            assertEquals(3, count);
        }
    }

    @Test
    void runtimeCannotCreateAlterDropTruncateOrReadMigrationHistory() throws Exception {
        try (var connection = connect("finsight_app"); var statement = connection.createStatement()) {
            for (String sql : new String[] {
                    "CREATE TABLE forbidden_runtime_table (id integer)",
                    "ALTER TABLE app_users ADD COLUMN forbidden_runtime_column integer",
                    "DROP TABLE app_users",
                    "TRUNCATE TABLE app_users",
                    "ALTER TABLE business_configuration ADD COLUMN forbidden_runtime_column integer",
                    "DROP TABLE business_configuration",
                    "TRUNCATE TABLE business_configuration",
                    "ALTER TABLE dashboard_preferences ADD COLUMN forbidden_runtime_column integer",
                    "DROP TABLE dashboard_preferences",
                    "TRUNCATE TABLE dashboard_preferences",
                    "ALTER TABLE document_financial_contributions ADD COLUMN forbidden_runtime_column integer",
                    "DROP TABLE document_financial_contributions",
                    "TRUNCATE TABLE document_financial_contributions",
                    "ALTER TABLE projects ADD COLUMN forbidden_runtime_column integer",
                    "DROP TABLE projects",
                    "TRUNCATE TABLE projects",
                    "ALTER TABLE project_budget_versions ADD COLUMN forbidden_runtime_column integer",
                    "DROP TABLE project_budget_versions",
                    "TRUNCATE TABLE project_budget_versions",
                    "ALTER TABLE project_budget_lines ADD COLUMN forbidden_runtime_column integer",
                    "DROP TABLE project_budget_lines",
                    "TRUNCATE TABLE project_budget_lines",
                    "ALTER TABLE commercial_documents ADD COLUMN forbidden_runtime_column integer",
                    "DROP TABLE commercial_documents",
                    "TRUNCATE TABLE commercial_documents",
                    "ALTER TABLE commercial_document_lines ADD COLUMN forbidden_runtime_column integer",
                    "DROP TABLE commercial_document_lines",
                    "TRUNCATE TABLE commercial_document_lines",
                    "ALTER TABLE commercial_number_sequences ADD COLUMN forbidden_runtime_column integer",
                    "DROP TABLE commercial_number_sequences",
                    "TRUNCATE TABLE commercial_number_sequences",
                    "DELETE FROM commercial_number_sequences WHERE false",
                    "UPDATE document_financial_contributions SET confirmed_amount=1 WHERE false",
                    "DELETE FROM document_financial_contributions WHERE false",
                    "SELECT * FROM flyway_schema_history",
                    "UPDATE flyway_schema_history SET checksum=0 WHERE false",
                    "DELETE FROM flyway_schema_history WHERE false",
                    "CREATE ROLE forbidden_runtime_role",
                    "CREATE DATABASE forbidden_runtime_database"}) {
                SQLException denied = assertThrows(SQLException.class, () -> statement.execute(sql),
                        "Runtime role unexpectedly permitted: " + sql);
                assertEquals("42501", denied.getSQLState());
            }
        }
    }

    @Test
    void v30BusinessConfigurationAndV31DashboardPreferencesEnforceRuntimeAndBackupPrivileges() throws Exception {
        for (String table : new String[] {
                "public.business_configuration",
                "public.dashboard_preferences",
                "public.projects",
                "public.project_budget_versions",
                "public.project_budget_lines",
                "public.commercial_documents",
                "public.commercial_document_lines"
        }) {
            try (var migrator = connect("finsight_migrator"); var statement = migrator.createStatement()) {
                for (String allowed : new String[] {"SELECT", "INSERT", "UPDATE", "DELETE"}) {
                    try (var rs = statement.executeQuery("SELECT has_table_privilege('finsight_app', '" + table + "', '" + allowed + "')")) {
                        assertTrue(rs.next());
                        assertTrue(rs.getBoolean(1), "finsight_app must have " + allowed + " on " + table);
                    }
                }
                for (String forbidden : new String[] {"TRUNCATE", "REFERENCES", "TRIGGER"}) {
                    try (var rs = statement.executeQuery("SELECT has_table_privilege('finsight_app', '" + table + "', '" + forbidden + "')")) {
                        assertTrue(rs.next());
                        assertFalse(rs.getBoolean(1), "finsight_app must not have " + forbidden + " on " + table);
                    }
                }
                try (var rs = statement.executeQuery("SELECT has_table_privilege('finsight_backup', '" + table + "', 'SELECT')")) {
                    assertTrue(rs.next());
                    assertTrue(rs.getBoolean(1), "finsight_backup must have SELECT on " + table);
                }
                for (String forbidden : new String[] {"INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER"}) {
                    try (var rs = statement.executeQuery("SELECT has_table_privilege('finsight_backup', '" + table + "', '" + forbidden + "')")) {
                        assertTrue(rs.next());
                        assertFalse(rs.getBoolean(1), "finsight_backup must not have " + forbidden + " on " + table);
                    }
                }
            }
        }

        UUID userId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        try (var app = connect("finsight_app"); var statement = app.createStatement()) {
            assertEquals(1, statement.executeUpdate("INSERT INTO app_users (id, email, full_name, password_hash, account_status, must_change_password) "
                    + "VALUES ('" + userId + "', 'priv-" + userId + "@example.com', 'Privilege Check', 'hash', 'ACTIVE', false)"));
            assertEquals(1, statement.executeUpdate("INSERT INTO businesses (id) VALUES ('" + businessId + "')"));
            assertEquals(1, statement.executeUpdate("INSERT INTO business_configuration (business_id, operating_profile, operating_answers, requested_modules, version, configured, updated_by) "
                    + "VALUES ('" + businessId + "', 'SERVICES', '{}'::jsonb, '[\"core_finance\"]'::jsonb, 1, true, '" + userId + "')"));
            assertEquals(1, statement.executeUpdate("INSERT INTO dashboard_preferences (business_id, user_id, version, registry_version, overrides_json) "
                    + "VALUES ('" + businessId + "', '" + userId + "', 1, 'f13-v1', '[{\"key\":\"cash-flow\",\"hidden\":true,\"order\":3,\"size\":\"wide\"}]'::jsonb)"));
            assertEquals(1, statement.executeUpdate("UPDATE business_configuration SET operating_profile='PROJECT_ORDER', version=2 WHERE business_id='" + businessId + "'"));
            assertEquals(1, statement.executeUpdate("UPDATE dashboard_preferences SET version=2 WHERE business_id='" + businessId + "' AND user_id='" + userId + "'"));
        }

        try (var backup = connect("finsight_backup"); var statement = backup.createStatement()) {
            try (var rs = statement.executeQuery("SELECT operating_profile, version FROM business_configuration WHERE business_id='" + businessId + "'")) {
                assertTrue(rs.next());
                assertEquals("PROJECT_ORDER", rs.getString(1));
                assertEquals(2L, rs.getLong(2));
            }
            try (var rs = statement.executeQuery("SELECT registry_version, version FROM dashboard_preferences WHERE business_id='" + businessId + "' AND user_id='" + userId + "'")) {
                assertTrue(rs.next());
                assertEquals("f13-v1", rs.getString(1));
                assertEquals(2L, rs.getLong(2));
            }
            for (String sql : new String[] {
                    "INSERT INTO business_configuration (business_id, operating_profile, requested_modules) VALUES ('" + UUID.randomUUID() + "', 'SERVICES', '[\"core_finance\"]'::jsonb)",
                    "UPDATE business_configuration SET version=99 WHERE business_id='" + businessId + "'",
                    "DELETE FROM business_configuration WHERE business_id='" + businessId + "'",
                    "TRUNCATE TABLE business_configuration",
                    "ALTER TABLE business_configuration ADD COLUMN backup_forbidden integer",
                    "DROP TABLE business_configuration",
                    "INSERT INTO dashboard_preferences (business_id, user_id, registry_version, version) VALUES ('" + businessId + "', '" + userId + "', 'f13-v1', 1)",
                    "UPDATE dashboard_preferences SET version=99 WHERE business_id='" + businessId + "'",
                    "DELETE FROM dashboard_preferences WHERE business_id='" + businessId + "'",
                    "TRUNCATE TABLE dashboard_preferences",
                    "ALTER TABLE dashboard_preferences ADD COLUMN backup_forbidden integer",
                    "DROP TABLE dashboard_preferences"}) {
                assertEquals("42501", assertThrows(SQLException.class, () -> statement.execute(sql),
                        "Backup role unexpectedly permitted: " + sql).getSQLState());
            }
        }

        try (var app = connect("finsight_app"); var statement = app.createStatement()) {
            assertEquals(1, statement.executeUpdate("DELETE FROM dashboard_preferences WHERE business_id='" + businessId + "' AND user_id='" + userId + "'"));
            assertEquals(1, statement.executeUpdate("DELETE FROM business_configuration WHERE business_id='" + businessId + "'"));
            assertEquals(1, statement.executeUpdate("DELETE FROM businesses WHERE id='" + businessId + "'"));
            assertEquals(1, statement.executeUpdate("DELETE FROM app_users WHERE id='" + userId + "'"));
        }
    }

    @Test
    void migrationHistoryHasNoRuntimePrivilegesAndRemainsAvailableForBackup() throws Exception {
        try (var connection = connect("finsight_migrator");
             var statement = connection.createStatement()) {
            for (String privilege : new String[] {"SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER"}) {
                try (var result = statement.executeQuery("SELECT has_table_privilege('finsight_app', "
                        + "'public.flyway_schema_history', '" + privilege + "')")) {
                    assertTrue(result.next());
                    assertFalse(result.getBoolean(1), "Runtime must not have " + privilege + " on migration history");
                }
            }
        }
        try (var connection = connect("finsight_backup");
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success")) {
            assertTrue(result.next());
            assertTrue(result.getInt(1) >= 15, "Backup must retain migration history including the permission correction");
        }
    }

    @Test
    void migratedFutureObjectsGrantRuntimeDmlAndBackupReadOnlyAccess() throws Exception {
        String table = "role_fixture_" + UUID.randomUUID().toString().replace("-", "");
        try (var migrator = connect("finsight_migrator"); var migration = migrator.createStatement()) {
            migration.execute("CREATE TABLE " + table + " (id integer primary key, value text)");
            try {
                migration.execute("ALTER TABLE " + table + " ADD COLUMN marker integer");
                try (var app = connect("finsight_app"); var statement = app.createStatement()) {
                    assertEquals(1, statement.executeUpdate("INSERT INTO " + table + " VALUES (1, 'original', 1)"));
                    assertEquals(1, statement.executeUpdate("UPDATE " + table + " SET value='updated' WHERE id=1"));
                }
                try (var backup = connect("finsight_backup"); var statement = backup.createStatement()) {
                    try (var result = statement.executeQuery("SELECT value FROM " + table + " WHERE id=1")) {
                        assertTrue(result.next());
                        assertEquals("updated", result.getString(1));
                    }
                    for (String sql : new String[] {"INSERT INTO " + table + " VALUES (2, 'bad', 2)",
                            "UPDATE " + table + " SET value='bad'", "DELETE FROM " + table,
                            "CREATE TABLE forbidden_backup_table (id integer)"}) {
                        assertEquals("42501", assertThrows(SQLException.class, () -> statement.execute(sql)).getSQLState());
                    }
                }
                try (var app = connect("finsight_app"); var statement = app.createStatement()) {
                    assertEquals(1, statement.executeUpdate("DELETE FROM " + table + " WHERE id=1"));
                }
            } finally {
                migration.execute("DROP TABLE " + table);
            }
        }
    }

    @Test
    void migratorCannotCreateRolesOrDatabases() throws Exception {
        try (var connection = connect("finsight_migrator"); var statement = connection.createStatement()) {
            assertEquals("42501", assertThrows(SQLException.class,
                    () -> statement.execute("CREATE ROLE forbidden_migrator_role")).getSQLState());
            assertEquals("42501", assertThrows(SQLException.class,
                    () -> statement.execute("CREATE DATABASE forbidden_migrator_database")).getSQLState());
        }
    }
}
