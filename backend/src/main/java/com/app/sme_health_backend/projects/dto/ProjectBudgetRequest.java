package com.app.sme_health_backend.projects.dto;

import java.util.List;

/**
 * Request payload for creating or updating a draft project budget version.
 * No businessId, actorUserId, currency, status, or browser-computed totals are accepted.
 */
public record ProjectBudgetRequest(
        Long expectedVersion,
        String plannedRevenue,
        List<ProjectBudgetLineRequest> lines
) { }
