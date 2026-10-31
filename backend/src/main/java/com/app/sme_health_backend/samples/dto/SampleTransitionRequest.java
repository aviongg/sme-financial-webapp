package com.app.sme_health_backend.samples.dto;

import com.app.sme_health_backend.samples.model.SampleStatus;

/**
 * Request payload for transitioning a planning activity status (e.g. DRAFT -> ACTIVE -> COMPLETED -> ARCHIVED).
 */
public record SampleTransitionRequest(
        SampleStatus targetStatus,
        String reason,
        Long expectedVersion
) { }
