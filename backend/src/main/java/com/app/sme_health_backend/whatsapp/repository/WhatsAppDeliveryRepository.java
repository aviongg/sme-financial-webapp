package com.app.sme_health_backend.whatsapp.repository;

import com.app.sme_health_backend.whatsapp.entity.WhatsAppDelivery;
import com.app.sme_health_backend.whatsapp.entity.WhatsAppDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WhatsAppDeliveryRepository extends JpaRepository<WhatsAppDelivery, UUID> {

    Optional<WhatsAppDelivery> findByUserIdAndDeliveryCycle(UUID userId, String deliveryCycle);

    boolean existsByUserIdAndDeliveryCycle(UUID userId, String deliveryCycle);

    Optional<WhatsAppDelivery> findByIdempotencyKey(String idempotencyKey);

    List<WhatsAppDelivery> findByDeliveryStatus(WhatsAppDeliveryStatus deliveryStatus);

    List<WhatsAppDelivery> findByDeliveryCycle(String deliveryCycle);

    List<WhatsAppDelivery> findByUserIdOrderByScheduledAtDesc(UUID userId);

    @Modifying
    @Query("UPDATE WhatsAppDelivery d SET d.deliveryStatus = :newStatus, d.attemptCount = d.attemptCount + 1, d.updatedAt = :now " +
            "WHERE d.id = :id AND d.deliveryStatus = :expectedStatus")
    int transitionStatusIfMatch(
            @Param("id") UUID id,
            @Param("expectedStatus") WhatsAppDeliveryStatus expectedStatus,
            @Param("newStatus") WhatsAppDeliveryStatus newStatus,
            @Param("now") LocalDateTime now
    );
}
