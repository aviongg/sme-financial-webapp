package com.app.sme_health_backend.inventory.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryMovementRecord(
        UUID id,
        UUID businessId,
        UUID itemId,
        long sequenceNo,
        InventoryMovementType movementType,
        InventoryPurpose purpose,
        BigDecimal quantityDelta,
        BigDecimal valueDelta,
        BigDecimal quantityAfter,
        BigDecimal valueAfter,
        LocalDate movementDate,
        InventorySourceType sourceType,
        UUID sourceId,
        UUID sourceLineId,
        UUID projectId,
        UUID sampleId,
        UUID relatedMovementId,
        String idempotencyKey,
        String payloadHash,
        String policyVersion,
        String notes,
        UUID createdBy,
        OffsetDateTime createdAt
) { }
