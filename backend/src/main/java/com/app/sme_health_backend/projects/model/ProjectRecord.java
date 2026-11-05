package com.app.sme_health_backend.projects.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectRecord(
        UUID id,
        UUID businessId,
        String code,
        String name,
        ProjectKind kind,
        String description,
        ProjectStatus status,
        LocalDate startDate,
        LocalDate targetEndDate,
        String baseCurrency,
        long version,
        UUID createdBy,
        UUID updatedBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) { }
