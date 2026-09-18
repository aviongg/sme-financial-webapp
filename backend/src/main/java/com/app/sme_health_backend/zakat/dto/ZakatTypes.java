package com.app.sme_health_backend.zakat.dto;

public final class ZakatTypes {
    private ZakatTypes() {
    }

    public enum HaulStatus { CONFIRMED, NOT_COMPLETED, UNKNOWN }

    public enum InventoryType { RESALE, FIXED_ASSET, RAW_MATERIAL, WORK_IN_PROGRESS, UNKNOWN }

    public enum InventoryValuation { CURRENT_SELLING_VALUE, HISTORICAL_COST, UNKNOWN }

    public enum ReceivableClassification { GOOD, COLLECTIBLE, DOUBTFUL, BAD, UNRECOVERABLE, UNKNOWN }

    public enum CalculationStatus {
        CALCULATED, BELOW_NISAB, NOT_DUE_HAUL_NOT_COMPLETED,
        INCOMPLETE, INCOMPLETE_HAUL_CONFIRMATION_REQUIRED,
        UNSUPPORTED, MANUAL_REVIEW_REQUIRED
    }

    public enum FinancingComplianceStatus {
        NO_FINANCING_DECLARED, USER_DECLARED_ISLAMIC, CONVENTIONAL_REQUIRES_REVIEW, UNKNOWN
    }
}
