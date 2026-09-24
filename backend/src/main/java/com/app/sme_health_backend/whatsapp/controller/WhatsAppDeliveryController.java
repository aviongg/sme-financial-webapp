package com.app.sme_health_backend.whatsapp.controller;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import com.app.sme_health_backend.whatsapp.dto.WhatsAppDeliveryResponse;
import com.app.sme_health_backend.whatsapp.service.WhatsAppDeliveryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/whatsapp/deliveries")
public class WhatsAppDeliveryController {

    private final WhatsAppDeliveryService deliveryService;
    private final BusinessAuthorizationService authService;

    public WhatsAppDeliveryController(
            WhatsAppDeliveryService deliveryService,
            BusinessAuthorizationService authService
    ) {
        this.deliveryService = deliveryService;
        this.authService = authService;
    }

    @GetMapping
    public ResponseEntity<List<WhatsAppDeliveryResponse>> getDeliveriesForActiveBusiness(HttpServletRequest request) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);

        return ResponseEntity.ok(deliveryService.getDeliveriesForUser(context.businessId()));
    }

    @GetMapping("/{deliveryId}")
    public ResponseEntity<WhatsAppDeliveryResponse> getDelivery(
            @PathVariable UUID deliveryId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);

        return deliveryService.getDelivery(deliveryId, context.businessId())
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("WhatsApp delivery not found: " + deliveryId));
    }
}
