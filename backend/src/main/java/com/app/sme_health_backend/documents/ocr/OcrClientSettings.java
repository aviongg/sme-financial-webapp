package com.app.sme_health_backend.documents.ocr;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
        String configuredFile = environment.getProperty("OCR_SERVICE_KEY_FILE");
        // Explicit secret-file configuration is authoritative. A broken mount must
        // not silently fall back to an unrelated legacy environment secret.
        if (configuredFile != null) {
            if (configuredFile.isBlank()) {
                throw new IllegalStateException("OCR_SERVICE_KEY_FILE must name a readable, non-empty secret file");
            }
            secret = readSecret(Path.of(configuredFile));
        } else if (secret == null || secret.isBlank()) {
            Path defaultFile = Path.of("/run/secrets/ocr_service_key");
            if (Files.exists(defaultFile)) {
                secret = readSecret(defaultFile);
            }
        }
        if (environment.acceptsProfiles(Profiles.of("prod", "production"))
                && (secret == null || secret.isBlank())) {
            throw new IllegalStateException("A production OCR service key is required");
        }
        new OcrClientSettings(service, connect, request, limit, secret);
        return new OcrClientSettings(URI.create(base.replaceAll("/+$", "") + "/extract"), connect, request, limit, secret);
    }

    private static String readSecret(Path path) {
        try {
            String value = Files.readString(path, StandardCharsets.UTF_8).strip();
            if (value.isEmpty()) {
                throw new IllegalStateException("OCR service key file must not be empty");
            }
            return value;
        } catch (java.io.IOException exception) {
            // Avoid exposing path details or secret content through configuration logs.
            throw new IllegalStateException("OCR service key file cannot be read");
        }
    }
}
