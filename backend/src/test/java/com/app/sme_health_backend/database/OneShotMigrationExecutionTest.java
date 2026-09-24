package com.app.sme_health_backend.database;

import com.app.sme_health_backend.database.migration.FlywayMigrationRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

public class OneShotMigrationExecutionTest {

    @Test
    @DisplayName("One-shot Flyway runner successfully validates/applies migrations and terminates with 0")
    void testOneShotMigrationSucceedsAndStartsNoWebServer() {
        int exitCode = FlywayMigrationRunner.runMigration(new String[]{});
        assertEquals(0, exitCode, "One-shot migration must return 0 on success");

        // Assert no HTTP port 8080 was opened / bound
        assertFalse(isPortBound(8080), "No web server or HTTP port must bind during migration process");
    }

    @Test
    @DisplayName("Production migration fails closed when migrator_db_password secret is missing")
    void testProductionMigrationFailsClosedWithoutSecret() {
        String originalProfile = System.getProperty("spring.profiles.active");
        try {
            System.setProperty("spring.profiles.active", "prod,migrate");
            int exitCode = FlywayMigrationRunner.runMigration(new String[]{"--spring.profiles.active=prod,migrate"});
            assertEquals(1, exitCode, "Production migration without migrator_db_password must exit non-zero (fail closed)");
        } finally {
            if (originalProfile != null) {
                System.setProperty("spring.profiles.active", originalProfile);
            } else {
                System.clearProperty("spring.profiles.active");
            }
        }
    }

    private boolean isPortBound(int port) {
        try (Socket socket = new Socket("localhost", port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
