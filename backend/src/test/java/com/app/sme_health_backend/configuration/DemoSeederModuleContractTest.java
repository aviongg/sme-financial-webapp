package com.app.sme_health_backend.configuration;

import com.app.sme_health_backend.configuration.dto.ConfigurationRequest;
import com.app.sme_health_backend.configuration.model.OperatingProfile;
import com.app.sme_health_backend.configuration.service.ModuleRegistry;
import com.app.sme_health_backend.configuration.service.ModuleResolver;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DEF-02/DEF-21 regression contract: the demo seeder (scripts/, not compiled by Maven) must request canonical, valid
 * ModuleRegistry keys. It previously wrote the non-registry key {@code commercial} and omitted real modules, which with
 * server-side enforcement locked seeded businesses out of invoices, bills, transactions, estimates, samples, etc.
 * Also pins the shared effective-enabled rule (DEF-23) and read-boundary normalisation.
 */
class DemoSeederModuleContractTest {
    private final ModuleRegistry registry = new ModuleRegistry();

    static String seederSource() throws Exception {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path base : List.of(cwd, cwd.getParent())) {
            if (base == null) continue;
            Path candidate = base.resolve("scripts").resolve("DemoScenarioSeeder.java");
            if (Files.isRegularFile(candidate)) return Files.readString(candidate);
        }
        throw new AssertionError("scripts/DemoScenarioSeeder.java not found from " + cwd);
    }

    static List<String> seederModules(String source) {
        Matcher block = Pattern.compile("DEMO_MODULES\\s*=\\s*List\\.of\\(([^)]*)\\)").matcher(source);
        assertTrue(block.find(), "DemoScenarioSeeder must declare DEMO_MODULES = List.of(...)");
        List<String> keys = new ArrayList<>();
        Matcher literal = Pattern.compile("\"([^\"]+)\"").matcher(block.group(1));
        while (literal.find()) keys.add(literal.group(1));
        return keys;
    }

    @Test void seederRequestsOnlyCanonicalValidModulesAndEnablesEveryImplementedModule() throws Exception {
        String source = seederSource();
        List<String> keys = seederModules(source);
        assertFalse(keys.isEmpty());
        for (String key : keys) {
            var module = registry.find(key);
            assertTrue(module.isPresent(), "Seeder requests unknown module key: " + key);
            assertTrue(module.get().implemented(), "Seeder requests unimplemented module: " + key);
        }
        // The same validation an owner's save goes through: known, implemented, no duplicates, dependency-closed,
        // required modules present, canonical order.
        var validated = new ModuleResolver(registry).validate(
                new ConfigurationRequest(OperatingProfile.MANUFACTURING, Map.of(), keys, 0L));
        assertEquals(keys, validated, "Seeder modules must already be canonical (registry order, no duplicates)");
        // Demo businesses are fully enabled.
        assertEquals(registry.all().stream().filter(ModuleRegistry.Module::implemented).map(ModuleRegistry.Module::key).toList(), keys);
        // No hand-written module JSON may bypass the validated constant.
        assertFalse(source.contains("\\\"commercial\\\""), "The non-registry key 'commercial' must not be seeded");
        assertFalse(Pattern.compile("requested_modules[^;]*'\\[").matcher(source).find(),
                "requested_modules must be written from DEMO_MODULES, not a literal JSON array");
    }

    @Test void effectiveRuleKeepsLegacyCompatibilityRequiredModulesAndExplicitDisables() {
        var implemented = registry.all().stream().filter(ModuleRegistry.Module::implemented).map(ModuleRegistry.Module::key).toList();
        // Never configured: every implemented module, regardless of the stored legacy default list.
        assertEquals(implemented, registry.effectiveModules(false, ModuleRegistry.LEGACY_DEFAULTS));
        // Configured: required core_finance is always on; anything not requested stays disabled.
        assertEquals(List.of("core_finance"), registry.effectiveModules(true, List.of()));
        assertEquals(List.of("core_finance", "transactions", "inventory"),
                registry.effectiveModules(true, List.of("inventory", "transactions")));
        assertFalse(registry.effectivelyEnabled("invoices", true, List.of("core_finance", "transactions")));
        // Unknown keys are never enabled, configured or not.
        assertFalse(registry.effectivelyEnabled("commercial", false, List.of("commercial")));
        assertFalse(registry.effectivelyEnabled("commercial", true, List.of("commercial")));
    }

    @Test void readBoundaryDropsUnknownKeysWithoutMappingThemToOtherModules() {
        var legacySeed = List.of("core_finance", "documents", "commercial", "inventory", "projects", "whatsapp", "ai");
        assertEquals(List.of("core_finance", "documents", "whatsapp", "ai", "projects", "inventory"), registry.canonical(legacySeed));
        // 'commercial' is dropped, not translated into invoices/receivables.
        assertFalse(registry.canonical(legacySeed).contains("invoices"));
        assertFalse(registry.canonical(legacySeed).contains("receivables"));
        assertEquals(List.of("core_finance"), registry.canonical(List.of("core_finance", "core_finance", "unknown")));
        assertEquals(List.of(), registry.canonical(null));
    }
}
