package com.app.sme_health_backend.inventory.ledger;

public enum InventoryMovementType {
    OPENING(true), RECEIPT(true), ISSUE(false), SUPPLIER_RETURN(false), CUSTOMER_RETURN(true),
    ADJUSTMENT_IN(true), ADJUSTMENT_OUT(false), REVERSAL(true);

    private final boolean inbound;

    InventoryMovementType(boolean inbound) {
        this.inbound = inbound;
    }

    /** Natural direction; REVERSAL direction is the inverse of the reversed movement. */
    public boolean inbound() {
        return inbound;
    }
}
