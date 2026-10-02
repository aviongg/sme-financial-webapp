package com.app.sme_health_backend.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Explicit opt-in target for the unchanged Docker/TLS acceptance checks. Never defaults to a developer DB. */
public abstract class ProductionPostgresTarget {
    protected static String setting(String name) {
        String value = System.getProperty(name, System.getenv(name));
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Production PostgreSQL acceptance requires " + name
                    + "; provide a disposable production stack, its secrets and certificates.");
        }
        return value;
    }

    @DynamicPropertySource
    static void configureExplicitTarget(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> setting("FINSIGHT_TEST_DB_URL"));
        properties.add("spring.datasource.username", () -> "finsight_app");
        properties.add("spring.datasource.password", () -> setting("FINSIGHT_TEST_APP_PASSWORD"));
        properties.add("spring.datasource.hikari.data-source-properties.sslmode", () -> "verify-full");
        properties.add("spring.datasource.hikari.data-source-properties.sslrootcert", () -> setting("FINSIGHT_TEST_DB_CA"));
        properties.add("spring.flyway.enabled", () -> "false");
    }
}
