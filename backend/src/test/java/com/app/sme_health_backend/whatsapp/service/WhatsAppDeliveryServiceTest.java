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
import com.app.sme_health_backend.whatsapp.client.WhatsAppSendResult;
import com.app.sme_health_backend.whatsapp.entity.WhatsAppDelivery;
import com.app.sme_health_backend.whatsapp.entity.WhatsAppDeliveryStatus;
import com.app.sme_health_backend.whatsapp.repository.WhatsAppDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppDeliveryServiceTest {

    @Mock
    private WhatsAppDeliveryRepository deliveryRepository;

    @Mock
    private BusinessProfileRepository profileRepository;

    @Mock
    private ScoreResultRepository scoreResultRepository;

    @Mock
    private InsightService insightService;

    @Mock
    private RecommendationService recommendationService;

    @Mock
    private WhatsAppSummaryComposer summaryComposer;

    @Mock
    private WhatsAppClient whatsappClient;

    private WhatsAppDeliveryService service;

    private UUID userId;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        service = new WhatsAppDeliveryService(
                deliveryRepository,
                profileRepository,
                scoreResultRepository,
                insightService,
                recommendationService,
                summaryComposer,
                whatsappClient,
                "Asia/Karachi"
        );
        userId = UUID.randomUUID();
        testDate = LocalDate.of(2026, 9, 23); // Wednesday of week 39
    }

    @Test
    void shouldCalculateDeliveryCycleInConfiguredTimezone() {
        ZoneId karachi = ZoneId.of("Asia/Karachi");
        ZonedDateTime zdt = ZonedDateTime.of(2026, 9, 23, 10, 0, 0, 0, karachi);
        String cycle = WhatsAppDeliveryService.calculateDeliveryCycle(zdt);
        assertEquals("2026-W39", cycle);

        String fromDate = WhatsAppDeliveryService.calculateDeliveryCycle(LocalDate.of(2026, 9, 23), karachi);
        assertEquals("2026-W39", fromDate);
    }

    @Test
    void shouldReturnExistingDeliveryWhenAlreadyDeliveredInSameCycle() {
        String cycle = "2026-W39";
        WhatsAppDelivery existing = new WhatsAppDelivery();
        existing.setId(UUID.randomUUID());
        existing.setUserId(userId);
        existing.setDeliveryCycle(cycle);
        existing.setDeliveryStatus(WhatsAppDeliveryStatus.SENT);

        when(deliveryRepository.findByUserIdAndDeliveryCycle(userId, cycle)).thenReturn(Optional.of(existing));

        Optional<WhatsAppDelivery> result = service.deliverWeeklySummary(userId, testDate);

        assertTrue(result.isPresent());
        assertEquals(WhatsAppDeliveryStatus.SENT, result.get().getDeliveryStatus());
        verify(whatsappClient, never()).sendSummary(any());
    }

    @Test
    void shouldReturnEmptyWhenUserNotOptedInOrMissingPhone() {
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(userId);
        profile.setWhatsappOptIn(false);
        profile.setWhatsappNumber(null);

        when(deliveryRepository.findByUserIdAndDeliveryCycle(eq(userId), any())).thenReturn(Optional.empty());
        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));

        Optional<WhatsAppDelivery> result = service.deliverWeeklySummary(userId, testDate);

        assertTrue(result.isEmpty());
        verify(whatsappClient, never()).sendSummary(any());
    }

    @Test
    void shouldReturnEmptyWhenNoScoreResultFound() {
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(userId);
        profile.setWhatsappOptIn(true);
        profile.setWhatsappNumber("+923001234567");

        when(deliveryRepository.findByUserIdAndDeliveryCycle(eq(userId), any())).thenReturn(Optional.empty());
        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(scoreResultRepository.findFirstByUserIdOrderByMonthDesc(userId)).thenReturn(Optional.empty());

        Optional<WhatsAppDelivery> result = service.deliverWeeklySummary(userId, testDate);

        assertTrue(result.isEmpty());
        verify(whatsappClient, never()).sendSummary(any());
    }

    @Test
    void shouldSuccessfullyDeliverSummaryWhenProviderAccepts() {
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(userId);
        profile.setWhatsappOptIn(true);
        profile.setWhatsappNumber("+923001234567");
        profile.setLanguagePreference("en");

        ScoreResult score = new ScoreResult();
        score.setId(UUID.randomUUID());
        score.setMonth("2026-09");
        score.setCompositeScore(new BigDecimal("75"));
        score.setBand("STRONG");

        Insight insight = new Insight();
        insight.setText("Insight text");
        insight.setSourceVersion("v1-ins");

        Recommendation rec = new Recommendation();
        rec.setText("Rec text");
        rec.setSourceVersion("v1-rec");

        WhatsAppSummaryComposer.ComposedSummary composed = new WhatsAppSummaryComposer.ComposedSummary(
                "Message text", "fp123", "tpl1", "2026-09", "en"
        );

        when(deliveryRepository.findByUserIdAndDeliveryCycle(eq(userId), any())).thenReturn(Optional.empty());
        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(scoreResultRepository.findFirstByUserIdOrderByMonthDesc(userId)).thenReturn(Optional.of(score));
        when(insightService.getInsights(userId, "2026-09")).thenReturn(List.of(insight));
        when(recommendationService.getRecommendations(userId, "2026-09")).thenReturn(List.of(rec));
        when(summaryComposer.compose(profile, score, insight, rec)).thenReturn(composed);
        when(whatsappClient.getProviderName()).thenReturn("mock");

        when(deliveryRepository.saveAndFlush(any(WhatsAppDelivery.class))).thenAnswer(i -> {
            WhatsAppDelivery d = i.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });
        when(deliveryRepository.transitionStatusIfMatch(any(), eq(WhatsAppDeliveryStatus.PENDING), eq(WhatsAppDeliveryStatus.SENDING), any()))
                .thenReturn(1);
        when(whatsappClient.sendSummary(any())).thenReturn(WhatsAppSendResult.sent("msg-123", "mock"));
        when(deliveryRepository.save(any(WhatsAppDelivery.class))).thenAnswer(i -> i.getArgument(0));

        Optional<WhatsAppDelivery> result = service.deliverWeeklySummary(userId, testDate);

        assertTrue(result.isPresent());
        WhatsAppDelivery delivery = result.get();
        assertEquals(WhatsAppDeliveryStatus.SENT, delivery.getDeliveryStatus());
        assertEquals("msg-123", delivery.getProviderMessageId());
        assertNotNull(delivery.getSentAt());
        assertEquals("fp123", delivery.getSourceFingerprint());
    }

    @Test
    void shouldMarkIndeterminateOnAmbiguousProviderTimeoutWithoutBlindRetry() {
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(userId);
        profile.setWhatsappOptIn(true);
        profile.setWhatsappNumber("+923001234567");

        ScoreResult score = new ScoreResult();
        score.setId(UUID.randomUUID());
        score.setMonth("2026-09");
        score.setCompositeScore(new BigDecimal("70"));
        score.setBand("STABLE");

        WhatsAppSummaryComposer.ComposedSummary composed = new WhatsAppSummaryComposer.ComposedSummary(
                "Message text", "fp123", "tpl1", "2026-09", "en"
        );

        when(deliveryRepository.findByUserIdAndDeliveryCycle(eq(userId), any())).thenReturn(Optional.empty());
        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(scoreResultRepository.findFirstByUserIdOrderByMonthDesc(userId)).thenReturn(Optional.of(score));
        when(insightService.getInsights(userId, "2026-09")).thenReturn(List.of());
        when(recommendationService.getRecommendations(userId, "2026-09")).thenReturn(List.of());
        when(summaryComposer.compose(any(), any(), any(), any())).thenReturn(composed);
        when(whatsappClient.getProviderName()).thenReturn("mock");

        when(deliveryRepository.saveAndFlush(any(WhatsAppDelivery.class))).thenAnswer(i -> {
            WhatsAppDelivery d = i.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });
        when(deliveryRepository.transitionStatusIfMatch(any(), eq(WhatsAppDeliveryStatus.PENDING), eq(WhatsAppDeliveryStatus.SENDING), any()))
                .thenReturn(1);
        when(whatsappClient.sendSummary(any()))
                .thenReturn(WhatsAppSendResult.indeterminate("Provider timeout: ambiguous status", "mock"));
        when(deliveryRepository.save(any(WhatsAppDelivery.class))).thenAnswer(i -> i.getArgument(0));

        Optional<WhatsAppDelivery> result = service.deliverWeeklySummary(userId, testDate);

        assertTrue(result.isPresent());
        WhatsAppDelivery delivery = result.get();
        assertEquals(WhatsAppDeliveryStatus.INDETERMINATE, delivery.getDeliveryStatus());
        assertTrue(delivery.getFailureReason().contains("timeout"));
        assertNull(delivery.getSentAt());
        // Verify sendSummary was called only once (no blind retries)
        verify(whatsappClient, times(1)).sendSummary(any());
    }

    @Test
    void shouldHandleDatabaseUniqueConstraintViolationGracefully() {
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(userId);
        profile.setWhatsappOptIn(true);
        profile.setWhatsappNumber("+923001234567");

        ScoreResult score = new ScoreResult();
        score.setId(UUID.randomUUID());
        score.setMonth("2026-09");
        score.setCompositeScore(new BigDecimal("70"));
        score.setBand("STABLE");

        WhatsAppSummaryComposer.ComposedSummary composed = new WhatsAppSummaryComposer.ComposedSummary(
                "Message text", "fp123", "tpl1", "2026-09", "en"
        );

        WhatsAppDelivery existing = new WhatsAppDelivery();
        existing.setId(UUID.randomUUID());
        existing.setUserId(userId);
        existing.setDeliveryCycle("2026-W39");
        existing.setDeliveryStatus(WhatsAppDeliveryStatus.SENT);

        // First check returns empty
        when(deliveryRepository.findByUserIdAndDeliveryCycle(userId, "2026-W39"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing)); // Second check after exception returns existing

        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(scoreResultRepository.findFirstByUserIdOrderByMonthDesc(userId)).thenReturn(Optional.of(score));
        when(summaryComposer.compose(any(), any(), any(), any())).thenReturn(composed);
        when(whatsappClient.getProviderName()).thenReturn("mock");

        when(deliveryRepository.saveAndFlush(any(WhatsAppDelivery.class)))
                .thenThrow(new DataIntegrityViolationException("uq_whatsapp_deliveries_user_cycle violated"));

        Optional<WhatsAppDelivery> result = service.deliverWeeklySummary(userId, testDate);

        assertTrue(result.isPresent());
        assertEquals(WhatsAppDeliveryStatus.SENT, result.get().getDeliveryStatus());
        verify(whatsappClient, never()).sendSummary(any());
    }
}
