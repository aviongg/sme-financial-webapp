package com.app.sme_health_backend.inventory.dto;

import java.util.List;
import java.util.UUID;

public record InventoryItemListResponse(
        UUID businessId,
        boolean catalogOnly,
        String stockTracking,
        List<InventoryItemResponse> items
) { }
