package com.app.sme_health_backend.financialquery.dto;

import com.app.sme_health_backend.financialquery.model.CapabilityStatus;
import com.app.sme_health_backend.financialquery.model.DataCompleteness;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Standard typed envelope for all FinancialQueryFacade tool responses.
 *
 * @param <T> data payload type
 */
public record FinancialQueryEnvelope<T>(
        String toolName,
        String contractVersion,
        UUID businessId,
        Instant evaluatedAt,
        String asOfDate,
        CapabilityStatus status,
        DataCompleteness completeness,
        String unavailableReason,
        List<String> warnings,
        List<FinancialEvidenceReference> evidence,
        T data
) {
    public FinancialQueryEnvelope {
        warnings = warnings == null ? Collections.emptyList() : List.copyOf(warnings);
        evidence = evidence == null ? Collections.emptyList() : List.copyOf(evidence);
    }

    public static <T> FinancialQueryEnvelope<T> available(
            String toolName,
            String contractVersion,
            UUID businessId,
            String asOfDate,
            DataCompleteness completeness,
            List<String> warnings,
            List<FinancialEvidenceReference> evidence,
            T data
    ) {
        return new FinancialQueryEnvelope<>(
                toolName,
                contractVersion,
                businessId,
                Instant.now(),
                asOfDate,
                CapabilityStatus.AVAILABLE,
                completeness,
                null,
                warnings,
                evidence,
                data
        );
    }

    public static <T> FinancialQueryEnvelope<T> unavailable(
            String toolName,
            String contractVersion,
            UUID businessId,
            String reason
    ) {
        return new FinancialQueryEnvelope<>(
                toolName,
                contractVersion,
                businessId,
                Instant.now(),
                null,
                CapabilityStatus.UNAVAILABLE,
                DataCompleteness.EMPTY,
                reason,
                Collections.emptyList(),
                Collections.emptyList(),
                null
        );
    }
}
