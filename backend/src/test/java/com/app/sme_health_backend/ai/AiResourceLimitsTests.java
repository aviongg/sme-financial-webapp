package com.app.sme_health_backend.ai;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AiResourceLimitsTests {
    static final class MutableClock extends Clock {
        Instant instant = Instant.parse("2026-10-12T10:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return instant; }
        void seconds(long v) { instant = instant.plusSeconds(v); }
    }
    final AiProperties p = new AiProperties();
    final MutableClock clock = new MutableClock();
    final AiResourceLimits limits = new AiResourceLimits(p, clock);
    final UUID business = UUID.randomUUID();
    @Test void perBusinessRequestRateDoesNotLeakAcrossBusinesses() {
        p.setRequestsPerMinute(1); assertNull(limits.request(business)); assertEquals("rate_limited", limits.request(business));
        assertNull(limits.request(UUID.randomUUID())); clock.seconds(60); assertNull(limits.request(business));
    }
    @Test void inFlightAndDailyQuotaAreAtomicAndFailureConsumesQuota() {
        p.setDailyProviderCalls(1); limits.request(business);
        assertNull(limits.beginProvider(business)); assertEquals("request_in_progress", limits.beginProvider(business));
        limits.finishProvider(business, false); assertEquals("daily_quota_reached", limits.beginProvider(business));
        clock.seconds(86400); assertNull(limits.request(business)); assertNull(limits.beginProvider(business));
    }
    @Test void capacityFailsClosedRatherThanEvictingActiveQuotas() {
        p.setMaxTrackedBusinesses(1); limits.request(business);
        assertEquals("capacity_limited", limits.request(UUID.randomUUID()));
        clock.seconds(86400); assertNull(limits.request(UUID.randomUUID()));
    }
    @Test void circuitStopsCallsAndRecoversAfterCooldown() {
        p.setCircuitFailureThreshold(1); p.setCircuitCooldownSeconds(10); limits.request(business);
        limits.beginProvider(business); limits.finishProvider(business, false);
        assertEquals("provider_circuit_open", limits.beginProvider(business));
        clock.seconds(10); assertNull(limits.beginProvider(business)); limits.finishProvider(business, true);
    }
    @Test void cacheExpiresAndCapacityRemainsBounded() {
        p.setMaxCacheEntries(1); p.setCacheTtlSeconds(5);
        var c = AiTestFixtures.context("score", "en", "55.00", "0.25", false);
        var response = new AiGroundingValidator().render(c, SimulatedAiProvider.selection(c), "simulated", null, null);
        limits.cache("first", response); assertTrue(limits.cached("first").provenance().cached());
        limits.cache("second", response); assertNull(limits.cached("first"));
        clock.seconds(5); assertNull(limits.cached("second"));
    }
    @Test void aggregateConcurrencyIsBoundedAtomicallyAndCapacityRejectionDoesNotSpendQuota() throws Exception {
        p.setMaxConcurrentProviderCalls(2); p.setDailyProviderCalls(1);
        var start = new java.util.concurrent.CountDownLatch(1);
        var decided = new java.util.concurrent.CountDownLatch(8);
        var release = new java.util.concurrent.CountDownLatch(1);
        var accepted = new java.util.concurrent.ConcurrentLinkedQueue<UUID>();
        var rejected = new java.util.concurrent.ConcurrentLinkedQueue<UUID>();
        var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        try {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 8; i++) {
                UUID tenant = UUID.randomUUID(); limits.request(tenant);
                tasks.add(executor.submit(() -> {
                    start.await(); String result = limits.beginProvider(tenant);
                    if (result == null) accepted.add(tenant);
                    else { assertEquals("provider_capacity_limited", result); rejected.add(tenant); }
                    decided.countDown(); release.await();
                    if (result == null) limits.finishProvider(tenant, true);
                    return null;
                }));
            }
            start.countDown(); assertTrue(decided.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(2, accepted.size()); assertEquals(6, rejected.size()); release.countDown();
            for (var task : tasks) task.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } finally { release.countDown(); executor.shutdownNow(); }
        UUID retry = rejected.iterator().next();
        assertNull(limits.beginProvider(retry)); limits.finishProvider(retry, true);
        assertEquals("daily_quota_reached", limits.beginProvider(retry));
    }
    @Test void duplicateCompletionCannotReleaseAnotherTenantsCapacity() {
        p.setMaxConcurrentProviderCalls(1); UUID other = UUID.randomUUID();
        limits.request(business); limits.request(other); limits.beginProvider(business);
        limits.finishProvider(other, true);
        assertEquals("provider_capacity_limited", limits.beginProvider(other));
        limits.finishProvider(business, true); limits.finishProvider(business, true);
        assertNull(limits.beginProvider(other));
    }
}
