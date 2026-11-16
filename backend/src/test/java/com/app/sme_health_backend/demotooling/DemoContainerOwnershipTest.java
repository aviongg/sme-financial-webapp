package com.app.sme_health_backend.demotooling;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Candidate-v3 Stage-B P1: the demo scripts act on a Docker container only when it is provably the container created by
 * this checkout's init-demo.ps1 (full ID, name, creation time, init instance label, Compose project/service/working
 * directory and data volume), never because of its name.
 *
 * <p><b>Fixture-backed, not Docker execution.</b> The exact scripts are copied into a temporary checkout and run by real
 * PowerShell with {@code src/test/resources/demotooling/DockerFunctionFixture.ps1}, a {@code docker} function that
 * shadows the CLI, simulates only the listed containers and records every call. No real container is touched.
 */
class DemoContainerOwnershipTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String NAME = "finsight-postgres-demo";
    static final String VOLUME = "finsight-demo_finsight_demo_pgdata";
    static final String DATA = "volume:" + VOLUME + ":/var/lib/postgresql/data";
    static final String OWN_ID = id("owned"), FOREIGN_ID = id("foreign"), OTHER_ID = id("other");
    static final String CREATED = "2026-11-16T10:00:00.123456789Z";
    static final String MARKER = "finsight-disposable-synthetic-demo:" + UUID.randomUUID() + " created 2026-11-16T10:00:01Z";
    static Path repo;
    static Optional<String> shell;

    @BeforeAll
    static void locate() {
        repo = DemoToolingContractTest.repositoryRoot();
        shell = DemoToolingContractTest.powershell();
    }

    static String id(String seed) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A temporary checkout holding the exact scripts, its .demo-runtime and the fixture state. */
    static final class Checkout {
        final Path root;
        final String instance = UUID.randomUUID().toString();
        final Map<String, Object> state = new LinkedHashMap<>();
        final List<Map<String, Object>> containers = new ArrayList<>();

        Checkout(Path root) throws Exception {
            this.root = root;
            Files.createDirectories(root.resolve("scripts"));
            for (String file : List.of("stop-demo.ps1", "start-demo.ps1", "init-demo.ps1", "backup-demo.ps1", "restore-demo.ps1",
                    "demo-guards.psm1", "demo-runtime.mjs", "demo_roles_init.sql", "grant_demo_permissions.sql")) {
                Files.copy(repo.resolve("scripts").resolve(file), root.resolve("scripts").resolve(file));
            }
            Files.createDirectories(root.resolve(".demo-runtime/documents"));
            Files.writeString(root.resolve(".demo-runtime/documents/synthetic-upload.txt"), "synthetic");
            state.put("containers", containers);
            state.put("volumes", List.of());
            state.put("calls", List.of());
            state.put("execCount", 0);
            state.put("swapAfterExec", 0);
            state.put("swapToId", OTHER_ID);
            state.put("psql", new LinkedHashMap<>(Map.of("tables", "0", "marker", MARKER, "version", "52", "tagged", "1")));
        }

        Map<String, Object> container(String id, String name, String instance, boolean running) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("Id", id); c.put("Name", name); c.put("Created", CREATED); c.put("Running", running);
            c.put("Project", "finsight-demo"); c.put("Service", "postgres-demo"); c.put("Instance", instance);
            c.put("WorkingDir", root.toString()); c.put("Mounts", List.of(DATA));
            containers.add(c);
            return c;
        }

        Map<String, Object> owned(boolean running) {
            return container(OWN_ID, NAME, instance, running);
        }

        Map<String, Object> environment() {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("provenance", "init-demo.ps1"); e.put("marker", MARKER); e.put("instance", instance);
            e.put("composeProject", "finsight-demo"); e.put("container", NAME); e.put("containerId", OWN_ID);
            e.put("containerCreated", CREATED); e.put("composeWorkingDir", root.toString()); e.put("volume", VOLUME);
            e.put("projectRoot", root.toString()); e.put("schemaVersion", "52"); e.put("createdAt", "2026-11-16T10:00:02Z");
            return e;
        }

        Map<String, Object> runtime() {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("startedAt", "2026-11-16T10:05:00Z"); r.put("dbStartedByLauncher", false);
            r.put("database", new LinkedHashMap<>(Map.of("containerId", OWN_ID, "instance", instance)));
            r.put("processes", List.of());
            return r;
        }

        void write(String file, Object value) throws Exception {
            Files.writeString(root.resolve(".demo-runtime").resolve(file), JSON.writeValueAsString(value));
        }

        /** Runs one exact script under the fixture; {@code prelude} is PowerShell run first in the same session. */
        Result run(String script, String prelude, String... arguments) throws Exception {
            Path statePath = root.resolve("docker-fixture-state.json");
            Files.writeString(statePath, JSON.writeValueAsString(state));
            String command = "$ErrorActionPreference='Stop'; . '" + esc(repo.resolve("backend/src/test/resources/demotooling/DockerFunctionFixture.ps1"))
                    + "' '" + esc(statePath) + "'; " + prelude + "; & '" + esc(root.resolve("scripts").resolve(script)) + "' "
                    + String.join(" ", arguments) + "; exit $LASTEXITCODE";
            DemoToolingContractTest.Run run = DemoToolingContractTest.run(shell.orElseThrow(), List.of("-Command", command));
            return new Result(run, JSON.readTree(Files.readString(statePath).replace("﻿", "")), root);
        }

        static String esc(Path path) {
            return path.toString().replace("'", "''");
        }
    }

    record Result(DemoToolingContractTest.Run run, JsonNode state, Path root) {
        int exit() { return run.exit(); }

        List<String> calls() {
            List<String> calls = new ArrayList<>();
            state.get("calls").forEach(c -> calls.add(c.asText()));
            return calls;
        }

        /** Any Docker call that could change a container, volume or Compose project. */
        List<String> mutations() {
            return calls().stream().filter(c -> c.matches("^(start|stop|restart|kill|rm|exec|cp|volume rm|compose).*")).toList();
        }

        boolean running(String id) {
            for (JsonNode c : state.get("containers")) if (c.get("Id").asText().equals(id)) return c.get("Running").asBoolean();
            throw new AssertionError("No fixture container " + id);
        }

        String output() {
            return run.stdout() + run.stderr();
        }
    }

    Checkout checkout(Path dir) throws Exception {
        assumeTrue(shell.isPresent(), "PowerShell is not installed; the static demo-tooling contracts still ran");
        return new Checkout(dir);
    }

    static void assertNothingStoredWasRemoved(Result r) {
        assertTrue(Files.exists(r.root().resolve(".demo-runtime/documents/synthetic-upload.txt")), "Document store kept");
        for (String call : r.calls()) {
            assertFalse(call.matches("^(rm|volume rm|compose).*") || call.contains(" down"), "Nothing is removed: " + call);
        }
    }

    // ------------------------------------------------------------------ static source contract (runs without PowerShell)

    @Test
    void noDemoScriptMutatesAContainerByNameAndLegacyTunnelShutdownIsGone() throws Exception {
        java.util.regex.Pattern byName = java.util.regex.Pattern.compile(
                "docker\\s+(?:start|stop|restart|kill|rm|exec(?:\\s+-i)?|cp)\\s+(?:\\$DbContainer|\\$container|\"?finsight-postgres-demo)");
        for (String script : List.of("stop-demo.ps1", "start-demo.ps1", "init-demo.ps1", "backup-demo.ps1", "restore-demo.ps1", "demo-guards.psm1")) {
            String source = Files.readString(repo.resolve("scripts").resolve(script));
            assertFalse(byName.matcher(source).find(), script + " addresses a container by name: " + byName.matcher(source).results().map(m -> m.group()).toList());
            assertFalse(source.contains("finsight-tunnel-demo") || source.contains("cloudflared"), script + " still references the legacy tunnel");
        }
        String stop = Files.readString(repo.resolve("scripts/stop-demo.ps1"));
        assertTrue(stop.contains("Stop-OwnedDemoContainer") && stop.contains("Stop-OwnedProcessTree"));
        assertFalse(stop.contains("docker ps") || stop.contains("Get-NetTCPConnection"), "No discovery by name or port");
        String start = Files.readString(repo.resolve("scripts/start-demo.ps1"));
        assertTrue(start.contains("Test-DemoContainerOwnership $environment $ProjectRoot"));
        assertTrue(start.contains("Stop-OwnedDemoContainer $environment $state $ProjectRoot"), "Cleanup re-verifies before stopping");
        String compose = Files.readString(repo.resolve("docker-compose.demo.yml"));
        assertTrue(compose.contains("com.finsight.demo.instance: \"${FINSIGHT_DEMO_INSTANCE:-unassigned}\""));
        String init = Files.readString(repo.resolve("scripts/init-demo.ps1"));
        for (String field : List.of("containerId = $facts.Id", "containerCreated = $facts.Created", "instance = $instance",
                "composeWorkingDir = $facts.WorkingDir", "projectRoot = $ProjectRoot")) {
            assertTrue(init.contains(field), "init-demo.ps1 records " + field);
        }
    }

    // ------------------------------------------------------------------ stop-demo.ps1

    @Test
    void authorizedShutdownStopsItsOwnProcessAndVerifiedContainerByIdAndKeepsData(@TempDir Path dir) throws Exception {
        assumeTrue(DemoToolingContractTest.windows(), "Owned-process records use Windows process start times");
        Checkout co = checkout(dir);
        co.owned(true);
        co.container(FOREIGN_ID, "finsight-tunnel-demo", "", true);
        co.write("environment.json", co.environment());
        co.write("runtime.json", co.runtime());
        Process owned = new ProcessBuilder("ping", "-n", "120", "127.0.0.1").redirectErrorStream(true).start();
        try {
            // The launcher's own record for a process it started (PID + exact start time), added to runtime.json.
            String prelude = "Import-Module '" + Checkout.esc(co.root.resolve("scripts/demo-guards.psm1")) + "' -Force; "
                    + "$rt = Get-Content -Raw '" + Checkout.esc(co.root.resolve(".demo-runtime/runtime.json")) + "' | ConvertFrom-Json; "
                    + "$own = New-OwnedProcessRecord 'backend' (Get-Process -Id " + owned.pid() + "); "
                    + "$reused = [ordered]@{ role = 'frontend'; pid = " + ProcessHandle.current().pid() + "; startTicks = 1; name = 'java' }; "
                    + "$rt.processes = @($own, $reused); "
                    + "$rt | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 '" + Checkout.esc(co.root.resolve(".demo-runtime/runtime.json")) + "'";
            Result r = co.run("stop-demo.ps1", prelude);
            assertEquals(0, r.exit(), r.output());
            assertTrue(owned.waitFor(30, TimeUnit.SECONDS), "The launcher-owned process was stopped");
            assertTrue(r.output().contains("frontend: PID " + ProcessHandle.current().pid() + " not running or reused"), r.output());
            assertEquals(List.of("stop " + OWN_ID), r.mutations(), "Only the verified container is stopped, by full ID");
            assertFalse(r.running(OWN_ID));
            assertTrue(r.running(FOREIGN_ID), "The legacy tunnel-named container is never touched");
            assertTrue(r.calls().stream().noneMatch(c -> c.contains("finsight-tunnel-demo") || c.endsWith(" " + NAME)), r.calls().toString());
            assertTrue(r.output().contains("stopped (volume and documents kept)"), r.output());
            assertNothingStoredWasRemoved(r);
            assertTrue(Files.exists(co.root.resolve(".demo-runtime/environment.json")), "Init record kept for the next start");
            assertFalse(Files.exists(co.root.resolve(".demo-runtime/runtime.json")));
        } finally {
            owned.destroyForcibly();
        }
    }

    @Test
    void withoutARuntimeRecordNothingIsStoppedEvenWhenTheNamedContainerIsTheRealOne(@TempDir Path dir) throws Exception {
        Checkout co = checkout(dir);
        co.owned(true);
        co.container(FOREIGN_ID, "finsight-tunnel-demo", "", true);
        co.write("environment.json", co.environment());
        Result r = co.run("stop-demo.ps1", "");
        assertEquals(0, r.exit(), r.output());
        assertEquals(List.of(), r.calls(), "No Docker call at all without a runtime record");
        assertTrue(r.running(OWN_ID) && r.running(FOREIGN_ID));
        assertTrue(r.output().contains("left untouched"), r.output());
    }

    /** Each case leaves a running container named finsight-postgres-demo in place; none may be stopped. */
    @Test
    void missingStaleForeignRenamedOrInconsistentEvidenceLeavesEveryContainerUntouched(@TempDir Path dir) throws Exception {
        Map<String, Consumer<Checkout>> cases = new LinkedHashMap<>();
        cases.put("missing environment manifest", co -> co.owned(true));
        cases.put("foreign container with the demo name; recorded ID gone (stale)", co -> co.container(FOREIGN_ID, NAME, UUID.randomUUID().toString(), true));
        cases.put("recreated under the same name and labels but a new ID", co -> co.container(FOREIGN_ID, NAME, co.instance, true));
        cases.put("own container renamed; a foreign one took the name", co -> {
            co.container(OWN_ID, NAME + "-renamed", co.instance, true);
            co.container(FOREIGN_ID, NAME, UUID.randomUUID().toString(), true);
        });
        cases.put("recorded ID now has a different init instance label", co -> co.container(OWN_ID, NAME, UUID.randomUUID().toString(), true));
        cases.put("recorded ID belongs to a different Compose project", co -> co.owned(true).put("Project", "someone-else"));
        cases.put("recorded ID mounts a different data volume", co -> co.owned(true).put("Mounts", List.of("volume:other:/var/lib/postgresql/data")));
        cases.put("recorded ID was created at a different time", co -> co.owned(true).put("Created", "2030-01-01T00:00:00Z"));
        cases.put("runtime record names another container than the environment", co -> co.owned(true));
        cases.put("environment manifest from another checkout", co -> co.owned(true));
        cases.put("legacy environment manifest without container identity", co -> co.owned(true));
        int index = 0;
        for (Map.Entry<String, Consumer<Checkout>> c : cases.entrySet()) {
            Checkout co = checkout(dir.resolve("case" + index++));
            c.getValue().accept(co);
            Map<String, Object> environment = co.environment();
            Map<String, Object> runtime = co.runtime();
            switch (c.getKey()) {
                case "runtime record names another container than the environment" ->
                        runtime.put("database", Map.of("containerId", OTHER_ID, "instance", co.instance));
                case "environment manifest from another checkout" -> environment.put("projectRoot", dir.resolve("elsewhere").toString());
                case "legacy environment manifest without container identity" -> {
                    environment.keySet().retainAll(List.of("marker", "composeProject", "container", "volume", "schemaVersion", "createdAt"));
                }
                default -> { }
            }
            if (!c.getKey().equals("missing environment manifest")) co.write("environment.json", environment);
            co.write("runtime.json", runtime);
            Result r = co.run("stop-demo.ps1", "");
            assertEquals(3, r.exit(), c.getKey() + ": " + r.output());
            assertEquals(List.of(), r.mutations(), c.getKey() + " must not change any container");
            for (JsonNode container : r.state().get("containers")) {
                assertTrue(container.get("Running").asBoolean(), c.getKey() + ": " + container);
            }
            assertTrue(r.run().stderr().contains("could not be verified"), c.getKey() + ": " + r.output());
            assertTrue(r.output().contains("left untouched"), c.getKey() + ": " + r.output());
            assertNothingStoredWasRemoved(r);
        }
    }

    // ------------------------------------------------------------------ start-demo.ps1 (Windows: port guards)

    Checkout startCheckout(Path dir) throws Exception {
        assumeTrue(DemoToolingContractTest.windows(), "start-demo.ps1 port guards use Windows networking cmdlets");
        Checkout co = checkout(dir);
        Files.createDirectories(co.root.resolve("backend/target"));
        Files.writeString(co.root.resolve("backend/target/sme-health-backend-0.0.1-SNAPSHOT.jar"), "not a real jar");
        return co;
    }

    @Test
    void startRefusesAnUnverifiedNamedContainerWithoutStartingOrChangingIt(@TempDir Path dir) throws Exception {
        Checkout co = startCheckout(dir);
        co.container(FOREIGN_ID, NAME, UUID.randomUUID().toString(), false);
        co.write("environment.json", co.environment());
        Result r = co.run("start-demo.ps1", "");
        assertEquals(4, r.exit(), r.output());
        assertTrue(r.run().stderr().contains("not verified as this checkout's init-demo.ps1 container"), r.output());
        assertEquals(List.of(), r.mutations(), "A foreign container is never started, queried or stopped");
        assertFalse(r.running(FOREIGN_ID));
        assertFalse(Files.exists(co.root.resolve(".demo-runtime/runtime.json")));
    }

    @Test
    void startupFailureCleanupStopsOnlyTheVerifiedContainerItStarted(@TempDir Path dir) throws Exception {
        Checkout co = startCheckout(dir);
        co.owned(false);
        co.write("environment.json", co.environment());
        // The database checks pass; packaging fails because this temporary checkout has no frontend build.
        Result r = co.run("start-demo.ps1", "");
        assertEquals(1, r.exit(), r.output());
        assertTrue(r.run().stderr().contains("Frontend build incomplete"), r.output());
        List<String> mutations = r.mutations();
        assertEquals("start " + OWN_ID, mutations.get(0));
        assertEquals("stop " + OWN_ID, mutations.get(mutations.size() - 1), "Cleanup stops the container it started, by ID");
        assertTrue(mutations.stream().allMatch(m -> m.contains(OWN_ID)), "Every container command uses the verified ID: " + mutations);
        assertFalse(r.running(OWN_ID));
        assertNothingStoredWasRemoved(r);
        assertFalse(Files.exists(co.root.resolve(".demo-runtime/runtime.json")));
    }

    @Test
    void startupFailureCleanupLeavesAContainerReplacedMidRunOrAlreadyRunningUntouched(@TempDir Path dir) throws Exception {
        Checkout replaced = startCheckout(dir.resolve("replaced"));
        replaced.owned(false);
        replaced.state.put("swapAfterExec", 1); // after pg_isready the name now resolves to a different container
        replaced.write("environment.json", replaced.environment());
        Result r = replaced.run("start-demo.ps1", "");
        assertEquals(1, r.exit(), r.output());
        assertTrue(r.mutations().contains("start " + OWN_ID));
        assertTrue(r.mutations().stream().noneMatch(m -> m.startsWith("stop")), "A replaced container is never stopped: " + r.mutations());
        assertTrue(r.running(OTHER_ID), "The replacement keeps running");
        assertTrue(r.run().stderr().contains("left untouched"), r.output());

        Checkout running = startCheckout(dir.resolve("running"));
        running.owned(true);
        running.write("environment.json", running.environment());
        Result s = running.run("start-demo.ps1", "");
        assertEquals(1, s.exit(), s.output());
        assertTrue(s.mutations().stream().noneMatch(m -> m.startsWith("stop") || m.startsWith("start")), s.mutations().toString());
        assertTrue(s.running(OWN_ID), "A container that was already running is not stopped by cleanup");
        assertTrue(s.run().stderr().contains("already running before this launch; left untouched"), s.output());
    }

    // ------------------------------------------------------------------ init-demo.ps1, backup and restore

    Checkout initCheckout(Path dir) throws Exception {
        Checkout co = checkout(dir);
        Files.delete(co.root.resolve(".demo-runtime/documents/synthetic-upload.txt")); // init requires an empty document store
        return co;
    }

    Map<String, Object> composeTemplate(Checkout co, String instanceOverride) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("Id", OWN_ID); t.put("Name", NAME); t.put("Created", CREATED); t.put("Project", "finsight-demo");
        t.put("Service", "postgres-demo"); t.put("WorkingDir", co.root.toString()); t.put("Mounts", List.of(DATA));
        t.put("instanceOverride", instanceOverride);
        return t;
    }

    @Test
    void initAddressesOnlyTheContainerItProvablyCreatedAndRefusesAnyOther(@TempDir Path dir) throws Exception {
        Checkout created = initCheckout(dir.resolve("created"));
        created.state.put("compose", composeTemplate(created, null));
        Result r = created.run("init-demo.ps1", "", "-CreateFreshDisposableDemo");
        // This temporary checkout has no backend, so initialization stops at the classpath step (after marking).
        assertEquals(1, r.exit(), r.output());
        List<String> execs = r.calls().stream().filter(c -> c.startsWith("exec")).toList();
        assertFalse(execs.isEmpty());
        assertTrue(execs.stream().allMatch(c -> c.startsWith("exec " + OWN_ID) || c.startsWith("exec -i " + OWN_ID)), execs.toString());
        assertTrue(execs.stream().anyMatch(c -> c.contains("COMMENT ON DATABASE finsight_demo IS 'finsight-disposable-synthetic-demo:")));
        assertTrue(r.mutations().stream().noneMatch(m -> m.startsWith("stop") || m.startsWith("rm") || m.contains(" down")));
        assertTrue(r.output().contains("Nothing was stopped or removed"), r.output());
        assertFalse(Files.exists(created.root.resolve(".demo-runtime/environment.json")), "No identity is recorded for a failed init");

        Checkout foreign = initCheckout(dir.resolve("foreign"));
        foreign.state.put("compose", composeTemplate(foreign, UUID.randomUUID().toString()));
        Result f = foreign.run("init-demo.ps1", "", "-CreateFreshDisposableDemo");
        assertEquals(1, f.exit(), f.output());
        assertTrue(f.run().stderr().contains("is not the one this initialization created (instance label)"), f.output());
        assertTrue(f.calls().stream().noneMatch(c -> c.startsWith("exec") || c.startsWith("stop")), "Never queried, marked or stopped: " + f.calls());
        assertTrue(f.running(OWN_ID));

        Checkout existing = initCheckout(dir.resolve("existing"));
        existing.container(FOREIGN_ID, NAME, "", false);
        Result e = existing.run("init-demo.ps1", "", "-CreateFreshDisposableDemo");
        assertEquals(3, e.exit(), e.output());
        assertEquals(List.of(), e.mutations(), "An existing named container is refused, not reused: " + e.calls());
    }

    @Test
    void backupReadsOnlyTheVerifiedContainerAndRestoreIsRefused(@TempDir Path dir) throws Exception {
        Checkout foreign = checkout(dir.resolve("foreign"));
        foreign.container(FOREIGN_ID, NAME, UUID.randomUUID().toString(), true);
        foreign.write("environment.json", foreign.environment());
        Result b = foreign.run("backup-demo.ps1", "");
        assertEquals(4, b.exit(), b.output());
        assertEquals(List.of(), b.mutations(), "No dump is written into or copied out of an unverified container");

        Checkout owned = checkout(dir.resolve("owned"));
        owned.owned(true);
        owned.write("environment.json", owned.environment());
        Result ok = owned.run("backup-demo.ps1", "");
        assertTrue(ok.mutations().size() >= 3 && ok.mutations().stream().allMatch(m -> m.contains(OWN_ID)), ok.mutations().toString());

        Checkout restore = checkout(dir.resolve("restore"));
        restore.owned(true);
        Result r = restore.run("restore-demo.ps1", "");
        assertEquals(2, r.exit(), r.output());
        assertEquals(List.of(), r.calls(), "Restore never drops or replaces a database");
        assertTrue(r.run().stderr().contains("Demo restore is disabled"), r.output());
    }
}
