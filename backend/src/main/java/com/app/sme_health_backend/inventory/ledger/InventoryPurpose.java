package com.app.sme_health_backend.inventory.ledger;

/**
 * Why stock left inventory. SALE value is cost of goods sold; PROJECT and SAMPLE value is direct material cost of
 * that project/sample; INTERNAL is own consumption (operating cost); WASTAGE is an inventory loss.
 */
public enum InventoryPurpose { SALE, PROJECT, SAMPLE, INTERNAL, WASTAGE }
