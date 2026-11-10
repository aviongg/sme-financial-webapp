package com.app.sme_health_backend.whatsapp.inbound;

import com.app.sme_health_backend.ai.copilot.CopilotService;
import com.app.sme_health_backend.ai.copilot.CopilotTypes;
import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.configuration.service.ModuleAccessPolicy;
import com.app.sme_health_backend.crypto.SensitiveDataCipher;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.productsupport.AuditRecorder;
import com.app.sme_health_backend.productsupport.DomainException;
import com.app.sme_health_backend.whatsapp.validation.PhoneNumberValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * S-B06 inbound conversational WhatsApp: Meta webhook verification, HMAC signature check, persistence with
 * provider-message-id dedupe, replay window, possession-verified phone -> user/business mapping, consent, per-phone
 * rate/cost limits, conversation sessions, audit and READ-ONLY Copilot routing under the linked user's own role.
 */
@Service
public class WhatsAppInboundService {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppInboundService.class);
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String AAD = "whatsapp_inbound";

    private final WhatsAppInboundProperties properties;
    private final JdbcTemplate jdbc;
    private final SensitiveDataCipher cipher;
    private final CopilotService copilot;
    private final WhatsAppReplySender sender;
    private final BusinessAuthorizationService authorization;
    private final ModuleAccessPolicy modules;
    private final AuditRecorder audit;

    public WhatsAppInboundService(WhatsAppInboundProperties properties, JdbcTemplate jdbc, SensitiveDataCipher cipher,
                                  CopilotService copilot, WhatsAppReplySender sender, BusinessAuthorizationService authorization,
                                  ModuleAccessPolicy modules, AuditRecorder audit) {
        this.properties = properties;
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.copilot = copilot;
        this.sender = sender;
        this.authorization = authorization;
        this.modules = modules;
        this.audit = audit;
    }

    public record Outcome(String providerMessageId, String status, String reason) { }

    // ================================================================ webhook security

    public boolean configured() {
        return properties.isConfigured();
    }

    /** Meta GET verification: returns the challenge only for subscribe + the exact configured token. */
    public String verifyChallenge(String mode, String token, String challenge) {
        if (!configured() || !"subscribe".equals(mode) || token == null || challenge == null || challenge.length() > 200
                || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), properties.getVerifyToken().getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        return challenge;
    }

    /** X-Hub-Signature-256 = "sha256=" + hex(HMAC_SHA256(appSecret, rawBody)), compared in constant time. */
    public boolean validSignature(byte[] rawBody, String header) {
        if (!configured() || header == null || !header.startsWith("sha256=") || header.length() != 71) return false;
        byte[] expected = hmac(properties.getAppSecret(), rawBody);
        byte[] given;
        try {
            given = HexFormat.of().parseHex(header.substring(7));
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(expected, given);
    }

    // ================================================================ inbound processing

    public List<Outcome> handle(byte[] rawBody) {
        JsonNode root;
        try {
            root = JSON.readTree(rawBody);
        } catch (RuntimeException e) {
            throw DomainException.invalid("body", "json_invalid");
        }
        if (!"whatsapp_business_account".equals(root.path("object").asString())) {
            return List.of();
        }
        List<Outcome> outcomes = new ArrayList<>();
        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                JsonNode value = change.path("value");
                String phoneNumberId = value.path("metadata").path("phone_number_id").asString();
                if (!properties.getPhoneNumberId().isBlank() && !properties.getPhoneNumberId().equals(phoneNumberId)) {
                    continue; // not addressed to this deployment's business number
                }
                for (JsonNode message : value.path("messages")) {
                    if (outcomes.size() >= 50) return outcomes;
                    outcomes.add(processSafely(message));
                }
            }
        }
        return outcomes;
    }

    private Outcome processSafely(JsonNode message) {
        String id = message.path("id").asString();
        try {
            return process(message);
        } catch (RuntimeException e) {
            log.warn("Inbound WhatsApp message processing failed: {}", e.getClass().getSimpleName());
            if (id != null && !id.isBlank()) {
                jdbc.update("UPDATE whatsapp_inbound_messages SET status = 'FAILED', reason = 'processing_error', processed_at = now() "
                        + "WHERE provider_message_id = ? AND status = 'RECEIVED'", id);
            }
            return new Outcome(id, "FAILED", "processing_error");
        }
    }

    /** Each statement auto-commits: the persisted message survives any later failure and dedupe is durable. */
    Outcome process(JsonNode message) {
        String providerId = message.path("id").asString();
        String from = message.path("from").asString();
        String type = message.path("type").asString();
        if (providerId == null || providerId.isBlank() || providerId.length() > 160 || from == null || !from.matches("[0-9]{8,15}")) {
            return new Outcome(providerId, "IGNORED", "malformed_message");
        }
        String e164 = "+" + from;
        String phoneHash = phoneHash(e164);
        String text = "text".equals(type) ? message.path("text").path("body").asString() : null;
        Instant providerTime = parseTimestamp(message.path("timestamp").asString());

        int inserted = jdbc.update("""
                INSERT INTO whatsapp_inbound_messages(provider_message_id, phone_hash, message_type, body_ciphertext,
                    provider_timestamp, status) VALUES (?, ?, ?, ?, ?, 'RECEIVED')
                ON CONFLICT (provider_message_id) DO NOTHING
                """, providerId, phoneHash, type == null ? "unknown" : type.length() > 20 ? type.substring(0, 20) : type,
                text == null ? null : cipher.encrypt(text.length() > 2000 ? text.substring(0, 2000) : text, AAD),
                providerTime == null ? null : OffsetDateTime.ofInstant(providerTime, ZoneOffset.UTC));
        if (inserted == 0) {
            return new Outcome(providerId, "DUPLICATE", "already_processed");
        }
        if (providerTime == null || providerTime.isBefore(Instant.now().minusSeconds(properties.getReplayWindowSeconds()))
                || providerTime.isAfter(Instant.now().plusSeconds(60))) {
            return finish(providerId, null, "STALE", "outside_replay_window", null, null);
        }

        String command = text == null ? "" : text.strip();
        String upper = command.toUpperCase(Locale.ROOT);
        if (upper.matches("^LINK\\s+[0-9]{6}$")) {
            return link(providerId, phoneHash, e164, upper.substring(upper.length() - 6));
        }

        List<Map<String, Object>> links = jdbc.queryForList("SELECT * FROM whatsapp_inbound_links WHERE phone_hash = ? AND status = 'ACTIVE'", phoneHash);
        if (links.isEmpty()) {
            boolean recentlyTold = count("SELECT count(*) FROM whatsapp_inbound_messages WHERE phone_hash = ? AND status = 'UNLINKED_SENDER' "
                    + "AND reply_status = 'SENT' AND received_at > now() - interval '1 hour'", phoneHash) > 0;
            return finish(providerId, null, "UNLINKED_SENDER", "no_active_link", recentlyTold ? null : e164,
                    recentlyTold ? null : "This number is not linked to FinSight. Link it from Settings > WhatsApp in the FinSight app.");
        }
        Map<String, Object> link = links.get(0);
        UUID linkId = (UUID) link.get("id");
        UUID businessId = (UUID) link.get("business_id");
        UUID userId = (UUID) link.get("user_id");
        String language = isUrdu(command) ? "ur" : (String) link.get("language");
        jdbc.update("UPDATE whatsapp_inbound_messages SET business_id = ?, link_id = ? WHERE provider_message_id = ?",
                businessId, linkId, providerId);

        if (upper.equals("STOP") || command.equals("بند")) {
            jdbc.update("UPDATE whatsapp_inbound_links SET status = 'REVOKED', revoked_at = now(), revoke_reason = 'user_stop', "
                    + "version = version + 1 WHERE id = ?", linkId);
            return finish(providerId, linkId, "REVOKED", "user_stop", e164, "ur".equals(language)
                    ? "واٹس ایپ رسائی بند کر دی گئی۔ دوبارہ فعال کرنے کے لیے ایپ استعمال کریں۔"
                    : "WhatsApp access is turned off. Re-link from the FinSight app to resume.");
        }
        if (upper.equals("HELP") || command.equals("مدد")) {
            return finish(providerId, linkId, "HELP", null, e164, "ur".equals(language)
                    ? "مالی سوال پوچھیں، مثلاً: اس مہینے کا منافع؟ بند کرنے کے لیے 'STOP' لکھیں۔"
                    : "Ask a financial question, e.g. 'What is my profit this month?'. Send STOP to turn off.");
        }
        if (text == null) {
            return finish(providerId, linkId, "UNSUPPORTED_TYPE", type, e164, "ur".equals(language)
                    ? "فی الحال صرف تحریری پیغامات سمجھے جاتے ہیں۔" : "Only text messages are supported.");
        }

        long hour = count("SELECT count(*) FROM whatsapp_inbound_messages WHERE phone_hash = ? AND received_at > now() - interval '1 hour'", phoneHash);
        long day = count("SELECT count(*) FROM whatsapp_inbound_messages WHERE phone_hash = ? AND received_at > now() - interval '1 day'", phoneHash);
        if (hour > properties.getMaxMessagesPerHour() || day > properties.getMaxMessagesPerDay()) {
            boolean told = count("SELECT count(*) FROM whatsapp_inbound_messages WHERE phone_hash = ? AND status = 'RATE_LIMITED' "
                    + "AND reply_status = 'SENT' AND received_at > now() - interval '1 hour'", phoneHash) > 0;
            return finish(providerId, linkId, "RATE_LIMITED", hour > properties.getMaxMessagesPerHour() ? "hourly_limit" : "daily_limit",
                    told ? null : e164, told ? null : ("ur".equals(language) ? "حد سے زیادہ پیغامات۔ کچھ دیر بعد کوشش کریں۔"
                            : "Message limit reached. Please try again later."));
        }

        // Re-resolve authorization NOW from the live membership; the link itself grants nothing.
        List<Map<String, Object>> membership = jdbc.queryForList("SELECT role, status FROM business_memberships WHERE user_id = ? AND business_id = ?",
                userId, businessId);
        String accountStatus = jdbc.queryForList("SELECT account_status FROM app_users WHERE id = ?", String.class, userId).stream().findFirst().orElse(null);
        if (membership.isEmpty() || !"ACTIVE".equals(membership.get(0).get("status")) || !"ACTIVE".equals(accountStatus)) {
            return finish(providerId, linkId, "ACCESS_DENIED", "membership_inactive", e164, "Access to this business is no longer active.");
        }
        MembershipRole role = MembershipRole.valueOf((String) membership.get(0).get("role"));
        if (!authorization.hasRolePermission(role, BusinessPermission.FINANCIAL_DATA_READ)) {
            return finish(providerId, linkId, "ACCESS_DENIED", "role_not_permitted", e164, "Your role cannot read financial data.");
        }
        if (!modules.isEnabled(businessId, "whatsapp") || !modules.isEnabled(businessId, "ai")) {
            return finish(providerId, linkId, "ACCESS_DENIED", "module_disabled", e164, "WhatsApp assistant is disabled for this business.");
        }
        BusinessAccessContext ctx = new BusinessAccessContext(userId, businessId, role);
        UUID conversationId = conversation(linkId, businessId);
        List<CopilotTypes.Turn> history = turns(conversationId);
        CopilotTypes.Response answer = copilot.ask(ctx, command.length() > 1000 ? command.substring(0, 1000) : command, language, history);
        List<CopilotTypes.Turn> updated = new ArrayList<>(history);
        updated.add(new CopilotTypes.Turn("user", command.length() > 1000 ? command.substring(0, 1000) : command));
        updated.add(new CopilotTypes.Turn("assistant", answer.answer().length() > 1000 ? answer.answer().substring(0, 1000) : answer.answer()));
        while (updated.size() > 8) updated.remove(0);
        jdbc.update("UPDATE whatsapp_conversations SET turns_ciphertext = ?, last_message_at = now(), version = version + 1 WHERE id = ?",
                cipher.encrypt(JSON.writeValueAsString(updated), AAD), conversationId);
        jdbc.update("UPDATE whatsapp_inbound_messages SET conversation_id = ? WHERE provider_message_id = ?", conversationId, providerId);
        audit.record(ctx, AuditEventType.WHATSAPP_INBOUND_RECEIVED, "WHATSAPP_INBOUND", linkId,
                Map.of("mode", answer.mode(), "tools", answer.tools().size(), "reason", answer.reasonCode() == null ? "" : answer.reasonCode()));
        return finish(providerId, linkId, "ANSWERED", answer.reasonCode(), e164, answer.answer());
    }

    private Outcome link(String providerId, String phoneHash, String e164, String code) {
        List<Map<String, Object>> pending = jdbc.queryForList("SELECT * FROM whatsapp_inbound_links WHERE phone_hash = ? AND status = 'PENDING' FOR UPDATE",
                phoneHash);
        if (pending.isEmpty()) {
            return finish(providerId, null, "UNLINKED_SENDER", "no_pending_link", e164, "No pending link for this number. Start linking in the FinSight app.");
        }
        Map<String, Object> link = pending.get(0);
        UUID linkId = (UUID) link.get("id");
        jdbc.update("UPDATE whatsapp_inbound_messages SET business_id = ?, link_id = ? WHERE provider_message_id = ?",
                link.get("business_id"), linkId, providerId);
        OffsetDateTime expires = (OffsetDateTime) jdbc.queryForObject("SELECT verification_expires_at FROM whatsapp_inbound_links WHERE id = ?",
                OffsetDateTime.class, linkId);
        int attempts = ((Number) link.get("verification_attempts")).intValue();
        if (expires == null || expires.toInstant().isBefore(Instant.now()) || attempts >= 5) {
            jdbc.update("UPDATE whatsapp_inbound_links SET status = 'EXPIRED', revoked_at = now(), revoke_reason = 'verification_expired', "
                    + "version = version + 1 WHERE id = ?", linkId);
            return finish(providerId, linkId, "UNLINKED_SENDER", "verification_expired", e164, "The link code expired. Start again in the app.");
        }
        String expected = (String) link.get("verification_code_hash");
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), codeHash(linkId, code).getBytes(StandardCharsets.UTF_8))) {
            jdbc.update("UPDATE whatsapp_inbound_links SET verification_attempts = verification_attempts + 1, version = version + 1 WHERE id = ?", linkId);
            return finish(providerId, linkId, "UNLINKED_SENDER", "verification_failed", e164, "That code is not correct.");
        }
        jdbc.update("UPDATE whatsapp_inbound_links SET status = 'ACTIVE', activated_at = now(), verification_code_hash = NULL, "
                + "verification_expires_at = NULL, version = version + 1 WHERE id = ?", linkId);
        return finish(providerId, linkId, "LINKED", null, e164, "ur".equals(link.get("language"))
                ? "آپ کا واٹس ایپ FinSight سے منسلک ہو گیا۔ مالی سوال پوچھیں۔"
                : "Your WhatsApp is linked to FinSight. Ask a financial question anytime.");
    }

    private Outcome finish(String providerId, UUID linkId, String status, String reason, String replyTo, String replyText) {
        String replyStatus = "SKIPPED";
        String replyId = null;
        if (replyTo != null && replyText != null) {
            WhatsAppReplySender.Sent sent = sender.send(replyTo, replyText);
            replyStatus = sent.status();
            replyId = sent.providerMessageId();
        }
        jdbc.update("""
                UPDATE whatsapp_inbound_messages SET status = ?, reason = ?, link_id = COALESCE(link_id, ?), reply_status = ?,
                    reply_ciphertext = ?, reply_provider_message_id = ?, processed_at = now()
                WHERE provider_message_id = ?
                """, status, reason == null ? null : reason.length() > 120 ? reason.substring(0, 120) : reason, linkId, replyStatus,
                replyText == null ? null : cipher.encrypt(replyText, AAD), replyId, providerId);
        return new Outcome(providerId, status, reason);
    }

    // ================================================================ app-side linking

    @Transactional
    public Map<String, Object> startLink(BusinessAccessContext ctx, String phoneNumber, boolean consent, String language) {
        if (!configured()) throw new DomainException("whatsapp_inbound_disabled", 503, "WhatsApp inbound is not configured.");
        if (!consent) throw DomainException.invalid("consent", "required");
        if (!"en".equals(language) && !"ur".equals(language)) throw DomainException.invalid("language", "enum_invalid");
        String e164;
        try {
            e164 = PhoneNumberValidator.normalizeAndValidate(phoneNumber);
        } catch (IllegalArgumentException e) {
            throw DomainException.invalid("phoneNumber", "phone_invalid");
        }
        String hash = phoneHash(e164);
        jdbc.update("UPDATE whatsapp_inbound_links SET status = 'REVOKED', revoked_at = now(), revoke_reason = 'replaced', version = version + 1 "
                + "WHERE business_id = ? AND user_id = ? AND status IN ('PENDING', 'ACTIVE')", ctx.businessId(), ctx.userId());
        List<Map<String, Object>> taken = jdbc.queryForList("SELECT id FROM whatsapp_inbound_links WHERE phone_hash = ? AND status IN ('PENDING', 'ACTIVE')", hash);
        if (!taken.isEmpty()) {
            throw DomainException.conflict("phone_already_linked", "This number is already linked to another FinSight user.");
        }
        UUID id = UUID.randomUUID();
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        jdbc.update("""
                INSERT INTO whatsapp_inbound_links(id, business_id, user_id, phone_hash, phone_last4, status, verification_code_hash,
                    verification_expires_at, language, consent_at) VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, now())
                """, id, ctx.businessId(), ctx.userId(), hash, e164.substring(e164.length() - 4), codeHash(id, code),
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(properties.getLinkCodeTtlMinutes()), language);
        audit.record(ctx, AuditEventType.WHATSAPP_INBOUND_RECEIVED, "WHATSAPP_LINK", id, Map.of("action", "LINK_STARTED"));
        Map<String, Object> body = status(ctx);
        body.put("verificationCode", code);
        body.put("instruction", "Send 'LINK " + code + "' from WhatsApp number ending " + e164.substring(e164.length() - 4)
                + " to the FinSight business number within " + properties.getLinkCodeTtlMinutes() + " minutes.");
        return body;
    }

    @Transactional
    public Map<String, Object> revoke(BusinessAccessContext ctx) {
        jdbc.update("UPDATE whatsapp_inbound_links SET status = 'REVOKED', revoked_at = now(), revoke_reason = 'user_app', version = version + 1 "
                + "WHERE business_id = ? AND user_id = ? AND status IN ('PENDING', 'ACTIVE')", ctx.businessId(), ctx.userId());
        audit.record(ctx, AuditEventType.WHATSAPP_INBOUND_RECEIVED, "WHATSAPP_LINK", null, Map.of("action", "LINK_REVOKED"));
        return status(ctx);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> status(BusinessAccessContext ctx) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, status, phone_last4, language, consent_at, activated_at "
                + "FROM whatsapp_inbound_links WHERE business_id = ? AND user_id = ? ORDER BY created_at DESC LIMIT 1", ctx.businessId(), ctx.userId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("configured", configured());
        if (rows.isEmpty()) {
            body.put("status", "NOT_LINKED");
            return body;
        }
        Map<String, Object> r = rows.get(0);
        body.put("status", r.get("status"));
        body.put("phoneLast4", r.get("phone_last4"));
        body.put("language", r.get("language"));
        body.put("consentAt", r.get("consent_at") == null ? null : r.get("consent_at").toString());
        body.put("activatedAt", r.get("activated_at") == null ? null : r.get("activated_at").toString());
        return body;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> recentMessages(BusinessAccessContext ctx) {
        return jdbc.queryForList("""
                SELECT m.received_at, m.message_type, m.status, m.reason, m.reply_status, l.phone_last4
                FROM whatsapp_inbound_messages m LEFT JOIN whatsapp_inbound_links l ON l.id = m.link_id
                WHERE m.business_id = ? ORDER BY m.received_at DESC LIMIT 100
                """, ctx.businessId()).stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("receivedAt", r.get("received_at").toString());
            m.put("type", r.get("message_type"));
            m.put("status", r.get("status"));
            m.put("reason", r.get("reason"));
            m.put("replyStatus", r.get("reply_status"));
            m.put("phoneLast4", r.get("phone_last4"));
            return m;
        }).toList();
    }

    // ================================================================ helpers

    private UUID conversation(UUID linkId, UUID businessId) {
        List<UUID> open = jdbc.queryForList("SELECT id FROM whatsapp_conversations WHERE link_id = ? AND status = 'OPEN' "
                + "AND last_message_at > now() - (? * interval '1 minute') ORDER BY last_message_at DESC LIMIT 1", UUID.class,
                linkId, properties.getSessionIdleMinutes());
        if (!open.isEmpty()) return open.get(0);
        jdbc.update("UPDATE whatsapp_conversations SET status = 'CLOSED', version = version + 1 WHERE link_id = ? AND status = 'OPEN'", linkId);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO whatsapp_conversations(id, link_id, business_id) VALUES (?, ?, ?)", id, linkId, businessId);
        return id;
    }

    private List<CopilotTypes.Turn> turns(UUID conversationId) {
        String encrypted = jdbc.queryForObject("SELECT turns_ciphertext FROM whatsapp_conversations WHERE id = ?", String.class, conversationId);
        if (encrypted == null) return List.of();
        try {
            List<CopilotTypes.Turn> out = new ArrayList<>();
            for (JsonNode t : JSON.readTree(cipher.decrypt(encrypted, AAD))) {
                out.add(new CopilotTypes.Turn(t.path("role").asString(), t.path("content").asString()));
            }
            return out;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    String phoneHash(String e164) {
        return HexFormat.of().formatHex(hmac(properties.getPhoneHashKey(), e164.getBytes(StandardCharsets.UTF_8)));
    }

    private String codeHash(UUID linkId, String code) {
        return HexFormat.of().formatHex(hmac(properties.getPhoneHashKey(), (linkId + ":" + code).getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] hmac(String key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private static Instant parseTimestamp(String raw) {
        try {
            return raw == null ? null : Instant.ofEpochSecond(Long.parseLong(raw));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isUrdu(String text) {
        return text.codePoints().anyMatch(cp -> cp >= 0x0600 && cp <= 0x06FF);
    }
}
