package com.app.sme_health_backend.documents.ocr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HttpOcrClientTests {
    @Mock OcrHttpTransport transport;
    private HttpOcrClient client;
    private final UUID docId = UUID.randomUUID();
    private final OcrRequest request = new OcrRequest(docId, new byte[]{1, 2, 3}, "invoice.pdf", "application/pdf", OcrExtraction.DocumentType.invoice);
    private final OcrClientSettings settings = new OcrClientSettings(URI.create("http://127.0.0.1:8001/extract"),
            Duration.ofSeconds(5), Duration.ofSeconds(90), 65_536, "test-secret");

    @BeforeEach
    void setUp() {
        client = new HttpOcrClient(settings, transport, new OcrJsonCodec());
    }

    @Test
    void postsMultipartRequestToConfiguredEndpointWithConfiguredLimits() throws Exception {
        when(transport.postMultipart(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(response(200, "application/json; charset=utf-8", OcrJsonCodecTests.PARTIAL));
        OcrExtraction result = client.extract(request);
        assertNull(result.vendorOrParty());
        assertEquals(OcrExtraction.DocumentType.invoice, result.documentTypeDetected());
        verify(transport).postMultipart(eq(settings.extractEndpoint()), anyString(), any(byte[].class),
                eq("test-secret"), eq(Duration.ofSeconds(90)), eq(65_536));
        verifyNoMoreInteractions(transport);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 422, 429, 500, 502, 503, 504, 301})
    void propagatesTerminalFailureWithoutRetryingAtJavaLayer(int status) throws Exception {
        when(transport.postMultipart(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(response(status, "application/json", "private upstream detail"));
        OcrClientException exception = assertThrows(OcrClientException.class, () -> client.extract(request));
        assertEquals(status == 400 || status == 422 ? OcrClientException.Reason.invalid_request
                : OcrClientException.Reason.unavailable, exception.reason());
        assertFalse(exception.getMessage().contains("private"));
        verify(transport, times(1)).postMultipart(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void connectionFailureDoesNotTriggerAnotherProviderAttempt() throws Exception {
        when(transport.postMultipart(any(), any(), any(), any(), any(), anyInt()))
                .thenThrow(new IOException("secret URL"));
        OcrClientException exception = assertThrows(OcrClientException.class, () -> client.extract(request));
        assertEquals(OcrClientException.Reason.unavailable, exception.reason());
        assertFalse(exception.getMessage().contains("secret"));
        verify(transport, times(1)).postMultipart(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void interruptedRequestRestoresThreadInterrupt() throws Exception {
        when(transport.postMultipart(any(), any(), any(), any(), any(), anyInt()))
                .thenThrow(new InterruptedException());
        try {
            assertThrows(OcrClientException.class, () -> client.extract(request));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void invalidJsonResponseFailsWithoutRetry() throws Exception {
        when(transport.postMultipart(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(response(200, "application/json", "{}"));
        assertEquals(OcrClientException.Reason.invalid_response,
                assertThrows(OcrClientException.class, () -> client.extract(request)).reason());
        verify(transport, times(1)).postMultipart(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void rejectsWrongContentTypeAndOversizedResponse() throws Exception {
        when(transport.postMultipart(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(response(200, "text/html", OcrJsonCodecTests.PARTIAL))
                .thenReturn(response(200, "application/json", " ".repeat(65_537)));
        assertThrows(OcrClientException.class, () -> client.extract(request));
        assertThrows(OcrClientException.class, () -> client.extract(request));
    }

    private OcrHttpTransport.Response response(int status, String contentType, String body) {
        return new OcrHttpTransport.Response(status, contentType, body.getBytes(StandardCharsets.UTF_8));
    }
}
