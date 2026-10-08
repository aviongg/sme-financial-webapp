package com.app.sme_health_backend.audit.service;

import com.app.sme_health_backend.audit.entity.SecurityAuditEvent;
import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.audit.model.AuditOutcome;
import com.app.sme_health_backend.audit.repository.SecurityAuditRepository;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class SecurityAuditService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);
    private static final int MAX_USER_AGENT_LENGTH = 512;
    private static final int MAX_REQUEST_ID_LENGTH = 64;

    private final SecurityAuditRepository auditRepository;
    private final ObjectMapper objectMapper;
    private final com.app.sme_health_backend.identity.repository.AppUserRepository userRepository;

    public SecurityAuditService(SecurityAuditRepository auditRepository) {
        this(auditRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SecurityAuditService(
            SecurityAuditRepository auditRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            com.app.sme_health_backend.identity.repository.AppUserRepository userRepository
    ) {
        this.auditRepository = auditRepository;
        this.userRepository = userRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Records a mandatory audit event in the SAME transaction (Propagation.REQUIRED).
     * If audit persistence fails, the calling business transaction rolls back (fail-closed).
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public SecurityAuditEvent recordEvent(
            AuditEventType eventType,
            UUID actorUserId,
            String actorPlatformRole,
            UUID businessId,
            String targetType,
            String targetId,
            AuditOutcome outcome,
            HttpServletRequest request,
            Map<String, Object> metadata
    ) {
        SecurityAuditEvent event = buildEvent(
                eventType, actorUserId, actorPlatformRole, businessId,
                targetType, targetId, outcome, request, false, metadata
        );
        return auditRepository.save(event);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public SecurityAuditEvent logSuccess(
            AuditEventType eventType,
            UUID actorUserId,
            String actorPlatformRole,
            UUID businessId,
            String targetType,
            String targetId,
            Map<String, ?> metadata
    ) {
        return recordEvent(
                eventType, actorUserId, actorPlatformRole, businessId,
                targetType, targetId, AuditOutcome.SUCCESS, null,
                metadata != null ? new java.util.HashMap<>(metadata) : null
        );
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public SecurityAuditEvent logFailure(
            AuditEventType eventType,
            UUID actorUserId,
            String actorPlatformRole,
            UUID businessId,
            String targetType,
            String targetId,
            String failureReason,
            Map<String, ?> metadata
    ) {
        Map<String, Object> meta = metadata != null ? new java.util.HashMap<>(metadata) : new java.util.HashMap<>();
        if (failureReason != null) {
            meta.put("failureReason", failureReason);
        }
        return recordEvent(
                eventType, actorUserId, actorPlatformRole, businessId,
                targetType, targetId, AuditOutcome.FAILURE, null, meta
        );
    }

    /**
     * Records an asynchronous/system background audit event in the current transaction.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public SecurityAuditEvent recordSystemEvent(
            AuditEventType eventType,
            UUID businessId,
            String targetType,
            String targetId,
            AuditOutcome outcome,
            Map<String, Object> metadata
    ) {
        SecurityAuditEvent event = buildEvent(
                eventType, null, null, businessId,
                targetType, targetId, outcome, null, true, metadata
        );
        return auditRepository.save(event);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public SecurityAuditEvent logSystemEvent(
            AuditEventType eventType,
            UUID businessId,
            String targetType,
            String targetId,
            Map<String, ?> metadata
    ) {
        return recordSystemEvent(
                eventType,
                businessId,
                targetType,
                targetId,
                AuditOutcome.SUCCESS,
                metadata != null ? new java.util.HashMap<>(metadata) : null
        );
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public SecurityAuditEvent logSystemFailure(
            AuditEventType eventType,
            UUID businessId,
            String targetType,
            String targetId,
            String failureReason,
            Map<String, ?> metadata
    ) {
        Map<String, Object> meta = metadata != null ? new java.util.HashMap<>(metadata) : new java.util.HashMap<>();
        if (failureReason != null) {
            meta.put("failureReason", failureReason);
        }
        return recordSystemEvent(
                eventType,
                businessId,
                targetType,
                targetId,
                AuditOutcome.FAILURE,
                meta
        );
    }

    /**
     * Records a security event (e.g. auth failure, rate violation, bad challenge)
     * in an ISOLATED new transaction (Propagation.REQUIRES_NEW).
     * The event commits durably even if the caller transaction rolls back or throws.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SecurityAuditEvent recordSecurityEvent(
            AuditEventType eventType,
            UUID actorUserId,
            String actorPlatformRole,
            UUID businessId,
            String targetType,
            String targetId,
            AuditOutcome outcome,
            HttpServletRequest request,
            Map<String, Object> metadata
    ) {
        SecurityAuditEvent event = buildEvent(
                eventType, actorUserId, actorPlatformRole, businessId,
                targetType, targetId, outcome, request, false, metadata
        );
        return auditRepository.save(event);
    }

    private SecurityAuditEvent buildEvent(
            AuditEventType eventType,
            UUID actorUserId,
            String actorPlatformRole,
            UUID businessId,
            String targetType,
            String targetId,
            AuditOutcome outcome,
            HttpServletRequest request,
            boolean isSystem,
            Map<String, Object> metadata
    ) {
        UUID safeActorUserId = actorUserId;
        Map<String, Object> finalMetadata = metadata != null ? new java.util.HashMap<>(metadata) : new java.util.HashMap<>();
        if (actorUserId != null && userRepository != null) {
            try {
                if (!userRepository.existsById(actorUserId)) {
                    safeActorUserId = null;
                    finalMetadata.put("unlinked_actor_user_id", actorUserId.toString());
                }
            } catch (Exception ignored) {}
        }

        SecurityAuditEvent event = new SecurityAuditEvent();
        event.setOccurredAt(OffsetDateTime.now());
        event.setEventType(eventType.name());
        event.setActorUserId(safeActorUserId);
        event.setActorPlatformRole(actorPlatformRole);
        event.setBusinessId(businessId);
        event.setTargetType(targetType);
        event.setTargetId(targetId);
        event.setOutcome(outcome);
        event.setSystem(isSystem);

        if (request != null) {
            // Source IP: Take actual socket address; do NOT trust X-Forwarded-For until S8 proxy boundary
            event.setSourceIp(request.getRemoteAddr());

            String userAgent = request.getHeader("User-Agent");
            if (userAgent != null) {
                event.setUserAgent(userAgent.length() > MAX_USER_AGENT_LENGTH
                        ? userAgent.substring(0, MAX_USER_AGENT_LENGTH)
                        : userAgent);
            }

            Object reqIdAttr = request.getAttribute("X-Request-ID");
            String reqId = reqIdAttr != null ? reqIdAttr.toString() : request.getHeader("X-Request-ID");
            if (reqId == null || reqId.isBlank()) {
                reqId = UUID.randomUUID().toString();
            }
            if (reqId.length() > MAX_REQUEST_ID_LENGTH) {
                reqId = reqId.substring(0, MAX_REQUEST_ID_LENGTH);
            }
            event.setRequestId(reqId);
        } else if (isSystem) {
            event.setRequestId(UUID.randomUUID().toString());
        }

        if (finalMetadata != null && !finalMetadata.isEmpty()) {
            try {
                event.setMetadata(objectMapper.writeValueAsString(finalMetadata));
            } catch (Exception e) {
                log.warn("Failed to serialize audit metadata for event {}: {}", eventType, e.getMessage());
                event.setMetadata("{}");
            }
        }

        return event;
    }
}
