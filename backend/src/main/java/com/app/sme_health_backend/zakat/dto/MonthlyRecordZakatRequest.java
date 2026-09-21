package com.app.sme_health_backend.zakat.dto;

import java.util.List;

import static com.app.sme_health_backend.zakat.dto.ZakatPreviewRequest.*;

public record MonthlyRecordZakatRequest(
        Assessment assessment,
        InventoryItem inventory,
        List<Receivable> receivables,
        List<LiabilityItem> currentPayables,
        List<LiabilityItem> principalDueWithin12LunarMonths,
        Boolean principalExcludedFromPayables,
        List<String> unsupportedCategories
) implements StrictZakatRequest {
}
