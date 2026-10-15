package com.app.sme_health_backend.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiResponsesProviderTests {
    HttpServer server;
    final AiProperties p = new AiProperties();
    final AtomicReference<String> received = new AtomicReference<>();
    final AtomicInteger calls = new AtomicInteger();
    @BeforeEach void prepare() throws Exception {
        p.setEnabled(true); p.setProvider("openai"); p.setLiveActivationApproved(true); p.setApiKey("fixture-not-a-real-key");
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()); server.start();
    }
    @AfterEach void stop() { server.stop(0); }
    OpenAiResponsesProvider provider(int status, String body, int delayMs) {
        server.createContext("/v1/responses", exchange -> {
            calls.incrementAndGet(); received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("Bearer fixture-not-a-real-key", exchange.getRequestHeaders().getFirst("Authorization"));
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            if (status == 302) exchange.getResponseHeaders().add("Location", "https://api.openai.com/v1/responses");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            try { exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); }
            finally { exchange.close(); }
        });
        URI endpoint = URI.create("http://localhost:" + server.getAddress().getPort() + "/v1/responses");
        return new OpenAiResponsesProvider(p, HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(), endpoint);
    }
    @Test void sendsPublishedResponsesContractAndParsesActualOutputItems() {
        var context = AiTestFixtures.context("score", "en", "50.00", "0.4", false);
        String selection = new SimulatedAiProvider().generate(context);
        assertEquals(selection, provider(200, AiTestFixtures.responseEnvelope(selection), 0).generate(context));
        var body = AiJson.MAPPER.readTree(received.get());
        assertEquals("gpt-6-luna", body.path("model").asString()); assertFalse(body.path("store").asBoolean());
        assertEquals("json_schema", body.path("text").path("format").path("type").asString());
        assertTrue(body.path("text").path("format").path("strict").asBoolean());
        assertFalse(received.get().contains("businessId")); assertFalse(received.get().contains("conversation"));
        assertFalse(body.has("tools")); assertEquals(1, calls.get());
    }
    @Test void providerHttpFailureDoesNotRetryOrExposeBody() {
        var exception = assertThrows(OpenAiResponsesProvider.ProviderFailure.class, () -> provider(429, "sensitive-provider-text", 0)
                .generate(AiTestFixtures.context("score", "en", "10", "0", false)));
        assertEquals("provider_unavailable", exception.code()); assertFalse(exception.getMessage().contains("sensitive")); assertEquals(1, calls.get());
    }
    @Test void timeoutCancelsWithoutRetry() {
        p.setTimeoutSeconds(1);
        var exception = assertThrows(OpenAiResponsesProvider.ProviderFailure.class, () -> provider(200, "{}", 1600)
                .generate(AiTestFixtures.context("score", "en", "10", "0", false)));
        assertTrue(java.util.Set.of("provider_timeout", "provider_unavailable").contains(exception.code())); assertEquals(1, calls.get());
    }
    @Test void hugeBodyIsRejected() {
        assertThrows(OpenAiResponsesProvider.ProviderFailure.class, () -> provider(200, "x".repeat(70000), 0)
                .generate(AiTestFixtures.context("score", "en", "10", "0", false)));
    }
    @Test void redirectIsNeverFollowedToRealProvider() {
        assertThrows(OpenAiResponsesProvider.ProviderFailure.class, () -> provider(302, "", 0)
                .generate(AiTestFixtures.context("score", "en", "10", "0", false))); assertEquals(1, calls.get());
    }
    @Test void disabledAdapterNeverMakesRequest() {
        p.setEnabled(false); var client = provider(200, "{}", 0);
        assertThrows(OpenAiResponsesProvider.ProviderFailure.class, () -> client.generate(AiTestFixtures.context("score", "en", "10", "0", false)));
        assertEquals(0, calls.get());
    }
    @Test void rejectsRefusalIncompleteToolsMalformedAndAmbiguousText() {
        for (String output : java.util.List.of("{}", "garbage", "{\"status\":\"incomplete\",\"output\":[]}",
                "{\"status\":\"completed\",\"output\":[{\"type\":\"function_call\"}]}",
                "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"refusal\",\"refusal\":\"no\"}]}]}"))
            assertThrows(OpenAiResponsesProvider.ProviderFailure.class, () -> OpenAiResponsesProvider.extract(output));
    }
}
