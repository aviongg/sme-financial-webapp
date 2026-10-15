package com.app.sme_health_backend.configuration.service;

import com.app.sme_health_backend.configuration.model.OperatingProfile;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class ModuleRegistry {
    public static final String VERSION = "f02-v1";
    public static final List<String> LEGACY_DEFAULTS = List.of("core_finance", "documents", "whatsapp", "ai");
    public record Module(String key, boolean implemented, boolean required, List<String> dependencies,
                         BusinessPermission readPermission) {}
    private final List<Module> modules;
    public ModuleRegistry() {
        this(List.of(
            new Module("core_finance", true, true, List.of(), BusinessPermission.FINANCIAL_DATA_READ),
            new Module("documents", true, false, List.of("core_finance"), BusinessPermission.DOCUMENT_READ),
            new Module("whatsapp", true, false, List.of("core_finance"), BusinessPermission.FINANCIAL_DATA_READ),
            new Module("ai", true, false, List.of("core_finance"), BusinessPermission.FINANCIAL_DATA_READ),
            future("transactions", "core_finance"), future("receivables", "transactions"),
            future("invoices", "receivables"), future("projects", "core_finance"), future("costing", "projects"),
            future("inventory", "transactions"), future("production", "inventory"),
            future("commissions", "transactions"), future("multi_currency", "transactions"),
            future("operations", "transactions"), future("reports", "core_finance")
        ));
    }
    public ModuleRegistry(List<Module> modules) {
        this.modules = List.copyOf(modules);
        Set<String> keys = new HashSet<>();
        for (Module module : modules) if (!keys.add(module.key())) throw new IllegalArgumentException("Duplicate module key");
        for (Module module : modules) visit(module.key(), new HashSet<>(), new HashSet<>());
    }
    private static Module future(String key, String dependency) {
        return new Module(key, false, false, List.of(dependency), BusinessPermission.FINANCIAL_DATA_READ);
    }
    private void visit(String key, Set<String> path, Set<String> visited) {
        if (visited.contains(key)) return;
        if (!path.add(key)) throw new IllegalArgumentException("Module dependency cycle");
        Module module = find(key).orElseThrow(() -> new IllegalArgumentException("Unknown registry dependency"));
        for (String dependency : module.dependencies()) visit(dependency, path, visited);
        path.remove(key); visited.add(key);
    }
    public List<Module> all() { return modules; }
    public Optional<Module> find(String key) { return modules.stream().filter(m -> m.key().equals(key)).findFirst(); }
    /** Operational setup suggestions only; these do not alter the legacy financial/scoring profile. */
    public Map<String, Boolean> resolvedAnswers(OperatingProfile profile, Map<String, Boolean> answers) {
        return Map.of(
                "holdsInventory", answers.getOrDefault("holdsInventory", profile == OperatingProfile.MANUFACTURING || profile == OperatingProfile.RETAIL_TRADE),
                "tracksProjects", answers.getOrDefault("tracksProjects", profile == OperatingProfile.PROJECT_ORDER),
                "sellsOnCredit", answers.getOrDefault("sellsOnCredit", false));
    }
    public List<String> recommended(OperatingProfile profile, Map<String, Boolean> answers) {
        var resolved = resolvedAnswers(profile, answers);
        Set<String> recommendations = new LinkedHashSet<>(profile == OperatingProfile.MANUFACTURING || profile == OperatingProfile.RETAIL_TRADE
                ? LEGACY_DEFAULTS : List.of("core_finance", "documents", "ai"));
        // Future capabilities are planning hints only. Availability and requestedModules remain independent;
        // the resolver still rejects attempts to enable services that have not been implemented.
        if (resolved.get("holdsInventory")) recommendations.add("inventory");
        if (resolved.get("tracksProjects")) recommendations.add("projects");
        if (resolved.get("sellsOnCredit")) recommendations.add("receivables");
        return modules.stream().map(Module::key).filter(recommendations::contains).toList();
    }
    public Map<String, String> terminology(OperatingProfile profile) {
        return switch (profile) {
            case MANUFACTURING, RETAIL_TRADE -> Map.of("sales", "sales", "work", "products");
            case SERVICES -> Map.of("sales", "serviceRevenue", "work", "services");
            case PROJECT_ORDER -> Map.of("sales", "projectRevenue", "work", "projects");
        };
    }
}
