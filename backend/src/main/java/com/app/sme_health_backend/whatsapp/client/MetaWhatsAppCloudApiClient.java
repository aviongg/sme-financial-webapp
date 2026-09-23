package com.app.sme_health_backend.whatsapp.client;

import com.app.sme_health_backend.whatsapp.validation.PhoneNumberValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

public class MetaWhatsAppCloudApiClient implements WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(MetaWhatsAppCloudApiClient.class);

    private final String baseUrl;
    private final String apiVersion;
    private final String phoneNumberId;
    private final String accessToken;
    private final int timeoutSeconds;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public MetaWhatsAppCloudApiClient(
            String baseUrl,
            String apiVersion,
            String phoneNumberId,
            String accessToken,
            int timeoutSeconds,
            ObjectMapper objectMapper
    ) {
        if (phoneNumberId == null || phoneNumberId.isBlank()) {
            throw new IllegalArgumentException("Meta WhatsApp phoneNumberId is required when provider=meta");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Meta WhatsApp accessToken is required when provider=meta");
        }

        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.replaceAll("/+$", "") : "https://graph.facebook.com";
        this.apiVersion = (apiVersion != null && !apiVersion.isBlank()) ? apiVersion : "v21.0";
        this.phoneNumberId = phoneNumberId.trim();
        this.accessToken = accessToken.trim();
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 15;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(this.timeoutSeconds))
                .build();
    }

    // Constructor with injected HttpClient for unit testing
    public MetaWhatsAppCloudApiClient(
            String baseUrl,
            String apiVersion,
            String phoneNumberId,
            String accessToken,
            int timeoutSeconds,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        if (phoneNumberId == null || phoneNumberId.isBlank()) {
            throw new IllegalArgumentException("Meta WhatsApp phoneNumberId is required when provider=meta");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Meta WhatsApp accessToken is required when provider=meta");
        }

        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.replaceAll("/+$", "") : "https://graph.facebook.com";
        this.apiVersion = (apiVersion != null && !apiVersion.isBlank()) ? apiVersion : "v21.0";
        this.phoneNumberId = phoneNumberId.trim();
        this.accessToken = accessToken.trim();
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 15;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public WhatsAppSendResult sendSummary(WhatsAppSendRequest request) {
        String maskedNumber = PhoneNumberValidator.mask(request.destinationNumber());
        String targetUrl = String.format("%s/%s/%s/messages", baseUrl, apiVersion, phoneNumberId);

        try {
            Map<String, Object> payload = Map.of(
                    "messaging_product", "whatsapp",
                    "recipient_type", "individual",
                    "to", request.destinationNumber(),
                    "type", "text",
                    "text", Map.of(
                            "preview_url", false,
                            "body", request.messageBody()
                    )
            );

            String requestBodyJson = objectMapper.writeValueAsString(payload);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                    .build();

            log.info("MetaWhatsAppCloudApiClient: Posting summary to {} via {}", maskedNumber, targetUrl);

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();

            if (statusCode >= 200 && statusCode < 300) {
                String responseBody = response.body();
                String messageId = extractMessageId(responseBody);
                log.info("MetaWhatsAppCloudApiClient: Message accepted with id {} for {}", messageId, maskedNumber);
                return WhatsAppSendResult.sent(messageId, getProviderName());
            } else if (statusCode == 504 || statusCode == 502) {
                log.warn("MetaWhatsAppCloudApiClient: Ambiguous HTTP {} gateway timeout for {}", statusCode, maskedNumber);
                return WhatsAppSendResult.indeterminate(
                        "Gateway timeout (HTTP " + statusCode + "): ambiguous provider delivery status",
                        getProviderName()
                );
            } else {
                String errorReason = extractErrorMessage(response.body(), statusCode);
                log.error("MetaWhatsAppCloudApiClient: Provider rejected request with HTTP {}: {}", statusCode, errorReason);
                return WhatsAppSendResult.failed(
                        "Provider rejected (HTTP " + statusCode + "): " + errorReason,
                        getProviderName()
                );
            }
        } catch (HttpTimeoutException e) {
            log.warn("MetaWhatsAppCloudApiClient: Ambiguous timeout sending WhatsApp message to {}: marking as INDETERMINATE without blind retry",
                    maskedNumber, e);
            return WhatsAppSendResult.indeterminate(
                    "Provider request timed out after " + timeoutSeconds + "s: ambiguous delivery status",
                    getProviderName()
            );
        } catch (ConnectException e) {
            log.error("MetaWhatsAppCloudApiClient: Connection refused to Meta Graph API for {}: {}", maskedNumber, e.getMessage());
            return WhatsAppSendResult.failed("Connection refused: " + e.getMessage(), getProviderName());
        } catch (IOException e) {
            log.warn("MetaWhatsAppCloudApiClient: IO error communicating with provider for {}: marking INDETERMINATE: {}",
                    maskedNumber, e.getMessage());
            return WhatsAppSendResult.indeterminate("IO error: " + e.getMessage(), getProviderName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("MetaWhatsAppCloudApiClient: Thread interrupted while sending to {}", maskedNumber, e);
            return WhatsAppSendResult.failed("Thread interrupted: " + e.getMessage(), getProviderName());
        } catch (Exception e) {
            log.error("MetaWhatsAppCloudApiClient: Unexpected error sending to {}", maskedNumber, e);
            return WhatsAppSendResult.failed("Unexpected error: " + e.getMessage(), getProviderName());
        }
    }

    private String extractMessageId(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode messages = root.path("messages");
            if (messages.isArray() && !messages.isEmpty()) {
                String id = messages.get(0).path("id").asText();
                if (id != null && !id.isBlank()) {
                    return id;
                }
            }
        } catch (Exception e) {
            log.warn("Could not parse message id from response body: {}", responseBody);
        }
        return "meta-accepted-" + System.currentTimeMillis();
    }

    private String extractErrorMessage(String responseBody, int statusCode) {
        if (responseBody == null || responseBody.isBlank()) {
            return "HTTP " + statusCode;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode error = root.path("error");
            if (!error.isMissingNode()) {
                String message = error.path("message").asText();
                int code = error.path("code").asInt();
                return String.format("%s (code: %d)", message, code);
            }
        } catch (Exception ignored) {
        }
        return responseBody.length() > 200 ? responseBody.substring(0, 200) : responseBody;
    }

    @Override
    public String getProviderName() {
        return "meta";
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public String getPhoneNumberId() {
        return phoneNumberId;
    }
}
