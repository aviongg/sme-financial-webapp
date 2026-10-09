package com.app.sme_health_backend.testsupport;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

/** Real, disposable PostgreSQL with separate migration/runtime roles; no developer DB access. */
public abstract class DisposablePostgres {
    public static final String PASSWORD = "fixture-" + UUID.randomUUID();
    public static final EmbeddedPostgres POSTGRES;
    public static final String URL;
    public static final Path DOCUMENTS;
    static {
        try {
            POSTGRES = EmbeddedPostgres.builder().setPort(0).start();
            // Do not retain EmbeddedPostgres's ?user=postgres parameter: JDBC URL
            // credentials override datasource credentials and would bypass role tests.
            URL = "jdbc:postgresql://localhost:" + POSTGRES.getPort() + "/postgres";
            DOCUMENTS = Files.createTempDirectory("finsight-closure-documents-");
            try (Connection connection = POSTGRES.getPostgresDatabase().getConnection();
                 Statement sql = connection.createStatement()) {
                for (String role : new String[] {"finsight_migrator", "finsight_app", "finsight_backup"}) {
                    sql.execute("CREATE ROLE " + role + " LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS PASSWORD '" + PASSWORD + "'");
                }
                sql.execute("ALTER SCHEMA public OWNER TO finsight_migrator");
                sql.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
                sql.execute("GRANT USAGE ON SCHEMA public TO finsight_app, finsight_backup");
                sql.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public");
                sql.execute("ALTER DEFAULT PRIVILEGES FOR ROLE finsight_migrator IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO finsight_app");
                sql.execute("ALTER DEFAULT PRIVILEGES FOR ROLE finsight_migrator IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO finsight_app");
                sql.execute("ALTER DEFAULT PRIVILEGES FOR ROLE finsight_migrator IN SCHEMA public GRANT SELECT ON TABLES TO finsight_backup");
            }
            Flyway.configure().dataSource(URL, "finsight_migrator", PASSWORD)
                    .locations("classpath:db/migration").cleanDisabled(true).load().migrate();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> "finsight_app");
        registry.add("spring.datasource.password", () -> PASSWORD);
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("app.documents.storage-dir", () -> DOCUMENTS.toString());
        registry.add("app.whatsapp.scheduler.enabled", () -> "false");
    }
}
