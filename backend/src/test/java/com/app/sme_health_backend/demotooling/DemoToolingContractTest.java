package com.app.sme_health_backend.demotooling;

import com.app.sme_health_backend.identity.model.MembershipRole;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * DEF-18: the local demo tooling under scripts/ (not compiled by Maven) stays correct and safe.
 * <ul>
 *   <li>The seeder defines exactly one synthetic account per {@link MembershipRole} (MANAGER included), and
 *       start-demo.ps1 and verify-public-demo.mjs advertise exactly those credentials.</li>
 *   <li>FlywayMigratorDemo resolves the repository's migrations portably and refuses wrong or partial locations, with
 *       clean disabled and the demo database fixed to loopback.</li>
 *   <li>start-demo.ps1 is local-only by default; a public tunnel needs two explicit switches (checked by running the
 *       script's -PlanOnly mode when PowerShell is available, which starts nothing).</li>
 * </ul>
 */
class DemoToolingContractTest {
    static Path repo;
    static Class<?> seeder;
    static Class<?> migrator;

    record Account(String role, String email, String password) { }

    @BeforeAll
    static void compileScripts(@TempDir Path out) throws Exception {
        repo = repositoryRoot();
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertNotNull(javac, "A JDK is required to compile scripts/*.java");
        String classpath = codeSource(Flyway.class) + File.pathSeparator + codeSource(Argon2PasswordEncoder.class);
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        int status = javac.run(null, diagnostics, diagnostics, "-proc:none", "-encoding", "UTF-8", "-classpath", classpath,
                "-d", out.toString(),
                repo.resolve("scripts/DemoScenarioSeeder.java").toString(),
                repo.resolve("scripts/FlywayMigratorDemo.java").toString());
        assertEquals(0, status, "scripts/*.java must compile: " + diagnostics.toString(StandardCharsets.UTF_8));
        ClassLoader loader = new URLClassLoader(new URL[] {out.toUri().toURL()}, DemoToolingContractTest.class.getClassLoader());
        seeder = loader.loadClass("DemoScenarioSeeder");
        migrator = loader.loadClass("FlywayMigratorDemo");
    }

    static Path repositoryRoot() {
        for (Path base = Path.of(System.getProperty("user.dir")).toAbsolutePath(); base != null; base = base.getParent()) {
            if (Files.isRegularFile(base.resolve("scripts/start-demo.ps1"))) return base;
        }
        throw new AssertionError("Repository root with scripts/start-demo.ps1 not found from " + System.getProperty("user.dir"));
    }

    static String codeSource(Class<?> type) throws Exception {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
    }

    static String read(String relative) throws Exception {
        return Files.readString(repo.resolve(relative));
    }

    static List<Account> seederAccounts() throws Exception {
        Field field = seeder.getDeclaredField("DEMO_ACCOUNTS");
        field.setAccessible(true);
        List<Account> accounts = new ArrayList<>();
        for (String[] row : (String[][]) field.get(null)) {
            assertEquals(4, row.length, "DEMO_ACCOUNTS rows are {email, name, role, password}");
            accounts.add(new Account(row[2], row[0], row[3]));
        }
        return accounts;
    }

    static Object invoke(String name, Class<?>[] types, Object... args) throws Throwable {
        Method method = migrator.getDeclaredMethod(name, types);
        method.setAccessible(true);
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    static Path resolve(String override, Path start) throws Throwable {
        return (Path) invoke("resolveMigrationDirectory", new Class<?>[] {String.class, Path.class}, override, start);
    }

    static void validate(Path dir) throws Throwable {
        invoke("validateMigrationDirectory", new Class<?>[] {Path.class, int.class}, dir, 52);
    }

    // ---------------------------------------------------------------------------------------------- accounts

    @Test
    void seederHasOneSyntheticAccountPerMembershipRoleIncludingManager() throws Exception {
        List<Account> accounts = seederAccounts();
        assertEquals(Arrays.stream(MembershipRole.values()).map(Enum::name).collect(Collectors.toSet()),
                accounts.stream().map(Account::role).collect(Collectors.toSet()), "Every membership role has a demo account");
        assertEquals(MembershipRole.values().length, accounts.size(), "Exactly one demo account per role");
        assertTrue(accounts.stream().anyMatch(a -> a.role().equals("MANAGER") && a.email().equals("demo.manager@finsight.local")));
        assertEquals(accounts.size(), accounts.stream().map(Account::email).distinct().count());
        assertEquals(accounts.size(), accounts.stream().map(Account::password).distinct().count(), "No shared demo password");
        for (Account a : accounts) {
            assertTrue(a.email().endsWith("@finsight.local"), "Demo accounts use the reserved synthetic domain: " + a);
            assertTrue(a.password().length() >= 12, a.toString());
        }
        // Users and every business membership are both driven by the one table.
        String source = read("scripts/DemoScenarioSeeder.java");
        assertFalse(Pattern.compile("String\\[\\]\\s+(roles|emails)\\s*=").matcher(source).find(),
                "Memberships must be created from DEMO_ACCOUNTS, not a parallel role/email list");
        assertEquals(2, Pattern.compile("for \\(String\\[\\] \\w+ : DEMO_ACCOUNTS\\)").matcher(source).results().count());
    }

    @Test
    void startDemoAdvertisesExactlyTheSeededCredentials() throws Exception {
        String script = read("scripts/start-demo.ps1");
        Matcher row = Pattern.compile("@\\{\\s*Role\\s*=\\s*'([A-Z]+)';\\s*Email\\s*=\\s*'([^']+)';\\s*Password\\s*=\\s*'([^']+)'\\s*}")
                .matcher(script);
        List<Account> advertised = new ArrayList<>();
        while (row.find()) advertised.add(new Account(row.group(1), row.group(2), row.group(3)));
        assertEquals(seederAccounts(), advertised, "start-demo.ps1 accounts must equal the seeder's DEMO_ACCOUNTS");
        assertFalse(script.contains("FinSight@Demo2026!"), "The non-existent shared demo password must not be printed");
        assertFalse(script.toLowerCase(Locale.ROOT).contains("all accounts"), "There is no shared password for all accounts");
    }

    @Test
    void startDemoKnowsEverySeededBusinessForTheSyntheticDataCheck() throws Exception {
        Matcher names = Pattern.compile("isSandbox \\? \"([^\"]+)\" : \"([^\"]+)\"").matcher(read("scripts/DemoScenarioSeeder.java"));
        Set<String> seeded = new TreeSet<>();
        while (names.find()) { seeded.add(names.group(1)); seeded.add(names.group(2)); }
        assertEquals(8, seeded.size(), seeded.toString());
        Matcher block = Pattern.compile("(?s)\\$SeededBusinessNames = @\\((.*?)\\r?\\n\\)").matcher(read("scripts/start-demo.ps1"));
        assertTrue(block.find(), "start-demo.ps1 must declare $SeededBusinessNames");
        Set<String> listed = new TreeSet<>();
        Matcher literal = Pattern.compile("'([^']+)'").matcher(block.group(1));
        while (literal.find()) listed.add(literal.group(1));
        assertEquals(seeded, listed);
    }

    @Test
    void publicDemoVerifierUsesTheSeededCredentials() throws Exception {
        Matcher row = Pattern.compile("\\{ email: '([^']+)', password: '([^']+)', expectedRole: '([A-Z]+)'")
                .matcher(read("scripts/verify-public-demo.mjs"));
        List<Account> verified = new ArrayList<>();
        while (row.find()) verified.add(new Account(row.group(3), row.group(1), row.group(2)));
        assertEquals(seederAccounts(), verified);
    }

    // ---------------------------------------------------------------------------------------------- migrator

    @Test
    void migratorResolvesTheRepositoryMigrationsFromAnyCheckoutDirectory() throws Throwable {
        Path expected = repo.resolve("backend/src/main/resources/db/migration").toAbsolutePath().normalize();
        assertEquals(expected, resolve(null, repo));
        assertEquals(expected, resolve("  ", repo.resolve("scripts")));
        assertEquals(expected, resolve(null, repo.resolve("backend/src/main/java")));
        validate(expected);
        String location = (String) invoke("flywayLocation", new Class<?>[] {Path.class}, expected);
        assertTrue(location.startsWith("filesystem:"), location);
        assertFalse(location.contains("\\"), location);
        assertTrue(location.endsWith("/backend/src/main/resources/db/migration"), location);
    }

    @Test
    void migratorKeepsItsSafetyRulesAndHasNoMachineSpecificPath() throws Exception {
        String source = read("scripts/FlywayMigratorDemo.java");
        assertFalse(Pattern.compile("[A-Za-z]:[/\\\\]+Users[/\\\\]").matcher(source).find(), "No developer-machine path");
        assertTrue(source.contains(".cleanDisabled(true)"));
        assertTrue(source.contains("\"jdbc:postgresql://127.0.0.1:5433/finsight_demo\""), "Demo database only");
        assertTrue(source.contains("EXPECTED_VERSION = 52"));
        assertTrue(source.contains("validateMigrationDirectory(migrations, EXPECTED_VERSION)"));
    }

    @Test
    void migratorHonoursAnExplicitLocationAndRefusesWrongOrPartialOnes(@TempDir Path tmp) throws Throwable {
        Path dir = Files.createDirectory(tmp.resolve("migration"));
        for (int v = 1; v <= 52; v++) Files.writeString(dir.resolve("V" + v + "__synthetic_" + v + ".sql"), "SELECT 1;");
        Files.writeString(dir.resolve("README.txt"), "non-SQL files are ignored by Flyway");
        assertEquals(dir.toAbsolutePath().normalize(), resolve(dir.toString(), repo), "Explicit override wins");
        validate(dir);

        assertThrows(IllegalStateException.class, () -> resolve(tmp.resolve("missing").toString(), repo));
        assertThrows(IllegalStateException.class, () -> resolve(null, tmp), "No repository above an unrelated directory");

        Files.move(dir.resolve("V7__synthetic_7.sql"), tmp.resolve("V7__synthetic_7.sql"));
        assertTrue(assertThrows(IllegalStateException.class, () -> validate(dir)).getMessage().contains("missing V7"));
        Files.move(tmp.resolve("V7__synthetic_7.sql"), dir.resolve("V7__synthetic_7.sql"));
        validate(dir);

        for (String stray : List.of("V53__future.sql", "V3__duplicate.sql", "R__repeatable.sql", "V8_bad_name.sql")) {
            Path file = Files.writeString(dir.resolve(stray), "SELECT 1;");
            assertThrows(IllegalStateException.class, () -> validate(dir), stray);
            Files.delete(file);
        }
        Path nested = Files.createDirectory(dir.resolve("nested"));
        assertThrows(IllegalStateException.class, () -> validate(dir), "Flyway would also scan subdirectories");
        Files.delete(nested);
        validate(dir);
        assertThrows(IllegalStateException.class, () -> validate(tmp.resolve("absent")));
    }

    // ---------------------------------------------------------------------------------------------- start-demo.ps1

    @Test
    void publicTunnelLaunchOnlyExistsBehindTheOptIn() throws Exception {
        String script = read("scripts/start-demo.ps1");
        assertFalse(script.contains("offering-forums-marathon-calibration"), "No stale hard-coded tunnel host or URL fallback");
        assertEquals(1, Pattern.compile("docker run[^\\n]*cloudflared").matcher(script).results().count());
        int section = script.indexOf("# 4. Cloudflare Quick Tunnel");
        int launch = script.indexOf("cloudflare/cloudflared");
        assertTrue(section > 0 && launch > section, "Tunnel launch belongs to step 4");
        assertTrue(script.substring(section, launch).contains("if ($PublicTunnel) {"), "Tunnel launch must be gated by -PublicTunnel");
        assertTrue(script.contains("$env:SERVER_ADDRESS = \"127.0.0.1\""), "Backend binds loopback only");
        for (String off : List.of("$env:AI_ENABLED = \"false\"", "$env:WHATSAPP_PROVIDER = \"mock\"", "$env:AI_LIVE_ACTIVATION_APPROVED = \"false\"")) {
            assertTrue(script.contains(off), "Live providers stay disabled: " + off);
        }
    }

    record Run(int exit, String stdout, String stderr) { }

    static Optional<String> powershell() {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        for (String exe : windows ? List.of("pwsh", "powershell") : List.of("pwsh")) {
            try {
                Process p = new ProcessBuilder(exe, "-NoProfile", "-NonInteractive", "-Command", "exit 0")
                        .redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                if (p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0) return Optional.of(exe);
            } catch (Exception ignored) {
                // try the next shell
            }
        }
        return Optional.empty();
    }

    static Run planOnly(String exe, String... switches) throws Exception {
        List<String> command = new ArrayList<>(List.of(exe, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", repo.resolve("scripts/start-demo.ps1").toString()));
        command.addAll(List.of(switches));
        command.add("-PlanOnly");
        Path err = Files.createTempFile("start-demo", ".err");
        try {
            Process p = new ProcessBuilder(command).redirectError(err.toFile()).start();
            String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(p.waitFor(120, TimeUnit.SECONDS), "start-demo.ps1 -PlanOnly timed out");
            return new Run(p.exitValue(), stdout, Files.readString(err));
        } finally {
            Files.deleteIfExists(err);
        }
    }

    @Test
    void startDemoIsLocalOnlyByDefaultAndPublicExposureNeedsBothSwitches() throws Exception {
        Optional<String> exe = powershell();
        assumeTrue(exe.isPresent(), "PowerShell (pwsh) is not installed; the static start-demo.ps1 checks still ran");

        Run local = planOnly(exe.get());
        assertEquals(0, local.exit(), local.stderr());
        assertTrue(local.stdout().contains("Mode: local-only"), local.stdout());
        assertTrue(local.stdout().contains("Frontend bind: 127.0.0.1:3000"), local.stdout());
        assertTrue(Pattern.compile("(?m)^Backend CORS origins: http://localhost:3000\\s*$").matcher(local.stdout()).find(),
                local.stdout());
        assertFalse(local.stdout().contains("trycloudflare"), local.stdout());
        assertTrue(local.stdout().contains("Plan only: nothing was started."));
        for (Account a : seederAccounts()) {
            assertTrue(Pattern.compile("(?m)^\\s+" + a.role() + "\\s+" + Pattern.quote(a.email()) + "\\s+" + Pattern.quote(a.password()) + "\\s*$")
                    .matcher(local.stdout()).find(), "Printed credentials for " + a + ":\n" + local.stdout());
        }

        for (String[] refused : List.of(new String[] {"-PublicTunnel"}, new String[] {"-ConfirmSyntheticPublicDemo"},
                new String[] {"-AllowNonSeededData"}, new String[] {"-PublicTunnel", "-AllowNonSeededData"})) {
            Run run = planOnly(exe.get(), refused);
            assertEquals(2, run.exit(), String.join(" ", refused) + "\n" + run.stdout() + run.stderr());
            assertTrue(run.stderr().contains("REFUSED"), run.stderr());
            assertFalse(run.stdout().contains("Plan only"), "Refused before planning");
        }

        Run publicPlan = planOnly(exe.get(), "-PublicTunnel", "-ConfirmSyntheticPublicDemo");
        assertEquals(0, publicPlan.exit(), publicPlan.stderr());
        assertTrue(publicPlan.stdout().contains("Mode: public-tunnel"), publicPlan.stdout());
        assertTrue(publicPlan.stdout().contains("https://*.trycloudflare.com"), publicPlan.stdout());
        assertTrue(publicPlan.stdout().contains("after the synthetic-data checks pass"), publicPlan.stdout());
    }
}
