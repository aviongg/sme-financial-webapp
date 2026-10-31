package com.app.sme_health_backend.inventory.controller;

import com.app.sme_health_backend.inventory.dto.InventoryItemArchiveRequest;
import com.app.sme_health_backend.inventory.dto.InventoryItemRequest;
import com.app.sme_health_backend.inventory.dto.ValuationPreviewRequest;
import com.app.sme_health_backend.inventory.model.InventoryItemKind;
import com.app.sme_health_backend.inventory.service.InventoryValidationException;
import com.app.sme_health_backend.inventory.service.WeightedAverageCostCalculator;
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
 * Closed-schema JSON reader for F08 Phase A. Rejects unknown fields (such as businessId, quantity on hand,
 * stock value or status), numeric coercion of decimal string fields, duplicate keys and trailing tokens.
 */
public final class InventoryRequestReader {
    private InventoryRequestReader() { }

    public static final int MAX_REQUEST_BYTES = 65_536;

    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(8)
                    .maxStringLength(2048)
                    .maxNumberLength(64)
                    .build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private static final Set<String> CREATE_FIELDS = Set.of(
            "sku", "name", "unit", "itemKind", "category", "reorderLevel"
    );
    private static final Set<String> UPDATE_FIELDS = Set.of(
            "sku", "name", "unit", "itemKind", "category", "reorderLevel", "expectedVersion"
    );
    private static final Set<String> ARCHIVE_FIELDS = Set.of("expectedVersion");
    private static final Set<String> PREVIEW_FIELDS = Set.of("openingQuantity", "openingValue", "steps");
    private static final Set<String> STEP_FIELDS = Set.of("type", "quantity", "value");

    public static InventoryItemRequest readCreate(byte[] body) {
        JsonNode root = parseTree(body);
        closedObject(root, CREATE_FIELDS, "request");
        return item(root, null);
    }

    public static InventoryItemRequest readUpdate(byte[] body) {
        JsonNode root = parseTree(body);
        closedObject(root, UPDATE_FIELDS, "request");
        return item(root, nonNegativeLong(root, "expectedVersion"));
    }

    public static InventoryItemArchiveRequest readArchive(byte[] body) {
        JsonNode root = parseTree(body);
        closedObject(root, ARCHIVE_FIELDS, "request");
        return new InventoryItemArchiveRequest(nonNegativeLong(root, "expectedVersion"));
    }

    public static ValuationPreviewRequest readPreview(byte[] body) {
        JsonNode root = parseTree(body);
        closedObject(root, PREVIEW_FIELDS, "request");
        JsonNode steps = root.get("steps");
        if (steps == null || !steps.isArray() || steps.size() > WeightedAverageCostCalculator.MAX_STEPS) {
            throw new InventoryValidationException("steps", "step_count_invalid");
        }
        List<ValuationPreviewRequest.Step> parsed = new ArrayList<>(steps.size());
        for (int index = 0; index < steps.size(); index++) {
            String path = "steps[" + index + "]";
            JsonNode step = steps.get(index);
            closedObject(step, STEP_FIELDS, path);
            parsed.add(new ValuationPreviewRequest.Step(
                    string(step, "type", path + ".type", false),
                    string(step, "quantity", path + ".quantity", false),
                    string(step, "value", path + ".value", true)
            ));
        }
        return new ValuationPreviewRequest(
                string(root, "openingQuantity", "openingQuantity", false),
                string(root, "openingValue", "openingValue", false),
                List.copyOf(parsed)
        );
    }

    private static InventoryItemRequest item(JsonNode root, Long expectedVersion) {
        return new InventoryItemRequest(
                string(root, "sku", "sku", false),
                string(root, "name", "name", false),
                string(root, "unit", "unit", false),
                enumValue(InventoryItemKind.class, string(root, "itemKind", "itemKind", false), "itemKind"),
                string(root, "category", "category", true),
                string(root, "reorderLevel", "reorderLevel", true),
                expectedVersion
        );
    }

    private static JsonNode parseTree(byte[] body) {
        if (body == null || body.length == 0) {
            throw new InventoryValidationException("request", "json_invalid");
        }
        if (body.length > MAX_REQUEST_BYTES) {
            throw new InventoryValidationException("request", "request_too_large");
        }
        try {
            return JSON.readTree(body);
        } catch (RuntimeException exception) {
            throw new InventoryValidationException("request", "json_invalid");
        }
    }

    private static void closedObject(JsonNode node, Set<String> allowed, String path) {
        if (node == null || !node.isObject()) {
            throw new InventoryValidationException(path, "object_required");
        }
        for (String name : node.propertyNames()) {
            if (!allowed.contains(name)) {
                throw new InventoryValidationException(path, "unknown_field");
            }
        }
    }

    private static String string(JsonNode parent, String field, String path, boolean nullable) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            if (nullable) {
                return null;
            }
            throw new InventoryValidationException(path, "required");
        }
        if (!node.isString()) {
            throw new InventoryValidationException(path, "string_required");
        }
        return node.stringValue();
    }

    private static Long nonNegativeLong(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            throw new InventoryValidationException(field, "required");
        }
        if (!node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() < 0) {
            throw new InventoryValidationException(field, "version_invalid");
        }
        return node.longValue();
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String path) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new InventoryValidationException(path, "enum_invalid");
        }
    }
}
