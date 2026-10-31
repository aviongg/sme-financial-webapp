package com.app.sme_health_backend.samples.dto;

import java.util.List;

/**
 * Request payload for creating or updating a draft activity plan version.
 * No businessId, actorUserId, currency, status, or browser-computed totals are accepted.
 */
public record SamplePlanRequest(
        Long expectedVersion,
        String plannedRecovery,
        List<SamplePlanLineRequest> lines
) { }
