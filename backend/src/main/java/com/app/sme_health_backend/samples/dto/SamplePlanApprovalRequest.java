package com.app.sme_health_backend.samples.dto;

/**
 * Request payload for approving a draft plan version.
 * Approving a plan version locks it as immutable and supersedes any prior approved plan version.
 */
public record SamplePlanApprovalRequest(
        Long expectedActivityVersion,
        Long expectedPlanVersion
) { }
