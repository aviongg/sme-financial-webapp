package com.app.sme_health_backend.documents.ocr;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class JdkOcrHttpTransport implements OcrHttpTransport {
    private final HttpClient client;

    public JdkOcrHttpTransport(HttpClient client) {
        this.client = client;
    }

    @Override
    public Response post(URI endpoint, byte[] requestBody, Duration timeout, int maxResponseBytes)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                .build();
        CompletableFuture<HttpResponse<byte[]>> pending = client.sendAsync(request,
                info -> new BoundedBodySubscriber(maxResponseBytes));
        try {
            // Cover body consumption as well as headers on every supported JDK version.
            HttpResponse<byte[]> response = pending.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            return new Response(response.statusCode(), response.headers().firstValue("Content-Type").orElse(""),
                    response.body());
        } catch (TimeoutException exception) {
            pending.cancel(true);
            throw new HttpTimeoutException("OCR response exceeded configured timeout");
        } catch (InterruptedException exception) {
            pending.cancel(true);
            throw exception;
        } catch (ExecutionException exception) {
            throw new IOException("OCR service transport failed");
        }
    }

    /** Cancels the subscription before buffering a response larger than the configured limit. */
    static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private final int maxBytes;
        private Flow.Subscription subscription;
        private long received;
        private boolean finished;

        BoundedBodySubscriber(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return delegate.getBody();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            delegate.onSubscribe(subscription);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (finished) return;
            for (ByteBuffer buffer : buffers) {
                received += buffer.remaining();
                if (received > maxBytes) {
                    finished = true;
                    subscription.cancel();
                    delegate.onError(new IOException("OCR response exceeds configured size limit"));
                    return;
                }
            }
            delegate.onNext(buffers);
        }

        @Override
        public void onError(Throwable throwable) {
            if (!finished) {
                finished = true;
                delegate.onError(throwable);
            }
        }

        @Override
        public void onComplete() {
            if (!finished) {
                finished = true;
                delegate.onComplete();
            }
        }
    }
}
