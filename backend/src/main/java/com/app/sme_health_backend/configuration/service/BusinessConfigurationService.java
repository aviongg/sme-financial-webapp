package com.app.sme_health_backend.configuration.service;

import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.audit.service.SecurityAuditService;
import com.app.sme_health_backend.configuration.dto.*;
import com.app.sme_health_backend.configuration.exception.ConfigurationException;
import com.app.sme_health_backend.configuration.model.*;
import com.app.sme_health_backend.configuration.repository.BusinessConfigurationRepository;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class BusinessConfigurationService {
    private static final Logger log = LoggerFactory.getLogger(BusinessConfigurationService.class);
    private final BusinessConfigurationRepository repository;
    private final ModuleRegistry registry;
    private final ModuleResolver resolver;
    private final BusinessAuthorizationService authorization;
    private final SecurityAuditService audit;
    public BusinessConfigurationService(BusinessConfigurationRepository repository, ModuleRegistry registry,
            ModuleResolver resolver, BusinessAuthorizationService authorization, SecurityAuditService audit) {
        this.repository = repository; this.registry = registry; this.resolver = resolver;
        this.authorization = authorization; this.audit = audit;
    }
    @Transactional(readOnly = true)
    public ConfigurationResponse get(BusinessAccessContext context) { return response(load(context.businessId()), context, false); }
    /**
     * Stored configuration with module keys normalised at the read boundary ({@link ModuleRegistry#canonical}): an
     * unknown or unimplemented historical key is dropped (and logged), never mapped to another module, so neither
     * enforcement nor the settings response can be bricked by a stale row and the owner can save a valid configuration.
     */
    @Transactional(readOnly = true)
    public BusinessConfiguration load(UUID businessId) {
        var stored = repository.find(businessId).orElseGet(() -> new BusinessConfiguration(businessId,
                OperatingProfile.fromLegacy(repository.legacyCategory(businessId)), Map.of(), ModuleRegistry.LEGACY_DEFAULTS,
                0, false, null, null));
        var canonical = registry.canonical(stored.requestedModules());
        if (canonical.equals(stored.requestedModules())) return stored;
        var ignored = stored.requestedModules().stream().filter(key -> !canonical.contains(key)).distinct().toList();
        if (!ignored.isEmpty()) log.warn("Ignoring unknown stored module keys {} for business {}", ignored, businessId);
        return new BusinessConfiguration(stored.businessId(), stored.operatingProfile(), stored.operatingAnswers(), canonical,
                stored.version(), stored.configured(), stored.updatedBy(), stored.updatedAt());
    }
    public ConfigurationResponse preview(BusinessAccessContext context, ConfigurationRequest request) {
        requireOwner(context);
        var modules = resolver.validate(request);
        return response(new BusinessConfiguration(context.businessId(), request.operatingProfile(),
                registry.resolvedAnswers(request.operatingProfile(), request.operatingAnswers()), modules, request.version(), false, null, null),
                context, true);
    }
    @Transactional
    public ConfigurationResponse update(BusinessAccessContext context, ConfigurationRequest request) {
        requireOwner(context);
        var modules = resolver.validate(request);
        repository.lockBusiness(context.businessId());
        var previous = load(context.businessId());
        if (request.version() != previous.version())
            throw new ConfigurationException("stale_version", 409, "Configuration changed. Reload before saving.");
        var updated = new BusinessConfiguration(context.businessId(), request.operatingProfile(),
                registry.resolvedAnswers(request.operatingProfile(), request.operatingAnswers()), modules, Math.addExact(previous.version(), 1), true,
                context.userId(), OffsetDateTime.now());
        repository.save(updated);
        audit.logSuccess(AuditEventType.BUSINESS_CONFIGURATION_UPDATED, context.userId(), null, context.businessId(),
                "business_configuration", context.businessId().toString(), Map.of("version", updated.version(),
                        "operatingProfile", updated.operatingProfile().name(), "modules", updated.requestedModules()));
        return response(updated, context, false);
    }
    private void requireOwner(BusinessAccessContext context) {
        if (!authorization.hasRolePermission(context.role(), BusinessPermission.BUSINESS_SETTINGS_MANAGE))
            throw new org.springframework.security.access.AccessDeniedException("Business settings permission required");
    }
    /**
     * Module state uses the same effective rule as {@link ModuleAccessPolicy}. {@code requestedModules} reports the
     * effective selection, so a never-configured business shows (and on save keeps) every implemented module. A preview
     * is evaluated as the configuration that would be saved, while still reporting {@code configured} as stored.
     */
    private ConfigurationResponse response(BusinessConfiguration value, BusinessAccessContext context, boolean preview) {
        boolean configured = value.configured() || preview;
        var effective = registry.effectiveModules(configured, value.requestedModules());
        var states = registry.all().stream().map(module -> {
            boolean enabled = effective.contains(module.key());
            boolean permitted = authorization.hasRolePermission(context.role(), module.readPermission());
            String reason = !module.implemented() ? "NOT_IMPLEMENTED" : !permitted ? "PERMISSION_REQUIRED" : !enabled ? "DISABLED" : null;
            return new ConfigurationResponse.ModuleState(module.key(), module.implemented(), enabled, module.required(),
                    module.dependencies(), reason == null, reason);
        }).toList();
        return new ConfigurationResponse(value.operatingProfile(), registry.resolvedAnswers(value.operatingProfile(), value.operatingAnswers()), effective,
                states, registry.recommended(value.operatingProfile(), value.operatingAnswers()), registry.terminology(value.operatingProfile()),
                value.operatingProfile().presetKey(), value.version(), ModuleRegistry.VERSION, value.configured(),
                authorization.hasRolePermission(context.role(), BusinessPermission.BUSINESS_SETTINGS_MANAGE));
    }
}
