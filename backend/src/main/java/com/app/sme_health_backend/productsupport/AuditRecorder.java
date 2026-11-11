package com.app.sme_health_backend.productsupport;

import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.audit.model.AuditOutcome;
import com.app.sme_health_backend.audit.service.SecurityAuditService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/** Fail-closed audit for product-completion state transitions (same transaction as the change). */
@Component
public class AuditRecorder {
    private final SecurityAuditService audit;

    public AuditRecorder(SecurityAuditService audit) {
        this.audit = audit;
    }

    public void record(BusinessAccessContext ctx, AuditEventType type, String targetType, UUID targetId,
                       Map<String, Object> metadata) {
        audit.recordEvent(type, ctx.userId(), null, ctx.businessId(), targetType,
                targetId == null ? null : targetId.toString(), AuditOutcome.SUCCESS, null,
                metadata == null ? Map.of() : metadata);
    }
}
