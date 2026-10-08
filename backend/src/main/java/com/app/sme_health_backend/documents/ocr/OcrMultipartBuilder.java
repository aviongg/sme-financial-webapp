package com.app.sme_health_backend.documents.ocr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class OcrMultipartBuilder {

    private OcrMultipartBuilder() {}

    public static byte[] build(String boundary, OcrRequest request) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] crlf = "\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] dashDash = "--".getBytes(StandardCharsets.UTF_8);
        byte[] boundaryBytes = boundary.getBytes(StandardCharsets.UTF_8);

        // Part 1: file
        baos.write(dashDash);
        baos.write(boundaryBytes);
        baos.write(crlf);
        String filename = sanitizeHeaderValue(request.filename());
        String contentType = sanitizeHeaderValue(request.contentType());
        String fileHeader = "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n";
        baos.write(fileHeader.getBytes(StandardCharsets.UTF_8));
        baos.write(request.fileBytes());
        baos.write(crlf);

        // Part 2: document_type_hint
        if (request.documentTypeHint() != null) {
            baos.write(dashDash);
            baos.write(boundaryBytes);
            baos.write(crlf);
            String hintHeader = "Content-Disposition: form-data; name=\"document_type_hint\"\r\n\r\n"
                    + request.documentTypeHint().name();
            baos.write(hintHeader.getBytes(StandardCharsets.UTF_8));
            baos.write(crlf);
        }

        // Part 3: document_id (optional correlation metadata)
        if (request.documentId() != null) {
            baos.write(dashDash);
            baos.write(boundaryBytes);
            baos.write(crlf);
            String idHeader = "Content-Disposition: form-data; name=\"document_id\"\r\n\r\n"
                    + request.documentId();
            baos.write(idHeader.getBytes(StandardCharsets.UTF_8));
            baos.write(crlf);
        }

        // Closing boundary: --boundary--\r\n
        baos.write(dashDash);
        baos.write(boundaryBytes);
        baos.write(dashDash);
        baos.write(crlf);

        return baos.toByteArray();
    }

    private static String sanitizeHeaderValue(String val) {
        if (val == null || val.isBlank()) {
            return "unknown";
        }
        return val.replaceAll("[\\r\\n\\\"\\\\;\\u0000]", "_");
    }
}
