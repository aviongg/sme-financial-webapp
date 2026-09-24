package com.app.sme_health_backend.database.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Dedicated one-shot Flyway migration runner.
 *
 * Requirements (Phase S6):
 * - No web server starts and no HTTP port binds.
 * - No normal runtime Hikari datasource initializes with finsight_app.
 * - No app_db_password is required.
 * - migrator_db_password is required (from config tree in production).
 * - dba_db_password is never accessible or requested.
 * - Flyway validates and applies migrations with cleanDisabled=true, validateOnMigrate=true, baselineOnMigrate=false.
 * - Exits 0 on success, non-zero on migration failure.
 */
public class FlywayMigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(FlywayMigrationRunner.class);

    private static final String DEFAULT_DEV_MIGRATOR_PASSWORD = "FinSight_Migrator_Ddl_2026_!$4mP";

    public static void main(String[] args) {
        int exitCode = runMigration(args);
        System.exit(exitCode);
    }

    /**
     * Executes the one-shot migration.
     * Returns 0 on success, non-zero on failure.
     */
    public static int runMigration(String[] args) {
        log.info("FinSight One-Shot Migration Runner starting...");

        boolean isProd = isProductionProfile(args);

        String jdbcUrl = resolveJdbcUrl(isProd);
        String user = resolveMigratorUser();
        String password = resolveMigratorPassword(isProd);

        if (password == null || password.trim().isEmpty()) {
            log.error("FATAL: Required migration secret 'migrator_db_password' is missing or empty. Migration failing closed.");
            return 1;
        }

        log.info("Executing Flyway migration with user: '{}' against: '{}'", user, maskUrl(jdbcUrl));

        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(jdbcUrl, user, password)
                    .cleanDisabled(true)
                    .validateOnMigrate(true)
                    .baselineOnMigrate(false)
                    .locations("classpath:db/migration")
                    .load();

            log.info("Validating migration checksums...");
            flyway.validate();

            log.info("Applying pending migrations...");
            MigrateResult result = flyway.migrate();

            log.info("Migration successful: schema='{}', initialVersion='{}', targetVersion='{}', migrationsExecuted={}",
                    result.schemaName, result.initialSchemaVersion, result.targetSchemaVersion, result.migrationsExecuted);

            return 0;
        } catch (Exception e) {
            log.error("FATAL: Flyway migration failed: {}", e.getMessage(), e);
            return 1;
        }
    }

    public static boolean isProductionProfile(String[] args) {
        String activeProfiles = System.getProperty("spring.profiles.active", "");
        String envProfiles = System.getenv("SPRING_PROFILES_ACTIVE");
        if (envProfiles != null) {
            activeProfiles = activeProfiles + "," + envProfiles;
        }
        if (args != null) {
            for (String arg : args) {
                if (arg.contains("spring.profiles.active")) {
                    activeProfiles = activeProfiles + "," + arg;
                }
            }
        }
        return Arrays.stream(activeProfiles.split("[,=]"))
                .map(String::trim)
                .anyMatch(p -> p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production"));
    }

    private static String resolveJdbcUrl(boolean isProd) {
        String sysProp = System.getProperty("spring.flyway.url");
        if (sysProp != null && !sysProp.trim().isEmpty()) {
            return sysProp;
        }
        String envUrl = System.getenv("FLYWAY_JDBC_URL");
        if (envUrl != null && !envUrl.trim().isEmpty()) {
            return envUrl;
        }
        envUrl = System.getenv("SPRING_DATASOURCE_URL");
        if (envUrl != null && !envUrl.trim().isEmpty()) {
            return envUrl;
        }

        String host = System.getProperty("DB_HOST", System.getenv("DB_HOST"));
        String port = System.getProperty("DB_PORT", System.getenv("DB_PORT"));
        String db = System.getProperty("DB_NAME", System.getenv("DB_NAME"));

        if (host == null || host.trim().isEmpty()) {
            host = isProd ? "postgres.finsight.internal" : "localhost";
        }
        if (port == null || port.trim().isEmpty()) {
            port = "5432";
        }
        if (db == null || db.trim().isEmpty()) {
            db = "sme_health";
        }

        if (isProd) {
            String certPath = System.getProperty("DB_SSL_ROOT_CERT", System.getenv("DB_SSL_ROOT_CERT"));
            if (certPath == null || certPath.trim().isEmpty()) {
                certPath = "/etc/finsight/certs/postgres-ca.crt";
            }
            return String.format("jdbc:postgresql://%s:%s/%s?sslmode=verify-full&sslrootcert=%s", host, port, db, certPath);
        } else {
            String sslMode = System.getProperty("POSTGRES_SSL_MODE", System.getenv("POSTGRES_SSL_MODE"));
            if (sslMode == null) {
                sslMode = "prefer";
            }
            String certPath = System.getProperty("POSTGRES_SSL_ROOT_CERT", System.getenv("POSTGRES_SSL_ROOT_CERT"));
            if (certPath == null) {
                certPath = "certs/postgres-ca.crt";
            }
            File certFile = new File(certPath);
            if (certFile.exists()) {
                return String.format("jdbc:postgresql://%s:%s/%s?sslmode=%s&sslrootcert=%s", host, port, db, sslMode, certPath);
            }
            return String.format("jdbc:postgresql://%s:%s/%s", host, port, db);
        }
    }

    private static String resolveMigratorUser() {
        String sysProp = System.getProperty("spring.flyway.user");
        if (sysProp != null && !sysProp.trim().isEmpty()) {
            return sysProp;
        }
        String envUser = System.getenv("POSTGRES_MIGRATOR_USER");
        if (envUser != null && !envUser.trim().isEmpty()) {
            return envUser;
        }
        return "finsight_migrator";
    }

    private static String resolveMigratorPassword(boolean isProd) {
        // 1. Check configtree secret /run/secrets/migrator_db_password
        Path secretPath = Paths.get("/run/secrets/migrator_db_password");
        if (Files.exists(secretPath)) {
            try {
                String content = Files.readString(secretPath, StandardCharsets.UTF_8).trim();
                if (!content.isEmpty()) {
                    return content;
                }
            } catch (IOException e) {
                log.warn("Could not read secret from {}: {}", secretPath, e.getMessage());
            }
        }

        // 2. Check custom secrets dir if configured via system property
        String secretsDir = System.getProperty("finsight.secrets.dir");
        if (secretsDir != null) {
            Path customSecret = Paths.get(secretsDir, "migrator_db_password");
            if (Files.exists(customSecret)) {
                try {
                    String content = Files.readString(customSecret, StandardCharsets.UTF_8).trim();
                    if (!content.isEmpty()) {
                        return content;
                    }
                } catch (IOException ignored) {
                }
            }
        }

        // 3. Check system property
        String sysProp = System.getProperty("migrator_db_password");
        if (sysProp != null && !sysProp.trim().isEmpty()) {
            return sysProp;
        }
        sysProp = System.getProperty("spring.flyway.password");
        if (sysProp != null && !sysProp.trim().isEmpty()) {
            return sysProp;
        }

        // 4. Check environment variable
        String envPass = System.getenv("MIGRATOR_DB_PASSWORD");
        if (envPass != null && !envPass.trim().isEmpty()) {
            return envPass;
        }
        envPass = System.getenv("migrator_db_password");
        if (envPass != null && !envPass.trim().isEmpty()) {
            return envPass;
        }

        // In production, failure to find the secret must fail closed
        if (isProd) {
            return null;
        }

        // 5. Development convenience fallback
        return DEFAULT_DEV_MIGRATOR_PASSWORD;
    }

    private static String maskUrl(String url) {
        if (url == null) return "null";
        return url.replaceAll("password=[^&;]*", "password=***");
    }
}
