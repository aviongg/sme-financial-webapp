package com.app.sme_health_backend.inventory.service;

import java.util.Map;

public class InventoryValidationException extends RuntimeException {
    private final Map<String, String> errors;

    public InventoryValidationException(String field, String code) {
        super("The inventory request contains an invalid value.");
        this.errors = Map.of(field, code);
    }

    public Map<String, String> errors() {
        return errors;
    }
}
