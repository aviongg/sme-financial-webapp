package com.app.sme_health_backend.estimates.dto;

public record EstimateLineRequest(CostGroup costGroup, String description, String quantity, String unit, String unitCost) {
    public enum CostGroup { MATERIAL, LABOUR, OVERHEAD, FREIGHT, OTHER }
}
