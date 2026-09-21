package com.app.sme_health_backend.zakat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static com.app.sme_health_backend.zakat.dto.ZakatTypes.*;

public record ZakatPreviewRequest(
        Assessment assessment,
        Assets assets,
        Liabilities liabilities,
        Financing financing
) implements StrictZakatRequest {
    public record Assessment(
            LocalDate assessmentDate,
            String currency,
            String nisabMetal,
            BigDecimal metalWeightGrams,
            BigDecimal metalPricePerGram,
            String priceSource,
            @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            OffsetDateTime priceTimestamp,
            HaulStatus haulStatus
    ) implements StrictZakatRequest {
    }

    public record Assets(
            BigDecimal cashAndBankBalances,
            List<InventoryItem> inventory,
            List<Receivable> receivables,
            List<String> unsupportedCategories
    ) implements StrictZakatRequest {
    }

    public record InventoryItem(
            String reference,
            InventoryType type,
            BigDecimal amount,
            InventoryValuation valuation
    ) implements StrictZakatRequest {
    }

    public record Receivable(
            String reference,
            BigDecimal amount,
            ReceivableClassification classification
    ) implements StrictZakatRequest {
    }

    public record LiabilityItem(String obligationId, BigDecimal amount) implements StrictZakatRequest {
    }

    public record Liabilities(
            BigDecimal accountsPayable,
            List<LiabilityItem> currentPayables,
            List<LiabilityItem> principalDueWithin12LunarMonths,
            Boolean principalExcludedFromPayables
    ) implements StrictZakatRequest {
    }

    public record Financing(String financingType, BigDecimal loanOutstanding, BigDecimal interestExpense) implements StrictZakatRequest {
    }
}
