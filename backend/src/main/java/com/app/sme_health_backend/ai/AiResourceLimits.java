package com.app.sme_health_backend.ai;

import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;

/** Bounded single-process cache/quotas. Deploy one replica or add a shared gateway quota before live rollout. */
@Component
public class AiResourceLimits {
    private final AiProperties properties;
    private final Clock clock;
    private final Map<UUID, Budget> budgets = new HashMap<>();
    private final LinkedHashMap<String, Cached> cache = new LinkedHashMap<>();
    private int consecutiveFailures;
    private int activeProviderCalls;
    private Instant circuitUntil = Instant.MIN;
    @org.springframework.beans.factory.annotation.Autowired
    public AiResourceLimits(AiProperties properties) { this(properties, Clock.systemUTC()); }
    AiResourceLimits(AiProperties properties, Clock clock) { this.properties = properties; this.clock = clock; }
    public synchronized String request(UUID businessId) {
        Instant now = clock.instant();
        LocalDate day = LocalDate.ofInstant(now, ZoneOffset.UTC);
        budgets.entrySet().removeIf(e -> !e.getValue().day.equals(day) && !e.getValue().inFlight);
        Budget b = budgets.get(businessId);
        if (b == null) {
            if (budgets.size() >= properties.getMaxTrackedBusinesses()) return "capacity_limited";
            b = new Budget(day, now); budgets.put(businessId, b);
        }
        if (!b.day.equals(day)) { b.day = day; b.calls = 0; }
        if (!now.isBefore(b.minute.plusSeconds(60))) { b.minute = now; b.requests = 0; }
        if (b.requests >= properties.getRequestsPerMinute()) return "rate_limited";
        b.requests++; return null;
    }
    public synchronized String beginProvider(UUID businessId) {
        if (clock.instant().isBefore(circuitUntil)) return "provider_circuit_open";
        Budget b = budgets.get(businessId);
        if (b == null) return "capacity_limited";
        if (b.inFlight) return "request_in_progress";
        if (b.calls >= properties.getDailyProviderCalls()) return "daily_quota_reached";
        if (activeProviderCalls >= properties.getMaxConcurrentProviderCalls()) return "provider_capacity_limited";
        b.calls++; b.inFlight = true; activeProviderCalls++; return null;
    }
    public synchronized void finishProvider(UUID businessId, boolean succeeded) {
        Budget b = budgets.get(businessId);
        // Only a matching active reservation may release capacity or affect the circuit.
        if (b == null || !b.inFlight) return;
        b.inFlight = false;
        activeProviderCalls--;
        if (succeeded) consecutiveFailures = 0;
        else if (++consecutiveFailures >= properties.getCircuitFailureThreshold()) {
            circuitUntil = clock.instant().plusSeconds(properties.getCircuitCooldownSeconds()); consecutiveFailures = 0;
        }
    }
    public synchronized AiTypes.Response cached(String key) {
        evictExpired(); var found = cache.get(key); return found == null ? null : found.response.cached();
    }
    public synchronized void cache(String key, AiTypes.Response response) {
        evictExpired();
        while (cache.size() >= properties.getMaxCacheEntries()) cache.remove(cache.keySet().iterator().next());
        cache.put(key, new Cached(response, clock.instant().plusSeconds(properties.getCacheTtlSeconds())));
    }
    private void evictExpired() { cache.entrySet().removeIf(e -> !clock.instant().isBefore(e.getValue().expires)); }
    private record Cached(AiTypes.Response response, Instant expires) { }
    private static final class Budget {
        LocalDate day; Instant minute; int requests; int calls; boolean inFlight;
        Budget(LocalDate day, Instant now) { this.day = day; this.minute = now; }
    }
}
