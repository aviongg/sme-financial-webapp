package com.app.sme_health_backend.estimates.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Immutable lineage from one APPROVED estimate revision to the F05 DRAFT planning budget it created. */
public record EstimateConversionRecord(UUID id, UUID businessId, UUID estimateId, UUID estimateVersionId, UUID projectId,
                                       UUID projectBudgetVersionId, String conversionContractVersion,
                                       String sourceCalculationVersion, String targetCalculationVersion,
                                       UUID convertedByUserId, OffsetDateTime convertedAt) { }
