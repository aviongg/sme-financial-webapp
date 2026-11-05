package com.app.sme_health_backend.configuration.dto;

import com.app.sme_health_backend.configuration.model.OperatingProfile;
import java.util.List;
import java.util.Map;

public record ConfigurationResponse(OperatingProfile operatingProfile, Map<String, Boolean> operatingAnswers,
        List<String> requestedModules, List<ModuleState> modules, List<String> recommendedModules,
        Map<String, String> terminology, String presetKey, long version, String registryVersion,
        boolean configured, boolean canManage) {
    public record ModuleState(String key, boolean implemented, boolean enabled, boolean required,
            List<String> dependencies, boolean available, String reason) {}
}
