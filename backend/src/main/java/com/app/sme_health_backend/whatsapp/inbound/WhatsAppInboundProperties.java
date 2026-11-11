package com.app.sme_health_backend.whatsapp.inbound;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Inbound conversational WhatsApp configuration. No secret has a default: without verifyToken, appSecret and
 * phoneHashKey the webhook stays disabled (503) and nothing is processed. No toString (secrets).
 */
@Component
@ConfigurationProperties(prefix = "app.whatsapp.inbound")
public class WhatsAppInboundProperties {
    private boolean enabled;
    private String verifyToken = "";
    private String appSecret = "";
    private String phoneHashKey = "";
    /** Optional: when set, webhook events for any other business phone number are ignored. */
    private String phoneNumberId = "";
    private int replayWindowSeconds = 300;
    private int maxMessagesPerHour = 20;
    private int maxMessagesPerDay = 100;
    private int linkCodeTtlMinutes = 15;
    private int sessionIdleMinutes = 30;

    public boolean isConfigured() {
        return enabled && !blank(verifyToken) && !blank(appSecret) && phoneHashKey != null && phoneHashKey.length() >= 32;
    }

    private static boolean blank(String v) {
        return v == null || v.isBlank();
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }
    public String getVerifyToken() { return verifyToken; }
    public void setVerifyToken(String v) { verifyToken = v; }
    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String v) { appSecret = v; }
    public String getPhoneHashKey() { return phoneHashKey; }
    public void setPhoneHashKey(String v) { phoneHashKey = v; }
    public String getPhoneNumberId() { return phoneNumberId; }
    public void setPhoneNumberId(String v) { phoneNumberId = v; }
    public int getReplayWindowSeconds() { return Math.clamp(replayWindowSeconds, 30, 3600); }
    public void setReplayWindowSeconds(int v) { replayWindowSeconds = v; }
    public int getMaxMessagesPerHour() { return Math.clamp(maxMessagesPerHour, 1, 200); }
    public void setMaxMessagesPerHour(int v) { maxMessagesPerHour = v; }
    public int getMaxMessagesPerDay() { return Math.clamp(maxMessagesPerDay, 1, 2000); }
    public void setMaxMessagesPerDay(int v) { maxMessagesPerDay = v; }
    public int getLinkCodeTtlMinutes() { return Math.clamp(linkCodeTtlMinutes, 5, 60); }
    public void setLinkCodeTtlMinutes(int v) { linkCodeTtlMinutes = v; }
    public int getSessionIdleMinutes() { return Math.clamp(sessionIdleMinutes, 5, 240); }
    public void setSessionIdleMinutes(int v) { sessionIdleMinutes = v; }
}
