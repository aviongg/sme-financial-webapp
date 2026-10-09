package com.app.sme_health_backend.platform.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class PlatformAdminOperatorServiceTest {
    @TempDir Path temporaryDirectory;

    private Path defaultSecret() {
        return temporaryDirectory.resolve("mounted-secret");
    }

    @Test
    void missingPasswordFailsBeforeAnOperatorConnectionCanBeOpened() {
        var failure = assertThrows(IllegalStateException.class, () ->
                PlatformAdminOperatorService.resolveOperatorPassword(defaultSecret(), new Properties(), Map.of()));
        assertTrue(failure.getMessage().contains("migrator_db_password"));
        assertTrue(failure.getMessage().contains("MIGRATOR_DB_PASSWORD"));
        assertTrue(failure.getMessage().contains("No default"));
    }

    @Test
    void blankPasswordSourcesFailClosed() throws Exception {
        Files.writeString(defaultSecret(), " \n\t");
        Properties properties = new Properties();
        properties.setProperty("migrator_db_password", " ");
        properties.setProperty("spring.flyway.password", "\t");
        assertThrows(IllegalStateException.class, () -> PlatformAdminOperatorService.resolveOperatorPassword(
                defaultSecret(), properties, Map.of("MIGRATOR_DB_PASSWORD", "\n", "POSTGRES_MIGRATOR_PASSWORD", " ")));
    }

    @Test
    void mountedSecretTakesPrecedenceOverOtherExplicitSources() throws Exception {
        Files.writeString(defaultSecret(), "mounted-fixture-value\n");
        Properties properties = new Properties();
        properties.setProperty("spring.flyway.password", "property-fixture-value");
        assertEquals("mounted-fixture-value", PlatformAdminOperatorService.resolveOperatorPassword(
                defaultSecret(), properties, Map.of("MIGRATOR_DB_PASSWORD", "environment-fixture-value")));
    }

    @Test
    void customSecretDirectoryIsSupported() throws Exception {
        Files.writeString(temporaryDirectory.resolve("migrator_db_password"), "custom-fixture-value\n");
        Properties properties = new Properties();
        properties.setProperty("finsight.secrets.dir", temporaryDirectory.toString());
        assertEquals("custom-fixture-value", PlatformAdminOperatorService.resolveOperatorPassword(
                defaultSecret(), properties, Map.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"migrator_db_password", "spring.flyway.password"})
    void explicitSystemPropertyIsSupported(String property) {
        Properties properties = new Properties();
        properties.setProperty(property, "explicit-fixture-value");
        assertEquals("explicit-fixture-value", PlatformAdminOperatorService.resolveOperatorPassword(
                defaultSecret(), properties, Map.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MIGRATOR_DB_PASSWORD", "POSTGRES_MIGRATOR_PASSWORD", "migrator_db_password"})
    void explicitEnvironmentCredentialIsSupported(String variable) {
        assertEquals("environment-fixture-value", PlatformAdminOperatorService.resolveOperatorPassword(
                defaultSecret(), new Properties(), Map.of(variable, "environment-fixture-value")));
    }
}
