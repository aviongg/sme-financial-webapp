package com.app.sme_health_backend.dashboard.workspace;

import com.app.sme_health_backend.ai.*;
import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.audit.service.SecurityAuditService;
import com.app.sme_health_backend.configuration.dto.ConfigurationResponse;
import com.app.sme_health_backend.configuration.repository.BusinessConfigurationRepository;
import com.app.sme_health_backend.configuration.service.BusinessConfigurationService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static com.app.sme_health_backend.dashboard.workspace.WorkspaceTypes.*;

@Service
public class DashboardWorkspaceService {
    private final WorkspacePreferenceRepository preferences;
    private final BusinessConfigurationRepository businessLock;
    private final BusinessConfigurationService configurations;
    private final WorkspaceResolver resolver;
    private final WorkspaceQueryService query;
    private final AiAvailability ai;
    private final BusinessAuthorizationService authorization;
    private final SecurityAuditService audit;
    public DashboardWorkspaceService(WorkspacePreferenceRepository preferences, BusinessConfigurationRepository businessLock,
            BusinessConfigurationService configurations, WorkspaceResolver resolver, WorkspaceQueryService query,
            AiAvailability ai, BusinessAuthorizationService authorization, SecurityAuditService audit) {
        this.preferences = preferences; this.businessLock = businessLock; this.configurations = configurations;
        this.resolver = resolver; this.query = query; this.ai = ai; this.authorization = authorization; this.audit = audit;
    }
    @Transactional
    public Response get(BusinessAccessContext actor) {
        requireRead(actor);
        return response(actor, configurations.get(actor), preferences.find(actor.businessId(), actor.userId()), null);
    }
    @Transactional
    public Response preview(BusinessAccessContext actor, String presetKey) {
        requireRead(actor);
        var previous = preferences.find(actor.businessId(), actor.userId());
        return response(actor, configurations.get(actor), new WorkspacePreferenceRepository.Stored(previous.version(), List.of()), presetKey);
    }
    @Transactional(readOnly = true)
    public List<Metadata> catalog(BusinessAccessContext actor) {
        requireRead(actor); var configuration = configurations.get(actor);
        return resolver.catalog(actor, configuration, status(actor, configuration));
    }
    @Transactional
    public Response save(BusinessAccessContext actor, SaveRequest request) {
        requireRead(actor);
        businessLock.lockBusiness(actor.businessId());
        var configuration = configurations.get(actor);
        var previous = preferences.find(actor.businessId(), actor.userId());
        checkVersions(request.version(), request.registryVersion(), request.configurationVersion(), previous, configuration);
        var catalog = resolver.catalog(actor, configuration, status(actor, configuration));
        var overrides = resolver.validateAndDiff(configuration.presetKey(), request.widgets(), previous.overrides(), catalog);
        var updated = new WorkspacePreferenceRepository.Stored(Math.addExact(previous.version(), 1), overrides);
        preferences.save(actor.businessId(), actor.userId(), updated.version(), overrides);
        log(actor, updated.version(), AuditEventType.WORKSPACE_PREFERENCES_SAVED);
        return response(actor, configuration, updated, null);
    }
    @Transactional
    public Response reset(BusinessAccessContext actor, ResetRequest request) {
        requireRead(actor);
        businessLock.lockBusiness(actor.businessId());
        var configuration = configurations.get(actor);
        var previous = preferences.find(actor.businessId(), actor.userId());
        checkVersions(request.version(), request.registryVersion(), request.configurationVersion(), previous, configuration);
        var updated = new WorkspacePreferenceRepository.Stored(Math.addExact(previous.version(), 1), List.of());
        // Keep a versioned row on reset: an old version-zero tab must never become current again.
        preferences.save(actor.businessId(), actor.userId(), updated.version(), updated.overrides());
        log(actor, updated.version(), AuditEventType.WORKSPACE_PREFERENCES_RESET);
        return response(actor, configuration, updated, null);
    }
    private Response response(BusinessAccessContext actor, ConfigurationResponse configuration,
            WorkspacePreferenceRepository.Stored stored, String preview) {
        var status = status(actor, configuration);
        var catalog = resolver.catalog(actor, configuration, status);
        String preset = preview == null ? configuration.presetKey() : preview;
        var effective = resolver.resolve(preset, stored.overrides(), catalog);
        List<Widget> widgets = new ArrayList<>();
        for (var p : effective) if (!p.hidden()) widgets.add(new Widget(p.key(), widgets.size(), p.size()));
        var summary = query.query(actor);
        return new Response(WidgetRegistry.VERSION, DashboardPresetRegistry.VERSION, preset, configuration.presetKey(), stored.version(), configuration.version(),
                preview == null && !stored.overrides().isEmpty(), List.copyOf(widgets), effective, catalog,
                summary.dashboard(), summary.records(), summary.cashHistory(), summary.cashSummaryByPeriod(), "SNAPSHOT", Instant.now(), status);
    }
    private AiTypes.Status status(BusinessAccessContext actor, ConfigurationResponse configuration) {
        var module = configuration.modules().stream().filter(m -> m.key().equals("ai")).findFirst();
        if (module.isEmpty() || !module.get().available())
            return new AiTypes.Status(false, false, "disabled", "module_disabled", null, 1000, 8);
        return ai.status(actor.businessId());
    }
    private void checkVersions(Long version, String registryVersion, Long configurationVersion,
            WorkspacePreferenceRepository.Stored previous, ConfigurationResponse configuration) {
        if (!WidgetRegistry.VERSION.equals(registryVersion)) throw new WorkspaceException("stale_registry", 409, "Widget definitions changed. Reload before saving.");
        if (configurationVersion == null || configurationVersion != configuration.version())
            throw new WorkspaceException("stale_configuration", 409, "Business configuration changed. Reload before saving.");
        if (version == null || version != previous.version()) throw new WorkspaceException("stale_version", 409, "Dashboard changed. Reload before saving.");
    }
    private void requireRead(BusinessAccessContext actor) {
        if (!authorization.hasRolePermission(actor.role(), BusinessPermission.FINANCIAL_DATA_READ))
            throw new AccessDeniedException("Financial read permission required");
    }
    private void log(BusinessAccessContext actor, long version, AuditEventType type) {
        audit.logSuccess(type, actor.userId(), null, actor.businessId(), "dashboard_preferences", actor.userId().toString(), Map.of("version", version, "registryVersion", WidgetRegistry.VERSION));
    }
}
