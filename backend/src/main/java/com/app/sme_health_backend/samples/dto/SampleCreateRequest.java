package com.app.sme_health_backend.samples.dto;

import com.app.sme_health_backend.samples.model.SampleKind;
import com.app.sme_health_backend.samples.model.RecoveryIntent;
import java.util.UUID;

/**
 * Request payload for creating a new sample / production-cost planning activity.
 * Tenant and actor identities come exclusively from the server-resolved BusinessAccessContext.
 */
public record SampleCreateRequest(
        String code,
        String name,
        SampleKind kind,
        String description,
        RecoveryIntent recoveryIntent,
        UUID projectId,
        String startDate,
        String targetEndDate
) { }
