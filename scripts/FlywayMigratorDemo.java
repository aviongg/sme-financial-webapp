import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Applies the repository's Flyway migrations to the local synthetic demo database only (127.0.0.1:5433/finsight_demo,
 * docker-compose.demo.yml). Run from anywhere inside the checkout, e.g. from the repository root:
 * {@code java -cp "<backend classpath>;scripts" FlywayMigratorDemo}.
 *
 * <p>The migration location is resolved portably: {@code FINSIGHT_MIGRATIONS_DIR} (or {@code -Dfinsight.migrations.dir})
 * when set, otherwise {@code backend/src/main/resources/db/migration} found by walking up from the working directory.
 * Before connecting, the directory must hold exactly the contiguous versioned migrations V1..V{@value #EXPECTED_VERSION}
 * and nothing Flyway would also pick up, so a wrong or partial checkout is refused instead of half-migrating.
 * {@code clean} stays disabled and the run must end at the expected schema version.
 */
public class FlywayMigratorDemo {
    static final String DEMO_URL = "jdbc:postgresql://127.0.0.1:5433/finsight_demo";
    static final String DEMO_MIGRATOR_USER = "finsight_migrator";
    static final String DEMO_MIGRATOR_PASSWORD = "DemoDbPassword2026!";
    static final int EXPECTED_VERSION = 52;
    static final String MIGRATIONS_DIR_ENV = "FINSIGHT_MIGRATIONS_DIR";
    static final String MIGRATIONS_DIR_PROPERTY = "finsight.migrations.dir";
    static final Path REPOSITORY_MIGRATIONS = Path.of("backend", "src", "main", "resources", "db", "migration");

    private static final Pattern VERSIONED = Pattern.compile("V([1-9][0-9]*)__[A-Za-z0-9_]+\\.sql");

    public static void main(String[] args) throws Exception {
        String override = System.getProperty(MIGRATIONS_DIR_PROPERTY, System.getenv(MIGRATIONS_DIR_ENV));
        Path migrations = resolveMigrationDirectory(override, Path.of("").toAbsolutePath());
        validateMigrationDirectory(migrations, EXPECTED_VERSION);
        String location = flywayLocation(migrations);

        System.out.println("Running Flyway migrations V1-V" + EXPECTED_VERSION + " on: " + DEMO_URL);
        System.out.println("Migration location: " + location);
        Flyway flyway = Flyway.configure()
                .dataSource(DEMO_URL, DEMO_MIGRATOR_USER, DEMO_MIGRATOR_PASSWORD)
                .locations(location)
                .cleanDisabled(true)
                .load();
        MigrateResult result = flyway.migrate();
        System.out.println("Migrations applied: " + result.migrationsExecuted);
        System.out.println("Current Flyway target version: " + result.targetSchemaVersion);
        if (!String.valueOf(EXPECTED_VERSION).equals(result.targetSchemaVersion)) {
            throw new IllegalStateException("Expected target version " + EXPECTED_VERSION + ", but got " + result.targetSchemaVersion);
        }
        System.out.println("Flyway migration V1-V" + EXPECTED_VERSION + " completed successfully!");
    }

    /** The explicit override when given, else the repository migration directory nearest above {@code start}. */
    static Path resolveMigrationDirectory(String override, Path start) {
        if (override != null && !override.isBlank()) {
            Path dir = Path.of(override.trim()).toAbsolutePath().normalize();
            if (!Files.isDirectory(dir)) {
                throw new IllegalStateException(MIGRATIONS_DIR_ENV + " is not a directory: " + dir);
            }
            return dir;
        }
        for (Path base = start.toAbsolutePath().normalize(); base != null; base = base.getParent()) {
            Path candidate = base.resolve(REPOSITORY_MIGRATIONS);
            if (Files.isDirectory(candidate)) return candidate;
        }
        throw new IllegalStateException("Could not find " + REPOSITORY_MIGRATIONS + " above " + start
                + ". Run from inside the repository checkout or set " + MIGRATIONS_DIR_ENV + ".");
    }

    /** Exactly V1..V{expected}, each once, and no other SQL that Flyway would load from the same location. */
    static void validateMigrationDirectory(Path dir, int expected) throws IOException {
        if (!Files.isDirectory(dir)) throw new IllegalStateException("Migration directory not found: " + dir);
        TreeSet<Integer> versions = new TreeSet<>();
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.sorted().toList()) {
                String name = file.getFileName().toString();
                if (Files.isDirectory(file)) {
                    problems.add("unexpected subdirectory " + name);
                    continue;
                }
                if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".sql")) continue;
                Matcher m = VERSIONED.matcher(name);
                if (!m.matches()) {
                    problems.add("unexpected migration file " + name);
                    continue;
                }
                int version = Integer.parseInt(m.group(1));
                if (!versions.add(version)) problems.add("duplicate version V" + version);
                if (version > expected) problems.add("unexpected version " + name + " (expected at most V" + expected + ")");
            }
        }
        for (int v = 1; v <= expected; v++) {
            if (!versions.contains(v)) problems.add("missing V" + v);
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to migrate from " + dir + ": " + String.join("; ", problems));
        }
    }

    static String flywayLocation(Path dir) {
        return "filesystem:" + dir.toAbsolutePath().normalize().toString().replace('\\', '/');
    }
}
