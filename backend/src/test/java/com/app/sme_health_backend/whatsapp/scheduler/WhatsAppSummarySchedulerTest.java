package com.app.sme_health_backend.whatsapp.scheduler;

import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.whatsapp.entity.WhatsAppDelivery;
import com.app.sme_health_backend.whatsapp.entity.WhatsAppDeliveryStatus;
import com.app.sme_health_backend.whatsapp.service.WhatsAppDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppSummarySchedulerTest {

    @Mock
    private WhatsAppDeliveryService deliveryService;

    @Mock
    private BusinessProfileRepository profileRepository;

    private WhatsAppSummaryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new WhatsAppSummaryScheduler(deliveryService, profileRepository, true);
    }

    @Test
    void shouldTriggerWeeklyDeliveryForEligibleProfiles() {
        when(deliveryService.getSchedulerZone()).thenReturn(ZoneId.of("Asia/Karachi"));
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();

        BusinessProfile p1 = new BusinessProfile();
        p1.setUserId(u1);
        p1.setWhatsappOptIn(true);
        p1.setWhatsappNumber("+923001111111");

        BusinessProfile p2 = new BusinessProfile();
        p2.setUserId(u2);
        p2.setWhatsappOptIn(true);
        p2.setWhatsappNumber("+923002222222");

        when(profileRepository.findByWhatsappOptInTrueAndWhatsappNumberIsNotNull())
                .thenReturn(List.of(p1, p2));

        WhatsAppDelivery d1 = new WhatsAppDelivery();
        d1.setDeliveryStatus(WhatsAppDeliveryStatus.SENT);

        WhatsAppDelivery d2 = new WhatsAppDelivery();
        d2.setDeliveryStatus(WhatsAppDeliveryStatus.SENT);

        LocalDate refDate = LocalDate.of(2026, 9, 23);
        when(deliveryService.deliverWeeklySummary(eq(u1), eq(refDate))).thenReturn(Optional.of(d1));
        when(deliveryService.deliverWeeklySummary(eq(u2), eq(refDate))).thenReturn(Optional.of(d2));

        int sent = scheduler.triggerWeeklyDelivery(refDate);

        assertEquals(2, sent);
        verify(deliveryService).deliverWeeklySummary(u1, refDate);
        verify(deliveryService).deliverWeeklySummary(u2, refDate);
    }

    @Test
    void shouldNotRunScheduledDeliveryWhenDisabled() {
        WhatsAppSummaryScheduler disabledScheduler =
                new WhatsAppSummaryScheduler(deliveryService, profileRepository, false);

        disabledScheduler.runScheduledWeeklyDelivery();

        verify(profileRepository, never()).findByWhatsappOptInTrueAndWhatsappNumberIsNotNull();
    }
}
