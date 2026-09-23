package com.app.sme_health_backend.whatsapp.recovery;

import com.app.sme_health_backend.whatsapp.entity.WhatsAppDeliveryStatus;
import com.app.sme_health_backend.whatsapp.repository.WhatsAppDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Startup and pre-schedule recovery service to sweep stale SENDING deliveries
 * to INDETERMINATE status following application crash or unpersisted outcomes.
 * Does NOT blindly retry stale deliveries to avoid duplicate outbound messages.
 */
@Component
public class WhatsAppDeliveryRecovery {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppDeliveryRecovery.class);

    private final WhatsAppDeliveryRepository deliveryRepository;
    private final int staleThresholdMinutes;

    public WhatsAppDeliveryRecovery(
            WhatsAppDeliveryRepository deliveryRepository,
            @Value("${app.whatsapp.recovery.stale-threshold-minutes:15}") int staleThresholdMinutes
    ) {
        this.deliveryRepository = deliveryRepository;
        this.staleThresholdMinutes = staleThresholdMinutes > 0 ? staleThresholdMinutes : 15;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationReady() {
        log.info("WhatsAppDeliveryRecovery: Sweeping for stale SENDING deliveries on startup (threshold: {}m)",
                staleThresholdMinutes);
        recoverStaleDeliveries(this.staleThresholdMinutes);
    }

    @Transactional
    public int recoverStaleDeliveries() {
        return recoverStaleDeliveries(this.staleThresholdMinutes);
    }

    @Transactional
    public int recoverStaleDeliveries(int thresholdMinutes) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minusMinutes(thresholdMinutes);
        String reason = String.format("Stale SENDING delivery recovered as INDETERMINATE (exceeded %dm timeout threshold)",
                thresholdMinutes);

        int recovered = deliveryRepository.recoverStaleDeliveries(
                WhatsAppDeliveryStatus.SENDING,
                WhatsAppDeliveryStatus.INDETERMINATE,
                reason,
                threshold,
                now
        );

        if (recovered > 0) {
            log.warn("WhatsAppDeliveryRecovery: Recovered {} stale SENDING delivery/deliveries to INDETERMINATE status (threshold: {}m). No blind retry performed.",
                    recovered, thresholdMinutes);
        } else {
            log.debug("WhatsAppDeliveryRecovery: No stale SENDING deliveries found beyond {}m threshold", thresholdMinutes);
        }

        return recovered;
    }

    public int getStaleThresholdMinutes() {
        return staleThresholdMinutes;
    }
}
