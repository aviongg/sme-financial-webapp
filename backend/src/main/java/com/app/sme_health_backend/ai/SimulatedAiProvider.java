package com.app.sme_health_backend.ai;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class SimulatedAiProvider implements AiProvider {
    @Override public String generate(AiTypes.Context context) {
        return AiJson.MAPPER.writeValueAsString(selection(context));
    }
    static AiTypes.Selection selection(AiTypes.Context c) {
        List<AiTypes.Clause> clauses = new ArrayList<>(c.interpretations());
        clauses.sort(Comparator.comparingInt(clause -> priority(clause.id(), c.topic())));
        return new AiTypes.Selection(AiTypes.SCHEMA_VERSION, c.language(), c.topic(),
                clauses.stream().limit(8).map(AiTypes.Clause::id).toList(),
                c.considerations().stream().limit(6).map(AiTypes.Clause::id).toList(), c.limitationCodes());
    }
    private static int priority(String id, String topic) {
        if (id.startsWith("fact-" + topic + ".") || id.equals("fact-projection")) return 0;
        if (id.startsWith("score-advice-") || id.contains("driver.")) return 1;
        if (id.equals("fact-score.total") || id.equals("fact-score.weakest")) return 2;
        return 3;
    }
}
