package com.app.sme_health_backend.samples.controller;

import com.app.sme_health_backend.samples.dto.SamplePlanApprovalRequest;
import com.app.sme_health_backend.samples.dto.SamplePlanLineRequest;
import com.app.sme_health_backend.samples.dto.SamplePlanRequest;
import com.app.sme_health_backend.samples.dto.SampleCreateRequest;
import com.app.sme_health_backend.samples.dto.SampleTransitionRequest;
import com.app.sme_health_backend.samples.dto.SampleUpdateRequest;
import com.app.sme_health_backend.projects.model.ProjectCostCategory;
import com.app.sme_health_backend.samples.model.SampleKind;
import com.app.sme_health_backend.samples.model.SampleStatus;
import com.app.sme_health_backend.samples.service.SamplePlanCalculator;
import com.app.sme_health_backend.samples.service.SampleValidationException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.app.sme_health_backend.samples.model.RecoveryIntent;

/**
 * Closed-schema JSON reader for F09 Phase A activity and plan payloads.
 * Rejects unknown fields (such as businessId, actorUserId, currency, status, or browser-computed totals),
 * numeric coercion on decimal string fields, duplicate JSON keys, and trailing tokens.
 */
public final class SampleRequestReader {
    private SampleRequestReader() { }

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
            "code", "name", "kind", "description", "recoveryIntent", "projectId", "startDate", "targetEndDate"
    );
    private static final Set<String> UPDATE_FIELDS = Set.of(
            "code", "name", "kind", "description", "recoveryIntent", "projectId", "startDate", "targetEndDate", "expectedVersion"
    );
    private static final Set<String> TRANSITION_FIELDS = Set.of(
            "targetStatus", "reason", "expectedVersion"
    );
    private static final Set<String> PLAN_CREATE_FIELDS = Set.of(
            "expectedActivityVersion", "plannedRecovery", "lines"
    );
    private static final Set<String> PLAN_UPDATE_FIELDS = Set.of(
            "expectedPlanVersion", "plannedRecovery", "lines"
    );
    private static final Set<String> PLAN_LINE_FIELDS = Set.of(
            "category", "description", "quantity", "unit", "unitCost"
    );
    private static final Set<String> PLAN_APPROVE_FIELDS = Set.of(
            "expectedActivityVersion", "expectedPlanVersion"
    );

    public static SampleCreateRequest readCreate(byte[] body) {
        JsonNode root = parseTree(body);
        closedObject(root, CREATE_FIELDS, "request");
        return new SampleCreateRequest(
                string(root, "code", "code", false),
                string(root, "name", "name", false),
                enumValue(SampleKind.class, string(root, "kind", "kind", false), "kind"),
                string(root, "description", "description", true),
                enumValue(RecoveryIntent.class, string(root, "recoveryIntent", "recoveryIntent", false), "recoveryIntent"),
                optionalUuid(root, "projectId"),
                string(root, "startDate", "startDate", true),
                string(root, "targetEndDate", "targetEndDate", true)
        );
    }

    public static SampleUpdateRequest readUpdate(byte[] body) {
        JsonNode root = parseTree(body);
        closedObject(root, UPDATE_FIELDS, "request");
        return new SampleUpdateRequest(
                string(root, "code", "code", false),
                string(root, "name", "name", false),
                enumValue(SampleKind.class, string(root, "kind", "kind", false), "kind"),
                string(root, "description", "description", true),
                enumValue(RecoveryIntent.class, string(root, "recoveryIntent", "recoveryIntent", false), "recoveryIntent"),
                optionalUuid(root, "projectId"),
                string(root, "startDate", "startDate", true),
                string(root, "targetEndDate", "targetEndDate", true),
                nonNegativeLong(root, "expectedVersion", "expectedVersion")
        );
    }

    public static SampleTransitionRequest readTransition(byte[] body) {
        JsonNode root = parseTree(body);
        closedObject(root, TRANSITION_FIELDS, "request");
        return new SampleTransitionRequest(
                enumValue(SampleStatus.class, string(root, "targetStatus", "targetStatus", false), "targetStatus"),
                string(root, "reason", "reason", true),
                nonNegativeLong(root, "expectedVersion", "expectedVersion")
        );
    }

    public static SamplePlanRequest readPlanCreate(byte[] body) {
        JsonNode root = parseTree(body);
        closedObject(root, PLAN_CREATE_FIELDS, "request");
        return new SamplePlanRequest(
                nonNegativeLong(root, "expectedActivityVersion", "expectedActivityVersion"),
                string(root, "plannedRecovery", "plannedRecovery", true),
                readPlanLines(root)
        );
    }

    public static SamplePlanRequest readPlanUpdate(byte[] body) {
        JsonNode root = parseTree(body);
        closedObject(root, PLAN_UPDATE_FIELDS, "request");
        return new SamplePlanRequest(
                nonNegativeLong(root, "expectedPlanVersion", "expectedPlanVersion"),
                string(root, "plannedRecovery", "plannedRecovery", true),
                readPlanLines(root)
        );
    }

    public static SamplePlanApprovalRequest readPlanApproval(byte[] body) {
        JsonNode root = parseTree(body);
        closedObject(root, PLAN_APPROVE_FIELDS, "request");
        return new SamplePlanApprovalRequest(
                nonNegativeLong(root, "expectedActivityVersion", "expectedActivityVersion"),
                nonNegativeLong(root, "expectedPlanVersion", "expectedPlanVersion")
        );
    }

    private static List<SamplePlanLineRequest> readPlanLines(JsonNode root) {
        JsonNode lines = root.get("lines");
        if (lines == null || !lines.isArray() || lines.isEmpty() || lines.size() > SamplePlanCalculator.MAX_LINES) {
            throw new SampleValidationException("lines", "line_count_invalid");
        }
        List<SamplePlanLineRequest> parsed = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            String path = "lines[" + index + "]";
            JsonNode line = lines.get(index);
            closedObject(line, PLAN_LINE_FIELDS, path);
            parsed.add(new SamplePlanLineRequest(
                    enumValue(ProjectCostCategory.class, string(line, "category", path + ".category", false), path + ".category"),
                    string(line, "description", path + ".description", false),
                    string(line, "quantity", path + ".quantity", false),
                    string(line, "unit", path + ".unit", false),
                    string(line, "unitCost", path + ".unitCost", false)
            ));
        }
        return List.copyOf(parsed);
    }

    private static JsonNode parseTree(byte[] body) {
        if (body == null || body.length == 0) {
            throw new SampleValidationException("request", "json_invalid");
        }
        if (body.length > MAX_REQUEST_BYTES) {
            throw new SampleValidationException("request", "request_too_large");
        }
        try {
            return JSON.readTree(body);
        } catch (RuntimeException exception) {
            throw new SampleValidationException("request", "json_invalid");
        }
    }

    private static void closedObject(JsonNode node, Set<String> allowed, String path) {
        if (node == null || !node.isObject()) {
            throw new SampleValidationException(path, "object_required");
        }
        for (String name : node.propertyNames()) {
            if (!allowed.contains(name)) {
                throw new SampleValidationException(path, "unknown_field");
            }
        }
    }

    private static String string(JsonNode parent, String field, String path, boolean nullable) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            if (nullable) {
                return null;
            }
            throw new SampleValidationException(path, "required");
        }
        if (!node.isString()) {
            throw new SampleValidationException(path, "string_required");
        }
        return node.stringValue();
    }

    private static UUID optionalUuid(JsonNode parent, String field) {
        String value = string(parent, field, field, true);
        if (value == null) return null;
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return id;
        } catch (IllegalArgumentException ex) { throw new SampleValidationException(field, "uuid_invalid"); }
    }

    private static Long nonNegativeLong(JsonNode parent, String field, String path) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            throw new SampleValidationException(path, "required");
        }
        if (!node.isIntegralNumber() || !node.canConvertToLong()) {
            throw new SampleValidationException(path, "version_invalid");
        }
        long value = node.longValue();
        if (value < 0) {
            throw new SampleValidationException(path, "version_invalid");
        }
        return value;
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String path) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new SampleValidationException(path, "enum_invalid");
        }
    }
}
