package com.app.sme_health_backend.documents.ocr;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JdkOcrHttpTransportTests {
    @Test
    void fullResponseDeadlineCancelsTheUnderlyingRequest() {
        HttpClient http = mock(HttpClient.class);
        CompletableFuture<HttpResponse<byte[]>> response = new CompletableFuture<>();
        when(http.<byte[]>sendAsync(any(), any())).thenReturn(response);
        JdkOcrHttpTransport transport = new JdkOcrHttpTransport(http);

        assertThrows(HttpTimeoutException.class, () -> transport.postMultipart(
                URI.create("http://127.0.0.1:8001/extract"),
                "boundary123",
                new byte[0],
                "my-secret",
                Duration.ofMillis(5),
                1024
        ));
        assertTrue(response.isCancelled());
        verify(http, times(1)).sendAsync(any(), any());
    }

    @Test
    void sendsExpectedMultipartHeadersAndAuthKey() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<byte[]> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(java.util.Map.of("Content-Type", List.of("application/json")), (a, b) -> true));
        when(mockResponse.body()).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        CompletableFuture<HttpResponse<byte[]>> future = CompletableFuture.completedFuture(mockResponse);

        JdkOcrHttpTransport transport = new JdkOcrHttpTransport(http);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        when(http.<byte[]>sendAsync(requestCaptor.capture(), any())).thenReturn(future);

        transport.postMultipart(
                URI.create("http://127.0.0.1:8001/extract"),
                "my-boundary",
                new byte[]{1, 2},
                "my-ocr-secret",
                Duration.ofSeconds(5),
                1024
        );

        HttpRequest sent = requestCaptor.getValue();
        assertEquals("POST", sent.method());
        assertEquals("multipart/form-data; boundary=my-boundary", sent.headers().firstValue("Content-Type").orElse(null));
        assertEquals("application/json", sent.headers().firstValue("Accept").orElse(null));
        assertEquals("my-ocr-secret", sent.headers().firstValue("X-OCR-Service-Key").orElse(null));
    }

    @Test
    void cancelsOversizedResponseBeforeBufferingTheOversizedChunk() {
        Flow.Subscription subscription = mock(Flow.Subscription.class);
        var subscriber = new JdkOcrHttpTransport.BoundedBodySubscriber(4);
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] {1, 2})));
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] {3, 4, 5})));
        subscriber.onComplete();
        assertThrows(CompletionException.class, () -> subscriber.getBody().toCompletableFuture().join());
        verify(subscription).cancel();
    }

    @Test
    void acceptsAnExactlyBoundedBodyAcrossChunks() {
        var subscriber = new JdkOcrHttpTransport.BoundedBodySubscriber(4);
        subscriber.onSubscribe(mock(Flow.Subscription.class));
        subscriber.onNext(List.of(ByteBuffer.wrap("ab".getBytes(StandardCharsets.UTF_8))));
        subscriber.onNext(List.of(ByteBuffer.wrap("cd".getBytes(StandardCharsets.UTF_8))));
        subscriber.onComplete();
        assertArrayEquals("abcd".getBytes(StandardCharsets.UTF_8), subscriber.getBody().toCompletableFuture().join());
    }
}
