package com.app.sme_health_backend.projects.dto;

/**
 * Request payload for approving a draft budget version.
 * Approving a budget version locks it as immutable and supersedes any prior approved budget version.
 */
public record ProjectBudgetApprovalRequest(
        Long expectedProjectVersion,
        Long expectedBudgetVersion
) { }
