package com.app.sme_health_backend.fx.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FxRecognitionEvidenceRequest(
        BigDecimal recognitionRate,
        String functionalCurrency,
        LocalDate recognitionDate,
        String recognitionSource,
        String evidenceProvenance,
        String notes
) {
    public FxRecognitionEvidenceRequest {
        if (functionalCurrency == null || functionalCurrency.isBlank()) {
            functionalCurrency = "PKR";
        }
        if (recognitionSource == null || recognitionSource.isBlank()) {
            recognitionSource = "MANUAL_DECLARATION";
        }
        if (evidenceProvenance == null || evidenceProvenance.isBlank()) {
            evidenceProvenance = "VERIFIED_AT_CREATION";
        }
    }
}

