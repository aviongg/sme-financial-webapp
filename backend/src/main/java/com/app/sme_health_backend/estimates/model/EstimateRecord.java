package com.app.sme_health_backend.estimates.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EstimateRecord(UUID id, UUID businessId, String code, String name, String description, EstimateStatus status,
                             String baseCurrency, long version, UUID createdByUserId, UUID updatedByUserId,
                             UUID archivedByUserId, OffsetDateTime archivedAt, OffsetDateTime createdAt, OffsetDateTime updatedAt) { }
