package com.app.sme_health_backend.whatsapp.client;

import com.app.sme_health_backend.whatsapp.entity.WhatsAppDeliveryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MockWhatsAppClientTest {

    private MockWhatsAppClient client;

    @BeforeEach
    void setUp() {
        client = new MockWhatsAppClient();
    }

    @Test
    void shouldReturnSentByDefault() {
        WhatsAppSendRequest request = new WhatsAppSendRequest(
                "+923001234567",
                "Summary content",
                "en",
                "default"
        );

        WhatsAppSendResult result = client.sendSummary(request);

        assertTrue(result.success());
        assertEquals(WhatsAppDeliveryStatus.SENT, result.status());
        assertNotNull(result.providerMessageId());
        assertTrue(result.providerMessageId().startsWith("mock-msg-"));
        assertEquals("mock", result.providerName());
    }

    @Test
    void shouldReturnFailedWhenConfiguredOrEndingWith9999() {
        WhatsAppSendRequest request = new WhatsAppSendRequest(
                "+923001239999",
                "Summary content",
                "en",
                "default"
        );

        WhatsAppSendResult result = client.sendSummary(request);

        assertFalse(result.success());
        assertEquals(WhatsAppDeliveryStatus.FAILED, result.status());
    }

    @Test
    void shouldReturnIndeterminateWhenConfiguredOrEndingWith0000() {
        WhatsAppSendRequest request = new WhatsAppSendRequest(
                "+923001230000",
                "Summary content",
                "en",
                "default"
        );

        WhatsAppSendResult result = client.sendSummary(request);

        assertFalse(result.success());
        assertEquals(WhatsAppDeliveryStatus.INDETERMINATE, result.status());
    }
}
