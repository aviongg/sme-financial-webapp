package com.app.sme_health_backend.financialquery.dto;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public record UpcomingObligationsResult(
        LocalDate startDate,
        LocalDate endDate,
        String currency,
        String totalUpcomingReceivables,
        String totalUpcomingPayables,
        String totalOverdueReceivables,
        String totalOverduePayables,
        List<UpcomingObligationItem> obligations,
        List<RecurringProjectionItem> recurringProjections
) {
    public UpcomingObligationsResult {
        obligations = obligations == null ? Collections.emptyList() : List.copyOf(obligations);
        recurringProjections = recurringProjections == null ? Collections.emptyList() : List.copyOf(recurringProjections);
    }
}
