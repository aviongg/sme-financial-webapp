package com.app.sme_health_backend.configuration.service;

import com.app.sme_health_backend.configuration.dto.ConfigurationRequest;
import com.app.sme_health_backend.configuration.exception.ConfigurationException;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class ModuleResolver {
    private static final Set<String> ANSWERS = Set.of("holdsInventory", "tracksProjects", "sellsOnCredit");
    private final ModuleRegistry registry;
    public ModuleResolver(ModuleRegistry registry) { this.registry = registry; }
    public List<String> validate(ConfigurationRequest request) {
        if (request == null || request.operatingProfile() == null || request.operatingAnswers() == null
                || request.requestedModules() == null || request.version() == null || request.version() < 0) {
            throw ConfigurationException.invalid("Profile, answers, modules and a non-negative version are required.");
        }
        if (!ANSWERS.containsAll(request.operatingAnswers().keySet()) || request.operatingAnswers().values().stream().anyMatch(Objects::isNull)) {
            throw ConfigurationException.invalid("Unsupported operating answer. Use holdsInventory, tracksProjects and sellsOnCredit.");
        }
        Set<String> requested = new LinkedHashSet<>(request.requestedModules());
        if (requested.size() != request.requestedModules().size()) throw ConfigurationException.invalid("Duplicate module keys.");
        for (String key : requested) {
            var module = registry.find(key).orElseThrow(() -> ConfigurationException.invalid("Unknown module key."));
            if (!module.implemented()) throw ConfigurationException.invalid("The requested module is not implemented: " + key);
            if (!requested.containsAll(module.dependencies())) {
                throw ConfigurationException.invalid("A requested module has missing dependencies: " + key);
            }
        }
        for (var module : registry.all()) {
            if (module.required() && !requested.contains(module.key())) throw ConfigurationException.invalid("Core finance is required.");
        }
        return registry.all().stream().map(ModuleRegistry.Module::key).filter(requested::contains).toList();
    }
}
