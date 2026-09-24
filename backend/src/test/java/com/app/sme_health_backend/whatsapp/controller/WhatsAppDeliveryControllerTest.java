package com.app.sme_health_backend.whatsapp.controller;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import com.app.sme_health_backend.whatsapp.dto.WhatsAppDeliveryResponse;
import com.app.sme_health_backend.whatsapp.entity.WhatsAppDeliveryStatus;
import com.app.sme_health_backend.whatsapp.service.WhatsAppDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    private BusinessAuthorizationService authService;

    private final UUID userId = UUID.randomUUID();
    private final UUID businessId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), any(BusinessPermission.class))).thenReturn(context);
    }

    @Test
    void shouldGetDeliveriesForActiveBusiness() throws Exception {
        WhatsAppDeliveryResponse resp = new WhatsAppDeliveryResponse(
                UUID.randomUUID(), businessId, UUID.randomUUID(), "2026-09", "fp-123", "2026-W39",
                businessId + ":2026-W39", "+92300***4567", "en", "tpl", "mock", "msg-1",
                WhatsAppDeliveryStatus.SENT, 1, LocalDateTime.now(), LocalDateTime.now(), null, null,
                LocalDateTime.now(), LocalDateTime.now()
        );

        when(deliveryService.getDeliveriesForUser(businessId)).thenReturn(List.of(resp));

        mockMvc.perform(get("/api/whatsapp/deliveries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deliveryCycle").value("2026-W39"))
                .andExpect(jsonPath("$[0].deliveryStatus").value("SENT"))
                .andExpect(jsonPath("$[0].maskedDestinationNumber").value("+92300***4567"))
                .andExpect(jsonPath("$[0].businessId").value(businessId.toString()));
    }

    @Test
    void shouldGetDeliveryById() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        WhatsAppDeliveryResponse resp = new WhatsAppDeliveryResponse(
                deliveryId, businessId, UUID.randomUUID(), "2026-09", "fp-123", "2026-W39",
                businessId + ":2026-W39", "+92300***4567", "en", "tpl", "mock", "msg-1",
                WhatsAppDeliveryStatus.SENT, 1, LocalDateTime.now(), LocalDateTime.now(), null, null,
                LocalDateTime.now(), LocalDateTime.now()
        );

        when(deliveryService.getDelivery(deliveryId, businessId)).thenReturn(Optional.of(resp));

        mockMvc.perform(get("/api/whatsapp/deliveries/{deliveryId}", deliveryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deliveryId.toString()))
                .andExpect(jsonPath("$.deliveryStatus").value("SENT"))
                .andExpect(jsonPath("$.businessId").value(businessId.toString()));
    }

    @Test
    void shouldReturn404WhenDeliveryNotFound() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        when(deliveryService.getDelivery(deliveryId, businessId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/whatsapp/deliveries/{deliveryId}", deliveryId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void shouldRejectLegacyUserDeliveriesRoute() throws Exception {
        mockMvc.perform(get("/api/whatsapp/deliveries/user/{userId}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
