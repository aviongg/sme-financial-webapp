package com.app.sme_health_backend.dashboard.workspace;

import com.app.sme_health_backend.ai.AiTypes;
import com.app.sme_health_backend.configuration.dto.ConfigurationResponse;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import org.springframework.stereotype.Component;
import java.util.*;
import static com.app.sme_health_backend.dashboard.workspace.WorkspaceTypes.*;

@Component
public class WorkspaceResolver {
    private final WidgetRegistry registry;
    private final DashboardPresetRegistry presets;
    private final BusinessAuthorizationService authorization;
    public WorkspaceResolver(WidgetRegistry registry, DashboardPresetRegistry presets, BusinessAuthorizationService authorization) {
        this.registry = registry; this.presets = presets; this.authorization = authorization;
    }
    public List<Metadata> catalog(BusinessAccessContext actor, ConfigurationResponse configuration, AiTypes.Status ai) {
        return registry.all().stream().map(widget -> {
            var module = configuration.modules().stream().filter(m -> m.key().equals(widget.module())).findFirst();
            String reason = !authorization.hasRolePermission(actor.role(), widget.permission()) ? "PERMISSION_REQUIRED"
                    : !widget.implemented() || module.isEmpty() || !module.get().implemented() ? "NOT_IMPLEMENTED"
                    : !module.get().available() ? "MODULE_DISABLED"
                    : widget.key().equals("ai-explanation") && !ai.available() ? "AI_UNAVAILABLE" : null;
            return new Metadata(widget.key(), widget.module(), widget.implemented(), widget.required(), widget.sizes(), reason == null, reason);
        }).toList();
    }
    public List<Preference> resolve(String preset, List<Preference> overrides, List<Metadata> catalog) {
        Map<String, Preference> effective = new LinkedHashMap<>();
        presets.defaults(preset, registry).forEach(p -> effective.put(p.key(), p));
        for (Preference override : overrides) {
            var definition = registry.find(override.key());
            // A removed registry key stays persisted but is never rendered or queried.
            if (definition.isEmpty() || !definition.get().sizes().contains(override.size())) continue;
            effective.put(override.key(), new Preference(override.key(), definition.get().required() ? false : override.hidden(), override.order(), override.size()));
        }
        Set<String> allowed = new HashSet<>();
        catalog.stream().filter(Metadata::available).forEach(m -> allowed.add(m.key()));
        var sorted = effective.values().stream().filter(p -> allowed.contains(p.key()))
                .sorted(Comparator.comparing(Preference::order).thenComparing(Preference::key)).toList();
        List<Preference> resolved = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            var p = sorted.get(i); resolved.add(new Preference(p.key(), p.hidden(), i, p.size()));
        }
        return List.copyOf(resolved);
    }
    public List<Preference> validateAndDiff(String preset, List<Preference> input, List<Preference> previous, List<Metadata> catalog) {
        if (input == null || input.size() > 40) throw invalid("Too many widgets.");
        Map<String, Metadata> metadata = new HashMap<>(); catalog.forEach(m -> metadata.put(m.key(), m));
        Set<String> keys = new HashSet<>(); Set<Integer> positions = new HashSet<>();
        for (Preference p : input) {
            if (p == null || p.key() == null || p.hidden() == null || p.order() == null || p.order() < 0 || p.order() > 100)
                throw invalid("Invalid widget layout.");
            var m = metadata.get(p.key());
            if (m == null) throw new WorkspaceException("unknown_widget", 400, "Unknown dashboard widget.");
            if (!m.available()) throw new WorkspaceException("PERMISSION_REQUIRED".equals(m.reason()) ? "widget_forbidden" : "widget_unavailable", 403, "The widget is unavailable for this workspace.");
            if (!keys.add(p.key()) || !positions.add(p.order())) throw invalid("Duplicate widget or position.");
            if (!m.sizes().contains(p.size()) || (m.required() && p.hidden())) throw invalid("Invalid widget size or required widget hidden.");
        }
        Map<String, Preference> baseline = new HashMap<>();
        resolve(preset, List.of(), catalog).forEach(p -> baseline.put(p.key(), p));
        List<Preference> result = new ArrayList<>();
        // Preserve temporarily disabled/restricted/retired overrides without exposing them to the caller.
        previous.stream().filter(p -> !metadata.containsKey(p.key()) || !metadata.get(p.key()).available()).forEach(result::add);
        for (Preference p : input) if (!p.equals(baseline.get(p.key()))) result.add(p);
        return List.copyOf(result);
    }
    private WorkspaceException invalid(String message) { return new WorkspaceException("layout_invalid", 400, message); }
}
