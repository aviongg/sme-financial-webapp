package com.app.sme_health_backend.whatsapp.controller;

import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import com.app.sme_health_backend.whatsapp.dto.WhatsAppDeliveryResponse;
import com.app.sme_health_backend.whatsapp.service.WhatsAppDeliveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/whatsapp/deliveries")
public class WhatsAppDeliveryController {

    private final WhatsAppDeliveryService deliveryService;

    public WhatsAppDeliveryController(WhatsAppDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<WhatsAppDeliveryResponse>> getDeliveriesForUser(
            @PathVariable UUID userId
    ) {
        return ResponseEntity.ok(deliveryService.getDeliveriesForUser(userId));
    }

    @GetMapping("/{deliveryId}")
    public ResponseEntity<WhatsAppDeliveryResponse> getDelivery(
            @PathVariable UUID deliveryId
    ) {
        return deliveryService.getDelivery(deliveryId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("WhatsApp delivery not found: " + deliveryId));
    }
}
