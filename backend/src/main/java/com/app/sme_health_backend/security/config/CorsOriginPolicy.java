package com.app.sme_health_backend.security.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Validates the credentialed CORS allow-list ({@code app.security.cors.allowed-origins}).
 *
 * <p>Credentials are allowed for every listed origin, so an entry must name one origin or, outside production, a
 * single left-most wildcard label under a fixed registrable suffix ({@code https://*.example.com}). Bare or broad
 * wildcards ({@code *}, {@code https://*}, {@code https://*.com}), {@code null}, paths, plain-HTTP wildcards and any
 * wildcard in production are rejected at startup instead of silently trusting arbitrary sites.
 */
public final class CorsOriginPolicy {

    private CorsOriginPolicy() {
    }

    public static List<String> parse(String configured, boolean production) {
        List<String> origins = new ArrayList<>();
        if (configured == null) {
            return origins;
        }
        for (String raw : Arrays.stream(configured.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()) {
            origins.add(validate(raw, production));
        }
        return List.copyOf(origins);
    }

    private static String validate(String origin, boolean production) {
        String lower = origin.toLowerCase(Locale.ROOT);
        if ("*".equals(lower) || "null".equals(lower)) {
            throw invalid(origin, "wildcard or null origins cannot be combined with credentials");
        }
        boolean wildcard = lower.contains("*");
        URI uri;
        try {
            uri = URI.create(wildcard ? lower.replace("*", "wildcard-placeholder") : lower);
        } catch (IllegalArgumentException e) {
            throw invalid(origin, "not a valid origin");
        }
        String scheme = uri.getScheme();
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            throw invalid(origin, "scheme must be http or https");
        }
        if (uri.getHost() == null || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getRawUserInfo() != null) {
            throw invalid(origin, "an origin is scheme://host[:port] with no path, query or credentials");
        }
        if (wildcard) {
            if (production) {
                throw invalid(origin, "wildcard origins are not allowed in production; list the public origin");
            }
            if (!"https".equals(scheme)) {
                throw invalid(origin, "wildcard origins must use https");
            }
            String host = lower.substring("https://".length()).replaceFirst(":\\d+$", "");
            String[] labels = host.split("\\.");
            boolean leftmostOnly = labels.length >= 3 && "*".equals(labels[0])
                    && Arrays.stream(labels).skip(1).noneMatch(l -> l.contains("*") || l.isEmpty());
            if (!leftmostOnly) {
                throw invalid(origin, "only one left-most wildcard label under a fixed domain is allowed (https://*.example.com)");
            }
        }
        return origin;
    }

    private static IllegalStateException invalid(String origin, String reason) {
        return new IllegalStateException("Invalid CORS allowed origin '" + origin + "': " + reason);
    }
}
