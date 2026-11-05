package com.app.sme_health_backend.documents.processing;

import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/** One stale definition for retry and multi-instance startup recovery. Equality is still fresh. */
@Component
public class ProcessingAttemptPolicy {
    public static final Duration STALE_TIMEOUT = Duration.ofMinutes(5);
    private final Clock clock;
    public ProcessingAttemptPolicy(Clock clock) { this.clock = Objects.requireNonNull(clock); }
    public LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), java.time.ZoneId.systemDefault()); }
    public LocalDateTime staleCutoff() { return now().minus(STALE_TIMEOUT); }
    public boolean isStale(LocalDateTime startedAt) {
        return startedAt == null || startedAt.isBefore(staleCutoff());
    }
}
