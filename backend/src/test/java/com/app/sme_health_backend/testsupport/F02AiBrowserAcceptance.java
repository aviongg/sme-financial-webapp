package com.app.sme_health_backend.testsupport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Explicit opt-in harness: real HTTP, real disposable PostgreSQL, simulated AI only. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.address=127.0.0.1", "server.port=8080",
        "app.security.internal-service-secret=browser-fixture-only-not-production",
        "app.ai.enabled=true", "app.ai.provider=mock", "app.ai.demo-enabled=true",
        "app.ai.live-activation-approved=false", "app.ai.api-key=",
        "app.ai.requests-per-minute=100", "app.ai.daily-provider-calls=200",
        "app.security.cors.allowed-origins=http://127.0.0.1:3100"
})
@ActiveProfiles("dev")
class F02AiBrowserAcceptance extends DisposablePostgres {
    @Test
    void actualBrowserJourney() throws Exception {
        String node = System.getenv("FINSIGHT_BROWSER_NODE");
        assertTrue(node != null && !node.isBlank(), "Set FINSIGHT_BROWSER_NODE to a Node executable");
        Path script = Path.of("..", "scripts", "test_f02_ai_browser.mjs").toAbsolutePath().normalize();
        Path output = Path.of(System.getenv().getOrDefault("BROWSER_ARTIFACT_DIR", "../tmp/f02-ai-launch/real-browser"));
        Files.createDirectories(output);
        Path log = output.resolve("browser-process.log");
        Process process = new ProcessBuilder(node, script.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(8, TimeUnit.MINUTES), "Browser acceptance timed out");
            assertEquals(0, process.exitValue(), "Real browser acceptance failed; inspect its output");
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            if (Files.exists(log)) System.out.println(Files.readString(log));
        }
    }
}
