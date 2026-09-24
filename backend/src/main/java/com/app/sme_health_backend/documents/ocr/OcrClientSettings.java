package com.app.sme_health_backend.documents.ocr;

import org.springframework.core.env.Environment;

import java.net.URI;
import java.time.Duration;

public record OcrClientSettings(
        URI extractEndpoint,
        Duration connectTimeout,
        Duration requestTimeout,
        int maxResponseBytes,
        String serviceSecret
) {
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
        serviceSecret = (serviceSecret == null) ? "" : serviceSecret.strip();
    }

    public OcrClientSettings(URI extractEndpoint, Duration connectTimeout, Duration requestTimeout, int maxResponseBytes) {
        this(extractEndpoint, connectTimeout, requestTimeout, maxResponseBytes, "");
    }

    public static OcrClientSettings from(Environment environment) {
        String base = environment.getProperty("OCR_SERVICE_URL", "http://127.0.0.1:8001");
        URI service = URI.create(base);
        Duration connect = Duration.ofSeconds(environment.getProperty("OCR_CONNECT_TIMEOUT_SECONDS", Long.class, 5L));
        Duration request = Duration.ofSeconds(environment.getProperty("OCR_REQUEST_TIMEOUT_SECONDS", Long.class, 90L));
        int limit = environment.getProperty("OCR_MAX_RESPONSE_BYTES", Integer.class, 65_536);
        String secret = environment.getProperty("OCR_SERVICE_SECRET",
                environment.getProperty("INTERNAL_SERVICE_SECRET",
                        environment.getProperty("app.security.internal-service-secret", "")));
        if (secret == null || secret.isBlank()) {
            java.nio.file.Path secretPath = java.nio.file.Paths.get("/run/secrets/ocr_service_key");
            if (java.nio.file.Files.exists(secretPath)) {
                try {
                    secret = java.nio.file.Files.readString(secretPath, java.nio.charset.StandardCharsets.UTF_8).trim();
                } catch (java.io.IOException ignored) {
                }
            }
        }
        new OcrClientSettings(service, connect, request, limit, secret);
        return new OcrClientSettings(URI.create(base.replaceAll("/+$", "") + "/extract"), connect, request, limit, secret);
    }
}
