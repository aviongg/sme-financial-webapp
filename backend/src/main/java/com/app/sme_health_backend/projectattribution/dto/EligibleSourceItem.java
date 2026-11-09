package com.app.sme_health_backend.projectattribution.dto;

import com.app.sme_health_backend.projectattribution.model.AttributionDirection;
import com.app.sme_health_backend.projectattribution.model.AttributionSourceType;
import com.app.sme_health_backend.projectattribution.model.EconomicClassification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EligibleSourceItem(
        AttributionSourceType sourceType,
        UUID sourceId,
        AttributionDirection direction,
        String currency,
        BigDecimal totalAmount,
        BigDecimal alreadyAttributedAmount,
        BigDecimal remainingEligibleAmount,
        LocalDate eventDate,
        String description,
        String counterpartyName,
        EconomicClassification economicClassification,
        String referenceNumber
) {
}
