package com.app.sme_health_backend.configuration.service;

import com.app.sme_health_backend.configuration.exception.ConfigurationException;
import org.springframework.stereotype.Service;
import java.util.UUID;

/** Capability check supplements existing role authorization; never grants permissions. */
@Service
public class ModuleAccessPolicy {
    private final BusinessConfigurationService configuration;
    private final ModuleRegistry registry;
    public ModuleAccessPolicy(BusinessConfigurationService configuration, ModuleRegistry registry) {
        this.configuration = configuration; this.registry = registry;
    }
    public boolean isEnabled(UUID businessId, String key) {
        return registry.find(key).filter(ModuleRegistry.Module::implemented)
                .filter(m -> configuration.load(businessId).requestedModules().contains(key)).isPresent();
    }
    public void requireEnabled(UUID businessId, String key) {
        if (!isEnabled(businessId, key)) throw new ConfigurationException("module_disabled", 403, "This module is unavailable or disabled.");
    }
}
