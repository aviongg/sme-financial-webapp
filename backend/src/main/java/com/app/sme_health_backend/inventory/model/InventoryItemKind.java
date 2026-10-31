package com.app.sme_health_backend.inventory.model;

/**
 * Catalog classification only. FINISHED_GOOD items may be catalogued, but production receipt is not
 * implemented in F08 Phase A.
 */
public enum InventoryItemKind {
    RAW_MATERIAL,
    RESALE_GOOD,
    CONSUMABLE,
    FINISHED_GOOD
}
