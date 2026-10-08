package com.app.sme_health_backend.whatsapp.recovery;

import com.app.sme_health_backend.whatsapp.entity.WhatsAppDeliveryStatus;
import com.app.sme_health_backend.whatsapp.repository.WhatsAppDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppDeliveryRecoveryTest {

    @Mock
    private WhatsAppDeliveryRepository deliveryRepository;

    private WhatsAppDeliveryRecovery recovery;

    @BeforeEach
    void setUp() {
        recovery = new WhatsAppDeliveryRecovery(deliveryRepository, 15);
    }

    @Test
    void shouldRecoverStaleSendingDeliveriesToIndeterminate() {
        when(deliveryRepository.recoverStaleDeliveries(
                eq(WhatsAppDeliveryStatus.SENDING),
                eq(WhatsAppDeliveryStatus.INDETERMINATE),
                any(),
                any(),
                any()
        )).thenReturn(3);

        int count = recovery.recoverStaleDeliveries();

        assertEquals(3, count);

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        verify(deliveryRepository).recoverStaleDeliveries(
                eq(WhatsAppDeliveryStatus.SENDING),
                eq(WhatsAppDeliveryStatus.INDETERMINATE),
                reasonCaptor.capture(),
                thresholdCaptor.capture(),
                any()
        );

        assertTrue(reasonCaptor.getValue().contains("Stale SENDING delivery recovered as INDETERMINATE"));
        assertTrue(reasonCaptor.getValue().contains("15m"));
        assertTrue(thresholdCaptor.getValue().isBefore(LocalDateTime.now().minusMinutes(14)));
    }

    @Test
    void shouldRunRecoveryOnApplicationReadyEvent() {
        when(deliveryRepository.recoverStaleDeliveries(any(), any(), any(), any(), any()))
                .thenReturn(0);

        recovery.onApplicationReady();

        verify(deliveryRepository).recoverStaleDeliveries(
                eq(WhatsAppDeliveryStatus.SENDING),
                eq(WhatsAppDeliveryStatus.INDETERMINATE),
                any(),
                any(),
                any()
        );
    }

    @Test
    void shouldAllowCustomThresholdMinutes() {
        recovery.recoverStaleDeliveries(30);

        ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(deliveryRepository).recoverStaleDeliveries(
                eq(WhatsAppDeliveryStatus.SENDING),
                eq(WhatsAppDeliveryStatus.INDETERMINATE),
                any(),
                thresholdCaptor.capture(),
                any()
        );

        assertTrue(thresholdCaptor.getValue().isBefore(LocalDateTime.now().minusMinutes(29)));
    }
}
