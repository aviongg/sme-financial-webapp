package com.app.sme_health_backend.records.service;

import com.app.sme_health_backend.documents.dto.DocumentConfirmationRequest;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Conservative duplicate warning identity, not a claim that two receipts are economically identical. */
final class DocumentSourceFingerprint {
    private DocumentSourceFingerprint() { }
    static String of(UUID business, DocumentConfirmationRequest request, String classification, String flow) {
        String party = request.confirmedParty() == null ? "" : Normalizer.normalize(request.confirmedParty(), Normalizer.Form.NFKC)
                .strip().replaceAll("(?U)\\s+", " ").toLowerCase(Locale.ROOT);
        String canonical = new ObjectMapper().writeValueAsString(List.of("document-source-v1", business.toString(),
                request.targetMonth(), request.confirmedDate() == null ? "" : request.confirmedDate().toString(),
                party, request.confirmedAmount().stripTrailingZeros().toPlainString(), classification, flow));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}