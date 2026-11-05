package com.app.sme_health_backend.inventory.dto;

import com.app.sme_health_backend.inventory.model.InventoryItemKind;
import com.app.sme_health_backend.inventory.model.InventoryItemRecord;
import com.app.sme_health_backend.inventory.model.InventoryItemStatus;
import com.app.sme_health_backend.inventory.service.InventoryDecimals;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Catalog facts only. No on-hand quantity, stock value or average cost is stored in F08 Phase A. */
public record InventoryItemResponse(
        UUID id,
        UUID businessId,
        String sku,
        String name,
        String unit,
        InventoryItemKind itemKind,
        String category,
        String reorderLevel,
        InventoryItemStatus status,
        long version,
        boolean catalogOnly,
        String stockTracking,
        UUID createdBy,
        UUID updatedBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static final String STOCK_TRACKING_STATUS = "NOT_IMPLEMENTED_PHASE_A";

    public static InventoryItemResponse fromRecord(InventoryItemRecord item) {
        return new InventoryItemResponse(
                item.id(),
                item.businessId(),
                item.sku(),
                item.name(),
                item.unit(),
                item.itemKind(),
                item.category(),
                InventoryDecimals.decimal(item.reorderLevel()),
                item.status(),
                item.version(),
                true,
                STOCK_TRACKING_STATUS,
                item.createdBy(),
                item.updatedBy(),
                item.createdAt(),
                item.updatedAt()
        );
    }
}
