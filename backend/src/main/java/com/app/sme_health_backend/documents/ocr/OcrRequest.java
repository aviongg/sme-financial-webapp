package com.app.sme_health_backend.documents.ocr;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URI;
import java.util.Objects;

public record OcrRequest(
        @JsonProperty("image_url") String imageUrl,
        @JsonProperty("document_type_hint") OcrExtraction.DocumentType documentTypeHint
) {
    public OcrRequest {
        Objects.requireNonNull(documentTypeHint, "document type hint is required");
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalArgumentException("An HTTP(S) document URL is required");
        }
        URI uri;
        try {
            uri = URI.create(imageUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid document URL");
        }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("An HTTP(S) document URL without credentials or fragment is required");
        }
    }
}
