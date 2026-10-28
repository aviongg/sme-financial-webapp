package com.app.sme_health_backend.records.projection.dto;

import java.time.LocalDate;

/**
 * End-of-month point-in-time outstanding obligation balances and advance balances.
 */
public record MonthlyObligationPositions(
        LocalDate asOfDate,
        String currency,
        String totalReceivablesOutstanding,
        int totalReceivablesCount,
        String totalPayablesOutstanding,
        int totalPayablesCount,
        String customerAdvancesUnapplied,
        String supplierAdvancesUnapplied,
        String netWorkingCapitalObligations
) {}
