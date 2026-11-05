package com.app.sme_health_backend.commercial.dto;

import java.util.List;

/**
 * Draft create/update payload. It never carries business, actor, status, number or totals:
 * those are server-owned and rejected by the closed request reader.
 * expectedVersion is null on create and required on update.
 */
public record CommercialDocumentDraftRequest(
        String counterpartyName,
        String documentDate,
        String dueDate,
        String notes,
        List<CommercialLineRequest> lines,
        Long expectedVersion
) { }
