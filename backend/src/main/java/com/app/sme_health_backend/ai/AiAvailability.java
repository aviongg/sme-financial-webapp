package com.app.sme_health_backend.ai;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class AiAvailability {
    private final AiProperties properties;
    private final Environment environment;
    public AiAvailability(AiProperties properties, Environment environment) {
        this.properties = properties; this.environment = environment;
    }
    public AiTypes.Status status(UUID businessId) {
        if (!properties.isEnabled()) return unavailable("ai_disabled");
        if ("mock".equals(properties.getProvider())) {
            if (java.util.Arrays.stream(environment.getActiveProfiles()).anyMatch(profile ->
                    "prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)))
                return unavailable("simulation_prohibited");
            if (!properties.isDemoEnabled() || !environment.acceptsProfiles(Profiles.of("dev", "demo")))
                return unavailable("demo_not_enabled");
            return new AiTypes.Status(true, true, "simulated", null, null, 1000, 8);
        }
        if (!"openai".equals(properties.getProvider())) return unavailable("provider_not_supported");
        if (!properties.isLiveActivationApproved()) return unavailable("activation_pending");
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()
                || properties.getModel() == null || !properties.getModel().matches("[A-Za-z0-9._-]{1,100}"))
            return unavailable("provider_not_configured");
        if (!properties.getAllowedBusinessIds().contains(businessId)) return unavailable("pilot_not_enabled");
        return new AiTypes.Status(true, true, "openai", null, properties.getModel(), 1000, 8);
    }
    AiTypes.Status unavailable(String code) {
        return new AiTypes.Status(properties.isEnabled(), false, "disabled", code, null, 1000, 8);
    }
}
