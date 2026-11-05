package com.app.sme_health_backend.ai;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import java.util.*;

@Component
public class AiGroundingValidator {
    public AiTypes.Selection validate(String output, AiTypes.Context context) {
        try {
            if (output == null || output.length() > 32000) throw invalid();
            JsonNode root = AiJson.MAPPER.readTree(output);
            Set<String> actual = new HashSet<>();
            root.propertyNames().forEach(actual::add);
            if (!root.isObject() || !actual.equals(Set.of("schemaVersion", "language", "summaryCode",
                    "interpretationIds", "considerationIds", "limitationCodes"))) throw invalid();
            if (!text(root, "schemaVersion").equals(AiTypes.SCHEMA_VERSION)
                    || !text(root, "language").equals(context.language())
                    || !text(root, "summaryCode").equals(context.topic())) throw invalid();
            var interpretations = array(root, "interpretationIds", 8);
            var considerations = array(root, "considerationIds", 6);
            var limitations = array(root, "limitationCodes", 12);
            if (!context.interpretations().isEmpty() && interpretations.isEmpty()) throw invalid();
            verifyClauses(interpretations, context.interpretations(), context.evidence());
            verifyClauses(considerations, context.considerations(), context.evidence());
            // A model cannot suppress a mandatory missing-data, snapshot or forecast qualification.
            if (!new HashSet<>(limitations).equals(new HashSet<>(context.limitationCodes()))) throw invalid();
            return new AiTypes.Selection(AiTypes.SCHEMA_VERSION, context.language(), context.topic(),
                    interpretations, considerations, limitations);
        } catch (RuntimeException e) { throw invalid(); }
    }
    private static void verifyClauses(List<String> ids, List<AiTypes.Clause> candidates, List<AiTypes.Evidence> facts) {
        Set<String> factIds = new HashSet<>(); facts.forEach(f -> factIds.add(f.id()));
        for (String id : ids) {
            var clause = candidates.stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow(AiGroundingValidator::invalid);
            if (clause.evidenceIds().isEmpty() || !factIds.containsAll(clause.evidenceIds())) throw invalid();
        }
    }
    private static List<String> array(JsonNode root, String name, int max) {
        var node = root.get(name);
        if (node == null || !node.isArray() || node.size() > max) throw invalid();
        List<String> values = new ArrayList<>();
        for (var value : node) {
            if (!value.isString() || value.asString().length() > 150) throw invalid();
            values.add(value.asString());
        }
        if (new HashSet<>(values).size() != values.size()) throw invalid();
        return List.copyOf(values);
    }
    private static String text(JsonNode root, String name) {
        var value = root.get(name);
        if (value == null || !value.isString()) throw invalid();
        return value.asString();
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid grounded AI response"); }

    public AiTypes.Response render(AiTypes.Context c, AiTypes.Selection selection, String mode, String model, String reason) {
        List<String> limitations = new ArrayList<>(c.limitationCodes().stream().map(code -> AiText.limitation(code, c.language())).toList());
        if ("simulated".equals(mode)) limitations.add(AiText.limitation("simulated", c.language()));
        if ("deterministic".equals(mode) && reason != null && !"unsupported_question".equals(reason))
            limitations.add(AiText.limitation("provider_unavailable", c.language()));
        String summary = c.unavailableReason() != null && !"unsupported".equals(c.topic())
                ? AiText.limitation("stale_source".equals(c.unavailableReason()) ? "stale_source" : "missing_data", c.language())
                : c.evidence().isEmpty() && !"unsupported".equals(c.topic()) ? AiText.limitation("provider_unavailable", c.language())
                : AiText.summary(c.topic(), c.language());
        return new AiTypes.Response(AiTypes.SCHEMA_VERSION, c.language(), summary,
                texts(selection.interpretationIds(), c.interpretations()),
                texts(selection.considerationIds(), c.considerations()), List.copyOf(limitations), c.evidence(),
                new AiTypes.Provenance(mode, model, AiTypes.PROMPT_VERSION, c.fingerprint(), false), reason);
    }
    private static List<String> texts(List<String> ids, List<AiTypes.Clause> clauses) {
        return ids.stream().map(id -> clauses.stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow().text()).toList();
    }
}
