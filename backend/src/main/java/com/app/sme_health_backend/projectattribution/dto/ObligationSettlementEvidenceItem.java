package com.app.sme_health_backend.projectattribution.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ObligationSettlementEvidenceItem(
        UUID allocationId,
        UUID financialTransactionId,
        BigDecimal amount,
        LocalDate effectiveDate,
        boolean isAttributedToProject
) {}
