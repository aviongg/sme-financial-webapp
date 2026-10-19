package com.app.sme_health_backend.obligation.dto;

import java.time.LocalDate;

public record ObligationSummaryTotalsResponse(
        String currency,
        LocalDate asOfDate,
        String totalRecordedReceivables,
        String totalRecordedPayables,
        String outstandingReceivables,
        String outstandingPayables,
        String overdueReceivables,
        String overduePayables,
        long customerCountWithBalance,
        long supplierCountWithBalance,
        String dueSoonReceivables,
        String dueSoonPayables
) {
}
