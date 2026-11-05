package com.app.sme_health_backend.configuration.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One atomic business aggregate; operating profile never changes the scoring category. */
public record BusinessConfiguration(UUID businessId, OperatingProfile operatingProfile,
        Map<String, Boolean> operatingAnswers, List<String> requestedModules, long version,
        boolean configured, UUID updatedBy, OffsetDateTime updatedAt) {}
