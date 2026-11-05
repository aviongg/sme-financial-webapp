package com.app.sme_health_backend.samples.dto;

import com.app.sme_health_backend.projects.model.ProjectCostCategory;

/**
 * Input line for a planned activity plan version.
 * Authoritative line cost is computed exclusively on the server as quantity * unitCost.
 */
public record SamplePlanLineRequest(
        ProjectCostCategory category,
        String description,
        String quantity,
        String unit,
        String unitCost
) { }
