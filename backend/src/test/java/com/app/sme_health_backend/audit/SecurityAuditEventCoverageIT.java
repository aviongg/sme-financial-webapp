package com.app.sme_health_backend.audit;

import com.app.sme_health_backend.audit.entity.SecurityAuditEvent;
import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.audit.model.AuditOutcome;
import com.app.sme_health_backend.audit.repository.SecurityAuditRepository;
import com.app.sme_health_backend.audit.service.SecurityAuditService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class SecurityAuditEventCoverageIT {

    @Autowired
    private SecurityAuditService auditService;

    @Autowired
    private SecurityAuditRepository auditRepository;

    @Autowired
    private com.app.sme_health_backend.identity.repository.AppUserRepository userRepository;

    @Autowired
    private com.app.sme_health_backend.identity.repository.BusinessRepository businessRepository;

    @Test
    @DisplayName("Verify authoritative event emission and persistence")
    void testAuthoritativeEventsPersistence() {
        com.app.sme_health_backend.identity.entity.AppUser actor = new com.app.sme_health_backend.identity.entity.AppUser();
        actor.setEmail("audit-cov-" + UUID.randomUUID() + "@example.com");
        actor.setPasswordHash("hash12345678");
        actor.setFullName("Audit Coverage User");
        actor.setAccountStatus(com.app.sme_health_backend.identity.model.AccountStatus.ACTIVE);
        actor.setPlatformRole("PLATFORM_ADMIN");
        actor.setAuthVersion(0L);
        actor = userRepository.saveAndFlush(actor);

        com.app.sme_health_backend.identity.entity.Business biz = new com.app.sme_health_backend.identity.entity.Business(UUID.randomUUID(), "ACTIVE");
        biz = businessRepository.saveAndFlush(biz);

        UUID actorId = actor.getId();
        UUID bizId = biz.getId();

        SecurityAuditEvent event = auditService.logSuccess(
                AuditEventType.AUTH_LOGIN_SUCCESS,
                actorId,
                "PLATFORM_ADMIN",
                bizId,
                "USER",
                actorId.toString(),
                Map.of("loginMethod", "PASSWORD_AND_MFA")
        );

        assertNotNull(event.getId());
        SecurityAuditEvent found = auditRepository.findById(event.getId()).orElse(null);
        assertNotNull(found);
        assertEquals(AuditEventType.AUTH_LOGIN_SUCCESS.name(), found.getEventType());
        assertEquals("PLATFORM_ADMIN", found.getActorPlatformRole());
        assertEquals(actorId, found.getActorUserId());
        assertEquals(bizId, found.getBusinessId());
        assertEquals(AuditOutcome.SUCCESS, found.getOutcome());
    }

    @Test
    @DisplayName("Prohibited metadata verification: passwords, tokens, secrets, sessions never appear")
    void testProhibitedMetadataNeverPersisted() {
        // Inspect all recent audit events in DB to ensure no sensitive leakage occurred
        List<SecurityAuditEvent> events = auditRepository.findAll();
        List<String> prohibitedSubstrings = List.of(
                "passwordHash",
                "$argon2id$",
                "totpSecret",
                "rawToken",
                "FINSIGHT_SESSION",
                "X-CSRF-TOKEN"
        );

        for (SecurityAuditEvent event : events) {
            String metadata = event.getMetadata();
            if (metadata != null) {
                for (String prohibited : prohibitedSubstrings) {
                    assertFalse(metadata.contains(prohibited),
                            "Prohibited sensitive substring '" + prohibited + "' found in audit metadata: " + metadata);
                }
            }
        }
    }
}
