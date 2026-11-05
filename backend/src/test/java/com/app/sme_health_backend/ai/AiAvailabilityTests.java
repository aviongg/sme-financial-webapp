package com.app.sme_health_backend.ai;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AiAvailabilityTests {
    final UUID business = UUID.randomUUID();
    final AiProperties p = new AiProperties();
    final MockEnvironment env = new MockEnvironment();
    final AiAvailability availability = new AiAvailability(p, env);
    @Test void disabledByDefaultWithoutKey() { assertEquals("ai_disabled", availability.status(business).reasonCode()); }
    @Test void simulationRequiresBothExplicitSwitchAndDevelopmentProfile() {
        p.setEnabled(true); assertFalse(availability.status(business).available());
        p.setDemoEnabled(true); assertFalse(availability.status(business).available());
        env.setActiveProfiles("dev"); assertEquals("simulated", availability.status(business).mode());
        assertNull(availability.status(business).model());
    }
    @Test void productionAlwaysRejectsSimulationEvenWithMixedProfiles() {
        p.setEnabled(true); p.setDemoEnabled(true); env.setActiveProfiles("dev", "production");
        assertEquals("simulation_prohibited", availability.status(business).reasonCode());
        env.setActiveProfiles("dev", "PRODUCTION");
        assertEquals("simulation_prohibited", availability.status(business).reasonCode());
    }
    @Test void liveRequiresApprovalKeyModelAndExplicitTenantAllowlist() {
        p.setEnabled(true); p.setProvider("openai");
        assertEquals("activation_pending", availability.status(business).reasonCode());
        p.setLiveActivationApproved(true); assertEquals("provider_not_configured", availability.status(business).reasonCode());
        p.setApiKey("fixture-not-a-real-key"); assertEquals("pilot_not_enabled", availability.status(business).reasonCode());
        p.setAllowedBusinessIds(Set.of(business)); assertEquals("openai", availability.status(business).mode());
        assertFalse(availability.status(UUID.randomUUID()).available());
        p.setModel("bad model\n"); assertFalse(availability.status(business).available());
    }
    @Test void unknownProviderDoesNotFallbackToMock() {
        p.setEnabled(true); p.setProvider("unknown"); p.setDemoEnabled(true); env.setActiveProfiles("dev");
        assertEquals("provider_not_supported", availability.status(business).reasonCode());
    }
    @Test void unsafeOperationalBoundsAreClamped() {
        p.setTimeoutSeconds(999); p.setMaxCacheEntries(-1); p.setMaxOutputTokens(Integer.MAX_VALUE);
        assertEquals(30, p.getTimeoutSeconds()); assertEquals(1, p.getMaxCacheEntries()); assertEquals(2000, p.getMaxOutputTokens());
    }
}
