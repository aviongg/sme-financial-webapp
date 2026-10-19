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

/** Closed JSON schema and explicit text-node checks prevent Jackson numeric-to-string coercion. */
final class EstimateRequestReader {
    private EstimateRequestReader() { }
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(1024).maxNumberLength(64).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final Set<String> ROOT_FIELDS = Set.of("lines", "saleableQuantity", "pricingMode", "pricingValue");
    private static final Set<String> LINE_FIELDS = Set.of("costGroup", "description", "quantity", "unit", "unitCost");

    static PricingPreviewRequest read(byte[] body) {
        final JsonNode root;
        try { root = JSON.readTree(body); }
        catch (RuntimeException exception) { throw new EstimateValidationException("request", "json_invalid"); }
        closedObject(root, ROOT_FIELDS, "request");
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
    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String path) {
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException exception) { throw new EstimateValidationException(path, "enum_invalid"); }
    }
}
