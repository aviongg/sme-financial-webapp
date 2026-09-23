package com.app.sme_health_backend.documents.ocr;

import org.springframework.core.env.Environment;

import java.net.URI;
import java.time.Duration;

public record OcrClientSettings(URI extractEndpoint, Duration connectTimeout, Duration requestTimeout,
                                int maxResponseBytes) {
    public OcrClientSettings {
        if (extractEndpoint == null || extractEndpoint.getHost() == null
                || !("http".equalsIgnoreCase(extractEndpoint.getScheme())
                || "https".equalsIgnoreCase(extractEndpoint.getScheme()))
                || extractEndpoint.getUserInfo() != null || extractEndpoint.getFragment() != null
                || extractEndpoint.getQuery() != null) {
            throw new IllegalArgumentException("OCR_SERVICE_URL must be HTTP(S), without credentials, query or fragment");
        }
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()
                || requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()
                || maxResponseBytes < 1) {
            throw new IllegalArgumentException("OCR timeouts and response limit must be positive");
        }
        try {
            requestTimeout.toNanos();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("OCR request timeout exceeds the supported duration");
        }
    }

    public static OcrClientSettings from(Environment environment) {
        String base = environment.getProperty("OCR_SERVICE_URL", "http://127.0.0.1:8001");
        URI service = URI.create(base);
        // Validate the base first so a query/fragment cannot swallow the appended path.
        Duration connect = Duration.ofSeconds(environment.getProperty("OCR_CONNECT_TIMEOUT_SECONDS", Long.class, 5L));
        Duration request = Duration.ofSeconds(environment.getProperty("OCR_REQUEST_TIMEOUT_SECONDS", Long.class, 90L));
        int limit = environment.getProperty("OCR_MAX_RESPONSE_BYTES", Integer.class, 65_536);
        new OcrClientSettings(service, connect, request, limit);
        return new OcrClientSettings(URI.create(base.replaceAll("/+$", "") + "/extract"), connect, request, limit);
    }
}
