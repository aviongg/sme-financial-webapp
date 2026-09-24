package com.app.sme_health_backend.database;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DatabaseRolePrivilegesPostgreSqlIT {

    private static final String APP_USER = "finsight_app";
    private static final String APP_PASSWORD = "FinSight_App_Runtime_2026_!*7vQ";

    private static final String MIGRATOR_USER = "finsight_migrator";
    private static final String MIGRATOR_PASSWORD = "FinSight_Migrator_Ddl_2026_!$4mP";

    private static final String DBA_USER = "finsight_dba";
    private static final String DBA_PASSWORD = "FinSight_Dba_Admin_Sec_2026_!#9xK";

    private static final String JDBC_URL_BASE = "jdbc:postgresql://localhost:5432/sme_health";

    @Autowired
    private Environment environment;

    private static String resolveCertPath(String relativePath) {
        URL res = DatabaseRolePrivilegesPostgreSqlIT.class.getClassLoader().getResource(relativePath);
        if (res != null) {
            try {
                return Paths.get(res.toURI()).toFile().getAbsolutePath();
            } catch (Exception ignored) {}
        }
        File f = new File(relativePath);
        if (f.exists()) {
            return f.getAbsolutePath();
        }
        File parentFile = new File("..", relativePath);
        if (parentFile.exists()) {
            return parentFile.getAbsolutePath();
        }
        return relativePath;
    }

    private Connection createConnection(String user, String password) throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);
        props.setProperty("ssl", "true");
        props.setProperty("sslmode", "verify-full");
        props.setProperty("sslrootcert", resolveCertPath("certs/postgres-ca.crt"));
        return DriverManager.getConnection(JDBC_URL_BASE, props);
    }

    @Test
    @DisplayName("DBA absent from runtime configuration and environment")
    void testFinsightDbaAbsentFromRuntime() {
        String dsUser = environment.getProperty("spring.datasource.username");
        assertNotEquals(DBA_USER, dsUser, "Runtime datasource must NOT use finsight_dba");

        String flywayUser = environment.getProperty("spring.flyway.user");
        assertNotEquals(DBA_USER, flywayUser, "Flyway configuration must NOT use finsight_dba");

        assertFalse(environment.containsProperty("dba_db_password"), "Runtime must not contain dba_db_password");
        assertFalse(environment.containsProperty("DBA_DB_PASSWORD"), "Runtime must not contain DBA_DB_PASSWORD");
    }

    @Test
    @DisplayName("DBA recovery login works locally via container socket and possesses superuser privileges")
    void testFinsightDbaRecoveryLoginAndSuperuser() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "exec",
                "-e", "PGPASSWORD=" + DBA_PASSWORD,
                "sme-health-postgres",
                "psql", "-U", DBA_USER, "-d", "sme_health", "-t", "-A", "-c",
                "SELECT current_user, rolsuper, rolcreaterole, rolcreatedb FROM pg_roles WHERE rolname = current_user;"
        );
        Process process = pb.start();
        int exitCode = process.waitFor();
        assertEquals(0, exitCode, "DBA local recovery login must exit 0");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String output = reader.readLine();
            assertNotNull(output, "DBA recovery output must not be null");
            String[] parts = output.trim().split("\\|");
            assertEquals("finsight_dba", parts[0], "Current user must be finsight_dba");
            assertEquals("t", parts[1], "rolsuper must be true");
            assertEquals("t", parts[2], "rolcreaterole must be true");
            assertEquals("t", parts[3], "rolcreatedb must be true");
        }

        // Verify DBA is NOT accessible over TCP network (rejected by pg_hba.conf)
        assertThrows(SQLException.class, () -> createConnection(DBA_USER, DBA_PASSWORD),
                "finsight_dba must be rejected from TCP network connections");
    }

    @Test
    @DisplayName("finsight_app DML allowed on domain tables")
    void testFinsightAppDmlAllowed() throws SQLException {
        try (Connection conn = createConnection(APP_USER, APP_PASSWORD);
             Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM app_users")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 0);
            }
        }
    }

    @Test
    @DisplayName("finsight_app CREATE TABLE denied")
    void testFinsightAppCreateTableDenied() throws SQLException {
        try (Connection conn = createConnection(APP_USER, APP_PASSWORD);
             Statement stmt = conn.createStatement()) {
            SQLException ex = assertThrows(SQLException.class,
                    () -> stmt.executeUpdate("CREATE TABLE app_forbidden (id INT)"));
            assertTrue(ex.getMessage().toLowerCase().contains("permission denied"),
                    "Expected permission denied, got: " + ex.getMessage());
        }
    }

    @Test
    @DisplayName("finsight_app ALTER TABLE denied")
    void testFinsightAppAlterTableDenied() throws SQLException {
        try (Connection conn = createConnection(APP_USER, APP_PASSWORD);
             Statement stmt = conn.createStatement()) {
            SQLException ex = assertThrows(SQLException.class,
                    () -> stmt.executeUpdate("ALTER TABLE app_users ADD COLUMN forbidden_col INT"));
            assertTrue(ex.getMessage().toLowerCase().contains("must be owner") ||
                            ex.getMessage().toLowerCase().contains("permission denied"),
                    "Expected owner or permission denied, got: " + ex.getMessage());
        }
    }

    @Test
    @DisplayName("finsight_app DROP TABLE denied")
    void testFinsightAppDropTableDenied() throws SQLException {
        try (Connection conn = createConnection(APP_USER, APP_PASSWORD);
             Statement stmt = conn.createStatement()) {
            SQLException ex = assertThrows(SQLException.class,
                    () -> stmt.executeUpdate("DROP TABLE app_users"));
            assertTrue(ex.getMessage().toLowerCase().contains("must be owner") ||
                            ex.getMessage().toLowerCase().contains("permission denied"),
                    "Expected owner or permission denied, got: " + ex.getMessage());
        }
    }

    @Test
    @DisplayName("finsight_app TRUNCATE TABLE denied")
    void testFinsightAppTruncateTableDenied() throws SQLException {
        try (Connection conn = createConnection(APP_USER, APP_PASSWORD);
             Statement stmt = conn.createStatement()) {
            SQLException ex = assertThrows(SQLException.class,
                    () -> stmt.executeUpdate("TRUNCATE TABLE app_users"));
            assertTrue(ex.getMessage().toLowerCase().contains("must be owner") ||
                            ex.getMessage().toLowerCase().contains("permission denied"),
                    "Expected owner or permission denied, got: " + ex.getMessage());
        }
    }

    @Test
    @DisplayName("finsight_app CREATE ROLE denied")
    void testFinsightAppCreateRoleDenied() throws SQLException {
        try (Connection conn = createConnection(APP_USER, APP_PASSWORD);
             Statement stmt = conn.createStatement()) {
            SQLException ex = assertThrows(SQLException.class,
                    () -> stmt.executeUpdate("CREATE ROLE malicious_role WITH LOGIN"));
            assertTrue(ex.getMessage().toLowerCase().contains("permission denied"),
                    "Expected permission denied, got: " + ex.getMessage());
        }
    }

    @Test
    @DisplayName("finsight_app CREATE DATABASE denied")
    void testFinsightAppCreateDatabaseDenied() throws SQLException {
        try (Connection conn = createConnection(APP_USER, APP_PASSWORD);
             Statement stmt = conn.createStatement()) {
            SQLException ex = assertThrows(SQLException.class,
                    () -> stmt.executeUpdate("CREATE DATABASE malicious_db"));
            assertTrue(ex.getMessage().toLowerCase().contains("permission denied"),
                    "Expected permission denied, got: " + ex.getMessage());
        }
    }

    @Test
    @DisplayName("finsight_app flyway_schema_history access denied")
    void testFinsightAppFlywayHistoryAccessDenied() throws SQLException {
        try (Connection conn = createConnection(APP_USER, APP_PASSWORD);
             Statement stmt = conn.createStatement()) {
            SQLException ex = assertThrows(SQLException.class,
                    () -> stmt.executeQuery("SELECT * FROM flyway_schema_history"));
            assertTrue(ex.getMessage().toLowerCase().contains("permission denied"),
                    "Expected permission denied, got: " + ex.getMessage());
        }
    }

    @Test
    @DisplayName("finsight_migrator schema CREATE allowed and migration DDL allowed")
    void testFinsightMigratorSchemaCreateAllowed() throws SQLException {
        try (Connection conn = createConnection(MIGRATOR_USER, MIGRATOR_PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE migrator_test_table (id INT PRIMARY KEY, name VARCHAR(100))");
            try {
                stmt.executeUpdate("ALTER TABLE migrator_test_table ADD COLUMN description TEXT");
                stmt.executeUpdate("INSERT INTO migrator_test_table (id, name) VALUES (1, 'Initial')");
            } finally {
                stmt.executeUpdate("DROP TABLE migrator_test_table");
            }
        }
    }

    @Test
    @DisplayName("finsight_migrator CREATE ROLE denied")
    void testFinsightMigratorCreateRoleDenied() throws SQLException {
        try (Connection conn = createConnection(MIGRATOR_USER, MIGRATOR_PASSWORD);
             Statement stmt = conn.createStatement()) {
            SQLException ex = assertThrows(SQLException.class,
                    () -> stmt.executeUpdate("CREATE ROLE mig_rogue_role"));
            assertTrue(ex.getMessage().toLowerCase().contains("permission denied"),
                    "Expected permission denied, got: " + ex.getMessage());
        }
    }

    @Test
    @DisplayName("finsight_migrator CREATE DATABASE denied")
    void testFinsightMigratorCreateDatabaseDenied() throws SQLException {
        try (Connection conn = createConnection(MIGRATOR_USER, MIGRATOR_PASSWORD);
             Statement stmt = conn.createStatement()) {
            SQLException ex = assertThrows(SQLException.class,
                    () -> stmt.executeUpdate("CREATE DATABASE mig_rogue_db"));
            assertTrue(ex.getMessage().toLowerCase().contains("permission denied"),
                    "Expected permission denied, got: " + ex.getMessage());
        }
    }

    @Test
    @DisplayName("Default privileges on future objects grant runtime DML automatically")
    void testDefaultPrivilegesOnFutureObjects() throws SQLException {
        try (Connection migConn = createConnection(MIGRATOR_USER, MIGRATOR_PASSWORD);
             Statement migStmt = migConn.createStatement()) {
            migStmt.executeUpdate("CREATE TABLE future_mig_table (id INT PRIMARY KEY, val VARCHAR(50))");
            try {
                try (Connection appConn = createConnection(APP_USER, APP_PASSWORD);
                     Statement appStmt = appConn.createStatement()) {
                    appStmt.executeUpdate("INSERT INTO future_mig_table (id, val) VALUES (10, 'Hello S6')");
                    try (ResultSet rs = appStmt.executeQuery("SELECT val FROM future_mig_table WHERE id = 10")) {
                        assertTrue(rs.next());
                        assertEquals("Hello S6", rs.getString(1));
                    }
                    appStmt.executeUpdate("UPDATE future_mig_table SET val = 'Updated S6' WHERE id = 10");
                    appStmt.executeUpdate("DELETE FROM future_mig_table WHERE id = 10");
                }
            } finally {
                migStmt.executeUpdate("DROP TABLE future_mig_table");
            }
        }
    }

    @Test
    @DisplayName("SCRAM-SHA-256 credentials verified on all three roles")
    void testScramSha256Credentials() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "exec",
                "-e", "PGPASSWORD=" + DBA_PASSWORD,
                "sme-health-postgres",
                "psql", "-U", DBA_USER, "-d", "sme_health", "-t", "-A", "-c",
                "SELECT rolname, rolpassword LIKE 'SCRAM-SHA-256$%' AS is_scram FROM pg_authid WHERE rolname IN ('finsight_dba', 'finsight_migrator', 'finsight_app') ORDER BY rolname;"
        );
        Process process = pb.start();
        int exitCode = process.waitFor();
        assertEquals(0, exitCode);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line1 = reader.readLine();
            String line2 = reader.readLine();
            String line3 = reader.readLine();

            assertNotNull(line1);
            assertNotNull(line2);
            assertNotNull(line3);

            assertTrue(line1.contains("finsight_app|t"), "finsight_app must be SCRAM-SHA-256: " + line1);
            assertTrue(line2.contains("finsight_dba|t"), "finsight_dba must be SCRAM-SHA-256: " + line2);
            assertTrue(line3.contains("finsight_migrator|t"), "finsight_migrator must be SCRAM-SHA-256: " + line3);
        }
    }
}
