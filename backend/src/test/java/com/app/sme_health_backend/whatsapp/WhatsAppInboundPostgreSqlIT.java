package com.app.sme_health_backend.whatsapp;

import com.app.sme_health_backend.testsupport.ProductApiTestBase;
import com.app.sme_health_backend.whatsapp.inbound.WhatsAppReplySender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** S-B06 inbound WhatsApp on real PostgreSQL with the mock reply provider (no Meta credentials needed). */
@SpringBootTest(properties = {
        "app.ai.enabled=true", "app.ai.provider=mock", "app.ai.demo-enabled=true",
        "app.whatsapp.inbound.enabled=true", "app.whatsapp.inbound.verify-token=verify-fixture-token",
        "app.whatsapp.inbound.app-secret=app-secret-fixture", "app.whatsapp.inbound.phone-hash-key=phone-hash-key-fixture-0123456789abcdef",
        "app.whatsapp.inbound.max-messages-per-hour=6"})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class WhatsAppInboundPostgreSqlIT extends ProductApiTestBase {
    private static final String SECRET = "app-secret-fixture";

    @Autowired WhatsAppReplySender sender;

    private static String sign(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }

    private static String payload(String from, String id, String text, long epochSeconds) {
        return """
                {"object":"whatsapp_business_account","entry":[{"id":"waba","changes":[{"field":"messages","value":{
                "messaging_product":"whatsapp","metadata":{"phone_number_id":"pn-1"},
                "messages":[{"from":"%s","id":"%s","timestamp":"%d","type":"text","text":{"body":"%s"}}]}}]}]}
                """.formatted(from, id, epochSeconds, text);
    }

    private int deliver(String body, String signature) throws Exception {
        var req = post("/api/whatsapp/webhook").contentType("application/json").content(body);
        if (signature != null) req.header("X-Hub-Signature-256", signature);
        return mvc.perform(req).andReturn().getResponse().getStatus();
    }

    private String send(String from, String text) throws Exception {
        String id = "wamid." + UUID.randomUUID();
        byte[] body = payload(from, id, text, Instant.now().getEpochSecond()).getBytes(StandardCharsets.UTF_8);
        assertEquals(200, deliver(new String(body, StandardCharsets.UTF_8), sign(body)));
        return id;
    }

    private String status(String providerId) {
        return jdbc.queryForObject("SELECT status FROM whatsapp_inbound_messages WHERE provider_message_id = ?", String.class, providerId);
    }

    @Test
    void signedLinkedConsentedSenderGetsGroundedAnswersAndEveryControlHolds() throws Exception {
        // Meta verification challenge.
        assertEquals("abc123", mvc.perform(get("/api/whatsapp/webhook?hub.mode=subscribe&hub.verify_token=verify-fixture-token&hub.challenge=abc123"))
                .andReturn().getResponse().getContentAsString());
        assertEquals(403, mvc.perform(get("/api/whatsapp/webhook?hub.mode=subscribe&hub.verify_token=wrong&hub.challenge=abc"))
                .andReturn().getResponse().getStatus());

        // Signature is mandatory and exact.
        String unsigned = payload("923001112233", "wamid.x", "hi", Instant.now().getEpochSecond());
        assertEquals(401, deliver(unsigned, null));
        assertEquals(401, deliver(unsigned, "sha256=" + "0".repeat(64)));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM whatsapp_inbound_messages WHERE provider_message_id = 'wamid.x'", Integer.class));

        Actor owner = owner();
        UUID account = account(owner, "1000.00");
        postTransaction(owner, account, "DIRECT_SALE_RECEIPT", "2468.00", TODAY);
        postJson(owner, "/api/whatsapp/inbound/link", map("phoneNumber", "+923001112233", "consent", false), null, 400);
        JsonNode link = postJson(owner, "/api/whatsapp/inbound/link", map("phoneNumber", "+92 300 1112233", "consent", true,
                "language", "en"), null, 200);
        assertEquals("PENDING", link.get("status").asText());
        String code = link.get("verificationCode").asText();

        // Unlinked sender and wrong code learn nothing.
        assertEquals("UNLINKED_SENDER", status(send("923009998877", "What is my profit?")));
        assertEquals("UNLINKED_SENDER", status(send("923001112233", "LINK 000000".equals("LINK " + code) ? "LINK 111111" : "LINK 000000")));
        assertEquals("LINKED", status(send("923001112233", "LINK " + code)));
        assertEquals("ACTIVE", getJson(owner, "/api/whatsapp/inbound/link").get("status").asText());

        String answered = send("923001112233", "What is my revenue this month?");
        assertEquals("ANSWERED", status(answered));
        var last = sender.mockOutbox().get(sender.mockOutbox().size() - 1);
        assertEquals("+923001112233", last.to());
        assertTrue(last.body().contains("2468.00"), last.body());
        String stored = jdbc.queryForObject("SELECT body_ciphertext FROM whatsapp_inbound_messages WHERE provider_message_id = ?", String.class, answered);
        assertTrue(stored.startsWith("enc:"), "Message bodies are encrypted at rest");
        assertFalse(stored.contains("revenue"));

        // Dedupe: an identical redelivery is recorded once and not answered twice.
        int outboxBefore = sender.mockOutbox().size();
        byte[] dup = payload("923001112233", answered, "What is my revenue this month?", Instant.now().getEpochSecond()).getBytes(StandardCharsets.UTF_8);
        assertEquals(200, deliver(new String(dup, StandardCharsets.UTF_8), sign(dup)));
        assertEquals(outboxBefore, sender.mockOutbox().size());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM whatsapp_inbound_messages WHERE provider_message_id = ?", Integer.class, answered));

        // Replay window.
        String staleId = "wamid.stale-" + UUID.randomUUID();
        byte[] stale = payload("923001112233", staleId, "profit?", Instant.now().minusSeconds(3600).getEpochSecond()).getBytes(StandardCharsets.UTF_8);
        assertEquals(200, deliver(new String(stale, StandardCharsets.UTF_8), sign(stale)));
        assertEquals("STALE", status(staleId));

        // Authorization is re-resolved per message from the live membership.
        jdbc.update("UPDATE business_memberships SET status = 'SUSPENDED' WHERE user_id = ? AND business_id = ?",
                owner.fixture().user().getId(), owner.businessId());
        assertEquals("ACCESS_DENIED", status(send("923001112233", "cash balance?")));
        jdbc.update("UPDATE business_memberships SET status = 'ACTIVE' WHERE user_id = ? AND business_id = ?",
                owner.fixture().user().getId(), owner.businessId());

        // Per-phone rate limit (6/hour in this fixture).
        String limited = null;
        for (int i = 0; i < 4; i++) limited = send("923001112233", "profit?");
        assertEquals("RATE_LIMITED", status(limited));

        // STOP revokes consent; further messages are from an unlinked sender.
        jdbc.update("UPDATE whatsapp_inbound_messages SET received_at = received_at - interval '2 hours' WHERE phone_hash = "
                + "(SELECT phone_hash FROM whatsapp_inbound_links WHERE user_id = ? AND status = 'ACTIVE')", owner.fixture().user().getId());
        assertEquals("REVOKED", status(send("923001112233", "STOP")));
        assertEquals("UNLINKED_SENDER", status(send("923001112233", "profit?")));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM whatsapp_inbound_messages m WHERE m.business_id = ? AND m.status = 'ANSWERED'",
                Integer.class, owner.businessId()) >= 1 ? 1 : 0);
        assertTrue(jdbc.queryForObject("SELECT count(*) FROM security_audit_events WHERE business_id = ? AND event_type = 'WHATSAPP_INBOUND_RECEIVED'",
                Integer.class, owner.businessId()) >= 2);
        JsonNode log = getJson(owner, "/api/whatsapp/inbound/messages");
        assertFalse(log.toString().contains("2468"), "Message log never exposes bodies");
    }
}
