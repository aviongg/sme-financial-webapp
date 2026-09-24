package com.app.sme_health_backend.security.service;

import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.audit.model.AuditOutcome;
import com.app.sme_health_backend.audit.service.SecurityAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class SessionRevocationService {

    private static final Logger log = LoggerFactory.getLogger(SessionRevocationService.class);

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final SecurityAuditService auditService;

    public SessionRevocationService(
            FindByIndexNameSessionRepository<? extends Session> sessionRepository,
            SecurityAuditService auditService
    ) {
        this.sessionRepository = sessionRepository;
        this.auditService = auditService;
    }

    /**
     * Purges all active sessions for the specified principal email across the cluster.
     *
     * @param principalEmail email of the user
     * @param userId user ID
     * @param reason operational reason (PASSWORD_RESET, PASSWORD_CHANGE, ACCOUNT_DISABLED, MFA_RESET, etc.)
     * @return number of revoked sessions
     */
    public int revokeAllSessions(String principalEmail, UUID userId, String reason) {
        if (principalEmail == null || principalEmail.isBlank()) {
            return 0;
        }

        int count = 0;
        try {
            Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(principalEmail);
            if (sessions != null && !sessions.isEmpty()) {
                for (String sessionId : sessions.keySet()) {
                    sessionRepository.deleteById(sessionId);
                    count++;
                }
            }
        } catch (Exception e) {
            log.error("Failed to revoke physical sessions for user {}: {}", principalEmail, e.getMessage(), e);
        }

        auditService.recordEvent(
                AuditEventType.SESSION_REVOKED,
                userId,
                null,
                null,
                "USER",
                userId != null ? userId.toString() : principalEmail,
                AuditOutcome.SUCCESS,
                null,
                Map.of("revoked_count", count, "reason", reason)
        );

        return count;
    }
}
