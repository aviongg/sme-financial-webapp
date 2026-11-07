package com.app.sme_health_backend.funding.dto;

import java.util.List;

public record ScenarioDefinition(
        String scenarioName,
        String description,
        List<PlannedCashEventInput> events
) {}
