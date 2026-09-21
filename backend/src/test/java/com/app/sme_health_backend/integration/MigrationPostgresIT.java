package com.app.sme_health_backend.integration;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises real Flyway histories and the explicit legacy mapping against PostgreSQL. */
class MigrationPostgresIT {

    private static EmbeddedPostgres postgres;
    @TempDir Path temporary;

    @BeforeAll
    static void startDatabase() throws IOException {
        postgres = EmbeddedPostgres.builder().setPort(0).start();
    }

    @AfterAll
    static void stopDatabase() throws IOException {
        if (postgres != null) postgres.close();
    }

    @Test
    void freshDatabaseAppliesAllCanonicalMigrationsAndValidates() {
        Database database = database();
        Flyway flyway = canonical(database, false);

        assertEquals(6, flyway.migrate().migrationsExecuted);
        assertDoesNotThrow(flyway::validate);
        assertEquals(List.of("1", "2", "3", "4", "5", "6"), versions(database.jdbc()));
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertCanonicalSchema(database);
    }

    @ParameterizedTest(name = "legacy insights V{0}, recommendations V{1}")
    @CsvSource({"2, 3", "3, 4"})
    void supportedLegacyHistoriesUpgradeWithoutLosingBusinessRowsOrNewestAdvice(
            int insightVersion, int recommendationVersion) throws Exception {
        Database database = legacyDatabase(insightVersion, recommendationVersion);
        Seed seed = seedLegacyRows(database.jdbc());

        // Ordinary migration must surface the version collision rather than silently repair it.
        assertThrows(RuntimeException.class, () -> canonical(database, false).validate());
        reconcile(database);

        assertEquals(List.of("1", "4", "5"), versions(database.jdbc()));
        assertEquals("V4__create_insights_table.sql", database.jdbc().queryForObject(
                "select script from flyway_schema_history where version='4'", String.class));
        assertEquals("V5__create_recommendations_table.sql", database.jdbc().queryForObject(
                "select script from flyway_schema_history where version='5'", String.class));

        Flyway upgrade = canonical(database, true);
        assertEquals(3, upgrade.migrate().migrationsExecuted);
        assertDoesNotThrow(upgrade::validate);
        assertDoesNotThrow(() -> canonical(database, false).validate());
        assertEquals(0, canonical(database, false).migrate().migrationsExecuted);
        assertCanonicalSchema(database);
        assertLegacyDataPreserved(database.jdbc(), seed);
        assertUniqueAdvice(database.jdbc(), seed.userId());
    }

    @Test
    void canonicalV5UpgradeDeduplicatesTiedTimestampsDeterministically() {
        Database database = database();
        Flyway.configure().dataSource(database.dataSource()).schemas(database.schema())
                .defaultSchema(database.schema()).locations("classpath:db/migration").target("5").load().migrate();
        Seed seed = seedLegacyRows(database.jdbc());
        UUID smaller = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID larger = UUID.fromString("00000000-0000-0000-0000-000000000002");
        for (String table : List.of("insights", "recommendations")) {
            String textColumn = table.equals("insights") ? "insight_text" : "recommendation_text";
            database.jdbc().update("insert into " + table + "(id,user_id,month," + textColumn
                            + ",category,priority,created_at) values (?,?,'2026-08','Tied older ID','trend','low','2026-08-31 12:00:00')",
                    smaller, seed.userId());
            database.jdbc().update("insert into " + table + "(id,user_id,month," + textColumn
                            + ",category,priority,created_at) values (?,?,'2026-08','Tied newer ID','trend','low','2026-08-31 12:00:00')",
                    larger, seed.userId());
        }

        assertEquals(1, canonical(database, false).migrate().migrationsExecuted);
        for (String table : List.of("insights", "recommendations")) {
            assertEquals(larger, database.jdbc().queryForObject(
                    "select id from " + table + " where user_id=? and month='2026-08' and category='trend'",
                    UUID.class, seed.userId()));
        }
        assertLegacyDataPreserved(database.jdbc(), seed);
        assertUniqueAdvice(database.jdbc(), seed.userId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"checksum", "unknown_migration", "failed_migration"})
    void unknownOrFailedHistoryIsRejectedWithoutPartiallyRemappingIt(String corruption) throws Exception {
        Database database = legacyDatabase(2, 3);
        Seed seed = seedLegacyRows(database.jdbc());
        switch (corruption) {
            case "checksum" -> database.jdbc().update(
                    "update flyway_schema_history set checksum=checksum+1 where version='2'");
            case "unknown_migration" -> database.jdbc().update("""
                    insert into flyway_schema_history(installed_rank,version,description,type,script,checksum,
                        installed_by,execution_time,success)
                    values (4,'7','Unknown custom change','SQL','V7__unknown.sql',123,'postgres',0,true)
                    """);
            case "failed_migration" -> database.jdbc().update(
                    "update flyway_schema_history set success=false where version='3'");
            default -> throw new AssertionError("Unsupported test corruption");
        }
        List<Map<String, Object>> before = history(database.jdbc());

        SQLException exception = assertThrows(SQLException.class, () -> reconcile(database));

        assertEquals("P0001", exception.getSQLState());
        assertEquals(before, history(database.jdbc()));
        assertEquals(2, database.jdbc().queryForObject(
                "select count(*) from insights where user_id=?", Integer.class, seed.userId()));
        assertEquals(2, database.jdbc().queryForObject(
                "select count(*) from recommendations where user_id=?", Integer.class, seed.userId()));
        assertEquals(0, columnCount(database, "insights", "source_version"));
        assertEquals("2026-09", database.jdbc().queryForObject(
                "select month from monthly_records where user_id=?", String.class, seed.userId()));
    }

    private Database legacyDatabase(int insightVersion, int recommendationVersion) throws IOException {
        Database database = database();
        Path directory = Files.createTempDirectory(temporary, "legacy-");
        copyMigration(directory, "V1__initial_schema.sql", "V1__initial_schema.sql");
        copyMigration(directory, "V4__create_insights_table.sql", "V" + insightVersion + "__create_insights_table.sql");
        copyMigration(directory, "V5__create_recommendations_table.sql",
                "V" + recommendationVersion + "__create_recommendations_table.sql");
        Flyway legacy = Flyway.configure().dataSource(database.dataSource()).schemas(database.schema())
                .defaultSchema(database.schema()).locations("filesystem:" + directory.toAbsolutePath().toString().replace('\\', '/'))
                .load();
        assertEquals(3, legacy.migrate().migrationsExecuted);
        return database;
    }

    private void copyMigration(Path directory, String source, String target) throws IOException {
        try (var stream = new ClassPathResource("db/migration/" + source).getInputStream()) {
            Files.copy(stream, directory.resolve(target));
        }
    }

    private void reconcile(Database database) throws Exception {
        String script = Files.readString(Path.of("docs", "reconcile-legacy-flyway.sql"), StandardCharsets.UTF_8);
        try (Connection connection = database.dataSource().getConnection(); Statement statement = connection.createStatement()) {
            try {
                // PostgreSQL parses the complete script, including its dollar-quoted DO block.
                statement.execute(script);
            } catch (SQLException exception) {
                statement.execute("ROLLBACK");
                throw exception;
            }
        }
    }

    private static Database database() {
        String schema = "migration_" + UUID.randomUUID().toString().replace("-", "");
        // Match a pre-existing application schema (normally public), without Flyway's
        // synthetic SCHEMA history entry that the deliberately narrow repair rejects.
        new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl("postgres", "postgres"), "postgres", ""))
                .execute("create schema " + schema);
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl("postgres", "postgres"), "postgres", "");
        Properties properties = new Properties();
        properties.setProperty("currentSchema", schema);
        dataSource.setConnectionProperties(properties);
        return new Database(schema, dataSource, new JdbcTemplate(dataSource));
    }

    private static Flyway canonical(Database database, boolean outOfOrder) {
        return Flyway.configure().dataSource(database.dataSource()).schemas(database.schema())
                .defaultSchema(database.schema()).locations("classpath:db/migration").outOfOrder(outOfOrder).load();
    }

    private static List<String> versions(JdbcTemplate jdbc) {
        return jdbc.queryForList("select version from flyway_schema_history where version is not null order by version::int",
                String.class);
    }

    private static List<Map<String, Object>> history(JdbcTemplate jdbc) {
        return jdbc.queryForList("select * from flyway_schema_history order by installed_rank");
    }

    private static int columnCount(Database database, String table, String column) {
        return database.jdbc().queryForObject("""
                select count(*) from information_schema.columns
                where table_schema=? and table_name=? and column_name=?
                """, Integer.class, database.schema(), table, column);
    }

    private static void assertCanonicalSchema(Database database) {
        for (String column : List.of("payment_behavior", "ntn_registered", "business_registered")) {
            assertEquals(1, columnCount(database, "business_profiles", column));
        }
        for (String table : List.of("insights", "recommendations")) {
            for (String column : List.of("source_version", "language", "source_computed_at")) {
                assertEquals(1, columnCount(database, table, column));
            }
        }
        assertEquals("YES", database.jdbc().queryForObject("""
                select is_nullable from information_schema.columns
                where table_schema=? and table_name='monthly_records' and column_name='cogs'
                """, String.class, database.schema()));
        assertNull(database.jdbc().queryForObject("""
                select column_default from information_schema.columns
                where table_schema=? and table_name='monthly_records' and column_name='cogs'
                """, String.class, database.schema()));
    }

    private static Seed seedLegacyRows(JdbcTemplate jdbc) {
        UUID userId = UUID.randomUUID();
        UUID insightId = UUID.randomUUID();
        UUID recommendationId = UUID.randomUUID();
        jdbc.update("insert into business_profiles(user_id,business_type,language_preference) values (?,'retail','ur')", userId);
        jdbc.update("insert into monthly_records(user_id,month,revenue,cogs) values (?,'2026-09',120000.75,75000.25)", userId);
        jdbc.update("""
                insert into score_results(user_id,month,composite_score,band,component_scores,weakest_component,data_completeness)
                values (?,'2026-09',60.25,'Stable',cast(? as jsonb),'cashflow',0.80)
                """, userId, "{\"cashflow\":40,\"profitability\":70,\"repayment\":null,\"trend\":70,\"compliance\":80}");
        jdbc.update("""
                insert into insights(user_id,month,insight_text,category,priority,created_at)
                values (?,'2026-09','Outdated insight','cashflow','low','2026-09-19 10:00:00')
                """, userId);
        jdbc.update("""
                insert into insights(id,user_id,month,insight_text,category,priority,created_at)
                values (?,?,'2026-09','Newest insight','cashflow','high','2026-09-19 12:00:00')
                """, insightId, userId);
        jdbc.update("""
                insert into recommendations(user_id,month,recommendation_text,category,priority,created_at)
                values (?,'2026-09','Outdated recommendation','cashflow','low','2026-09-19 10:00:00')
                """, userId);
        jdbc.update("""
                insert into recommendations(id,user_id,month,recommendation_text,category,priority,created_at)
                values (?,?,'2026-09','Newest recommendation','cashflow','high','2026-09-19 12:00:00')
                """, recommendationId, userId);
        return new Seed(userId, insightId, recommendationId);
    }

    private static void assertLegacyDataPreserved(JdbcTemplate jdbc, Seed seed) {
        assertEquals("ur", jdbc.queryForObject(
                "select language_preference from business_profiles where user_id=?", String.class, seed.userId()));
        assertEquals(new BigDecimal("120000.75"), jdbc.queryForObject(
                "select revenue from monthly_records where user_id=?", BigDecimal.class, seed.userId()));
        assertEquals(new BigDecimal("75000.25"), jdbc.queryForObject(
                "select cogs from monthly_records where user_id=?", BigDecimal.class, seed.userId()));
        assertEquals(new BigDecimal("60.25"), jdbc.queryForObject(
                "select composite_score from score_results where user_id=?", BigDecimal.class, seed.userId()));
        assertEquals(seed.insightId(), jdbc.queryForObject(
                "select id from insights where user_id=? and month='2026-09'", UUID.class, seed.userId()));
        assertEquals(seed.recommendationId(), jdbc.queryForObject(
                "select id from recommendations where user_id=? and month='2026-09'", UUID.class, seed.userId()));
        assertEquals("Newest insight", jdbc.queryForObject(
                "select insight_text from insights where id=?", String.class, seed.insightId()));
        assertEquals("Newest recommendation", jdbc.queryForObject(
                "select recommendation_text from recommendations where id=?", String.class, seed.recommendationId()));
        assertNull(jdbc.queryForObject("select source_version from insights where id=?", String.class, seed.insightId()));
        assertNull(jdbc.queryForObject("select source_version from recommendations where id=?", String.class, seed.recommendationId()));
    }

    private static void assertUniqueAdvice(JdbcTemplate jdbc, UUID userId) {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                insert into insights(user_id,month,insight_text,category,priority)
                values (?,'2026-09','Duplicate','cashflow','high')
                """, userId));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                insert into recommendations(user_id,month,recommendation_text,category,priority)
                values (?,'2026-09','Duplicate','cashflow','high')
                """, userId));
    }

    private record Database(String schema, DriverManagerDataSource dataSource, JdbcTemplate jdbc) { }
    private record Seed(UUID userId, UUID insightId, UUID recommendationId) { }
}
