package com.app.sme_health_backend.ai.copilot;

import com.app.sme_health_backend.ai.AiProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Real-provider adapter contract against a controlled loopback Responses API fixture (no network, no secrets). */
class OpenAiCopilotProviderTests {
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private OpenAiCopilotProvider provider(List<String> responses, List<String> requests, List<String> auth, int status) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger i = new AtomicInteger();
        server.createContext("/v1/responses", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.add(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = responses.get(Math.min(i.getAndIncrement(), responses.size() - 1)).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        AiProperties p = new AiProperties();
        p.setEnabled(true);
        p.setProvider("openai");
        p.setLiveActivationApproved(true);
        p.setApiKey("test-key-not-a-secret");
        p.setModel("gpt-test");
        return new OpenAiCopilotProvider(p, HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/responses"));
    }

    private static CopilotTypes.Conversation conversation(int round, List<CopilotTypes.ToolCall> calls, List<CopilotTypes.ToolResult> results) {
        var spec = new CopilotTypes.ToolSpec("profit_and_loss", "P&L", List.of(new CopilotTypes.Param("month", "string", "YYYY-MM",
                null, "^[0-9]{4}-(0[1-9]|1[0-2])$", 7, null, null, false)));
        return new CopilotTypes.Conversation("en", "What is my profit?", List.of(), List.of(spec), calls, results, round);
    }

    @Test
    void functionCallRoundTripUsesStrictToolsAndEchoesCallIds() throws Exception {
        List<String> requests = new ArrayList<>(), auth = new ArrayList<>();
        var provider = provider(List.of(
                "{\"status\":\"completed\",\"output\":[{\"type\":\"reasoning\"},{\"type\":\"function_call\",\"call_id\":\"call_9\",\"name\":\"profit_and_loss\",\"arguments\":\"{\\\"month\\\":null}\"}]}",
                "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\"{\\\"answer\\\":\\\"Revenue is 1050.00\\\",\\\"citedEvidenceIds\\\":[\\\"profit_and_loss.revenue\\\"]}\"}]}]}"),
                requests, auth, 200);
        CopilotTypes.Step first = provider.next(conversation(0, List.of(), List.of()));
        assertFalse(first.isFinal());
        assertEquals("call_9", first.toolCalls().get(0).callId());
        assertEquals("profit_and_loss", first.toolCalls().get(0).name());
        var call = first.toolCalls().get(0);
        var result = new CopilotTypes.ToolResult("call_9", "profit_and_loss", "OK", null,
                List.of(new CopilotTypes.Evidence("profit_and_loss.revenue", "profit_and_loss", "revenue", "1050.00")));
        CopilotTypes.Step second = provider.next(conversation(1, List.of(call), List.of(result)));
        assertTrue(second.isFinal());
        assertEquals("Revenue is 1050.00", second.answer());
        assertEquals(List.of("profit_and_loss.revenue"), second.citedEvidenceIds());

        assertEquals("Bearer test-key-not-a-secret", auth.get(0));
        assertTrue(requests.get(0).contains("\"strict\":true"));
        assertTrue(requests.get(0).contains("\"additionalProperties\":false"));
        assertTrue(requests.get(0).contains("\"store\":false"));
        assertTrue(requests.get(1).contains("\"function_call_output\""));
        assertTrue(requests.get(1).contains("\"call_id\":\"call_9\""));
        assertFalse(requests.get(1).contains("businessId"), "No identity reaches the provider");
    }

    @Test
    void disabledProviderAndErrorsAreTypedFailures() throws Exception {
        AiProperties off = new AiProperties();
        var disabled = new OpenAiCopilotProvider(off, HttpClient.newHttpClient(), URI.create("http://127.0.0.1:1/"));
        assertEquals("provider_not_configured", assertThrows(CopilotProvider.ProviderFailure.class,
                () -> disabled.next(conversation(0, List.of(), List.of()))).code());
        var failing = provider(List.of("{\"error\":{\"message\":\"x\"}}"), new ArrayList<>(), new ArrayList<>(), 500);
        assertEquals("provider_unavailable", assertThrows(CopilotProvider.ProviderFailure.class,
                () -> failing.next(conversation(0, List.of(), List.of()))).code());
        assertThrows(CopilotProvider.ProviderFailure.class, () -> OpenAiCopilotProvider.parse("{\"output\":[{\"type\":\"web_search_call\"}]}"));
        assertThrows(CopilotProvider.ProviderFailure.class, () -> OpenAiCopilotProvider.parse("not json"));
        var malformedArgs = OpenAiCopilotProvider.parse("{\"output\":[{\"type\":\"function_call\",\"call_id\":\"c\",\"name\":\"x\",\"arguments\":\"{oops\"}]}");
        assertEquals(Map.of("__malformed__", "true"), malformedArgs.toolCalls().get(0).arguments(), "Malformed args are rejected by the registry");
    }
}
