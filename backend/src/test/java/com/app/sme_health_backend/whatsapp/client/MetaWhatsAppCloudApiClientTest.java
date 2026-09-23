package com.app.sme_health_backend.whatsapp.client;

import com.app.sme_health_backend.whatsapp.entity.WhatsAppDeliveryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetaWhatsAppCloudApiClientTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private MetaWhatsAppCloudApiClient client;

    @BeforeEach
    void setUp() {
        client = new MetaWhatsAppCloudApiClient(
                "https://graph.facebook.com",
                "v21.0",
                "phone-id-123",
                "meta-token-xyz",
                10,
                httpClient,
                new ObjectMapper()
        );
    }

    @Test
    void shouldReturnSentWhenProviderAccepts() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"messages\":[{\"id\":\"wamid.HBg123\"}]}");
        doReturn(httpResponse).when(httpClient).send(any(), any());

        WhatsAppSendRequest request = new WhatsAppSendRequest(
                "+923001234567",
                "Hello Test",
                "en",
                "test-tpl"
        );

        WhatsAppSendResult result = client.sendSummary(request);

        assertTrue(result.success());
        assertEquals(WhatsAppDeliveryStatus.SENT, result.status());
        assertEquals("wamid.HBg123", result.providerMessageId());
        assertEquals("meta", result.providerName());
    }

    @Test
    void shouldReturnFailedWhenProviderReturns4xx() throws Exception {
        when(httpResponse.statusCode()).thenReturn(400);
        when(httpResponse.body()).thenReturn("{\"error\":{\"message\":\"Invalid phone number\",\"code\":100}}");
        doReturn(httpResponse).when(httpClient).send(any(), any());

        WhatsAppSendRequest request = new WhatsAppSendRequest(
                "+923001234567",
                "Hello Test",
                "en",
                "test-tpl"
        );

        WhatsAppSendResult result = client.sendSummary(request);

        assertFalse(result.success());
        assertEquals(WhatsAppDeliveryStatus.FAILED, result.status());
        assertTrue(result.failureReason().contains("Invalid phone number"));
    }

    @Test
    void shouldReturnIndeterminateOnHttpTimeoutException() throws Exception {
        doThrow(new HttpTimeoutException("Request timed out"))
                .when(httpClient).send(any(), any());

        WhatsAppSendRequest request = new WhatsAppSendRequest(
                "+923001234567",
                "Hello Test",
                "en",
                "test-tpl"
        );

        WhatsAppSendResult result = client.sendSummary(request);

        assertFalse(result.success());
        assertEquals(WhatsAppDeliveryStatus.INDETERMINATE, result.status());
        assertTrue(result.failureReason().contains("timed out"));
    }

    @Test
    void shouldReturnIndeterminateOnGateway504() throws Exception {
        when(httpResponse.statusCode()).thenReturn(504);
        doReturn(httpResponse).when(httpClient).send(any(), any());

        WhatsAppSendRequest request = new WhatsAppSendRequest(
                "+923001234567",
                "Hello Test",
                "en",
                "test-tpl"
        );

        WhatsAppSendResult result = client.sendSummary(request);

        assertFalse(result.success());
        assertEquals(WhatsAppDeliveryStatus.INDETERMINATE, result.status());
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
