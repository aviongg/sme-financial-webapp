package com.app.sme_health_backend.counterparty;

public enum CounterpartyType {
    CUSTOMER,
    SUPPLIER,
    BOTH;

    public boolean isCustomerCompatible() {
        return this == CUSTOMER || this == BOTH;
    }

    public boolean isSupplierCompatible() {
        return this == SUPPLIER || this == BOTH;
    }
}
