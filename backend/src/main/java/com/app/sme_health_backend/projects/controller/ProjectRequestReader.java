package com.app.sme_health_backend.projects.controller;

import com.app.sme_health_backend.projects.dto.ProjectBudgetApprovalRequest;
import com.app.sme_health_backend.projects.dto.ProjectBudgetLineRequest;
import com.app.sme_health_backend.projects.dto.ProjectBudgetRequest;
import com.app.sme_health_backend.projects.dto.ProjectCreateRequest;
import com.app.sme_health_backend.projects.dto.ProjectTransitionRequest;
import com.app.sme_health_backend.projects.dto.ProjectUpdateRequest;
import com.app.sme_health_backend.projects.model.ProjectCostCategory;
import com.app.sme_health_backend.projects.model.ProjectKind;
import com.app.sme_health_backend.projects.model.ProjectStatus;
import com.app.sme_health_backend.projects.service.ProjectBudgetCalculator;
import com.app.sme_health_backend.projects.service.ProjectValidationException;
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
 * Closed-schema JSON reader for F05 Phase A project and budget payloads.
 * Rejects unknown fields (such as businessId, actorUserId, currency, status, or browser-computed totals),
 * numeric coercion on decimal string fields, duplicate JSON keys, and trailing tokens.
 */
public final class ProjectRequestReader {
    private ProjectRequestReader() { }

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
            "code", "name", "kind", "description", "startDate", "targetEndDate"
    );
    private static final Set<String> UPDATE_FIELDS = Set.of(
            "code", "name", "kind", "description", "startDate", "targetEndDate", "expectedVersion"
    );
    private static final Set<String> TRANSITION_FIELDS = Set.of(
            "targetStatus", "reason", "expectedVersion"
    );
    private static final Set<String> BUDGET_CREATE_FIELDS = Set.of(
            "expectedProjectVersion", "plannedRevenue", "lines"
    );
    private static final Set<String> BUDGET_UPDATE_FIELDS = Set.of(
            "expectedBudgetVersion", "plannedRevenue", "lines"
    );
    private static final Set<String> BUDGET_LINE_FIELDS = Set.of(
            "category", "description", "quantity", "unit", "unitCost"
    );
    private static final Set<String> BUDGET_APPROVE_FIELDS = Set.of(
            "expectedProjectVersion", "expectedBudgetVersion"
    );

    public static ProjectCreateRequest readCreate(byte[] body) {
        JsonNode root = parseTree(body);
        closedObject(root, CREATE_FIELDS, "request");
        return new ProjectCreateRequest(
                string(root, "code", "code", false),
                string(root, "name", "name", false),
                enumValue(ProjectKind.class, string(root, "kind", "kind", false), "kind"),
                string(root, "description", "description", true),
                string(root, "startDate", "startDate", true),
                string(root, "targetEndDate", "targetEndDate", true)
        );
    }

    public static ProjectUpdateRequest readUpdate(byte[] body) {
        JsonNode root = parseTree(body);
        closedObject(root, UPDATE_FIELDS, "request");
        return new ProjectUpdateRequest(
                string(root, "code", "code", false),
                string(root, "name", "name", false),
                enumValue(ProjectKind.class, string(root, "kind", "kind", false), "kind"),
                string(root, "description", "description", true),
                string(root, "startDate", "startDate", true),
                string(root, "targetEndDate", "targetEndDate", true),
                nonNegativeLong(root, "expectedVersion", "expectedVersion")
        );
    }

    public static ProjectTransitionRequest readTransition(byte[] body) {
        JsonNode root = parseTree(body);
        closedObject(root, TRANSITION_FIELDS, "request");
        return new ProjectTransitionRequest(
                enumValue(ProjectStatus.class, string(root, "targetStatus", "targetStatus", false), "targetStatus"),
                string(root, "reason", "reason", true),
                nonNegativeLong(root, "expectedVersion", "expectedVersion")
        );
    }

    public static ProjectBudgetRequest readBudgetCreate(byte[] body) {
        JsonNode root = parseTree(body);
        closedObject(root, BUDGET_CREATE_FIELDS, "request");
        return new ProjectBudgetRequest(
                nonNegativeLong(root, "expectedProjectVersion", "expectedProjectVersion"),
                string(root, "plannedRevenue", "plannedRevenue", false),
                readBudgetLines(root)
        );
    }

    public static ProjectBudgetRequest readBudgetUpdate(byte[] body) {
        JsonNode root = parseTree(body);
        closedObject(root, BUDGET_UPDATE_FIELDS, "request");
        return new ProjectBudgetRequest(
                nonNegativeLong(root, "expectedBudgetVersion", "expectedBudgetVersion"),
                string(root, "plannedRevenue", "plannedRevenue", false),
                readBudgetLines(root)
        );
    }

    public static ProjectBudgetApprovalRequest readBudgetApproval(byte[] body) {
        JsonNode root = parseTree(body);
        closedObject(root, BUDGET_APPROVE_FIELDS, "request");
        return new ProjectBudgetApprovalRequest(
                nonNegativeLong(root, "expectedProjectVersion", "expectedProjectVersion"),
                nonNegativeLong(root, "expectedBudgetVersion", "expectedBudgetVersion")
        );
    }

    private static List<ProjectBudgetLineRequest> readBudgetLines(JsonNode root) {
        JsonNode lines = root.get("lines");
        if (lines == null || !lines.isArray() || lines.isEmpty() || lines.size() > ProjectBudgetCalculator.MAX_LINES) {
            throw new ProjectValidationException("lines", "line_count_invalid");
        }
        List<ProjectBudgetLineRequest> parsed = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            String path = "lines[" + index + "]";
            JsonNode line = lines.get(index);
            closedObject(line, BUDGET_LINE_FIELDS, path);
            parsed.add(new ProjectBudgetLineRequest(
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
            throw new ProjectValidationException("request", "json_invalid");
        }
        if (body.length > MAX_REQUEST_BYTES) {
            throw new ProjectValidationException("request", "request_too_large");
        }
        try {
            return JSON.readTree(body);
        } catch (RuntimeException exception) {
            throw new ProjectValidationException("request", "json_invalid");
        }
    }

    private static void closedObject(JsonNode node, Set<String> allowed, String path) {
        if (node == null || !node.isObject()) {
            throw new ProjectValidationException(path, "object_required");
        }
        for (String name : node.propertyNames()) {
            if (!allowed.contains(name)) {
                throw new ProjectValidationException(path, "unknown_field");
            }
        }
    }

    private static String string(JsonNode parent, String field, String path, boolean nullable) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            if (nullable) {
                return null;
            }
            throw new ProjectValidationException(path, "required");
        }
        if (!node.isString()) {
            throw new ProjectValidationException(path, "string_required");
        }
        return node.stringValue();
    }

    private static Long nonNegativeLong(JsonNode parent, String field, String path) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            throw new ProjectValidationException(path, "required");
        }
        if (!node.isIntegralNumber() || !node.canConvertToLong()) {
            throw new ProjectValidationException(path, "version_invalid");
        }
        long value = node.longValue();
        if (value < 0) {
            throw new ProjectValidationException(path, "version_invalid");
        }
        return value;
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String path) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new ProjectValidationException(path, "enum_invalid");
        }
    }
}
