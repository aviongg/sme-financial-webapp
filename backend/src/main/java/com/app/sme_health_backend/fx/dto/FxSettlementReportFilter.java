package com.app.sme_health_backend.fx.dto;

import com.app.sme_health_backend.fx.model.FxSettlementStatus;
import com.app.sme_health_backend.fx.model.RecognitionEvidenceStatus;
import com.app.sme_health_backend.fx.model.SettlementDirection;

import java.time.LocalDate;
import java.util.UUID;

public record FxSettlementReportFilter(
        String currency,
        UUID counterpartyId,
        SettlementDirection direction,
        FxSettlementStatus status,
        RecognitionEvidenceStatus evidenceStatus,
        LocalDate startDate,
        LocalDate endDate,
        Integer limit,
        Integer offset
) {
    public FxSettlementReportFilter {
        if (limit == null || limit <= 0) {
            limit = 50;
        } else if (limit > 500) {
            limit = 500;
        }
        if (offset == null || offset < 0) {
            offset = 0;
        }
    }
}

