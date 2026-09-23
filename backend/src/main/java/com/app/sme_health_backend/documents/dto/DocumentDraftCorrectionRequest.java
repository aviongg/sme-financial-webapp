package com.app.sme_health_backend.documents.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DocumentDraftCorrectionRequest(
        LocalDate date,
        BigDecimal amount,
        String vendorOrParty,
        String category,
        String documentType
) {}
