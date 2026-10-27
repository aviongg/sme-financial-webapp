package com.app.sme_health_backend.commercial.dto;

import com.app.sme_health_backend.commercial.model.CommercialDocumentKind;
import com.app.sme_health_backend.commercial.service.CommercialDocumentCalculator;

import java.util.List;

/** Stateless server calculation for the editor. Nothing is persisted and no financial effect exists. */
public record CommercialPreviewResponse(
        CommercialDocumentKind kind,
        String baseCurrency,
        List<Line> lines,
        String subtotal,
        String total,
        String financialEffect,
        String calculationVersion
) {
    public record Line(int lineNumber, String quantity, String unitPrice, String lineAmount) { }

    public static CommercialPreviewResponse from(
            CommercialDocumentKind kind,
            CommercialDocumentCalculator.CalculatedLines calculation
    ) {
        return new CommercialPreviewResponse(
                kind,
                CommercialDocumentCalculator.BASE_CURRENCY,
                calculation.lines().stream().map(line -> new Line(
                        line.lineNumber(),
                        CommercialDocumentCalculator.quantity(line.quantity()),
                        CommercialDocumentCalculator.amount(line.unitPrice()),
                        CommercialDocumentCalculator.amount(line.lineAmount())
                )).toList(),
                CommercialDocumentCalculator.amount(calculation.subtotal()),
                CommercialDocumentCalculator.amount(calculation.total()),
                CommercialDocumentCalculator.FINANCIAL_EFFECT,
                CommercialDocumentCalculator.CALCULATION_VERSION
        );
    }
}
