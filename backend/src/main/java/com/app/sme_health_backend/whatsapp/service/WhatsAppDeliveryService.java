package com.app.sme_health_backend.whatsapp.service;

import com.app.sme_health_backend.insight.entity.Insight;
import com.app.sme_health_backend.insight.service.InsightService;
import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.recommendation.entity.Recommendation;
import com.app.sme_health_backend.recommendation.service.RecommendationService;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import com.app.sme_health_backend.scoring.repository.ScoreResultRepository;
import com.app.sme_health_backend.whatsapp.client.WhatsAppClient;
import com.app.sme_health_backend.whatsapp.client.WhatsAppSendRequest;
import com.app.sme_health_backend.whatsapp.client.WhatsAppSendResult;
import com.app.sme_health_backend.whatsapp.dto.WhatsAppDeliveryResponse;
import com.app.sme_health_backend.whatsapp.entity.WhatsAppDelivery;
import com.app.sme_health_backend.whatsapp.entity.WhatsAppDeliveryStatus;
import com.app.sme_health_backend.whatsapp.repository.WhatsAppDeliveryRepository;
import com.app.sme_health_backend.whatsapp.validation.PhoneNumberValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class WhatsAppDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppDeliveryService.class);

    private final WhatsAppDeliveryRepository deliveryRepository;
    private final BusinessProfileRepository profileRepository;
    private final ScoreResultRepository scoreResultRepository;
    private final InsightService insightService;
    private final RecommendationService recommendationService;
    private final WhatsAppSummaryComposer summaryComposer;
    private final WhatsAppClient whatsappClient;
    private final ZoneId schedulerZone;
    private final com.app.sme_health_backend.audit.service.SecurityAuditService auditService;

    public WhatsAppDeliveryService(
            WhatsAppDeliveryRepository deliveryRepository,
            BusinessProfileRepository profileRepository,
            ScoreResultRepository scoreResultRepository,
            InsightService insightService,
            RecommendationService recommendationService,
            WhatsAppSummaryComposer summaryComposer,
            WhatsAppClient whatsappClient,
            @Value("${app.whatsapp.scheduler.timezone:Asia/Karachi}") String timezone
    ) {
        this(deliveryRepository, profileRepository, scoreResultRepository, insightService,
                recommendationService, summaryComposer, whatsappClient, timezone, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public WhatsAppDeliveryService(
            WhatsAppDeliveryRepository deliveryRepository,
            BusinessProfileRepository profileRepository,
            ScoreResultRepository scoreResultRepository,
            InsightService insightService,
            RecommendationService recommendationService,
            WhatsAppSummaryComposer summaryComposer,
            WhatsAppClient whatsappClient,
            @Value("${app.whatsapp.scheduler.timezone:Asia/Karachi}") String timezone,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.app.sme_health_backend.audit.service.SecurityAuditService auditService
    ) {
        this.deliveryRepository = deliveryRepository;
        this.profileRepository = profileRepository;
        this.scoreResultRepository = scoreResultRepository;
        this.insightService = insightService;
        this.recommendationService = recommendationService;
        this.summaryComposer = summaryComposer;
        this.whatsappClient = whatsappClient;
        this.auditService = auditService;

        ZoneId zone;
        try {
            zone = ZoneId.of(timezone);
        } catch (Exception e) {
            log.warn("Invalid timezone '{}', defaulting to Asia/Karachi", timezone);
            zone = ZoneId.of("Asia/Karachi");
        }
        this.schedulerZone = zone;
    }

    public static String calculateDeliveryCycle(ZonedDateTime zonedDateTime) {
        if (zonedDateTime == null) {
            throw new IllegalArgumentException("DateTime is required to calculate delivery cycle");
        }
        int week = zonedDateTime.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int year = zonedDateTime.get(IsoFields.WEEK_BASED_YEAR);
        return String.format("%d-W%02d", year, week);
    }

    public static String calculateDeliveryCycle(LocalDate date, ZoneId zoneId) {
        if (date == null) {
            throw new IllegalArgumentException("Date is required to calculate delivery cycle");
        }
        ZoneId zone = zoneId != null ? zoneId : ZoneId.of("Asia/Karachi");
        return calculateDeliveryCycle(date.atStartOfDay(zone));
    }

    public ZoneId getSchedulerZone() {
        return schedulerZone;
    }

    public Optional<WhatsAppDelivery> deliverWeeklySummary(UUID userId, LocalDate referenceDate) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        LocalDate targetDate = (referenceDate != null) ? referenceDate : LocalDate.now(schedulerZone);
        String deliveryCycle = calculateDeliveryCycle(targetDate, schedulerZone);

        // Check if delivery already exists for this weekly cycle
        Optional<WhatsAppDelivery> existing = deliveryRepository.findByUserIdAndDeliveryCycle(userId, deliveryCycle);
        if (existing.isPresent()) {
            log.info("WhatsApp summary delivery already exists for user {} in cycle {}: status={}",
                    userId, deliveryCycle, existing.get().getDeliveryStatus());
            return existing;
        }

        Optional<BusinessProfile> profileOpt = profileRepository.findById(userId);
        if (profileOpt.isEmpty()) {
            log.warn("Cannot deliver WhatsApp summary: profile not found for user {}", userId);
            return Optional.empty();
        }

        BusinessProfile profile = profileOpt.get();
        if (!profile.isWhatsappOptIn() || profile.getWhatsappNumber() == null || profile.getWhatsappNumber().isBlank()) {
            log.info("User {} is not eligible for WhatsApp summary delivery (optIn={}, numberPresent={})",
                    userId, profile.isWhatsappOptIn(), profile.getWhatsappNumber() != null);
            return Optional.empty();
        }

        Optional<ScoreResult> latestScoreOpt = scoreResultRepository.findFirstByUserIdOrderByMonthDesc(userId);
        if (latestScoreOpt.isEmpty()) {
            log.info("Cannot deliver WhatsApp summary: no score results found for user {}", userId);
            return Optional.empty();
        }

        ScoreResult score = latestScoreOpt.get();

        // Load fresh insights and recommendations
        List<Insight> insights = insightService.getInsights(userId, score.getMonth());
        List<Recommendation> recs = recommendationService.getRecommendations(userId, score.getMonth());
        Insight topInsight = insights.isEmpty() ? null : insights.get(0);
        Recommendation topRec = recs.isEmpty() ? null : recs.get(0);

        WhatsAppSummaryComposer.ComposedSummary composed = summaryComposer.compose(
                profile,
                score,
                topInsight,
                topRec
        );

        String idempotencyKey = String.format("%s:%s", userId, deliveryCycle);

        WhatsAppDelivery delivery = new WhatsAppDelivery();
        delivery.setUserId(userId);
        delivery.setScoreResultId(score.getId());
        delivery.setTargetMonth(composed.targetMonth());
        delivery.setSourceFingerprint(composed.sourceFingerprint());
        delivery.setDeliveryCycle(deliveryCycle);
        delivery.setIdempotencyKey(idempotencyKey);
        delivery.setDestinationNumber(profile.getWhatsappNumber());
        delivery.setLanguage(composed.language());
        delivery.setTemplateName(composed.templateName());
        delivery.setProviderName(whatsappClient.getProviderName());
        delivery.setDeliveryStatus(WhatsAppDeliveryStatus.PENDING);
        delivery.setScheduledAt(LocalDateTime.now());

        WhatsAppDelivery savedDelivery;
        try {
            savedDelivery = deliveryRepository.saveAndFlush(delivery);
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent duplicate delivery creation for user {} in cycle {}: returning existing record",
                    userId, deliveryCycle);
            return deliveryRepository.findByUserIdAndDeliveryCycle(userId, deliveryCycle);
        }

        // Claim delivery atomically: transition PENDING -> SENDING
        int claimed = deliveryRepository.transitionStatusIfMatch(
                savedDelivery.getId(),
                WhatsAppDeliveryStatus.PENDING,
                WhatsAppDeliveryStatus.SENDING,
                LocalDateTime.now()
        );

        if (claimed == 0) {
            log.info("Delivery {} was already claimed or processed by another worker", savedDelivery.getId());
            return deliveryRepository.findById(savedDelivery.getId());
        }

        // Dispatch via provider
        WhatsAppSendRequest sendRequest = new WhatsAppSendRequest(
                profile.getWhatsappNumber(),
                composed.messageText(),
                composed.language(),
                composed.templateName(),
                composed.templateParameters()
        );

        WhatsAppSendResult sendResult = whatsappClient.sendSummary(sendRequest);

        savedDelivery.setAttemptCount(Math.max(1, sendResult.attempts()));
        LocalDateTime now = LocalDateTime.now();

        if (sendResult.status() == WhatsAppDeliveryStatus.SENT) {
            savedDelivery.setDeliveryStatus(WhatsAppDeliveryStatus.SENT);
            savedDelivery.setProviderMessageId(sendResult.providerMessageId());
            savedDelivery.setSentAt(now);
            log.info("WhatsApp delivery {} SENT to {} for user {}",
                    savedDelivery.getId(), PhoneNumberValidator.mask(profile.getWhatsappNumber()), userId);

            if (auditService != null) {
                auditService.logSuccess(
                        com.app.sme_health_backend.audit.model.AuditEventType.WHATSAPP_SENT,
                        userId,
                        null,
                        null,
                        "whatsapp_delivery",
                        savedDelivery.getId().toString(),
                        java.util.Map.of(
                                "deliveryCycle", deliveryCycle,
                                "provider", whatsappClient.getProviderName()
                        )
                );
            }
        } else if (sendResult.status() == WhatsAppDeliveryStatus.INDETERMINATE) {
            savedDelivery.setDeliveryStatus(WhatsAppDeliveryStatus.INDETERMINATE);
            savedDelivery.setFailureReason(sendResult.failureReason());
            log.warn("WhatsApp delivery {} INDETERMINATE for user {}: ambiguous timeout, will NOT blindly retry",
                    savedDelivery.getId(), userId);

            if (auditService != null) {
                auditService.logFailure(
                        com.app.sme_health_backend.audit.model.AuditEventType.WHATSAPP_FAILED,
                        userId,
                        null,
                        null,
                        "whatsapp_delivery",
                        savedDelivery.getId().toString(),
                        sendResult.failureReason() != null ? sendResult.failureReason() : "Indeterminate timeout",
                        java.util.Map.of(
                                "deliveryCycle", deliveryCycle,
                                "provider", whatsappClient.getProviderName()
                        )
                );
            }
        } else {
            savedDelivery.setDeliveryStatus(WhatsAppDeliveryStatus.FAILED);
            savedDelivery.setFailedAt(now);
            savedDelivery.setFailureReason(sendResult.failureReason());
            log.error("WhatsApp delivery {} FAILED for user {}: {}",
                    savedDelivery.getId(), userId, sendResult.failureReason());

            if (auditService != null) {
                auditService.logFailure(
                        com.app.sme_health_backend.audit.model.AuditEventType.WHATSAPP_FAILED,
                        userId,
                        null,
                        null,
                        "whatsapp_delivery",
                        savedDelivery.getId().toString(),
                        sendResult.failureReason() != null ? sendResult.failureReason() : "Delivery failed",
                        java.util.Map.of(
                                "deliveryCycle", deliveryCycle,
                                "provider", whatsappClient.getProviderName()
                        )
                );
            }
        }

        savedDelivery.setUpdatedAt(now);
        return Optional.of(deliveryRepository.save(savedDelivery));
    }

    @Transactional(readOnly = true)
    public List<WhatsAppDeliveryResponse> getDeliveriesForUser(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        return deliveryRepository.findByUserIdOrderByScheduledAtDesc(userId)
                .stream()
                .map(WhatsAppDeliveryResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<WhatsAppDeliveryResponse> getDelivery(UUID deliveryId, UUID businessId) {
        if (deliveryId == null) {
            throw new IllegalArgumentException("Delivery ID is required");
        }
        if (businessId == null) {
            throw new IllegalArgumentException("Business ID is required");
        }
        return deliveryRepository.findByIdAndUserId(deliveryId, businessId)
                .map(WhatsAppDeliveryResponse::fromEntity);
    }
}
