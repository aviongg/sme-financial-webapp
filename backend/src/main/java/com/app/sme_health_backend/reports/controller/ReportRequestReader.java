package com.app.sme_health_backend.reports.controller;

import com.app.sme_health_backend.reports.dto.ReportRequest;
import com.app.sme_health_backend.reports.service.ReportValidationException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

/** Closed JSON schema: exactly three string fields; no tenant, user, SQL or metric-expression input is possible. */
final class ReportRequestReader {
    private ReportRequestReader() { }

    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(4).maxStringLength(64).maxNumberLength(16).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final Set<String> FIELDS = Set.of("fromMonth", "toMonth", "language");

    static ReportRequest read(byte[] body) {
        final JsonNode root;
        try {
            root = JSON.readTree(body);
        } catch (RuntimeException exception) {
            throw new ReportValidationException("request", "json_invalid");
        }
        if (root == null || !root.isObject()) throw new ReportValidationException("request", "object_required");
        for (String name : root.propertyNames()) {
            if (!FIELDS.contains(name)) throw new ReportValidationException("request", "unknown_field");
        }
        return new ReportRequest(string(root, "fromMonth", false), string(root, "toMonth", false), string(root, "language", true));
    }

    private static String string(JsonNode parent, String field, boolean optional) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            if (optional) return null;
            throw new ReportValidationException(field, "required");
        }
        if (!node.isString()) throw new ReportValidationException(field, "string_required");
        return node.stringValue();
    }
}
