package com.app.sme_health_backend.whatsapp.inbound;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Free-form session replies (inside the customer-service window opened by the user's own message). The provider is
 * selected by app.whatsapp.provider: "meta" uses the Cloud API, "mock" captures replies in memory (dev/tests only;
 * refused in production by WhatsAppProductionGuard), and anything else ("disabled") sends nothing and reports FAILED.
 */
@Component
public class WhatsAppReplySender {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppReplySender.class);
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    public record Sent(String status, String providerMessageId) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Outbound(String to, String body) { }

    private final String provider;
    private final String baseUrl;
    private final String apiVersion;
    private final String phoneNumberId;
    private final String accessToken;
    private final int timeoutSeconds;
    private final HttpClient client;
    private final List<Outbound> mockOutbox = Collections.synchronizedList(new ArrayList<>());

    public WhatsAppReplySender(@Value("${app.whatsapp.provider:mock}") String provider,
                               @Value("${app.whatsapp.meta.base-url:https://graph.facebook.com}") String baseUrl,
                               @Value("${app.whatsapp.meta.api-version:v21.0}") String apiVersion,
                               @Value("${app.whatsapp.meta.phone-number-id:}") String phoneNumberId,
                               @Value("${app.whatsapp.meta.access-token:}") String accessToken,
                               @Value("${app.whatsapp.meta.timeout-seconds:15}") int timeoutSeconds) {
        this.provider = provider;
        this.baseUrl = baseUrl;
        this.apiVersion = apiVersion;
        this.phoneNumberId = phoneNumberId;
        this.accessToken = accessToken;
        this.timeoutSeconds = Math.clamp(timeoutSeconds, 1, 30);
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(this.timeoutSeconds))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public Sent send(String e164, String body) {
        String text = body.length() > 1500 ? body.substring(0, 1497) + "..." : body;
        if ("mock".equals(provider)) {
            mockOutbox.add(new Outbound(e164, text));
            return new Sent("SENT", "mock-reply-" + UUID.randomUUID());
        }
        if (!"meta".equals(provider)) {
            log.warn("WhatsApp reply not sent: no WhatsApp provider is configured");
            return new Sent("FAILED", null);
        }
        if (phoneNumberId == null || phoneNumberId.isBlank() || accessToken == null || accessToken.isBlank()) {
            return new Sent("FAILED", null);
        }
        try {
            String payload = JSON.writeValueAsString(Map.of("messaging_product", "whatsapp", "recipient_type", "individual",
                    "to", e164.startsWith("+") ? e164.substring(1) : e164, "type", "text",
                    "text", Map.of("preview_url", false, "body", text)));
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/" + apiVersion + "/" + phoneNumberId + "/messages"))
                    .timeout(Duration.ofSeconds(timeoutSeconds)).header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + accessToken).POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("WhatsApp reply rejected with HTTP {}", response.statusCode());
                return new Sent("FAILED", null);
            }
            var node = JSON.readTree(response.body()).path("messages").path(0).path("id");
            return new Sent("SENT", node.isMissingNode() ? null : node.asString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Sent("FAILED", null);
        } catch (Exception e) {
            log.warn("WhatsApp reply failed: {}", e.getClass().getSimpleName());
            return new Sent("FAILED", null);
        }
    }

    /** Test/dev visibility of mock replies only. */
    public List<Outbound> mockOutbox() {
        return List.copyOf(mockOutbox);
    }
}
