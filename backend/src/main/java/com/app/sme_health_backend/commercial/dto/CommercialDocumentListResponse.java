package com.app.sme_health_backend.commercial.dto;

import com.app.sme_health_backend.commercial.model.CommercialDocumentKind;

import java.util.List;
import java.util.UUID;

/** Bounded register of recorded documents; items omit lines (lines=null) and carry lineCount. */
public record CommercialDocumentListResponse(
        UUID businessId,
        CommercialDocumentKind kind,
        String baseCurrency,
        String financialEffect,
        String settlementAvailability,
        List<CommercialDocumentResponse> items
) { }
