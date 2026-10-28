package com.app.sme_health_backend.financialquery.dto;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public record OverdueReceivablesResult(
        LocalDate asOfDate,
        String currency,
        String totalOverdueAmount,
        int overdueCount,
        List<AgingBucketSummary> agingBuckets,
        List<OverdueReceivableItem> items
) {
    public OverdueReceivablesResult {
        agingBuckets = agingBuckets == null ? Collections.emptyList() : List.copyOf(agingBuckets);
        items = items == null ? Collections.emptyList() : List.copyOf(items);
    }
}
