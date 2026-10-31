package com.app.sme_health_backend.inventory.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Catalog identity only: no quantity, stock value or average cost exists in F08 Phase A. */
public record InventoryItemRecord(
        UUID id,
        UUID businessId,
        String sku,
        String name,
        String unit,
        InventoryItemKind itemKind,
        String category,
        BigDecimal reorderLevel,
        InventoryItemStatus status,
        long version,
        UUID createdBy,
        UUID updatedBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) { }
