package com.app.sme_health_backend.financialquery.dto;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public record CashPositionResult(
        LocalDate asOfDate,
        List<CurrencyCashSummary> currencySummaries,
        List<AccountCashPosition> accounts,
        int totalAccounts
) {
    public CashPositionResult {
        currencySummaries = currencySummaries == null ? Collections.emptyList() : List.copyOf(currencySummaries);
        accounts = accounts == null ? Collections.emptyList() : List.copyOf(accounts);
    }
}
