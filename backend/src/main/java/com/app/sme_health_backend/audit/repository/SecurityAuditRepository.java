package com.app.sme_health_backend.audit.repository;

import com.app.sme_health_backend.audit.entity.SecurityAuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface SecurityAuditRepository extends JpaRepository<SecurityAuditEvent, UUID> {

    Page<SecurityAuditEvent> findByEventTypeOrderByOccurredAtDesc(String eventType, Pageable pageable);

    Page<SecurityAuditEvent> findByOccurredAtBetweenOrderByOccurredAtDesc(
            OffsetDateTime from, OffsetDateTime to, Pageable pageable);

    Page<SecurityAuditEvent> findByEventTypeAndOccurredAtBetweenOrderByOccurredAtDesc(
            String eventType, OffsetDateTime from, OffsetDateTime to, Pageable pageable);

    List<SecurityAuditEvent> findByActorUserIdOrderByOccurredAtDesc(UUID actorUserId);

    List<SecurityAuditEvent> findByTargetTypeAndTargetIdOrderByOccurredAtDesc(String targetType, String targetId);
}
