package com.app.sme_health_backend.database;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

public class PostgreSqlTlsConnectionIT {

    private static final String APP_USER = "finsight_app";
    private static final String APP_PASSWORD = "FinSight_App_Runtime_2026_!*7vQ";

    private static String resolveCertPath(String relativePath) {
        URL res = PostgreSqlTlsConnectionIT.class.getClassLoader().getResource(relativePath);
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

    @Test
    @DisplayName("Valid TLS connection with verify-full succeeds and establishes TLS 1.3")
    void testValidTlsConnectionWithVerifyFull() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", APP_USER);
        props.setProperty("password", APP_PASSWORD);
        props.setProperty("ssl", "true");
        props.setProperty("sslmode", "verify-full");
        props.setProperty("sslrootcert", resolveCertPath("certs/postgres-ca.crt"));

        String url = "jdbc:postgresql://localhost:5432/sme_health";
        try (Connection conn = DriverManager.getConnection(url, props);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT ssl, version, cipher, bits FROM pg_stat_ssl WHERE pid = pg_backend_pid()")) {

            assertTrue(rs.next(), "pg_stat_ssl row should exist for current backend");
            boolean sslActive = rs.getBoolean("ssl");
            String tlsVersion = rs.getString("version");
            String cipher = rs.getString("cipher");
            int bits = rs.getInt("bits");

            assertTrue(sslActive, "SSL must be reported as active");
            assertNotNull(tlsVersion, "TLS version must not be null");
            assertTrue(tlsVersion.startsWith("TLSv1."), "Must be TLSv1.x: " + tlsVersion);
            assertTrue(bits >= 128, "Key length must be at least 128 bits: " + bits);
            assertNotNull(cipher, "Cipher must not be null");
        }
    }

    @Test
    @DisplayName("Invalid / Untrusted CA fails closed under sslmode=verify-full")
    void testUntrustedCaRejectedWithVerifyFull() {
        Properties props = new Properties();
        props.setProperty("user", APP_USER);
        props.setProperty("password", APP_PASSWORD);
        props.setProperty("ssl", "true");
        props.setProperty("sslmode", "verify-full");
        props.setProperty("sslrootcert", resolveCertPath("certs/test-fixtures/rogue-ca.crt"));

        String url = "jdbc:postgresql://localhost:5432/sme_health";
        SQLException ex = assertThrows(SQLException.class, () -> DriverManager.getConnection(url, props));

        String message = ex.getMessage().toLowerCase();
        assertTrue(message.contains("certificate") || message.contains("validator") || message.contains("pkix") || message.contains("handshake"),
                "Expected certificate validation failure, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("Hostname mismatch fails closed under sslmode=verify-full")
    void testHostnameMismatchRejectedWithVerifyFull() {
        Properties props = new Properties();
        props.setProperty("user", APP_USER);
        props.setProperty("password", APP_PASSWORD);
        props.setProperty("ssl", "true");
        props.setProperty("sslmode", "verify-full");
        props.setProperty("sslrootcert", resolveCertPath("certs/postgres-ca.crt"));

        // 127.0.0.2 is not in the SAN DNS/IP list
        String url = "jdbc:postgresql://127.0.0.2:5432/sme_health";
        SQLException ex = assertThrows(SQLException.class, () -> DriverManager.getConnection(url, props));

        String message = ex.getMessage().toLowerCase();
        assertTrue(message.contains("match") || message.contains("hostname") || message.contains("name"),
                "Expected hostname verification failure, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("Plaintext (non-SSL) connection is rejected by pg_hba.conf")
    void testPlaintextConnectionRejected() {
        Properties props = new Properties();
        props.setProperty("user", APP_USER);
        props.setProperty("password", APP_PASSWORD);
        props.setProperty("sslmode", "disable");

        String url = "jdbc:postgresql://localhost:5432/sme_health";
        SQLException ex = assertThrows(SQLException.class, () -> DriverManager.getConnection(url, props));

        String message = ex.getMessage().toLowerCase();
        assertTrue(message.contains("pg_hba.conf") || message.contains("reject") || message.contains("no pg_hba.conf entry"),
                "Expected pg_hba rejection for plaintext, got: " + ex.getMessage());
    }
}
