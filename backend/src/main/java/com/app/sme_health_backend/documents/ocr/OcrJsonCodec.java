package com.app.sme_health_backend.documents.ocr;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/** Strict boundary validation avoids accepting coercions or silently changed shared contracts. */
public final class OcrJsonCodec {
    private static final Set<String> FIELDS = Set.of(
            "date", "amount", "vendor_or_party", "category", "confidence", "document_type_detected");

    private final JsonMapper mapper = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    public byte[] encode(OcrRequest request) {
        return mapper.writeValueAsBytes(request);
    }

    public OcrExtraction decode(byte[] response) {
        try {
            JsonNode root = mapper.readTree(response);
            if (root == null || !root.isObject() || root.size() != FIELDS.size()
                    || FIELDS.stream().anyMatch(field -> !root.has(field))) {
                throw new IllegalArgumentException("Unexpected extraction fields");
            }
            String dateString = nullableString(root.get("date"));
            if (dateString != null && !dateString.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                throw new IllegalArgumentException("Invalid date format");
            }
            LocalDate date = dateString == null ? null : LocalDate.parse(dateString);
            JsonNode amountNode = root.get("amount");
            if (!amountNode.isNull() && !amountNode.isNumber()) {
                throw new IllegalArgumentException("Amount must be numeric or null");
            }
            BigDecimal amount = amountNode.isNull() ? null : amountNode.decimalValue();
            return new OcrExtraction(date, amount, nullableString(root.get("vendor_or_party")),
                    OcrExtraction.Category.valueOf(requiredString(root.get("category"))),
                    OcrExtraction.Confidence.valueOf(requiredString(root.get("confidence"))),
                    OcrExtraction.DocumentType.valueOf(requiredString(root.get("document_type_detected"))));
        } catch (RuntimeException exception) {
            throw new OcrClientException(OcrClientException.Reason.invalid_response);
        }
    }

    private static String nullableString(JsonNode node) {
        return node.isNull() ? null : requiredString(node);
    }

    private static String requiredString(JsonNode node) {
        if (!node.isString()) {
            throw new IllegalArgumentException("Expected a string");
        }
        return node.stringValue();
    }
}
