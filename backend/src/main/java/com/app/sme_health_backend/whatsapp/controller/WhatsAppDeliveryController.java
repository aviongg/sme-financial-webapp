package com.app.sme_health_backend.whatsapp.controller;

import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import com.app.sme_health_backend.whatsapp.dto.WhatsAppDeliveryResponse;
import com.app.sme_health_backend.whatsapp.entity.WhatsAppDelivery;
import com.app.sme_health_backend.whatsapp.scheduler.WhatsAppSummaryScheduler;
import com.app.sme_health_backend.whatsapp.service.WhatsAppDeliveryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/whatsapp/deliveries")
public class WhatsAppDeliveryController {

    private final WhatsAppDeliveryService deliveryService;
    private final WhatsAppSummaryScheduler scheduler;

    public WhatsAppDeliveryController(
            WhatsAppDeliveryService deliveryService,
            WhatsAppSummaryScheduler scheduler
    ) {
        this.deliveryService = deliveryService;
        this.scheduler = scheduler;
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

    @PostMapping("/trigger/{userId}")
    public ResponseEntity<WhatsAppDeliveryResponse> triggerForUser(
            @PathVariable UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return deliveryService.deliverWeeklySummary(userId, date)
                .map(WhatsAppDeliveryResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("User not eligible or score not available for delivery"));
    }

    @PostMapping("/trigger-weekly")
    public ResponseEntity<Map<String, Object>> triggerWeeklyBatch(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        int sent = scheduler.triggerWeeklyDelivery(date);
        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "sentCount", sent
        ));
    }
}
