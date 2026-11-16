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
 *   <li>start-demo.ps1 is local-only and refuses public exposure; readiness is HTTP and ownership based, init-demo.ps1
 *       only seeds a new marked environment, and unrelated processes are never reused or stopped (PowerShell checks run
 *       -PlanOnly modes and the guard module only; nothing is started).</li>
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
    void publicExposureIsDisabledWithoutAnyOverride() throws Exception {
        String script = read("scripts/start-demo.ps1");
        for (String forbidden : List.of("cloudflared", "trycloudflare", "0.0.0.0", "AllowNonSeededData", "ConfirmSyntheticPublicDemo",
                "offering-forums-marathon-calibration")) {
            assertFalse(script.contains(forbidden), "Public exposure path or override remains: " + forbidden);
        }
        assertTrue(script.contains("HOSTNAME = '127.0.0.1'"), "Frontend binds loopback only");
        assertTrue(script.contains("SERVER_ADDRESS = '127.0.0.1'"), "Backend binds loopback only");
        for (String off : List.of("AI_ENABLED = 'false'", "WHATSAPP_PROVIDER = 'mock'", "AI_LIVE_ACTIVATION_APPROVED = 'false'",
                "OCR_INTEGRATION_ENABLED = 'false'", "'SPRING_PROFILES_ACTIVE'", "'OPENAI_API_KEY'")) {
            assertTrue(script.contains(off), "Live providers stay disabled: " + off);
        }
    }

    @Test
    void readinessIsHttpAndOwnershipBasedNeverTcpOrPortBased() throws Exception {
        String start = read("scripts/start-demo.ps1");
        assertFalse(start.contains("TcpClient"), "A listening TCP port is not readiness");
        for (String required : List.of("Invoke-RuntimeCheck 'verify-backend' $BackendUrl", "Invoke-RuntimeCheck 'verify-frontend' $FrontendUrl",
                "Assert-PortOwnedBy 8080 $backendRecord", "Assert-PortOwnedBy 3000 $frontendRecord", "Assert-PortFree 8080",
                "Assert-PortFree 3000", "node $RuntimeHelper package", "application_name = '$connectionTag'")) {
            assertTrue(start.contains(required), "start-demo.ps1 must " + required);
        }
        assertFalse(Pattern.compile("(?i)reusing it").matcher(start).find(), "Existing listeners are never reused");
        String stop = read("scripts/stop-demo.ps1");
        assertFalse(stop.contains("Get-NetTCPConnection") || stop.contains("-LocalPort"), "stop-demo must not stop processes by port");
        assertTrue(stop.contains("Stop-OwnedProcessTree"));
    }

    @Test
    void initializationTargetsOnlyANewMarkedEnvironmentAndResetIsDisabled() throws Exception {
        String init = read("scripts/init-demo.ps1");
        String seeder = read("scripts/DemoScenarioSeeder.java");
        Matcher prefix = Pattern.compile("DISPOSABLE_MARKER_PREFIX = \"([^\"]+)\"").matcher(seeder);
        assertTrue(prefix.find());
        assertTrue(init.contains("\"" + prefix.group(1) + "$([guid]::NewGuid())"), "init-demo writes the marker the seeder requires");
        assertTrue(read("scripts/start-demo.ps1").contains("StartsWith('" + prefix.group(1) + "')"), "start-demo requires the same marker");
        for (String guard : List.of("docker container inspect $DbContainer", "docker volume inspect $DbVolume", "--fresh-disposable-database",
                "pg_catalog.pg_tables", "-ne $ExpectedSchemaVersion")) {
            assertTrue(init.contains(guard), "init-demo.ps1 must " + guard);
        }
        assertFalse(seeder.contains("session_replication_role"), "No trigger bypass");
        assertFalse(seeder.contains("UPDATE app_users"), "Existing credentials are never rewritten");
        assertFalse(seeder.contains("UPDATE business_profiles"), "Existing profiles are never rewritten");
        assertFalse(read("scripts/reset-sandbox.ps1").contains("DemoScenarioSeeder"), "Sandbox reset no longer runs the seeder");
    }

    record Run(int exit, String stdout, String stderr) { }

    static boolean windows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    static Optional<String> powershell() {
        for (String exe : windows() ? List.of("pwsh", "powershell") : List.of("pwsh")) {
            try {
                Process p = new ProcessBuilder(exe, "-NoProfile", "-NonInteractive", "-Command", "exit 0").redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                if (p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0) return Optional.of(exe);
            } catch (Exception ignored) {
                // try the next shell
            }
        }
        return Optional.empty();
    }

    static Run run(String exe, List<String> arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of(exe, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass"));
        command.addAll(arguments);
        Path err = Files.createTempFile("demo-script", ".err");
        try {
            Process p = new ProcessBuilder(command).redirectError(err.toFile()).start();
            String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(p.waitFor(120, TimeUnit.SECONDS), "PowerShell timed out: " + command);
            return new Run(p.exitValue(), stdout, Files.readString(err));
        } finally {
            Files.deleteIfExists(err);
        }
    }

    static Run script(String exe, String name, String... switches) throws Exception {
        List<String> arguments = new ArrayList<>(List.of("-File", repo.resolve("scripts/" + name).toString()));
        arguments.addAll(List.of(switches));
        return run(exe, arguments);
    }

    @Test
    void startDemoIsLocalOnlyAndEveryPublicExposureRequestIsRefused() throws Exception {
        Optional<String> exe = powershell();
        assumeTrue(exe.isPresent(), "PowerShell (pwsh) is not installed; the static start-demo.ps1 checks still ran");

        Run local = script(exe.get(), "start-demo.ps1", "-PlanOnly");
        assertEquals(0, local.exit(), local.stderr());
        assertTrue(local.stdout().contains("Mode: local-only"), local.stdout());
        assertTrue(local.stdout().contains("Frontend bind: 127.0.0.1:3000"), local.stdout());
        assertTrue(local.stdout().contains("Public exposure: disabled (not approved)"), local.stdout());
        assertTrue(Pattern.compile("(?m)^Backend CORS origins: http://localhost:3000\\s*$").matcher(local.stdout()).find(), local.stdout());
        assertTrue(local.stdout().contains("Plan only: nothing was started."));
        for (Account a : seederAccounts()) {
            assertTrue(Pattern.compile("(?m)^\\s+" + a.role() + "\\s+" + Pattern.quote(a.email()) + "\\s+" + Pattern.quote(a.password()) + "\\s*$")
                    .matcher(local.stdout()).find(), "Printed credentials for " + a + ":\n" + local.stdout());
        }

        Run tunnel = script(exe.get(), "start-demo.ps1", "-PublicTunnel", "-PlanOnly");
        assertEquals(2, tunnel.exit(), tunnel.stdout() + tunnel.stderr());
        assertTrue(tunnel.stderr().contains("REFUSED: Public demonstration is disabled"), tunnel.stderr());
        assertFalse(tunnel.stdout().contains("Plan only"));
        for (String removedOverride : List.of("-ConfirmSyntheticPublicDemo", "-AllowNonSeededData")) {
            Run override = script(exe.get(), "start-demo.ps1", "-PublicTunnel", removedOverride, "-PlanOnly");
            assertNotEquals(0, override.exit(), removedOverride + " must not exist as an override");
            assertFalse(override.stdout().contains("Plan only"), removedOverride);
        }
    }

    @Test
    void initRequiresExplicitFreshDesignationAndSandboxResetIsRefused() throws Exception {
        Optional<String> exe = powershell();
        assumeTrue(exe.isPresent(), "PowerShell (pwsh) is not installed");
        Run undesignated = script(exe.get(), "init-demo.ps1", "-PlanOnly");
        assertEquals(2, undesignated.exit(), undesignated.stdout() + undesignated.stderr());
        assertTrue(undesignated.stderr().contains("-CreateFreshDisposableDemo"), undesignated.stderr());
        Run plan = script(exe.get(), "init-demo.ps1", "-CreateFreshDisposableDemo", "-PlanOnly");
        assertEquals(0, plan.exit(), plan.stderr());
        assertTrue(plan.stdout().contains("Plan only: nothing was created."), plan.stdout());
        Run reset = script(exe.get(), "reset-sandbox.ps1");
        assertEquals(2, reset.exit(), reset.stdout() + reset.stderr());
        assertTrue(reset.stderr().contains("Sandbox reset is disabled"), reset.stderr());
    }

    /**
     * Codex's process findings: an unrelated listener must be refused, never reused or stopped, and a recorded PID that
     * is not the launched process (PID reuse) must never be terminated. Uses a task-owned ephemeral loopback socket.
     */
    @Test
    void unrelatedListenersAreRefusedButNeverStoppedOrAdopted() throws Exception {
        Optional<String> exe = powershell();
        assumeTrue(windows() && exe.isPresent(), "Port-ownership guards use Windows networking cmdlets");
        long self = ProcessHandle.current().pid();
        try (java.net.ServerSocket unrelated = new java.net.ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress())) {
            int port = unrelated.getLocalPort();
            String module = repo.resolve("scripts/demo-guards.psm1").toString().replace("'", "''");
            String probe = "$ErrorActionPreference='Stop'; Import-Module '" + module + "' -Force;"
                    + "try { Assert-PortFree " + port + " 'probe'; 'FREE' } catch { 'REFUSED ' + $_.Exception.Message };"
                    + "$other = New-OwnedProcessRecord 'frontend' (Get-Process -Id $PID);"
                    + "try { Assert-PortOwnedBy " + port + " $other; 'ADOPTED' } catch { 'NOT-OWNED ' + $_.Exception.Message };"
                    + "$reused = [ordered]@{ role = 'backend'; pid = " + self + "; startTicks = 1; name = 'java' };"
                    + "Stop-OwnedProcessTree $reused";
            Run result = run(exe.get(), List.of("-Command", probe));
            assertEquals(0, result.exit(), result.stdout() + result.stderr());
            assertTrue(result.stdout().contains("REFUSED Port " + port + " (probe) is already in use by java"), result.stdout());
            assertTrue(result.stdout().contains("neither reused nor stopped"), result.stdout());
            assertTrue(result.stdout().contains("NOT-OWNED Port " + port + " is served by java"), result.stdout());
            assertFalse(result.stdout().contains("ADOPTED") || result.stdout().contains("FREE"), result.stdout());
            assertTrue(result.stdout().contains("backend: PID " + self + " not running or reused by another program; left untouched"), result.stdout());
            // The unrelated listener (this JVM) is still alive and still serving.
            try (java.net.Socket client = new java.net.Socket(java.net.InetAddress.getLoopbackAddress(), port);
                 java.net.Socket accepted = unrelated.accept()) {
                assertTrue(client.isConnected() && accepted.isConnected());
            }
        }
    }
}
