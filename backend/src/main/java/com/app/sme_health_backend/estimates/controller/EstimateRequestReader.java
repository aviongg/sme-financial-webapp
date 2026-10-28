package com.app.sme_health_backend.estimates.controller;

import com.app.sme_health_backend.estimates.dto.*;
import com.app.sme_health_backend.estimates.service.EstimateCostCalculator;
import com.app.sme_health_backend.estimates.service.EstimateValidationException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Closed JSON schema and explicit text-node checks prevent Jackson numeric-to-string coercion.
 * Server-owned fields (businessId, actor IDs, status, revision, totals, outputs, lineage, currency) are unknown fields.
 */
final class EstimateRequestReader {
    private EstimateRequestReader() { }
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(1024).maxNumberLength(64).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final Set<String> ROOT_FIELDS = Set.of("lines", "saleableQuantity", "pricingMode", "pricingValue");
    private static final Set<String> LINE_FIELDS = Set.of("costGroup", "description", "quantity", "unit", "unitCost");
    private static final Set<String> REVISION_FIELDS = Set.of("lines", "saleableQuantity", "pricingMode", "pricingValue", "expectedVersion");
    private static final Set<String> CREATE_FIELDS = Set.of("code", "name", "description", "draft");
    private static final Set<String> UPDATE_FIELDS = Set.of("code", "name", "description", "expectedVersion");
    private static final Set<String> TRANSITION_FIELDS = Set.of("expectedVersion");
    private static final Set<String> CONVERSION_FIELDS = Set.of("targetProjectId", "expectedProjectVersion");
    private static final Pattern UUID_TEXT = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /** Phase A preview body, unchanged. */
    static PricingPreviewRequest read(byte[] body) {
        JsonNode root = tree(body);
        closedObject(root, ROOT_FIELDS, "request");
        return pricing(root);
    }
    static EstimateCreateRequest readCreate(byte[] body) {
        JsonNode root = tree(body);
        closedObject(root, CREATE_FIELDS, "request");
        JsonNode draft = root.get("draft");
        PricingPreviewRequest content = null;
        if (draft != null && !draft.isNull()) {
            // Draft field paths match the preview contract (lines[0].quantity), so one error mapping serves both.
            closedObject(draft, ROOT_FIELDS, "draft");
            content = pricing(draft);
        }
        return new EstimateCreateRequest(string(root, "code", "code", true), string(root, "name", "name", true),
                string(root, "description", "description", true), content);
    }
    static EstimateUpdateRequest readUpdate(byte[] body) {
        JsonNode root = tree(body);
        closedObject(root, UPDATE_FIELDS, "request");
        return new EstimateUpdateRequest(string(root, "code", "code", true), string(root, "name", "name", true),
                string(root, "description", "description", true), version(root, "expectedVersion"));
    }
    static EstimateRevisionRequest readRevision(byte[] body) {
        JsonNode root = tree(body);
        closedObject(root, REVISION_FIELDS, "request");
        return new EstimateRevisionRequest(pricing(root), version(root, "expectedVersion"));
    }
    static EstimateTransitionRequest readTransition(byte[] body) {
        JsonNode root = tree(body);
        closedObject(root, TRANSITION_FIELDS, "request");
        return new EstimateTransitionRequest(version(root, "expectedVersion"));
    }
    static EstimateConversionRequest readConversion(byte[] body) {
        JsonNode root = tree(body);
        closedObject(root, CONVERSION_FIELDS, "request");
        String target = string(root, "targetProjectId", "targetProjectId", false);
        if (!UUID_TEXT.matcher(target).matches()) throw new EstimateValidationException("targetProjectId", "uuid_invalid");
        return new EstimateConversionRequest(UUID.fromString(target), version(root, "expectedProjectVersion"));
    }

    private static JsonNode tree(byte[] body) {
        try { return JSON.readTree(body); }
        catch (RuntimeException exception) { throw new EstimateValidationException("request", "json_invalid"); }
    }
    private static PricingPreviewRequest pricing(JsonNode root) {
        JsonNode lines = root.get("lines");
        if (lines == null || !lines.isArray() || lines.isEmpty() || lines.size() > EstimateCostCalculator.MAX_LINES)
            throw new EstimateValidationException("lines", "line_count_invalid");
        List<EstimateLineRequest> parsed = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String path = "lines[" + index + "]";
            JsonNode line = lines.get(index);
            closedObject(line, LINE_FIELDS, path);
            parsed.add(new EstimateLineRequest(enumValue(EstimateLineRequest.CostGroup.class, string(line, "costGroup", path + ".costGroup", false), path + ".costGroup"),
                    string(line, "description", path + ".description", false), string(line, "quantity", path + ".quantity", false),
                    string(line, "unit", path + ".unit", false), string(line, "unitCost", path + ".unitCost", false)));
        }
        return new PricingPreviewRequest(List.copyOf(parsed), string(root, "saleableQuantity", "saleableQuantity", true),
                enumValue(PricingPreviewRequest.PricingMode.class, string(root, "pricingMode", "pricingMode", false), "pricingMode"),
                string(root, "pricingValue", "pricingValue", false));
    }
    private static void closedObject(JsonNode node, Set<String> allowed, String path) {
        if (node == null || !node.isObject()) throw new EstimateValidationException(path, "object_required");
        for (String name : node.propertyNames()) if (!allowed.contains(name))
            throw new EstimateValidationException(path, "unknown_field");
    }
    private static String string(JsonNode parent, String field, String path, boolean nullable) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            if (nullable) return null;
            throw new EstimateValidationException(path, "required");
        }
        if (!node.isString()) throw new EstimateValidationException(path, "string_required");
        return node.stringValue();
    }
    private static Long version(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) throw new EstimateValidationException(field, "required");
        if (!node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() < 0)
            throw new EstimateValidationException(field, "version_invalid");
        return node.longValue();
    }
    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String path) {
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException exception) { throw new EstimateValidationException(path, "enum_invalid"); }
    }
}
