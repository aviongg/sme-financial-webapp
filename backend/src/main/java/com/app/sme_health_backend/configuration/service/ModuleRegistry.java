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
            // Product completion: every transaction-based module now has an authoritative implementation.
            live("transactions", "core_finance"), live("receivables", "transactions"),
            live("invoices", "receivables"), live("projects", "core_finance"), live("costing", "projects"),
            live("inventory", "transactions"), live("production", "inventory"),
            live("commissions", "transactions"), live("multi_currency", "transactions"),
            live("operations", "transactions"), live("reports", "core_finance")
        ));
    }
    public ModuleRegistry(List<Module> modules) {
        this.modules = List.copyOf(modules);
        Set<String> keys = new HashSet<>();
        for (Module module : modules) if (!keys.add(module.key())) throw new IllegalArgumentException("Duplicate module key");
        for (Module module : modules) visit(module.key(), new HashSet<>(), new HashSet<>());
    }
    private static Module live(String key, String dependency) {
        return new Module(key, true, false, List.of(dependency), BusinessPermission.FINANCIAL_DATA_READ);
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

    /**
     * The single effective-enabled rule, shared by enforcement ({@link ModuleAccessPolicy}) and the configuration
     * response so the two cannot drift. Unimplemented or unknown keys are never enabled. A business whose owner never
     * saved a configuration keeps every implemented module (backward compatible); once saved, a module is enabled only
     * when it is required or explicitly requested, so an explicitly disabled module stays disabled.
     */
    public boolean effectivelyEnabled(String key, boolean configured, Collection<String> requested) {
        return find(key).filter(Module::implemented)
                .map(module -> !configured || module.required() || requested.contains(key)).orElse(false);
    }

    /** Effectively enabled module keys in registry order (see {@link #effectivelyEnabled}). */
    public List<String> effectiveModules(boolean configured, Collection<String> requested) {
        return modules.stream().map(Module::key).filter(key -> effectivelyEnabled(key, configured, requested)).toList();
    }

    /**
     * Read-boundary normalisation of stored keys: keeps only known, implemented keys, de-duplicated, in registry order.
     * Unknown historical keys (for example the pre-registry {@code commercial}) are dropped, never mapped to another
     * module, so a stale row cannot block the owner from loading and re-saving a valid configuration.
     */
    public List<String> canonical(Collection<String> stored) {
        Set<String> keys = stored == null ? Set.of() : new HashSet<>(stored);
        return modules.stream().filter(Module::implemented).map(Module::key).filter(keys::contains).toList();
    }
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
