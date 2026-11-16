package com.app.sme_health_backend.demotooling;

import com.app.sme_health_backend.testsupport.DisposablePostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DEF-18 / Codex QA (candidate v2 independent QA section 5): demo seeding must never modify existing data. Each case
 * creates its own freshly migrated database on the disposable embedded PostgreSQL server and runs the real
 * scripts/DemoScenarioSeeder.java as the restricted runtime role. Reproduces Codex's sentinel (an existing profile's
 * null WhatsApp number was overwritten) and the credential rewrite, and proves a marked fresh database seeds exactly once.
 */
class DemoSeederFreshDatabasePostgreSqlIT extends DisposablePostgres {
    static Class<?> seederClass;

    @BeforeAll
    static void compileSeeder(@TempDir Path out) throws Exception {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertNotNull(javac, "A JDK is required");
        Path repo = DemoToolingContractTest.repositoryRoot();
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        int status = javac.run(null, diagnostics, diagnostics, "-proc:none", "-encoding", "UTF-8", "-classpath",
                DemoToolingContractTest.codeSource(Argon2PasswordEncoder.class), "-d", out.toString(),
                repo.resolve("scripts/DemoScenarioSeeder.java").toString());
        assertEquals(0, status, diagnostics.toString(StandardCharsets.UTF_8));
        seederClass = new URLClassLoader(new URL[] {out.toUri().toURL()}, DemoSeederFreshDatabasePostgreSqlIT.class.getClassLoader())
                .loadClass("DemoScenarioSeeder");
    }

    /** A brand-new database with the same role/privilege layout as the demo, migrated to V52 by the migrator role. */
    static String freshDatabase(boolean marked) throws Exception {
        String name = "demo_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = POSTGRES.getPostgresDatabase().getConnection(); Statement sql = admin.createStatement()) {
            sql.execute("CREATE DATABASE " + name);
        }
        try (Connection admin = POSTGRES.getDatabase("postgres", name).getConnection(); Statement sql = admin.createStatement()) {
            sql.execute("ALTER SCHEMA public OWNER TO finsight_migrator");
            sql.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
            sql.execute("GRANT USAGE ON SCHEMA public TO finsight_app, finsight_backup");
            sql.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public");
            sql.execute("ALTER DEFAULT PRIVILEGES FOR ROLE finsight_migrator IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO finsight_app");
            sql.execute("ALTER DEFAULT PRIVILEGES FOR ROLE finsight_migrator IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO finsight_app");
            if (marked) sql.execute("COMMENT ON DATABASE " + name + " IS 'finsight-disposable-synthetic-demo:it-" + name + "'");
        }
        Flyway.configure().dataSource(url(name), "finsight_migrator", PASSWORD).locations("classpath:db/migration")
                .cleanDisabled(true).load().migrate();
        return name;
    }

    static String url(String database) {
        return "jdbc:postgresql://localhost:" + POSTGRES.getPort() + "/" + database;
    }

    static Connection app(String database) throws Exception {
        Connection connection = DriverManager.getConnection(url(database), "finsight_app", PASSWORD);
        connection.setAutoCommit(false);
        return connection;
    }

    static Connection admin(String database) throws Exception {
        return POSTGRES.getDatabase("postgres", database).getConnection();
    }

    /** What scripts/init-demo.ps1 → main() does after connecting: prove freshness, then seed, in one transaction. */
    static void initialize(Connection connection) throws Throwable {
        Constructor<?> constructor = seederClass.getConstructor(Connection.class);
        Object seeder = constructor.newInstance(connection);
        try {
            Method preflight = seederClass.getDeclaredMethod("requireFreshDisposableDatabase");
            preflight.setAccessible(true);
            preflight.invoke(seeder);
            seederClass.getMethod("seedAll").invoke(seeder);
            connection.commit();
        } catch (InvocationTargetException e) {
            connection.rollback();
            throw e.getCause();
        }
    }

    /** Row count of every public table plus a fingerprint of all user/profile/business rows. */
    static Map<String, Object> snapshot(String database) throws Exception {
        Map<String, Object> state = new TreeMap<>();
        try (Connection c = admin(database); Statement sql = c.createStatement()) {
            var tables = new java.util.ArrayList<String>();
            try (ResultSet rs = sql.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'")) {
                while (rs.next()) tables.add(rs.getString(1));
            }
            for (String table : tables) {
                try (ResultSet rs = sql.executeQuery("SELECT count(*) FROM public.\"" + table + "\"")) { rs.next(); state.put(table, rs.getLong(1)); }
            }
            for (String table : new String[] {"app_users", "business_profiles", "businesses", "business_memberships"}) {
                try (ResultSet rs = sql.executeQuery("SELECT md5(coalesce(string_agg(t::text, '|' ORDER BY t::text), '')) FROM public." + table + " t")) {
                    rs.next(); state.put(table + "#md5", rs.getString(1));
                }
            }
        }
        return state;
    }

    static long count(String database, String query) throws Exception {
        try (Connection c = admin(database); Statement sql = c.createStatement(); ResultSet rs = sql.executeQuery(query)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    void markedFreshDatabaseSeedsOnceAndAnyReseedIsRefusedWithoutChanges() throws Throwable {
        String db = freshDatabase(true);
        try (Connection c = app(db)) { initialize(c); }
        assertEquals(8, count(db, "SELECT count(*) FROM businesses"));
        assertEquals(5, count(db, "SELECT count(*) FROM app_users"));
        assertEquals(5, count(db, "SELECT count(DISTINCT role) FROM business_memberships"));
        assertEquals(40, count(db, "SELECT count(*) FROM business_memberships"));
        assertEquals(0, count(db, "SELECT count(*) FROM business_profiles WHERE whatsapp_number IS NULL OR whatsapp_number NOT LIKE 'enc:v1:%'"));

        Map<String, Object> before = snapshot(db);
        try (Connection c = app(db)) {
            IllegalStateException refused = assertThrows(IllegalStateException.class, () -> initialize(c));
            assertTrue(refused.getMessage().contains("already contains data"), refused.getMessage());
            assertTrue(refused.getMessage().contains("nothing was changed"), refused.getMessage());
        }
        assertEquals(before, snapshot(db), "A refused reseed must not change any row, credential or profile");
    }

    @Test
    void preExistingProfileIsPreservedAndSeedingRefused() throws Throwable {
        // Codex's sentinel: an existing business whose profile has no WhatsApp number.
        String db = freshDatabase(true);
        UUID sentinel = UUID.randomUUID();
        try (Connection c = admin(db); Statement sql = c.createStatement()) {
            sql.execute("INSERT INTO businesses (id, business_name, status, created_at, updated_at) VALUES ('" + sentinel + "', 'Pre-existing sentinel', 'ACTIVE', now(), now())");
            sql.execute("INSERT INTO business_profiles (user_id, business_type, language_preference, whatsapp_number, whatsapp_opt_in, created_at) "
                    + "VALUES ('" + sentinel + "', 'retail', 'en', NULL, false, now())");
        }
        Map<String, Object> before = snapshot(db);
        try (Connection c = app(db)) {
            IllegalStateException refused = assertThrows(IllegalStateException.class, () -> initialize(c));
            assertTrue(refused.getMessage().contains("table businesses already contains data"), refused.getMessage());
        }
        assertEquals(before, snapshot(db));
        assertEquals(1, count(db, "SELECT count(*) FROM business_profiles WHERE user_id = '" + sentinel + "' AND whatsapp_number IS NULL"),
                "The existing profile's null phone must not be overwritten");
        assertEquals(0, count(db, "SELECT count(*) FROM app_users"));
    }

    @Test
    void preExistingAccountCredentialsArePreservedAndSeedingRefused() throws Throwable {
        String db = freshDatabase(true);
        try (Connection c = admin(db); Statement sql = c.createStatement()) {
            sql.execute("INSERT INTO app_users (id, email, password_hash, full_name, account_status, must_change_password, auth_version, created_at, updated_at) "
                    + "VALUES (gen_random_uuid(), 'demo.owner@finsight.local', 'sentinel-hash', 'sentinel-name', 'ACTIVE', false, 0, now(), now())");
        }
        Map<String, Object> before = snapshot(db);
        try (Connection c = app(db)) {
            assertThrows(IllegalStateException.class, () -> initialize(c));
        }
        assertEquals(before, snapshot(db));
        assertEquals(1, count(db, "SELECT count(*) FROM app_users WHERE email = 'demo.owner@finsight.local' "
                + "AND password_hash = 'sentinel-hash' AND full_name = 'sentinel-name'"), "Existing credentials are never rewritten");
    }

    @Test
    void unmarkedDatabaseIsRefusedEvenWhenEmpty() throws Throwable {
        // Fresh and empty, but not designated by init-demo (e.g. a restored, recreated or arbitrary database).
        String db = freshDatabase(false);
        try (Connection c = app(db)) {
            IllegalStateException refused = assertThrows(IllegalStateException.class, () -> initialize(c));
            assertTrue(refused.getMessage().contains("not marked as a disposable synthetic demo"), refused.getMessage());
        }
        assertEquals(0, count(db, "SELECT count(*) FROM businesses"));
        assertEquals(0, count(db, "SELECT count(*) FROM app_users"));
    }

    @Test
    void entryPointRefusesSandboxResetAndAnyRunWithoutTheFreshDesignation() throws Exception {
        Method main = seederClass.getMethod("main", String[].class);
        for (String[] args : new String[][] {{}, {"--reset-sandbox"}, {"--fresh-disposable-database", "--reset-sandbox"}, {"--FRESH"}}) {
            InvocationTargetException e = assertThrows(InvocationTargetException.class, () -> main.invoke(null, (Object) args), String.join(" ", args));
            assertInstanceOf(IllegalStateException.class, e.getCause(), String.join(" ", args));
        }
        assertFalse(java.util.Arrays.stream(seederClass.getDeclaredMethods()).anyMatch(m -> m.getName().equals("resetSandboxOnly")),
                "The replica-mode sandbox reset must not exist");
    }
}
