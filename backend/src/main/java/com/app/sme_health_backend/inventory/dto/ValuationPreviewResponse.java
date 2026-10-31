package com.app.sme_health_backend.inventory.dto;

import java.util.List;
import java.util.UUID;

/**
 * PLANNING / VALUATION PREVIEW. Nothing in this response is persisted or authoritative: no stock movement,
 * item, MonthlyRecord, score, COGS or accounting entry is created or changed.
 */
public record ValuationPreviewResponse(
        UUID businessId,
        String mode,
        boolean authoritative,
        boolean persisted,
        String financialEffect,
        String baseCurrency,
        Policy policy,
        State opening,
        List<Step> steps,
        State closing,
        Totals totals
) {
    public static final String MODE = "PLANNING_VALUATION_PREVIEW";
    public static final String FINANCIAL_EFFECT = "NONE";

    public record Policy(
            String version,
            String status,
            int valueScale,
            String valueRounding,
            int averageDisplayScale,
            String averageRounding,
            String fullDepletionRule
    ) { }

    /** averageUnitCost is null when quantity is zero. */
    public record State(String quantity, String value, String averageUnitCost) { }

    public record Step(
            int index,
            String type,
            String quantity,
            String receiptValue,
            String issueValue,
            boolean fullDepletion,
            State after
    ) { }

    public record Totals(
            String receivedQuantity,
            String receivedValue,
            String issuedQuantity,
            String issuedValue,
            boolean valueConserved
    ) { }
}
