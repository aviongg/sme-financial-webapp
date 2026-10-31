package com.app.sme_health_backend.inventory.dto;

import com.app.sme_health_backend.inventory.model.InventoryItemKind;

/**
 * Catalog create/update payload. Tenant and actor come only from the server-resolved business context;
 * there is no quantity, value or cost field. expectedVersion is null on create and required on update.
 */
public record InventoryItemRequest(
        String sku,
        String name,
        String unit,
        InventoryItemKind itemKind,
        String category,
        String reorderLevel,
        Long expectedVersion
) { }
