package com.app.sme_health_backend.dashboard.workspace;

import org.springframework.stereotype.Component;
import java.util.*;
import static com.app.sme_health_backend.dashboard.workspace.WorkspaceTypes.*;

@Component
public class DashboardPresetRegistry {
    public static final String VERSION = "f13-presets-v1";
    private final Map<String, List<String>> presets = Map.of(
            "manufacturing", List.of("health-score", "cash-flow", "financial-summary", "health-components", "next-step", "financial-evidence", "score-completeness", "recommendation", "cash-projection", "ai-explanation"),
            "retail-trade", List.of("health-score", "financial-summary", "cash-flow", "next-step", "health-components", "recommendation", "score-completeness", "cash-projection", "ai-explanation"),
            "services", List.of("health-score", "financial-summary", "health-components", "cash-flow", "score-completeness", "next-step", "financial-evidence", "ai-explanation"),
            "project-order", List.of("health-score", "cash-flow", "next-step", "cash-projection", "financial-evidence", "score-completeness", "recommendation", "ai-explanation"));
    public Set<String> keys() { return presets.keySet(); }
    public List<Preference> defaults(String preset, WidgetRegistry registry) {
        List<String> selected = presets.get(preset);
        if (selected == null) throw new WorkspaceException("unknown_preset", 400, "Unknown dashboard preset.");
        List<String> ordered = new ArrayList<>(selected);
        registry.all().stream().filter(WidgetRegistry.Definition::implemented).map(WidgetRegistry.Definition::key)
                .filter(key -> !ordered.contains(key)).forEach(ordered::add);
        List<Preference> result = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            String key = ordered.get(i);
            boolean wide = key.equals("health-score") || key.equals("cash-flow")
                    || (key.equals("financial-summary") && !preset.equals("project-order"))
                    || (key.equals("financial-evidence") && preset.equals("manufacturing"));
            result.add(new Preference(key, !selected.contains(key), i, wide ? "wide" : "compact"));
        }
        return List.copyOf(result);
    }
}
