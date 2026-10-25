package com.app.sme_health_backend.testsupport;

import com.app.sme_health_backend.documents.ocr.OcrClient;
import com.app.sme_health_backend.documents.ocr.OcrExtraction;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Opt-in real Next/Spring/PostgreSQL financial-integrity journey; only the OCR provider is simulated. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.address=127.0.0.1", "server.port=8080",
        "app.security.internal-service-secret=pilot-browser-fixture-only-not-production",
        "app.ai.enabled=false", "app.ai.live-activation-approved=false", "app.ai.api-key=",
        "app.security.cors.allowed-origins=http://127.0.0.1:3100"
})
@ActiveProfiles("dev")
class PilotHardeningBrowserAcceptance extends DisposablePostgres {
    @MockitoBean OcrClient ocrClient;

    @Test
    void explicitFinancialConflictRecoveryJourney() throws Exception {
        when(ocrClient.extract(any())).thenReturn(new OcrExtraction(
                LocalDate.of(2026, 9, 15), new BigDecimal("200.00"), "Synthetic pilot vendor",
                OcrExtraction.Category.sales, OcrExtraction.Confidence.high, OcrExtraction.DocumentType.invoice));
        String node = System.getenv("FINSIGHT_BROWSER_NODE");
        assertTrue(node != null && !node.isBlank(), "Set FINSIGHT_BROWSER_NODE to a Node executable");
        Path script = Path.of("..", "scripts", "test_pilot_hardening_browser.mjs").toAbsolutePath().normalize();
        String requestedOutput = System.getenv("BROWSER_ARTIFACT_DIR");
        Path output = requestedOutput == null || requestedOutput.isBlank()
                ? Files.createTempDirectory("finsight-pilot-browser-") : Path.of(requestedOutput).toAbsolutePath();
        Files.createDirectories(output);
        Path log = output.resolve("browser-process.log");
        ProcessBuilder builder = new ProcessBuilder(node, script.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("BROWSER_ARTIFACT_DIR", output.toString());
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(10, TimeUnit.MINUTES), "Pilot browser acceptance timed out");
            assertEquals(0, process.exitValue(), "Pilot browser acceptance failed; inspect " + log);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            if (Files.exists(log)) System.out.println(Files.readString(log));
        }
    }
}
