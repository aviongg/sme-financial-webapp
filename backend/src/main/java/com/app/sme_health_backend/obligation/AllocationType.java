package com.app.sme_health_backend.obligation;

public enum AllocationType {
    CASH,
    ADVANCE,
    /** Non-cash reduction by an F04 credit note (invoice) or debit note (bill); see V45. */
    CREDIT_NOTE
}
