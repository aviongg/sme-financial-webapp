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
