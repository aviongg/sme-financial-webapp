package com.app.sme_health_backend.ai;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic contract evaluation; authoritative financial parity and real-model quality are separate checks. */
class AiGroundingEvaluationTests {
    record FixtureFact(String id, String kind, String labelKey, String value, String unit, String period, String sourceMode) { }
    record Scenario(String id, String category, int variant, String language, String question,
                    String expectedTopic, String expected, String expectedMonth,
                    List<FixtureFact> evidence, List<String> limitationCodes) {
        @Override public String toString() { return id + " " + category + "/" + variant + " " + expectedTopic; }
    }
    static List<Scenario> loadScenarios() throws IOException {
        try (var stream = AiGroundingEvaluationTests.class.getResourceAsStream("/ai/offline-evaluation-v1.json")) {
            assertNotNull(stream);
            var data = AiJson.MAPPER.readTree(stream);
            assertEquals(2, data.path("revision").asInt());
            List<Scenario> cases = new ArrayList<>();
            for (var node : data.path("scenarios")) cases.add(AiJson.MAPPER.treeToValue(node, Scenario.class));
            assertEquals(100, cases.size());
            assertEquals(100, cases.stream().map(Scenario::id).distinct().count());
            assertEquals(100, cases.stream().map(Scenario::question).distinct().count());
            assertEquals(20, cases.stream().map(Scenario::category).distinct().count());
            assertEquals(30, cases.stream().filter(s -> "valid".equals(s.expected())).count());
            assertEquals(45, cases.stream().filter(s -> "reject".equals(s.expected())).count());
            assertEquals(25, cases.stream().filter(s -> "unsupported".equals(s.expected())).count());
            assertTrue(cases.stream().anyMatch(s -> s.evidence().stream().map(FixtureFact::period).distinct().count() > 1));
            assertEquals(Set.of("NEW", "VIEWED", "DONE", "DISMISSED"), new HashSet<>(cases.stream()
                    .flatMap(s -> s.evidence().stream()).filter(f -> f.id().endsWith(".status")).map(FixtureFact::value).toList()));
            return cases;
        }
    }
    static Stream<Scenario> scenarios() throws IOException { return loadScenarios().stream(); }

    @ParameterizedTest(name = "{0}") @MethodSource("scenarios")
    void evaluatesSafetyAndEvidenceGrounding(Scenario scenario) {
        var router = new AiQuestionRouter();
        var request = router.normalize(new AiTypes.Request("ask", scenario.language(), scenario.question(), null, null, null));
        assertEquals(scenario.expectedMonth(), request.month(), scenario.id());
        String topic = router.topic(request);
        assertEquals(scenario.expectedTopic(), topic, scenario.id());
        if ("unsupported".equals(scenario.expected())) {
            assertTrue(scenario.evidence().isEmpty(), "Blocked questions must not need tenant evidence or provider output");
            return;
        }
        var context = context(scenario);
        verifyTopicCatalog(scenario, context);
        var validator = new AiGroundingValidator();
        String validOutput = new SimulatedAiProvider().generate(context);
        // Every supported fixture has a valid baseline, including the 45 subsequently corrupted outputs.
        var baseline = validator.render(context, validator.validate(validOutput, context), "simulated", null, null);
        assertCatalogPreserved(scenario, context, baseline);
        if ("reject".equals(scenario.expected())) {
            String invalid = corrupted(scenario, validOutput, context);
            assertThrows(IllegalArgumentException.class, () -> validator.validate(invalid, context), scenario.id());
        }
    }

    private static AiTypes.Context context(Scenario s) {
        var evidence = s.evidence().stream().map(f -> new AiTypes.Evidence(f.id(), f.kind(),
                AiText.label(f.labelKey(), s.language()), f.value(), f.unit(), f.period(), f.sourceMode())).toList();
        var clauses = evidence.stream().map(f -> new AiTypes.Clause("fact-" + f.id(),
                f.label() + " (" + f.period() + "): " + (f.value() == null
                        ? AiText.choose(s.language(), "not recorded", "درج نہیں") : f.value()), List.of(f.id()))).toList();
        var actions = evidence.stream().filter(f -> "recommendation".equals(f.kind()) && "text".equals(f.unit()))
                .map(f -> new AiTypes.Clause("action-" + f.id(), f.value(),
                        List.of(f.id(), f.id() + ".priority", f.id() + ".status"))).toList();
        return new AiTypes.Context(s.expectedTopic(), s.language(), evidence, clauses, actions,
                s.limitationCodes(), AiJson.hash(List.of(s.expectedTopic(), s.language(), evidence, clauses, actions, s.limitationCodes())), null);
    }

    private static void verifyTopicCatalog(Scenario s, AiTypes.Context c) {
        assertFalse(c.evidence().isEmpty());
        String expectedKind = switch (s.expectedTopic()) {
            case "records" -> "monthly_record";
            default -> s.expectedTopic();
        };
        assertTrue(c.evidence().stream().anyMatch(f -> expectedKind.equals(f.kind())), s.id());
        if ("projection".equals(s.expectedTopic())) {
            assertTrue(c.evidence().stream().allMatch(f -> "PLAN".equals(f.sourceMode())));
            assertTrue(c.limitationCodes().contains("projection"));
            assertTrue(c.evidence().stream().allMatch(f -> "2026-10".equals(f.period())));
        } else assertTrue(c.evidence().stream().allMatch(f -> "SNAPSHOT".equals(f.sourceMode())));
        if (c.evidence().stream().anyMatch(f -> f.value() == null)) assertTrue(c.limitationCodes().contains("missing_data"));
        if (c.evidence().stream().anyMatch(f -> "recorded_amount".equals(f.unit())))
            assertTrue(c.limitationCodes().contains("currency_unspecified"));
        if ("recommendation".equals(s.expectedTopic())) {
            assertFalse(c.considerations().isEmpty());
            assertTrue(c.evidence().stream().anyMatch(f -> f.id().endsWith(".status")));
            assertTrue(c.evidence().stream().anyMatch(f -> f.id().endsWith(".priority")));
        }
    }

    private static void assertCatalogPreserved(Scenario s, AiTypes.Context c, AiTypes.Response response) {
        assertEquals("simulated", response.provenance().mode());
        assertNull(response.provenance().model());
        assertEquals(s.language(), response.language());
        assertEquals(AiText.summary(s.expectedTopic(), s.language()), response.summary());
        assertEquals(c.evidence(), response.evidence(), "All exact values, periods, units, nulls and PLAN/SNAPSHOT markers must survive");
        for (int i = 0; i < s.evidence().size(); i++) {
            var expected = s.evidence().get(i);
            var actual = response.evidence().get(i);
            assertEquals(expected.value(), actual.value(), expected.id());
            assertEquals(expected.period(), actual.period(), expected.id());
            assertEquals(expected.sourceMode(), actual.sourceMode(), expected.id());
        }
        assertTrue(response.interpretation().size() <= 8);
        assertTrue(response.considerations().size() <= 6);
        assertTrue(c.interpretations().stream().map(AiTypes.Clause::text).toList().containsAll(response.interpretation()));
        assertTrue(c.considerations().stream().map(AiTypes.Clause::text).toList().containsAll(response.considerations()));
        for (String code : s.limitationCodes()) assertTrue(response.limitations().contains(AiText.limitation(code, s.language())));
        assertTrue(response.limitations().contains(AiText.limitation("simulated", s.language())));
    }

    private static String corrupted(Scenario s, String output, AiTypes.Context c) {
        ObjectNode json = (ObjectNode) AiJson.MAPPER.readTree(output);
        int variant = s.variant();
        String first = c.interpretations().getFirst().id();
        switch (s.category()) {
            case "invalid_number" -> {
                switch (variant) {
                    case 0 -> json.put("financialAmount", "999999999.99");
                    case 1 -> json.putArray("interpretationIds").add(999999999);
                    case 2 -> json.putObject("interpretationIds").put("amount", 999999999);
                    case 3 -> json.putArray("interpretationIds").add("Projected cash is 999999999");
                    default -> json.put("summaryCode", "999999999");
                }
            }
            case "invalid_reference" -> {
                switch (variant) {
                    case 0 -> json.putArray("interpretationIds").add("fact-invented-receivable");
                    case 1 -> json.putArray("interpretationIds").add("fact-score.total");
                    case 2 -> json.putArray("interpretationIds");
                    case 3 -> { var ids = json.putArray("interpretationIds"); for (int i = 0; i < 9; i++) ids.add("unknown-" + i); }
                    default -> json.putArray("considerationIds").add(first);
                }
            }
            case "causal_claim" -> {
                switch (variant) {
                    case 0 -> json.putArray("interpretationIds").add("Sales fell because the owner stole money");
                    case 1 -> json.putArray("considerationIds").add("Raise prices because staff caused the loss");
                    case 2 -> json.put("summaryCode", "Revenue fell because of fraud");
                    case 3 -> json.put("explanation", "Guaranteed future cash is positive");
                    default -> json.putArray("causes").add("Poor management caused the score");
                }
            }
            case "wrong_language" -> {
                switch (variant) {
                    case 0 -> json.put("language", "en".equals(s.language()) ? "ur" : "en");
                    case 1 -> json.put("language", "fr");
                    case 2 -> json.putNull("language");
                    case 3 -> json.put("language", 123);
                    default -> json.remove("language");
                }
            }
            case "wrong_schema" -> {
                switch (variant) {
                    case 0 -> json.put("schemaVersion", "arbitrary-v999");
                    case 1 -> json.putNull("schemaVersion");
                    case 2 -> json.put("schemaVersion", 1);
                    case 3 -> json.remove("schemaVersion");
                    default -> json.putObject("schemaVersion").put("value", AiTypes.SCHEMA_VERSION);
                }
            }
            case "missing_limitations" -> {
                switch (variant) {
                    case 0 -> json.putArray("limitationCodes");
                    case 1 -> { var limits = json.putArray("limitationCodes"); c.limitationCodes().stream().skip(1).forEach(limits::add); }
                    case 2 -> json.putArray("limitationCodes").add("unverified_positive_claim");
                    case 3 -> json.putNull("limitationCodes");
                    default -> json.put("limitationCodes", "snapshot");
                }
            }
            case "duplicate_reference" -> {
                switch (variant) {
                    case 0 -> json.putArray("interpretationIds").add(first).add(first);
                    case 1 -> { var limits = json.putArray("limitationCodes"); c.limitationCodes().forEach(limits::add); limits.add(c.limitationCodes().getFirst()); }
                    case 2 -> { var ids = json.putArray("interpretationIds"); ids.add(first).add(c.interpretations().get(1).id()).add(first); }
                    case 3 -> { return output.replace("\"language\":\"" + s.language() + "\"", "\"language\":\"fr\",\"language\":\"" + s.language() + "\""); }
                    default -> { String action = c.considerations().getFirst().id(); json.putArray("considerationIds").add(action).add(action); }
                }
            }
            case "unknown_summary" -> {
                switch (variant) {
                    case 0 -> json.put("summaryCode", "guaranteed_profit");
                    case 1 -> json.put("summaryCode", "score");
                    case 2 -> json.putNull("summaryCode");
                    case 3 -> json.put("summaryCode", 42);
                    default -> json.put("summaryCode", "");
                }
            }
            case "malformed_json" -> {
                return switch (variant) {
                    case 0 -> output.substring(0, output.length() / 2);
                    case 1 -> "[]";
                    case 2 -> "\"unstructured answer\"";
                    case 3 -> "null";
                    default -> "{broken}";
                };
            }
            default -> throw new AssertionError("Unknown mutation family: " + s.category());
        }
        return AiJson.MAPPER.writeValueAsString(json);
    }

    @Test void duplicateJsonPropertiesCannotSmuggleAlternativeClaims() {
        var context = AiTestFixtures.context("score", "en", "45.00", "0", false);
        String json = new SimulatedAiProvider().generate(context).replace("\"language\":\"en\"", "\"language\":\"ur\",\"language\":\"en\"");
        assertThrows(IllegalArgumentException.class, () -> new AiGroundingValidator().validate(json, context));
    }
}
