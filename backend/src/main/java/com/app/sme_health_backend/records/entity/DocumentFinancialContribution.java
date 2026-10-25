package com.app.sme_health_backend.records.entity;

import java.math.BigDecimal;
import java.util.UUID;

/** Immutable prospective source record; inserted in the confirmation transaction, never updated. */
public record DocumentFinancialContribution(UUID id, UUID businessId, UUID documentId, UUID monthlyRecordId,
        String targetMonth, String targetClassification, String cashFlowImpact, BigDecimal confirmedAmount,
        UUID actorUserId, String sourceFingerprint, boolean duplicateOverride) { }