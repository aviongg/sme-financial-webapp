package com.app.sme_health_backend.configuration;

import com.app.sme_health_backend.configuration.dto.ConfigurationRequest;
import com.app.sme_health_backend.configuration.exception.ConfigurationException;
import com.app.sme_health_backend.configuration.model.OperatingProfile;
import com.app.sme_health_backend.configuration.service.*;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ModuleResolverTests {
    final ModuleRegistry registry = new ModuleRegistry();
    final ModuleResolver resolver = new ModuleResolver(registry);
    ConfigurationRequest request(List<String> modules) { return new ConfigurationRequest(OperatingProfile.SERVICES, Map.of(), modules, 0L); }
    @ParameterizedTest @EnumSource(OperatingProfile.class)
    void everyProfileAppliesOnlyImplementedRecommendationsAndKeepsFutureHintsSeparate(OperatingProfile profile) {
        var recommended = registry.recommended(profile, Map.of());
        var applicable = recommended.stream().filter(key -> registry.find(key).orElseThrow().implemented()).toList();
        assertEquals(applicable, resolver.validate(new ConfigurationRequest(profile, Map.of(), applicable, 0L)));
        assertTrue(applicable.contains("core_finance"));
        for (String key : recommended) {
            if (!registry.find(key).orElseThrow().implemented()) {
                assertThrows(ConfigurationException.class, () -> resolver.validate(new ConfigurationRequest(profile, Map.of(), recommended, 0L)));
            }
        }
    }
    @Test void recommendationsAndTerminologyAdaptWithoutChangingScoringCategories() {
        assertTrue(registry.recommended(OperatingProfile.RETAIL_TRADE, Map.of()).contains("whatsapp"));
        assertFalse(registry.recommended(OperatingProfile.SERVICES, Map.of()).contains("whatsapp"));
        assertEquals("projectRevenue", registry.terminology(OperatingProfile.PROJECT_ORDER).get("sales"));
        assertEquals(OperatingProfile.RETAIL_TRADE, OperatingProfile.fromLegacy("trade"));
        assertEquals(OperatingProfile.RETAIL_TRADE, OperatingProfile.fromLegacy("retail"));
        assertEquals(OperatingProfile.MANUFACTURING, OperatingProfile.fromLegacy("manufacturing"));
    }
    @Test void rejectsUnavailableUnknownDuplicateAndMissingDependencyModules() {
        for (List<String> keys : List.of(List.of("documents"), List.of("core_finance", "inventory"),
                List.of("core_finance", "unknown"), List.of("core_finance", "core_finance"))) {
            assertThrows(ConfigurationException.class, () -> resolver.validate(request(keys)));
        }
    }
    @Test void rejectsUnknownAndNullAnswersAndNegativeVersions() {
        assertThrows(ConfigurationException.class, () -> resolver.validate(new ConfigurationRequest(OperatingProfile.SERVICES,
                Map.of("businessId", true), List.of("core_finance"), 0L)));
        Map<String,Boolean> nullable = new HashMap<>(); nullable.put("holdsInventory", null);
        assertThrows(ConfigurationException.class, () -> resolver.validate(new ConfigurationRequest(OperatingProfile.SERVICES,
                nullable, List.of("core_finance"), 0L)));
        assertThrows(ConfigurationException.class, () -> resolver.validate(new ConfigurationRequest(OperatingProfile.SERVICES,
                Map.of(), List.of("core_finance"), -1L)));
    }
    @Test void validatesNonEmptyImmutableAnswersAndCanonicalizesModuleOrder() {
        assertEquals(ModuleRegistry.LEGACY_DEFAULTS, resolver.validate(new ConfigurationRequest(OperatingProfile.PROJECT_ORDER,
                Map.of("tracksProjects", true, "holdsInventory", false), List.of("ai", "documents", "core_finance", "whatsapp"), 4L)));
    }
    @Test void eachOperatingAnswerChangesPlanningRecommendationsWithoutEnablingFutureServices() {
        Map<String, String> hints = Map.of("holdsInventory", "inventory", "tracksProjects", "projects", "sellsOnCredit", "receivables");
        for (var hint : hints.entrySet()) {
            var accepted = new ConfigurationRequest(OperatingProfile.SERVICES, Map.of(hint.getKey(), true), List.of("core_finance"), 0L);
            assertTrue(registry.recommended(OperatingProfile.SERVICES, accepted.operatingAnswers()).contains(hint.getValue()));
            assertFalse(registry.recommended(OperatingProfile.SERVICES, Map.of(hint.getKey(), false)).contains(hint.getValue()));
            assertEquals(List.of("core_finance"), resolver.validate(accepted));
            assertFalse(registry.find(hint.getValue()).orElseThrow().implemented());
            assertThrows(ConfigurationException.class, () -> resolver.validate(new ConfigurationRequest(OperatingProfile.SERVICES,
                    accepted.operatingAnswers(), List.of("core_finance", hint.getValue()), 0L)));
        }
    }
    @Test void completeDefaultAnswersRemainSuggestionsAndExplicitFalseOverridesPresetHints() {
        assertEquals(Map.of("holdsInventory", true, "tracksProjects", false, "sellsOnCredit", false),
                registry.resolvedAnswers(OperatingProfile.MANUFACTURING, Map.of()));
        assertTrue(registry.recommended(OperatingProfile.PROJECT_ORDER, Map.of()).contains("projects"));
        assertFalse(registry.recommended(OperatingProfile.PROJECT_ORDER, Map.of("tracksProjects", false)).contains("projects"));
        assertFalse(registry.recommended(OperatingProfile.RETAIL_TRADE, Map.of("holdsInventory", false)).contains("inventory"));
        assertEquals(Set.of("holdsInventory", "tracksProjects", "sellsOnCredit"), registry.resolvedAnswers(OperatingProfile.SERVICES, Map.of()).keySet());
    }
    @Test void registryRejectsCyclesAndUnknownDependencies() {
        var permission = BusinessPermission.FINANCIAL_DATA_READ;
        assertThrows(IllegalArgumentException.class, () -> new ModuleRegistry(List.of(
                new ModuleRegistry.Module("a", true, false, List.of("b"), permission),
                new ModuleRegistry.Module("b", true, false, List.of("a"), permission))));
        assertThrows(IllegalArgumentException.class, () -> new ModuleRegistry(List.of(
                new ModuleRegistry.Module("a", true, false, List.of("missing"), permission))));
    }
}
