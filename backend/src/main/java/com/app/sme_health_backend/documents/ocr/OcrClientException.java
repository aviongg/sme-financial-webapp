package com.app.sme_health_backend.documents.ocr;

/** Deliberately excludes upstream response bodies and document URLs from error messages. */
public final class OcrClientException extends RuntimeException {
    private final Reason reason;

    public OcrClientException(Reason reason) {
        super("OCR extraction failed: " + reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason { invalid_request, unavailable, invalid_response }
}
