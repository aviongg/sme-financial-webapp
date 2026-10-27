package com.app.sme_health_backend.projects.dto;

import com.app.sme_health_backend.projects.model.ProjectStatus;

/**
 * Request payload for transitioning a planning project status (e.g. DRAFT -> ACTIVE -> COMPLETED -> ARCHIVED).
 */
public record ProjectTransitionRequest(
        ProjectStatus targetStatus,
        String reason,
        Long expectedVersion
) { }
