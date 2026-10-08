package com.app.sme_health_backend.platform.controller;

import com.app.sme_health_backend.audit.entity.SecurityAuditEvent;
import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.audit.model.AuditOutcome;
import com.app.sme_health_backend.audit.repository.SecurityAuditRepository;
import com.app.sme_health_backend.audit.service.SecurityAuditService;
import com.app.sme_health_backend.security.service.AppUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/platform")
public class PlatformController {

    private final SecurityAuditRepository auditRepository;
    private final SecurityAuditService auditService;

    public PlatformController(
            SecurityAuditRepository auditRepository,
            SecurityAuditService auditService
    ) {
        this.auditRepository = auditRepository;
        this.auditService = auditService;
    }

    @GetMapping("/audit-events")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Page<SecurityAuditEvent>> listAuditEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            HttpServletRequest httpRequest
    ) {
        int boundedSize = Math.max(1, Math.min(100, size));
        Pageable pageable = PageRequest.of(Math.max(0, page), boundedSize);

        Page<SecurityAuditEvent> result;
        if (eventType != null && !eventType.isBlank() && from != null && to != null) {
            result = auditRepository.findByEventTypeAndOccurredAtBetweenOrderByOccurredAtDesc(
                    eventType.trim(), from, to, pageable
            );
        } else if (eventType != null && !eventType.isBlank()) {
            result = auditRepository.findByEventTypeOrderByOccurredAtDesc(eventType.trim(), pageable);
        } else if (from != null && to != null) {
            result = auditRepository.findByOccurredAtBetweenOrderByOccurredAtDesc(from, to, pageable);
        } else {
            result = auditRepository.findAll(pageable);
        }

        UUID adminId = resolveAdminId();
        auditService.recordEvent(
                AuditEventType.PLATFORM_ADMIN_ACTION,
                adminId,
                "PLATFORM_ADMIN",
                null,
                "AUDIT_LOG",
                "PAGE_" + page,
                AuditOutcome.SUCCESS,
                httpRequest,
                Map.of("action", "inspect_audit_events", "page", page, "size", boundedSize)
        );

        return ResponseEntity.ok(result);
    }

    private UUID resolveAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserDetails details) {
            return details.getId();
        }
        return null;
    }
}
