package com.app.sme_health_backend.bridge.dto;

import com.app.sme_health_backend.records.dto.MonthlyRecordResponse;

import java.time.Instant;
import java.util.UUID;

public record LedgerCutoverResponse(
        UUID monthlyRecordId,
        UUID businessId,
        String yearMonth,
        Long version,
        MonthlyRecordResponse monthlyRecord,
        String bridgeProvenance,
        Instant cutoverTimestamp,
        String message
) {
}

