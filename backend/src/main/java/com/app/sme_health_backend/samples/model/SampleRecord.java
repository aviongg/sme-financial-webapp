package com.app.sme_health_backend.samples.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SampleRecord(
        UUID id,
        UUID businessId,
        String code,
        String name,
        SampleKind kind,
        String description,
        RecoveryIntent recoveryIntent,
        UUID projectId,
        SampleStatus status,
        LocalDate startDate,
        LocalDate targetEndDate,
        String baseCurrency,
        long version,
        UUID createdBy,
        UUID updatedBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) { }
