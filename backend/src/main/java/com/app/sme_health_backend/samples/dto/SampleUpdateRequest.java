package com.app.sme_health_backend.samples.dto;

import com.app.sme_health_backend.samples.model.SampleKind;
import com.app.sme_health_backend.samples.model.RecoveryIntent;
import java.util.UUID;

/**
 * Request payload for updating mutable planning activity metadata with optimistic concurrency control.
 */
public record SampleUpdateRequest(
        String code,
        String name,
        SampleKind kind,
        String description,
        RecoveryIntent recoveryIntent,
        UUID projectId,
        String startDate,
        String targetEndDate,
        Long expectedVersion
) { }
