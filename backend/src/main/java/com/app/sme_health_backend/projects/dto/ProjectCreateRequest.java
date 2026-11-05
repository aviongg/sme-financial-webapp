package com.app.sme_health_backend.projects.dto;

import com.app.sme_health_backend.projects.model.ProjectKind;

/**
 * Request payload for creating a new planning project/job/order.
 * Tenant and actor identities come exclusively from the server-resolved BusinessAccessContext.
 */
public record ProjectCreateRequest(
        String code,
        String name,
        ProjectKind kind,
        String description,
        String startDate,
        String targetEndDate
) { }
