package com.app.sme_health_backend.projects.dto;

import com.app.sme_health_backend.projects.model.ProjectKind;

/**
 * Request payload for updating mutable planning project metadata with optimistic concurrency control.
 */
public record ProjectUpdateRequest(
        String code,
        String name,
        ProjectKind kind,
        String description,
        String startDate,
        String targetEndDate,
        Long expectedVersion
) { }
