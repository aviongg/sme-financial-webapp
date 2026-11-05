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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class BusinessConfigurationService {
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
    public ConfigurationResponse get(BusinessAccessContext context) { return response(load(context.businessId()), context); }
    @Transactional(readOnly = true)
    public BusinessConfiguration load(UUID businessId) {
        return repository.find(businessId).orElseGet(() -> new BusinessConfiguration(businessId,
                OperatingProfile.fromLegacy(repository.legacyCategory(businessId)), Map.of(), ModuleRegistry.LEGACY_DEFAULTS,
                0, false, null, null));
    }
    public ConfigurationResponse preview(BusinessAccessContext context, ConfigurationRequest request) {
        requireOwner(context);
        var modules = resolver.validate(request);
        return response(new BusinessConfiguration(context.businessId(), request.operatingProfile(),
                registry.resolvedAnswers(request.operatingProfile(), request.operatingAnswers()), modules, request.version(), false, null, null), context);
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
        return response(updated, context);
    }
    private void requireOwner(BusinessAccessContext context) {
        if (!authorization.hasRolePermission(context.role(), BusinessPermission.BUSINESS_SETTINGS_MANAGE))
            throw new org.springframework.security.access.AccessDeniedException("Business settings permission required");
    }
    private ConfigurationResponse response(BusinessConfiguration value, BusinessAccessContext context) {
        var states = registry.all().stream().map(module -> {
            boolean enabled = value.requestedModules().contains(module.key());
            boolean permitted = authorization.hasRolePermission(context.role(), module.readPermission());
            String reason = !module.implemented() ? "NOT_IMPLEMENTED" : !permitted ? "PERMISSION_REQUIRED" : !enabled ? "DISABLED" : null;
            return new ConfigurationResponse.ModuleState(module.key(), module.implemented(), enabled, module.required(),
                    module.dependencies(), reason == null, reason);
        }).toList();
        return new ConfigurationResponse(value.operatingProfile(), registry.resolvedAnswers(value.operatingProfile(), value.operatingAnswers()), value.requestedModules(),
                states, registry.recommended(value.operatingProfile(), value.operatingAnswers()), registry.terminology(value.operatingProfile()),
                value.operatingProfile().presetKey(), value.version(), ModuleRegistry.VERSION, value.configured(),
                authorization.hasRolePermission(context.role(), BusinessPermission.BUSINESS_SETTINGS_MANAGE));
    }
}
