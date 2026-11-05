package com.app.sme_health_backend.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** No toString: runtime secrets must never enter diagnostics. Disabled without configuration. */
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {
    private boolean enabled;
    private String provider = "mock";
    private boolean demoEnabled;
    private boolean liveActivationApproved;
    private String model = "gpt-6-luna";
    private String apiKey = "";
    private Set<UUID> allowedBusinessIds = new HashSet<>();
    private int timeoutSeconds = 15;
    private int requestsPerMinute = 10;
    private int dailyProviderCalls = 100;
    private int cacheTtlSeconds = 300;
    private int maxCacheEntries = 500;
    private int maxTrackedBusinesses = 1000;
    private int circuitFailureThreshold = 3;
    private int circuitCooldownSeconds = 60;
    private int maxOutputTokens = 800;
    private int maxConcurrentProviderCalls = 4;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }
    public String getProvider() { return provider; }
    public void setProvider(String v) { provider = v; }
    public boolean isDemoEnabled() { return demoEnabled; }
    public void setDemoEnabled(boolean v) { demoEnabled = v; }
    public boolean isLiveActivationApproved() { return liveActivationApproved; }
    public void setLiveActivationApproved(boolean v) { liveActivationApproved = v; }
    public String getModel() { return model; }
    public void setModel(String v) { model = v; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String v) { apiKey = v; }
    public Set<UUID> getAllowedBusinessIds() { return allowedBusinessIds; }
    public void setAllowedBusinessIds(Set<UUID> v) { allowedBusinessIds = v == null ? Set.of() : Set.copyOf(v); }
    public int getTimeoutSeconds() { return Math.clamp(timeoutSeconds, 1, 30); }
    public void setTimeoutSeconds(int v) { timeoutSeconds = v; }
    public int getRequestsPerMinute() { return Math.clamp(requestsPerMinute, 1, 60); }
    public void setRequestsPerMinute(int v) { requestsPerMinute = v; }
    public int getDailyProviderCalls() { return Math.clamp(dailyProviderCalls, 1, 10000); }
    public void setDailyProviderCalls(int v) { dailyProviderCalls = v; }
    public int getCacheTtlSeconds() { return Math.clamp(cacheTtlSeconds, 1, 3600); }
    public void setCacheTtlSeconds(int v) { cacheTtlSeconds = v; }
    public int getMaxCacheEntries() { return Math.clamp(maxCacheEntries, 1, 5000); }
    public void setMaxCacheEntries(int v) { maxCacheEntries = v; }
    public int getMaxTrackedBusinesses() { return Math.clamp(maxTrackedBusinesses, 1, 10000); }
    public void setMaxTrackedBusinesses(int v) { maxTrackedBusinesses = v; }
    public int getCircuitFailureThreshold() { return Math.clamp(circuitFailureThreshold, 1, 10); }
    public void setCircuitFailureThreshold(int v) { circuitFailureThreshold = v; }
    public int getCircuitCooldownSeconds() { return Math.clamp(circuitCooldownSeconds, 1, 600); }
    public void setCircuitCooldownSeconds(int v) { circuitCooldownSeconds = v; }
    public int getMaxOutputTokens() { return Math.clamp(maxOutputTokens, 256, 2000); }
    public void setMaxOutputTokens(int v) { maxOutputTokens = v; }
    public int getMaxConcurrentProviderCalls() { return Math.clamp(maxConcurrentProviderCalls, 1, 32); }
    public void setMaxConcurrentProviderCalls(int v) { maxConcurrentProviderCalls = v; }
}
