package com.app.sme_health_backend.commercial.controller;

import com.app.sme_health_backend.commercial.dto.CommercialDocumentDraftRequest;
import com.app.sme_health_backend.commercial.dto.CommercialLineRequest;
import com.app.sme_health_backend.commercial.dto.CommercialPreviewRequest;
import com.app.sme_health_backend.commercial.dto.CommercialTransitionRequest;
import com.app.sme_health_backend.commercial.service.CommercialDocumentCalculator;
import com.app.sme_health_backend.commercial.service.CommercialValidationException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Closed-schema JSON reader for F04 Phase A. Only client-owned fields are accepted; server-owned fields such as
 * businessId, actorUserId, createdBy/updatedBy/finalizedBy/voidedBy, status, documentNumber, subtotal, total,
 * lineAmount, currency or financialEffect are rejected as unknown. Decimals must be JSON strings (no numeric
 * coercion); duplicate keys and trailing tokens are rejected.
 */
public final class CommercialDocumentRequestReader {
    private CommercialDocumentRequestReader() { }

    public static final int MAX_REQUEST_BYTES = 65_536;

    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(8)
                    .maxStringLength(4096)
                    .maxNumberLength(32)
                    .build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private static final Set<String> CREATE_FIELDS = Set.of(
            "counterpartyName", "documentDate", "dueDate", "notes", "lines"
    );
    private static final Set<String> UPDATE_FIELDS = Set.of(
            "counterpartyName", "documentDate", "dueDate", "notes", "lines", "expectedVersion"
    );
    private static final Set<String> PREVIEW_FIELDS = Set.of("lines");
    private static final Set<String> LINE_FIELDS = Set.of("description", "quantity", "unit", "unitPrice");
    private static final Set<String> FINALIZE_FIELDS = Set.of("expectedVersion");
    private static final Set<String> VOID_FIELDS = Set.of("expectedVersion", "reason");

    public static CommercialDocumentDraftRequest readCreate(byte[] body) {
        JsonNode root = parseTree(body);
        closedObject(root, CREATE_FIELDS, "request");
        return draft(root, null);
    }

    public static CommercialDocumentDraftRequest readUpdate(byte[] body) {
        JsonNode root = parseTree(body);
        closedObject(root, UPDATE_FIELDS, "request");
        return draft(root, nonNegativeLong(root, "expectedVersion"));
    }

    public static CommercialPreviewRequest readPreview(byte[] body) {
        JsonNode root = parseTree(body);
        closedObject(root, PREVIEW_FIELDS, "request");
        return new CommercialPreviewRequest(lines(root));
    }

    public static CommercialTransitionRequest readFinalize(byte[] body) {
        JsonNode root = parseTree(body);
        closedObject(root, FINALIZE_FIELDS, "request");
        return new CommercialTransitionRequest(nonNegativeLong(root, "expectedVersion"), null);
    }

    public static CommercialTransitionRequest readVoid(byte[] body) {
        JsonNode root = parseTree(body);
        closedObject(root, VOID_FIELDS, "request");
        return new CommercialTransitionRequest(
                nonNegativeLong(root, "expectedVersion"),
                string(root, "reason", "reason", false)
        );
    }

    private static CommercialDocumentDraftRequest draft(JsonNode root, Long expectedVersion) {
        return new CommercialDocumentDraftRequest(
                string(root, "counterpartyName", "counterpartyName", true),
                string(root, "documentDate", "documentDate", true),
                string(root, "dueDate", "dueDate", true),
                string(root, "notes", "notes", true),
                lines(root),
                expectedVersion
        );
    }

    private static List<CommercialLineRequest> lines(JsonNode root) {
        JsonNode lines = root.get("lines");
        if (lines == null || lines.isNull()) {
            throw new CommercialValidationException("lines", "required");
        }
        if (!lines.isArray() || lines.size() > CommercialDocumentCalculator.MAX_LINES) {
            throw new CommercialValidationException("lines", "line_count_invalid");
        }
        List<CommercialLineRequest> parsed = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            String path = "lines[" + index + "]";
            JsonNode line = lines.get(index);
            closedObject(line, LINE_FIELDS, path);
            parsed.add(new CommercialLineRequest(
                    string(line, "description", path + ".description", false),
                    string(line, "quantity", path + ".quantity", false),
                    string(line, "unit", path + ".unit", true),
                    string(line, "unitPrice", path + ".unitPrice", false)
            ));
        }
        return List.copyOf(parsed);
    }

    private static JsonNode parseTree(byte[] body) {
        if (body == null || body.length == 0) {
            throw new CommercialValidationException("request", "json_invalid");
        }
        if (body.length > MAX_REQUEST_BYTES) {
            throw new CommercialValidationException("request", "request_too_large");
        }
        try {
            return JSON.readTree(body);
        } catch (RuntimeException exception) {
            throw new CommercialValidationException("request", "json_invalid");
        }
    }

    private static void closedObject(JsonNode node, Set<String> allowed, String path) {
        if (node == null || !node.isObject()) {
            throw new CommercialValidationException(path, "object_required");
        }
        for (String name : node.propertyNames()) {
            if (!allowed.contains(name)) {
                throw new CommercialValidationException(path.equals("request") ? name : path + "." + name, "unknown_field");
            }
        }
    }

    private static String string(JsonNode parent, String field, String path, boolean nullable) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            if (nullable) {
                return null;
            }
            throw new CommercialValidationException(path, "required");
        }
        if (!node.isString()) {
            throw new CommercialValidationException(path, "string_required");
        }
        return node.stringValue();
    }

    private static Long nonNegativeLong(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            throw new CommercialValidationException(field, "required");
        }
        if (!node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() < 0) {
            throw new CommercialValidationException(field, "version_invalid");
        }
        return node.longValue();
    }
}
