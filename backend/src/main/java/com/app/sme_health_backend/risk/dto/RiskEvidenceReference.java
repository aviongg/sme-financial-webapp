package com.app.sme_health_backend.risk.dto;

import com.app.sme_health_backend.risk.model.EvidenceType;

import java.time.LocalDate;
import java.util.Map;

public record RiskEvidenceReference(
        EvidenceType evidenceType,
        String referenceId,
        String description,
        String amount,
        LocalDate eventDate,
        String counterpartyName,
        Map<String, Object> metadata
) {}
