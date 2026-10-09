package com.app.sme_health_backend.whatsapp.client;

import com.app.sme_health_backend.whatsapp.entity.WhatsAppDeliveryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetaWhatsAppCloudApiClientTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    @Mock
    private HttpResponse<String> httpResponseRetry;

    private MetaWhatsAppCloudApiClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        client = new MetaWhatsAppCloudApiClient(
                "https://graph.facebook.com",
                "v21.0",
                "phone-id-123",
                "meta-token-xyz",
                10,
                "financial_health_weekly_summary_v1",
                httpClient,
                objectMapper
        );
    }

    private static String extractRequestBody(HttpRequest request) {
        if (request.bodyPublisher().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        request.bodyPublisher().get().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                sb.append(new String(bytes, StandardCharsets.UTF_8));
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });
        return sb.toString();
    }

    @Test
    void shouldSendMetaTemplatePayloadWithDynamicParameters() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"messages\":[{\"id\":\"wamid.HBg123\"}]}");
        doReturn(httpResponse).when(httpClient).send(any(), any());

        List<String> dynamicParams = List.of(
                "2026-09",
                "Acme Traders",
                "78",
                "Strong",
                "Cash Flow",
                "Cash flow improved.",
                "Maintain buffer.",
                "https://sme.app"
        );

        WhatsAppSendRequest request = new WhatsAppSendRequest(
                "+923001234567",
                "Preview Text",
                "en",
                "financial_health_weekly_summary_v1",
                dynamicParams
        );

        WhatsAppSendResult result = client.sendSummary(request);

        assertTrue(result.success());
        assertEquals(WhatsAppDeliveryStatus.SENT, result.status());
        assertEquals("wamid.HBg123", result.providerMessageId());
        assertEquals("meta", result.providerName());
        assertEquals(1, result.attempts());

        // Verify captured HTTP request contains template payload
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());

        HttpRequest captured = captor.getValue();
        assertEquals("POST", captured.method());
        assertEquals("https://graph.facebook.com/v21.0/phone-id-123/messages", captured.uri().toString());
        assertTrue(captured.headers().firstValue("Authorization").orElse("").contains("Bearer meta-token-xyz"));

        String bodyJson = extractRequestBody(captured);
        JsonNode root = objectMapper.readTree(bodyJson);

        assertEquals("whatsapp", root.path("messaging_product").asText());
        assertEquals("+923001234567", root.path("to").asText());
        assertEquals("template", root.path("type").asText());

        JsonNode templateNode = root.path("template");
        assertEquals("financial_health_weekly_summary_v1", templateNode.path("name").asText());
        assertEquals("en", templateNode.path("language").path("code").asText());

        JsonNode components = templateNode.path("components");
        assertTrue(components.isArray());
        assertEquals(1, components.size());
        assertEquals("body", components.get(0).path("type").asText());

        JsonNode parameters = components.get(0).path("parameters");
        assertTrue(parameters.isArray());
        assertEquals(8, parameters.size());
        assertEquals("2026-09", parameters.get(0).path("text").asText());
        assertEquals("Acme Traders", parameters.get(1).path("text").asText());
        assertEquals("78", parameters.get(2).path("text").asText());
        assertEquals("Strong", parameters.get(3).path("text").asText());
        assertEquals("Cash Flow", parameters.get(4).path("text").asText());
        assertEquals("https://sme.app", parameters.get(7).path("text").asText());
    }

    @Test
    void shouldRetryOnConnectExceptionAndSucceedOnSecondAttempt() throws Exception {
        // Attempt 1 throws ConnectException, Attempt 2 succeeds with 200
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"messages\":[{\"id\":\"wamid.HBgRetry\"}]}");

        doThrow(new ConnectException("Connection refused"))
                .doReturn(httpResponse)
                .when(httpClient).send(any(), any());

        WhatsAppSendRequest request = new WhatsAppSendRequest("+923001234567", "Hello", "en", "tpl");
        WhatsAppSendResult result = client.sendSummary(request);

        assertTrue(result.success());
        assertEquals(WhatsAppDeliveryStatus.SENT, result.status());
        assertEquals("wamid.HBgRetry", result.providerMessageId());
        assertEquals(2, result.attempts());
        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    void shouldFailWhenConnectExceptionExhaustsRetries() throws Exception {
        doThrow(new ConnectException("Connection refused"))
                .when(httpClient).send(any(), any());

        WhatsAppSendRequest request = new WhatsAppSendRequest("+923001234567", "Hello", "en", "tpl");
        WhatsAppSendResult result = client.sendSummary(request);

        assertFalse(result.success());
        assertEquals(WhatsAppDeliveryStatus.FAILED, result.status());
        assertTrue(result.failureReason().contains("Connection refused"));
        assertEquals(2, result.attempts());
        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    void shouldRetryOnHttp500AndSucceedOnSecondAttempt() throws Exception {
        when(httpResponse.statusCode()).thenReturn(500);

        when(httpResponseRetry.statusCode()).thenReturn(200);
        when(httpResponseRetry.body()).thenReturn("{\"messages\":[{\"id\":\"wamid.500Recovered\"}]}");

        doReturn(httpResponse)
                .doReturn(httpResponseRetry)
                .when(httpClient).send(any(), any());

        WhatsAppSendRequest request = new WhatsAppSendRequest("+923001234567", "Hello", "en", "tpl");
        WhatsAppSendResult result = client.sendSummary(request);

        assertTrue(result.success());
        assertEquals(WhatsAppDeliveryStatus.SENT, result.status());
        assertEquals("wamid.500Recovered", result.providerMessageId());
        assertEquals(2, result.attempts());
        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    void shouldFailWhenHttp500ExhaustsRetries() throws Exception {
        when(httpResponse.statusCode()).thenReturn(500);
        when(httpResponse.body()).thenReturn("{\"error\":{\"message\":\"Internal error\",\"code\":500}}");
        doReturn(httpResponse).when(httpClient).send(any(), any());

        WhatsAppSendRequest request = new WhatsAppSendRequest("+923001234567", "Hello", "en", "tpl");
        WhatsAppSendResult result = client.sendSummary(request);

        assertFalse(result.success());
        assertEquals(WhatsAppDeliveryStatus.FAILED, result.status());
        assertEquals(2, result.attempts());
        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    void shouldRetryOnHttp503AndSucceedOnSecondAttempt() throws Exception {
        when(httpResponse.statusCode()).thenReturn(503);

        when(httpResponseRetry.statusCode()).thenReturn(200);
        when(httpResponseRetry.body()).thenReturn("{\"messages\":[{\"id\":\"wamid.503Recovered\"}]}");

        doReturn(httpResponse)
                .doReturn(httpResponseRetry)
                .when(httpClient).send(any(), any());

        WhatsAppSendRequest request = new WhatsAppSendRequest("+923001234567", "Hello", "en", "tpl");
        WhatsAppSendResult result = client.sendSummary(request);

        assertTrue(result.success());
        assertEquals(WhatsAppDeliveryStatus.SENT, result.status());
        assertEquals(2, result.attempts());
        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    void shouldReturnIndeterminateOnHttpTimeoutWithoutBlindRetry() throws Exception {
        doThrow(new HttpTimeoutException("Request timed out"))
                .when(httpClient).send(any(), any());

        WhatsAppSendRequest request = new WhatsAppSendRequest("+923001234567", "Hello", "en", "tpl");
        WhatsAppSendResult result = client.sendSummary(request);

        assertFalse(result.success());
        assertEquals(WhatsAppDeliveryStatus.INDETERMINATE, result.status());
        assertTrue(result.failureReason().contains("timed out"));
        assertEquals(1, result.attempts());
        verify(httpClient, times(1)).send(any(), any()); // Exactly 1 attempt, NO blind retry
    }

    @Test
    void shouldReturnIndeterminateOnGateway502WithoutBlindRetry() throws Exception {
        when(httpResponse.statusCode()).thenReturn(502);
        doReturn(httpResponse).when(httpClient).send(any(), any());

        WhatsAppSendRequest request = new WhatsAppSendRequest("+923001234567", "Hello", "en", "tpl");
        WhatsAppSendResult result = client.sendSummary(request);

        assertFalse(result.success());
        assertEquals(WhatsAppDeliveryStatus.INDETERMINATE, result.status());
        assertEquals(1, result.attempts());
        verify(httpClient, times(1)).send(any(), any()); // Exactly 1 attempt
    }

    @Test
    void shouldReturnIndeterminateOnGateway504WithoutBlindRetry() throws Exception {
        when(httpResponse.statusCode()).thenReturn(504);
        doReturn(httpResponse).when(httpClient).send(any(), any());

        WhatsAppSendRequest request = new WhatsAppSendRequest("+923001234567", "Hello", "en", "tpl");
        WhatsAppSendResult result = client.sendSummary(request);

        assertFalse(result.success());
        assertEquals(WhatsAppDeliveryStatus.INDETERMINATE, result.status());
        assertEquals(1, result.attempts());
        verify(httpClient, times(1)).send(any(), any()); // Exactly 1 attempt
    }

    @Test
    void shouldReturnFailedWithoutRetryOnClientError400() throws Exception {
        when(httpResponse.statusCode()).thenReturn(400);
        when(httpResponse.body()).thenReturn("{\"error\":{\"message\":\"Invalid parameter\",\"code\":100}}");
        doReturn(httpResponse).when(httpClient).send(any(), any());

        WhatsAppSendRequest request = new WhatsAppSendRequest("+923001234567", "Hello", "en", "tpl");
        WhatsAppSendResult result = client.sendSummary(request);

        assertFalse(result.success());
        assertEquals(WhatsAppDeliveryStatus.FAILED, result.status());
        assertTrue(result.failureReason().contains("Invalid parameter"));
        assertEquals(1, result.attempts());
        verify(httpClient, times(1)).send(any(), any()); // Exactly 1 attempt, no retry
    }

    @Test
    void shouldReturnFailedWithoutRetryOnAuthenticationFailure401() throws Exception {
        when(httpResponse.statusCode()).thenReturn(401);
        when(httpResponse.body()).thenReturn("{\"error\":{\"message\":\"Invalid OAuth access token\",\"code\":190}}");
        doReturn(httpResponse).when(httpClient).send(any(), any());

        WhatsAppSendRequest request = new WhatsAppSendRequest("+923001234567", "Hello", "en", "tpl");
        WhatsAppSendResult result = client.sendSummary(request);

        assertFalse(result.success());
        assertEquals(WhatsAppDeliveryStatus.FAILED, result.status());
        assertTrue(result.failureReason().contains("Invalid OAuth"));
        assertEquals(1, result.attempts());
        verify(httpClient, times(1)).send(any(), any());
    }

    @Test
    void shouldValidateRequiredConfigWhenCreatingClient() {
        assertThrows(IllegalArgumentException.class, () -> new MetaWhatsAppCloudApiClient(
                "https://graph.facebook.com", "v21.0", "", "token", 10, new ObjectMapper()
        ));
        assertThrows(IllegalArgumentException.class, () -> new MetaWhatsAppCloudApiClient(
                "https://graph.facebook.com", "v21.0", "phone-id", null, 10, new ObjectMapper()
        ));
    }
}
