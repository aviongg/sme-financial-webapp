package com.app.sme_health_backend.testsupport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real Next/Spring/PostgreSQL journey with synthetic accounts and simulated AI only. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.address=127.0.0.1", "server.port=8080",
        "app.security.internal-service-secret=f13-browser-fixture-only-not-production",
        "app.ai.enabled=true", "app.ai.provider=mock", "app.ai.demo-enabled=true",
        "app.ai.live-activation-approved=false", "app.ai.api-key=",
        "app.ai.requests-per-minute=100", "app.ai.daily-provider-calls=200",
        "app.security.cors.allowed-origins=http://127.0.0.1:3100"
})
@ActiveProfiles("dev")
class F13BrowserAcceptance extends DisposablePostgres {
    @Test
    void authenticatedWorkspaceJourney() throws Exception {
        String node = System.getenv("FINSIGHT_BROWSER_NODE");
        assertNotNull(node, "Set FINSIGHT_BROWSER_NODE to a Node executable");
        Path script = Path.of("..", "scripts", "test_f13_browser.mjs").toAbsolutePath().normalize();
        Path output = Path.of(System.getenv().getOrDefault("BROWSER_ARTIFACT_DIR", "../tmp/f13/real-browser"));
        Files.createDirectories(output);
        Path log = output.resolve("browser-process.log");
        Process process = new ProcessBuilder(node, script.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(10, TimeUnit.MINUTES), "F13 browser acceptance timed out");
            assertEquals(0, process.exitValue(), "F13 browser acceptance failed; inspect browser-process.log");
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            if (Files.exists(log)) System.out.println(Files.readString(log));
        }
    }
}
