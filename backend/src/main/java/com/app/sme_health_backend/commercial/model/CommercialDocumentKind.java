package com.app.sme_health_backend.commercial.model;

/** One internal document model; separate API paths keep customer and supplier semantics distinct. */
public enum CommercialDocumentKind {
    INVOICE("INV"),
    BILL("BILL");

    private final String numberPrefix;

    CommercialDocumentKind(String numberPrefix) {
        this.numberPrefix = numberPrefix;
    }

    public String numberPrefix() {
        return numberPrefix;
    }

    public String formatNumber(long value) {
        return numberPrefix + "-" + String.format("%06d", value);
    }
}
