package com.app.sme_health_backend.configuration.model;

public enum OperatingProfile {
    MANUFACTURING("manufacturing"), RETAIL_TRADE("retail-trade"), SERVICES("services"), PROJECT_ORDER("project-order");
    private final String presetKey;
    OperatingProfile(String presetKey) { this.presetKey = presetKey; }
    public String presetKey() { return presetKey; }
    public static OperatingProfile fromLegacy(String category) {
        if ("manufacturing".equals(category)) return MANUFACTURING;
        if ("trade".equals(category) || "retail".equals(category)) return RETAIL_TRADE;
        return SERVICES;
    }
}
