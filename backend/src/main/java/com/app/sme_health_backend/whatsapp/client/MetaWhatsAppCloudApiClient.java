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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MetaWhatsAppCloudApiClient implements WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(MetaWhatsAppCloudApiClient.class);
    private static final int MAX_ATTEMPTS = 2;

    private final String baseUrl;
    private final String apiVersion;
    private final String phoneNumberId;
    private final String accessToken;
    private final int timeoutSeconds;
    private final String defaultTemplateName;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public MetaWhatsAppCloudApiClient(
            String baseUrl,
            String apiVersion,
            String phoneNumberId,
            String accessToken,
            int timeoutSeconds,
            String defaultTemplateName,
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
        this.defaultTemplateName = (defaultTemplateName != null && !defaultTemplateName.isBlank())
                ? defaultTemplateName.trim()
                : "financial_health_weekly_summary_v1";
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(this.timeoutSeconds))
                .build();
    }

    public MetaWhatsAppCloudApiClient(
            String baseUrl,
            String apiVersion,
            String phoneNumberId,
            String accessToken,
            int timeoutSeconds,
            ObjectMapper objectMapper
    ) {
        this(baseUrl, apiVersion, phoneNumberId, accessToken, timeoutSeconds, "financial_health_weekly_summary_v1", objectMapper);
    }

    // Constructor with injected HttpClient for unit testing
    public MetaWhatsAppCloudApiClient(
            String baseUrl,
            String apiVersion,
            String phoneNumberId,
            String accessToken,
            int timeoutSeconds,
            String defaultTemplateName,
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
        this.defaultTemplateName = (defaultTemplateName != null && !defaultTemplateName.isBlank())
                ? defaultTemplateName.trim()
                : "financial_health_weekly_summary_v1";
        this.httpClient = httpClient;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public MetaWhatsAppCloudApiClient(
            String baseUrl,
            String apiVersion,
            String phoneNumberId,
            String accessToken,
            int timeoutSeconds,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this(baseUrl, apiVersion, phoneNumberId, accessToken, timeoutSeconds, "financial_health_weekly_summary_v1", httpClient, objectMapper);
    }

    @Override
    public WhatsAppSendResult sendSummary(WhatsAppSendRequest request) {
        String maskedNumber = PhoneNumberValidator.mask(request.destinationNumber());
        String targetUrl = String.format("%s/%s/%s/messages", baseUrl, apiVersion, phoneNumberId);

        try {
            Map<String, Object> templateMap = new LinkedHashMap<>();
            String templateName = (request.templateName() != null && !request.templateName().isBlank())
                    ? request.templateName()
                    : defaultTemplateName;
            templateMap.put("name", templateName);

            String langCode = (request.language() != null && !request.language().isBlank())
                    ? request.language()
                    : "en";
            templateMap.put("language", Map.of("code", langCode));

            List<Map<String, String>> parameterObjects = new ArrayList<>();
            if (request.templateParameters() != null && !request.templateParameters().isEmpty()) {
                for (String param : request.templateParameters()) {
                    parameterObjects.add(Map.of("type", "text", "text", param != null ? param : ""));
                }
            } else if (request.messageBody() != null && !request.messageBody().isBlank()) {
                parameterObjects.add(Map.of("type", "text", "text", request.messageBody()));
            }

            if (!parameterObjects.isEmpty()) {
                templateMap.put("components", List.of(
                        Map.of("type", "body", "parameters", parameterObjects)
                ));
            }

            Map<String, Object> payload = Map.of(
                    "messaging_product", "whatsapp",
                    "recipient_type", "individual",
                    "to", request.destinationNumber(),
                    "type", "template",
                    "template", templateMap
            );

            String requestBodyJson = objectMapper.writeValueAsString(payload);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                    .build();

            int attempt = 0;
            while (attempt < MAX_ATTEMPTS) {
                attempt++;
                try {
                    log.info("MetaWhatsAppCloudApiClient: Posting template summary to {} via {} (attempt {}/{})",
                            maskedNumber, targetUrl, attempt, MAX_ATTEMPTS);

                    HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                    int statusCode = response.statusCode();

                    if (statusCode >= 200 && statusCode < 300) {
                        String responseBody = response.body();
                        String messageId = extractMessageId(responseBody);
                        log.info("MetaWhatsAppCloudApiClient: Message accepted with id {} for {} (attempt {})",
                                messageId, maskedNumber, attempt);
                        return WhatsAppSendResult.sent(messageId, getProviderName(), attempt);
                    }

                    // Ambiguous gateway timeouts: DO NOT blind retry, mark INDETERMINATE immediately
                    if (statusCode == 502 || statusCode == 504) {
                        log.warn("MetaWhatsAppCloudApiClient: Ambiguous HTTP {} gateway timeout for {}: marking INDETERMINATE",
                                statusCode, maskedNumber);
                        return WhatsAppSendResult.indeterminate(
                                "Gateway timeout (HTTP " + statusCode + "): ambiguous provider delivery status",
                                getProviderName(),
                                attempt
                        );
                    }

                    // Bounded retry for transient 500 / 503 server errors
                    if ((statusCode == 500 || statusCode == 503) && attempt < MAX_ATTEMPTS) {
                        log.warn("MetaWhatsAppCloudApiClient: Transient HTTP {} for {}, retrying (attempt {}/{})",
                                statusCode, maskedNumber, attempt, MAX_ATTEMPTS);
                        continue;
                    }

                    // 4xx (client error / authentication / template rejection) or exhausted 5xx -> permanent FAILED
                    String errorReason = extractErrorMessage(response.body(), statusCode);
                    log.error("MetaWhatsAppCloudApiClient: Provider rejected request with HTTP {}: {}", statusCode, errorReason);
                    return WhatsAppSendResult.failed(
                            "Provider rejected (HTTP " + statusCode + "): " + errorReason,
                            getProviderName(),
                            attempt
                    );

                } catch (ConnectException e) {
                    // Definitive connection failure before request acceptance: transient retry allowed
                    if (attempt < MAX_ATTEMPTS) {
                        log.warn("MetaWhatsAppCloudApiClient: Connection failure before acceptance for {}, retrying (attempt {}/{}): {}",
                                maskedNumber, attempt, MAX_ATTEMPTS, e.getMessage());
                        continue;
                    }
                    log.error("MetaWhatsAppCloudApiClient: Connection failure to Meta Graph API for {} after {} attempts: {}",
                            maskedNumber, attempt, e.getMessage());
                    return WhatsAppSendResult.failed("Connection refused: " + e.getMessage(), getProviderName(), attempt);
                } catch (HttpTimeoutException e) {
                    // Timeout where acceptance is ambiguous: INDETERMINATE, NO blind retry
                    log.warn("MetaWhatsAppCloudApiClient: Ambiguous timeout sending WhatsApp message to {}: marking as INDETERMINATE without blind retry",
                            maskedNumber, e);
                    return WhatsAppSendResult.indeterminate(
                            "Provider request timed out after " + timeoutSeconds + "s: ambiguous delivery status",
                            getProviderName(),
                            attempt
                    );
                } catch (IOException e) {
                    log.warn("MetaWhatsAppCloudApiClient: IO error communicating with provider for {}: marking INDETERMINATE: {}",
                            maskedNumber, e.getMessage());
                    return WhatsAppSendResult.indeterminate("IO error: " + e.getMessage(), getProviderName(), attempt);
                }
            }

            return WhatsAppSendResult.failed("Retry attempts exhausted", getProviderName(), attempt);

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
