package com.app.sme_health_backend.whatsapp.scheduler;

import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.whatsapp.entity.WhatsAppDelivery;
import com.app.sme_health_backend.whatsapp.entity.WhatsAppDeliveryStatus;
import com.app.sme_health_backend.whatsapp.recovery.WhatsAppDeliveryRecovery;
import com.app.sme_health_backend.whatsapp.service.WhatsAppDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class WhatsAppSummaryScheduler {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppSummaryScheduler.class);

    private final WhatsAppDeliveryService deliveryService;
    private final BusinessProfileRepository profileRepository;
    private final WhatsAppDeliveryRecovery deliveryRecovery;
    private final boolean enabled;

    @Autowired
    public WhatsAppSummaryScheduler(
            WhatsAppDeliveryService deliveryService,
            BusinessProfileRepository profileRepository,
            WhatsAppDeliveryRecovery deliveryRecovery,
            @Value("${app.whatsapp.scheduler.enabled:true}") boolean enabled
    ) {
        this.deliveryService = deliveryService;
        this.profileRepository = profileRepository;
        this.deliveryRecovery = deliveryRecovery;
        this.enabled = enabled;
    }

    public WhatsAppSummaryScheduler(
            WhatsAppDeliveryService deliveryService,
            BusinessProfileRepository profileRepository,
            @Value("${app.whatsapp.scheduler.enabled:true}") boolean enabled
    ) {
        this(deliveryService, profileRepository, null, enabled);
    }

    @Scheduled(
            cron = "${app.whatsapp.scheduler.cron:0 0 9 * * MON}",
            zone = "${app.whatsapp.scheduler.timezone:Asia/Karachi}"
    )
    public void runScheduledWeeklyDelivery() {
        if (!enabled) {
            log.info("WhatsApp weekly summary scheduler is disabled by configuration");
            return;
        }
        log.info("Starting scheduled WhatsApp weekly summary delivery");
        triggerWeeklyDelivery(null);
    }

    public int triggerWeeklyDelivery(LocalDate referenceDate) {
        // Recover any stale SENDING jobs prior to scheduled processing
        if (deliveryRecovery != null) {
            int recovered = deliveryRecovery.recoverStaleDeliveries();
            if (recovered > 0) {
                log.warn("WhatsAppSummaryScheduler: Recovered {} stale SENDING deliveries prior to batch run", recovered);
            }
        }

        LocalDate date = (referenceDate != null)
                ? referenceDate
                : LocalDate.now(deliveryService.getSchedulerZone());

        String cycle = WhatsAppDeliveryService.calculateDeliveryCycle(date, deliveryService.getSchedulerZone());
        log.info("Triggering weekly WhatsApp summaries for cycle {} (reference date: {})", cycle, date);

        List<BusinessProfile> eligibleProfiles =
                profileRepository.findByWhatsappOptInTrueAndWhatsappNumberIsNotNull();

        log.info("Found {} eligible opt-in profiles for cycle {}", eligibleProfiles.size(), cycle);

        int sentCount = 0;
        int failedCount = 0;
        int indeterminateCount = 0;
        int skippedCount = 0;

        for (BusinessProfile profile : eligibleProfiles) {
            try {
                Optional<WhatsAppDelivery> deliveryOpt =
                        deliveryService.deliverWeeklySummary(profile.getUserId(), date);

                if (deliveryOpt.isPresent()) {
                    WhatsAppDelivery delivery = deliveryOpt.get();
                    if (delivery.getDeliveryStatus() == WhatsAppDeliveryStatus.SENT) {
                        sentCount++;
                    } else if (delivery.getDeliveryStatus() == WhatsAppDeliveryStatus.INDETERMINATE) {
                        indeterminateCount++;
                    } else if (delivery.getDeliveryStatus() == WhatsAppDeliveryStatus.FAILED) {
                        failedCount++;
                    } else {
                        skippedCount++;
                    }
                } else {
                    skippedCount++;
                }
            } catch (Exception e) {
                log.error("Unexpected error delivering WhatsApp summary for user {}: {}",
                        profile.getUserId(), e.getMessage(), e);
                failedCount++;
            }
        }

        log.info("Completed weekly WhatsApp summaries for cycle {}: sent={}, indeterminate={}, failed={}, skipped={}",
                cycle, sentCount, indeterminateCount, failedCount, skippedCount);

        return sentCount;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
