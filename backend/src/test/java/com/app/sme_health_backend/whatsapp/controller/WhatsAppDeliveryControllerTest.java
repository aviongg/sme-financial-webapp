package com.app.sme_health_backend.whatsapp.controller;

import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import com.app.sme_health_backend.whatsapp.dto.WhatsAppDeliveryResponse;
import com.app.sme_health_backend.whatsapp.entity.WhatsAppDelivery;
import com.app.sme_health_backend.whatsapp.entity.WhatsAppDeliveryStatus;
import com.app.sme_health_backend.whatsapp.scheduler.WhatsAppSummaryScheduler;
import com.app.sme_health_backend.whatsapp.service.WhatsAppDeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WhatsAppDeliveryController.class)
@Import(GlobalExceptionHandler.class)
class WhatsAppDeliveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WhatsAppDeliveryService deliveryService;

    @MockitoBean
    private WhatsAppSummaryScheduler scheduler;

    @Test
    void shouldGetDeliveriesForUser() throws Exception {
        UUID userId = UUID.randomUUID();
        WhatsAppDeliveryResponse resp = new WhatsAppDeliveryResponse(
                UUID.randomUUID(), userId, UUID.randomUUID(), "2026-09", "fp-123", "2026-W39",
                userId + ":2026-W39", "+92300***4567", "en", "tpl", "mock", "msg-1",
                WhatsAppDeliveryStatus.SENT, 1, LocalDateTime.now(), LocalDateTime.now(), null, null,
                LocalDateTime.now(), LocalDateTime.now()
        );

        when(deliveryService.getDeliveriesForUser(userId)).thenReturn(List.of(resp));

        mockMvc.perform(get("/api/whatsapp/deliveries/user/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deliveryCycle").value("2026-W39"))
                .andExpect(jsonPath("$[0].deliveryStatus").value("SENT"))
                .andExpect(jsonPath("$[0].maskedDestinationNumber").value("+92300***4567"));
    }

    @Test
    void shouldGetDeliveryById() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        WhatsAppDeliveryResponse resp = new WhatsAppDeliveryResponse(
                deliveryId, userId, UUID.randomUUID(), "2026-09", "fp-123", "2026-W39",
                userId + ":2026-W39", "+92300***4567", "en", "tpl", "mock", "msg-1",
                WhatsAppDeliveryStatus.SENT, 1, LocalDateTime.now(), LocalDateTime.now(), null, null,
                LocalDateTime.now(), LocalDateTime.now()
        );

        when(deliveryService.getDelivery(deliveryId)).thenReturn(Optional.of(resp));

        mockMvc.perform(get("/api/whatsapp/deliveries/{deliveryId}", deliveryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deliveryId.toString()))
                .andExpect(jsonPath("$.deliveryStatus").value("SENT"));
    }
}
