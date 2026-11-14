package com.app.sme_health_backend.configuration.service;

import com.app.sme_health_backend.configuration.exception.ConfigurationException;
import com.app.sme_health_backend.configuration.model.BusinessConfiguration;
import org.springframework.stereotype.Service;
import java.util.UUID;

/**
 * Capability check supplements existing role authorization; never grants permissions.
 *
 * <p>Backward compatibility: a business whose owner has never saved a configuration ({@code configured = false},
 * which includes every V30-backfilled business and any business without a row) keeps every implemented module, as it
 * had before module enforcement existed. Once an owner saves a configuration, exactly the saved modules (whose
 * dependency closure {@link ModuleResolver} validates) are enabled.
 */
@Service
public class ModuleAccessPolicy {
    private final BusinessConfigurationService configuration;
    private final ModuleRegistry registry;
    public ModuleAccessPolicy(BusinessConfigurationService configuration, ModuleRegistry registry) {
        this.configuration = configuration; this.registry = registry;
    }
    public boolean isEnabled(UUID businessId, String key) {
        var module = registry.find(key).filter(ModuleRegistry.Module::implemented);
        if (module.isEmpty()) return false;
        BusinessConfiguration config = configuration.load(businessId);
        return !config.configured() || module.get().required() || config.requestedModules().contains(key);
    }
    public void requireEnabled(UUID businessId, String key) {
        if (!isEnabled(businessId, key)) throw new ConfigurationException("module_disabled", 403, "This module is unavailable or disabled.");
    }
}
