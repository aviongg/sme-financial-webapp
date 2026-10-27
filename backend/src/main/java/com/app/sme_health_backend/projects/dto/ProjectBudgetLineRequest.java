package com.app.sme_health_backend.projects.dto;

import com.app.sme_health_backend.projects.model.ProjectCostCategory;

/**
 * Input line for a planned project budget version.
 * Authoritative line cost is computed exclusively on the server as quantity * unitCost.
 */
public record ProjectBudgetLineRequest(
        ProjectCostCategory category,
        String description,
        String quantity,
        String unit,
        String unitCost
) { }
