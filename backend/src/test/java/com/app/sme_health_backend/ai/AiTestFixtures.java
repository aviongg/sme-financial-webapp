package com.app.sme_health_backend.ai;

import java.util.*;

final class AiTestFixtures {
    private AiTestFixtures() { }
    static AiTypes.Context context(String topic, String language, String score, String buffer, boolean missing) {
        var facts = List.of(new AiTypes.Evidence("score.total", "score", AiText.label("score", language), score, "score_points", "2026-09", "SNAPSHOT"),
                new AiTypes.Evidence("driver.cashflow.cashBufferMonths", "score_driver", AiText.label("cashBufferMonths", language), missing ? null : buffer, "months", "2026-09", "SNAPSHOT"));
        var clauses = List.of(new AiTypes.Clause("fact-score.total", AiText.label("score", language) + ": " + score, List.of("score.total")),
                new AiTypes.Clause("fact-driver.cashflow.cashBufferMonths", AiText.label("cashBufferMonths", language) + ": " + (missing ? AiText.choose(language, "not recorded", "درج نہیں") : buffer), List.of("driver.cashflow.cashBufferMonths")));
        var actions = List.of(new AiTypes.Clause("review-evidence", AiText.choose(language, "Review the recorded evidence before changing a plan.", "منصوبہ بدلنے سے پہلے ریکارڈ شدہ شواہد کا جائزہ لیں۔"), List.of("score.total")));
        List<String> limits = missing ? List.of("snapshot", "no_causality", "missing_data") : List.of("snapshot", "no_causality");
        return new AiTypes.Context(topic, language, facts, clauses, actions, limits,
                AiJson.hash(List.of(topic, language, facts, clauses, actions, limits)), null);
    }
    static String responseEnvelope(String text) {
        return AiJson.MAPPER.writeValueAsString(Map.of("status", "completed", "output", List.of(Map.of(
                "type", "message", "role", "assistant", "content", List.of(Map.of("type", "output_text", "text", text))))));
    }
}
