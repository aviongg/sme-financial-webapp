package com.app.sme_health_backend.documents.ocr;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

/** Injectable transport keeps unit tests offline. Implementations must enforce timeout/body limits. */
@FunctionalInterface
public interface OcrHttpTransport {
    Response post(URI endpoint, byte[] requestBody, Duration timeout, int maxResponseBytes)
            throws IOException, InterruptedException;

    record Response(int statusCode, String contentType, byte[] body) { }
}
