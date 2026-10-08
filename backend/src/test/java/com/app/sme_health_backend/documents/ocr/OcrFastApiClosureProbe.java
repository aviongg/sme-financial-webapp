package com.app.sme_health_backend.documents.ocr;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Explicit executable probe: ./mvnw -Dtest=OcrFastApiClosureProbe test
 * Requires FINSIGHT_OCR_CONTRACT_PYTHON (defaults to python) with AI requirements
 * and cryptography installed. This is deliberately outside default test naming,
 * not a skipped test or a substitute for Google/Docker/production-DNS acceptance.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OcrFastApiClosureProbe {
    @TempDir
    static Path directory;
    private Process server;
    private String endpoint;
    private byte[] png;

    @BeforeAll
    void startRealFastApiHttpsServer() throws Exception {
        Path fixture = Path.of("../ai-service/tests/https_contract_server.py").toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(fixture), "Run this probe from backend/");
        String python = System.getenv().getOrDefault("FINSIGHT_OCR_CONTRACT_PYTHON", "python");
        server = new ProcessBuilder(python, fixture.toString(), "--directory", directory.toString())
                .redirectErrorStream(true).redirectOutput(directory.resolve("fixture.log").toFile()).start();
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        Path ready = directory.resolve("endpoint.txt");
        while (!Files.exists(ready) && server.isAlive() && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        assertTrue(Files.exists(ready), "Real FastAPI HTTPS fixture did not start; install AI requirements and cryptography in FINSIGHT_OCR_CONTRACT_PYTHON");
        endpoint = Files.readString(ready).strip();
        ByteArrayOutputStream image = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", image);
        png = image.toByteArray();
    }

    @AfterAll
    void stopRealServer() throws Exception {
        if (server != null) {
            server.destroy();
            if (!server.waitFor(5, TimeUnit.SECONDS)) {
                server.destroyForcibly();
                server.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private MockEnvironment environment() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("OCR_SERVICE_URL", endpoint)
                .withProperty("OCR_SERVICE_KEY_FILE", directory.resolve("ocr_service_key").toString())
                .withProperty("OCR_SSL_ROOT_CERT", directory.resolve("ca.crt").toString())
                .withProperty("OCR_CONNECT_TIMEOUT_SECONDS", "3")
                .withProperty("OCR_REQUEST_TIMEOUT_SECONDS", "5");
        env.setActiveProfiles("prod");
        return env;
    }

    private OcrRequest request() {
        return new OcrRequest(png, "image/png", OcrExtraction.DocumentType.invoice);
    }

    @Test
    void trustedCertificateAndSharedSecretProduceParsedDraftOnly() {
        OcrClient client = new OcrIntegrationConfiguration().ocrClient(environment());
        OcrExtraction draft = client.extract(request());
        assertEquals(LocalDate.of(2026, 9, 19), draft.date());
        assertEquals(0, new java.math.BigDecimal("1250.50").compareTo(draft.amount()));
        assertEquals(OcrExtraction.DocumentType.invoice, draft.documentTypeDetected());
        assertEquals(OcrExtraction.Category.unknown, draft.category());
        assertEquals(OcrExtraction.Confidence.medium, draft.confidence());
        assertNull(draft.vendorOrParty());
        assertFalse(draft.isCompleteAndHighConfidence());
    }

    @Test
    void wrongSecretIsRejectedByRealFastApiBeforeProvider() throws Exception {
        String before = Files.exists(directory.resolve("provider-calls.txt"))
                ? Files.readString(directory.resolve("provider-calls.txt")) : "0";
        Path wrong = directory.resolve("wrong-service-key");
        Files.writeString(wrong, "disposable-incorrect-service-key");
        MockEnvironment env = environment().withProperty("OCR_SERVICE_KEY_FILE", wrong.toString());
        OcrClient client = new OcrIntegrationConfiguration().ocrClient(env);
        assertEquals(OcrClientException.Reason.unavailable,
                assertThrows(OcrClientException.class, () -> client.extract(request())).reason());
        String after = Files.exists(directory.resolve("provider-calls.txt"))
                ? Files.readString(directory.resolve("provider-calls.txt")) : "0";
        assertEquals(before, after, "Unauthorized request must not invoke the fake provider");
        // Observe the actual wire status as well as the application's safe mapping.
        var factory = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
        var store = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());
        store.load(null, null);
        try (var input = Files.newInputStream(directory.resolve("ca.crt"))) {
            store.setCertificateEntry("fixture-ca", java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(input));
        }
        factory.init(store);
        var ssl = javax.net.ssl.SSLContext.getInstance("TLS");
        ssl.init(null, factory.getTrustManagers(), null);
        var response = new JdkOcrHttpTransport(HttpClient.newBuilder().sslContext(ssl).build())
                .postMultipart(URI.create(endpoint + "/extract"), "test-boundary",
                        OcrMultipartBuilder.build("test-boundary", request()), "wrong-key",
                        Duration.ofSeconds(5), 65536);
        assertEquals(401, response.statusCode());
        assertTrue(new String(response.body(), java.nio.charset.StandardCharsets.UTF_8).contains("unauthorized"));
    }

    @Test
    void untrustedCaFailsWhileServiceRemainsReachable() {
        OcrClient wrongCaClient = new OcrIntegrationConfiguration().ocrClient(environment()
                .withProperty("OCR_SSL_ROOT_CERT", directory.resolve("wrong-ca.crt").toString()));
        assertEquals(OcrClientException.Reason.unavailable,
                assertThrows(OcrClientException.class, () -> wrongCaClient.extract(request())).reason());
        assertNotNull(new OcrIntegrationConfiguration().ocrClient(environment()).extract(request()));
    }

    @Test
    void plaintextProductionEndpointFailsBeforeAnyRequest() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new OcrIntegrationConfiguration().ocrClient(environment()
                        .withProperty("OCR_SERVICE_URL", endpoint.replace("https:", "http:"))));
        assertTrue(error.getMessage().contains("Plaintext production OCR endpoint prohibited"));
    }
}
