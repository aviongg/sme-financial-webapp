package com.app.sme_health_backend.whatsapp.controller;

import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.whatsapp.client.WhatsAppClient;
import com.app.sme_health_backend.whatsapp.dto.WhatsAppDeliveryCapabilityResponse;
import com.app.sme_health_backend.whatsapp.scheduler.WhatsAppSummaryScheduler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DEF-08: read-only disclosure of the outbound WhatsApp summary transport, so the "WhatsApp updates" card can say
 * whether sending is simulated, turned off or configured for Meta even when no delivery exists yet.
 *
 * <p>The provider comes from the {@link WhatsAppClient} bean that {@code WhatsAppDeliveryService} sends through, not
 * from consent, the module toggle, the inbound assistant or delivery history. Same permission as the delivery list.
 */
@RestController
public class WhatsAppDeliveryCapabilityController {

    private final WhatsAppClient outboundClient;
    private final WhatsAppSummaryScheduler scheduler;
    private final BusinessAuthorizationService authService;

    public WhatsAppDeliveryCapabilityController(WhatsAppClient outboundClient, WhatsAppSummaryScheduler scheduler,
                                                BusinessAuthorizationService authService) {
        this.outboundClient = outboundClient;
        this.scheduler = scheduler;
        this.authService = authService;
    }

    @GetMapping("/api/whatsapp/delivery-capability")
    public WhatsAppDeliveryCapabilityResponse capability(HttpServletRequest request) {
        authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        return WhatsAppDeliveryCapabilityResponse.of(outboundClient.getProviderName(), scheduler.isAutomaticDeliveryActive());
    }
}
