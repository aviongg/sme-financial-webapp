package com.app.sme_health_backend.productsupport;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Deterministic payload fingerprints for idempotent writes. */
public final class Hashing {
    private Hashing() { }

    public static String sha256(Object... parts) {
        StringBuilder canonical = new StringBuilder();
        for (Object part : parts) {
            canonical.append(part == null ? "␀" : part.toString()).append('|');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
