package com.app.sme_health_backend.dashboard.workspace;

import com.app.sme_health_backend.ai.AiTypes;
import com.app.sme_health_backend.configuration.dto.ConfigurationResponse;
import com.app.sme_health_backend.configuration.model.OperatingProfile;
import com.app.sme_health_backend.configuration.service.ModuleRegistry;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import static com.app.sme_health_backend.dashboard.workspace.WorkspaceTypes.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceResolverTests {
    final WidgetRegistry registry = new WidgetRegistry();
    final DashboardPresetRegistry presets = new DashboardPresetRegistry();
    final WorkspaceResolver resolver = new WorkspaceResolver(registry, presets, new BusinessAuthorizationService(null));
    final BusinessAccessContext owner = new BusinessAccessContext(UUID.randomUUID(), UUID.randomUUID(), MembershipRole.OWNER);
    final AiTypes.Status enabled = new AiTypes.Status(true, true, "simulated", null, null, 1000, 8);
    ConfigurationResponse configuration(OperatingProfile profile, List<String> enabledModules) {
        var modules = new ModuleRegistry().all().stream().map(m -> new ConfigurationResponse.ModuleState(m.key(), m.implemented(),
                enabledModules.contains(m.key()), m.required(), m.dependencies(), m.implemented() && enabledModules.contains(m.key()), null)).toList();
        return new ConfigurationResponse(profile, Map.of(), enabledModules, modules, List.of(), Map.of(), profile.presetKey(), 0, "f02-v1", true, true);
    }
    List<Metadata> catalog() { return resolver.catalog(owner, configuration(OperatingProfile.SERVICES, ModuleRegistry.LEGACY_DEFAULTS), enabled); }
    @ParameterizedTest @EnumSource(OperatingProfile.class)
    void allFourF02ProfilesHaveDistinctDeterministicCurrentPresets(OperatingProfile profile) {
        var configured = configuration(profile, ModuleRegistry.LEGACY_DEFAULTS);
        var layout = resolver.resolve(configured.presetKey(), List.of(), resolver.catalog(owner, configured, enabled));
        assertEquals("health-score", layout.getFirst().key()); assertFalse(layout.getFirst().hidden());
        assertEquals("wide", layout.getFirst().size());
        assertEquals(layout.size(), layout.stream().map(Preference::key).distinct().count());
        assertEquals(layout, resolver.resolve(configured.presetKey(), List.of(), resolver.catalog(owner, configured, enabled)));
        assertTrue(layout.stream().noneMatch(p -> p.key().equals("inventory") || p.key().equals("projects")));
        assertEquals(4, presets.keys().stream().map(key -> presets.defaults(key, registry)).distinct().count());
    }
    @Test void existingPermissionAndGlobalAiAvailabilityOverrideThePresetAndStoredPreferences() {
        var viewer = new BusinessAccessContext(owner.userId(), owner.businessId(), MembershipRole.VIEWER);
        var disabledAi = new AiTypes.Status(false, false, "disabled", "ai_disabled", null, 1000, 8);
        var catalog = resolver.catalog(viewer, configuration(OperatingProfile.SERVICES, ModuleRegistry.LEGACY_DEFAULTS), disabledAi);
        assertEquals("PERMISSION_REQUIRED", catalog.stream().filter(m -> m.key().equals("document-workflow")).findFirst().orElseThrow().reason());
        assertEquals("AI_UNAVAILABLE", catalog.stream().filter(m -> m.key().equals("ai-explanation")).findFirst().orElseThrow().reason());
        var resolved = resolver.resolve("services", List.of(new Preference("document-workflow", false, 0, "wide"), new Preference("ai-explanation", false, 1, "wide")), catalog);
        assertTrue(resolved.stream().noneMatch(p -> p.key().equals("document-workflow") || p.key().equals("ai-explanation")));
    }
    @Test void rejectsUnknownDuplicateInvalidAndRequiredHiddenWidgets() {
        List<List<Preference>> cases = List.of(
                List.of(new Preference("cash-flow", false, 1, "gigantic")),
                List.of(new Preference("cash-flow", false, 1, "wide"), new Preference("cash-flow", true, 2, "wide")),
                List.of(new Preference("cash-flow", false, 1, "wide"), new Preference("next-step", false, 1, "compact")),
                List.of(new Preference("health-score", true, 0, "wide")),
                List.of(new Preference("health-score", false, 0, "compact")));
        for (var invalid : cases) assertEquals("layout_invalid", assertThrows(WorkspaceException.class,
                () -> resolver.validateAndDiff("services", invalid, List.of(), catalog())).code());
        assertEquals("unknown_widget", assertThrows(WorkspaceException.class, () -> resolver.validateAndDiff("services",
                List.of(new Preference("injected-query", false, 0, "wide")), List.of(), catalog())).code());
        assertEquals("widget_unavailable", assertThrows(WorkspaceException.class, () -> resolver.validateAndDiff("services",
                List.of(new Preference("inventory", false, 0, "wide")), List.of(), catalog())).code());
    }
    @Test void onlyOverridesPersistAndDisabledOverridesSurviveAnUnrelatedSave() {
        var defaults = resolver.resolve("services", List.of(), catalog());
        assertTrue(resolver.validateAndDiff("services", defaults, List.of(), catalog()).isEmpty());
        var old = new Preference("ai-explanation", true, 2, "wide");
        var disabledCatalog = resolver.catalog(owner, configuration(OperatingProfile.SERVICES, List.of("core_finance")), enabled);
        var disabledLayout = resolver.resolve("services", List.of(old), disabledCatalog);
        var saved = resolver.validateAndDiff("services", disabledLayout, List.of(old), disabledCatalog);
        assertTrue(saved.contains(old));
        var restored = resolver.resolve("services", saved, catalog()).stream().filter(p -> p.key().equals("ai-explanation")).findFirst().orElseThrow();
        assertTrue(restored.hidden()); assertEquals("wide", restored.size());
    }
    @Test void retiredKeysAreKeptButNeverRenderedAndRequiredScoreCannotBeRemovedByOmission() {
        var retired = new Preference("retired-widget", false, 0, "wide");
        var stored = resolver.validateAndDiff("services", List.of(), List.of(retired), catalog());
        assertEquals(List.of(retired), stored);
        var resolved = resolver.resolve("services", stored, catalog());
        assertTrue(resolved.stream().anyMatch(p -> p.key().equals("health-score") && !p.hidden()));
        assertTrue(resolved.stream().noneMatch(p -> p.key().equals("retired-widget")));
    }
    @Test void temporarilyDisabledDocumentOverrideKeepsVisibilityWidthAndStoredPosition() {
        var document = new Preference("document-workflow", false, 3, "wide");
        var disabled = resolver.catalog(owner, configuration(OperatingProfile.SERVICES, List.of("core_finance", "ai")), enabled);
        var current = resolver.resolve("services", List.of(document), disabled);
        var edited = current.stream().map(p -> p.key().equals("next-step") ? new Preference(p.key(), true, p.order(), p.size()) : p).toList();
        var saved = resolver.validateAndDiff("services", edited, List.of(document), disabled);
        assertTrue(saved.contains(document));
        var restored = resolver.resolve("services", saved, catalog());
        var value = restored.stream().filter(p -> p.key().equals("document-workflow")).findFirst().orElseThrow();
        assertFalse(value.hidden()); assertEquals("wide", value.size());
        assertEquals(restored, resolver.resolve("services", saved, catalog()));
    }
    @ParameterizedTest @EnumSource(OperatingProfile.class)
    void exactSubmittedNormalizedOrderSurvivesSaveWhenAiIsUnavailableAndPostAiWidgetsShift(OperatingProfile profile) {
        var disabledAi = new AiTypes.Status(false, false, "disabled", "ai_disabled", null, 1000, 8);
        var configured = configuration(profile, ModuleRegistry.LEGACY_DEFAULTS);
        var unavailableAiCatalog = resolver.catalog(owner, configured, disabledAi);
        var inactiveAiOverride = new Preference("ai-explanation", true, 2, "wide");
        var initial = resolver.resolve(profile.presetKey(), List.of(inactiveAiOverride), unavailableAiCatalog);
        assertTrue(initial.stream().noneMatch(p -> p.key().equals("ai-explanation")));
        // Unhide an optional hidden widget and hide/reorder an earlier visible widget to the end so post-AI widgets sit adjacent to moved items.
        String unhideKey = initial.stream().filter(Preference::hidden).findFirst().orElseThrow().key();
        List<Preference> reordered = new ArrayList<>();
        Preference moved = null;
        for (var p : initial) {
            if (p.key().equals("cash-flow")) {
                moved = new Preference(p.key(), true, initial.size() - 1, p.size());
            } else if (p.key().equals(unhideKey)) {
                reordered.add(new Preference(p.key(), false, reordered.size(), p.size()));
            } else {
                reordered.add(new Preference(p.key(), p.hidden(), reordered.size(), p.size()));
            }
        }
        assertNotNull(moved);
        reordered.add(new Preference(moved.key(), moved.hidden(), reordered.size(), moved.size()));
        var savedOverrides = resolver.validateAndDiff(profile.presetKey(), reordered, List.of(inactiveAiOverride), unavailableAiCatalog);
        assertTrue(savedOverrides.contains(inactiveAiOverride));
        var resolvedAfterSave = resolver.resolve(profile.presetKey(), savedOverrides, unavailableAiCatalog);
        assertEquals(reordered, resolvedAfterSave);
        assertTrue(resolvedAfterSave.stream().noneMatch(p -> p.key().equals("ai-explanation")));
        assertEquals("health-score", resolvedAfterSave.getFirst().key());
        assertFalse(resolvedAfterSave.getFirst().hidden());
    }
}
