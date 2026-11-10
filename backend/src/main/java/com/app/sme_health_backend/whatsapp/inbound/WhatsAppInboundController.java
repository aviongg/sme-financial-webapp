package com.app.sme_health_backend.whatsapp.inbound;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

/**
 * Public Meta webhook (unauthenticated by session, authenticated by HMAC signature) plus authenticated app endpoints
 * for linking a user's own WhatsApp number with explicit consent.
 */
@RestController
public class WhatsAppInboundController {
    private static final int MAX_BODY = 262_144;

    private final WhatsAppInboundService inbound;
    private final BusinessAuthorizationService authorization;

    public WhatsAppInboundController(WhatsAppInboundService inbound, BusinessAuthorizationService authorization) {
        this.inbound = inbound;
        this.authorization = authorization;
    }

    public record LinkBody(String phoneNumber, Boolean consent, String language) { }

    @GetMapping(value = "/api/whatsapp/webhook", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(@RequestParam(value = "hub.mode", required = false) String mode,
                                         @RequestParam(value = "hub.verify_token", required = false) String token,
                                         @RequestParam(value = "hub.challenge", required = false) String challenge) {
        if (!inbound.configured()) return ResponseEntity.status(503).body("disabled");
        String echo = inbound.verifyChallenge(mode, token, challenge);
        return echo == null ? ResponseEntity.status(403).body("forbidden") : ResponseEntity.ok(echo);
    }

    @PostMapping("/api/whatsapp/webhook")
    public ResponseEntity<?> receive(@RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
                                     HttpServletRequest request) throws IOException {
        if (!inbound.configured()) return ResponseEntity.status(503).body(Map.of("error", "whatsapp_inbound_disabled"));
        if (request.getContentLengthLong() > MAX_BODY) return ResponseEntity.status(413).body(Map.of("error", "too_large"));
        byte[] body = request.getInputStream().readNBytes(MAX_BODY + 1);
        if (body.length > MAX_BODY) return ResponseEntity.status(413).body(Map.of("error", "too_large"));
        if (!inbound.validSignature(body, signature)) return ResponseEntity.status(401).body(Map.of("error", "invalid_signature"));
        var outcomes = inbound.handle(body);
        // Always 200 for a correctly signed delivery so Meta does not retry already-recorded messages.
        return ResponseEntity.ok(Map.of("processed", outcomes.size()));
    }

    @GetMapping("/api/whatsapp/inbound/link")
    public ResponseEntity<?> status(HttpServletRequest request) {
        BusinessAccessContext ctx = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(inbound.status(ctx));
    }

    @PostMapping("/api/whatsapp/inbound/link")
    public ResponseEntity<?> link(@RequestBody LinkBody body, HttpServletRequest request) {
        BusinessAccessContext ctx = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(inbound.startLink(ctx, body.phoneNumber(),
                Boolean.TRUE.equals(body.consent()), body.language() == null ? "en" : body.language()));
    }

    @PostMapping("/api/whatsapp/inbound/link/revoke")
    public ResponseEntity<?> revoke(HttpServletRequest request) {
        BusinessAccessContext ctx = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(inbound.revoke(ctx));
    }

    @GetMapping("/api/whatsapp/inbound/messages")
    public ResponseEntity<?> messages(HttpServletRequest request) {
        BusinessAccessContext ctx = authorization.requirePermission(request, BusinessPermission.WHATSAPP_CONFIG_MANAGE);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(Map.of("messages", inbound.recentMessages(ctx)));
    }
}
