package com.app.sme_health_backend.documents.ocr;

/** Replaceable boundary to the standalone OCR service. It never confirms accounting records. */
@FunctionalInterface
public interface OcrClient {
    OcrExtraction extract(OcrRequest request);
}
