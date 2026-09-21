package com.app.sme_health_backend.shared.advice;

import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.score.dto.ScoreResult;
import com.app.sme_health_backend.score.repository.ScoreResultReader;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdviceContextServiceTests {

    @Mock private BusinessProfileRepository profileRepository;
    @Mock private ScoreResultReader reader;
    private AdviceContextService service;
    private final UUID userId = UUID.randomUUID();
    private final LocalDateTime computedAt = LocalDateTime.of(2026, 9, 19, 12, 30);

    @BeforeEach
    void setUp() {
        service = new AdviceContextService(reader, profileRepository);
    }

    @Test
    void locksProfileBeforeReadingScoresAndUsesExactPreviousCalendarMonth() {
        profile("en");
        ScoreResult current = score("2026-01", "60", "0.8", computedAt);
        when(reader.findLatest(userId)).thenReturn(Optional.of(current));
        when(reader.findByUserIdAndMonth(userId, "2025-12")).thenReturn(Optional.empty());

        AdviceContext context = service.latest(userId).orElseThrow();

        assertSame(current, context.score());
        assertNull(context.previousScore());
        assertEquals("en", context.language());
        assertTrue(context.sourceVersion().matches("[a-f0-9]{64}"));
        InOrder order = inOrder(profileRepository, reader);
        order.verify(profileRepository).findByUserIdForUpdate(userId);
        order.verify(reader).findLatest(userId);
        order.verify(reader).findByUserIdAndMonth(userId, "2025-12");
        verifyNoMoreInteractions(reader);
    }

    @Test
    void usesRequestedMonthAndItsPreviousMonthEvenWhenNewerScoresExist() {
        profile("ur");
        ScoreResult current = score("2026-07", "60", "0.8", computedAt);
        ScoreResult previous = score("2026-06", "58", "0.6", computedAt);
        when(reader.findByUserIdAndMonth(userId, "2026-07")).thenReturn(Optional.of(current));
        when(reader.findByUserIdAndMonth(userId, "2026-06")).thenReturn(Optional.of(previous));

        AdviceContext context = service.forMonth(userId, "2026-07").orElseThrow();
        assertSame(previous, context.previousScore());
        assertSame(current, context.score());
        assertEquals("ur", context.language());
        verify(reader, never()).findLatest(userId);
    }

    @Test
    void missingCurrentScoreDoesNotInventContextOrReadPreviousScores() {
        profile("en");
        when(reader.findLatest(userId)).thenReturn(Optional.empty());
        assertTrue(service.latest(userId).isEmpty());
        verify(reader).findLatest(userId);
        verifyNoMoreInteractions(reader);
    }

    @Test
    void fingerprintIsStableAcrossMapOrderingAndEquivalentDecimalScales() {
        profile("en");
        ScoreResult first = score("2026-09", "60.00", "0.80", computedAt);
        Map<String, BigDecimal> reversed = new LinkedHashMap<>();
        ScoreResult.COMPONENT_KEYS.reversed().forEach(key -> reversed.put(key,
                first.getComponentScores().get(key) == null ? null : new BigDecimal("60.0000")));
        ScoreResult equivalent = new ScoreResult(userId, "2026-09", new BigDecimal("60"), "Stable",
                reversed, "cashflow", new BigDecimal("0.8"), computedAt);
        when(reader.findLatest(userId)).thenReturn(Optional.of(first), Optional.of(equivalent));

        assertEquals(service.latest(userId).orElseThrow().sourceVersion(),
                service.latest(userId).orElseThrow().sourceVersion());
    }

    @Test
    void fingerprintInvalidatesWhenCurrentScoreOrTimestampLanguageOrPreviousSnapshotChanges() {
        BusinessProfile profile = profile("en");
        ScoreResult current = score("2026-09", "60", "0.8", computedAt);
        when(reader.findLatest(userId)).thenReturn(Optional.of(current));
        String original = service.latest(userId).orElseThrow().sourceVersion();

        when(reader.findLatest(userId)).thenReturn(Optional.of(score("2026-09", "61", "0.8", computedAt)));
        assertNotEquals(original, service.latest(userId).orElseThrow().sourceVersion());
        when(reader.findLatest(userId)).thenReturn(Optional.of(score("2026-09", "60", "0.8", computedAt.plusSeconds(1))));
        assertNotEquals(original, service.latest(userId).orElseThrow().sourceVersion());
        when(reader.findLatest(userId)).thenReturn(Optional.of(current));
        profile.setLanguagePreference("ur");
        assertNotEquals(original, service.latest(userId).orElseThrow().sourceVersion());
        profile.setLanguagePreference("en");
        when(reader.findByUserIdAndMonth(userId, "2026-08"))
                .thenReturn(Optional.of(score("2026-08", "59", "0.8", computedAt)));
        String withPrevious = service.latest(userId).orElseThrow().sourceVersion();
        assertNotEquals(original, withPrevious);
        when(reader.findByUserIdAndMonth(userId, "2026-08"))
                .thenReturn(Optional.of(score("2026-08", "59", "0.8", computedAt.plusSeconds(1))));
        assertNotEquals(withPrevious, service.latest(userId).orElseThrow().sourceVersion());
    }

    @Test
    void explicitGenerationRejectsMissingOrStaleScoresAndAcceptsEquivalentPersistedSnapshot() {
        profile("en");
        ScoreResult supplied = score("2026-09", "60", "0.8", computedAt);
        when(reader.findByUserIdAndMonth(userId, "2026-09")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.forScore(supplied));
        when(reader.findByUserIdAndMonth(userId, "2026-09"))
                .thenReturn(Optional.of(score("2026-09", "60", "0.8", computedAt.plusSeconds(1))));
        assertThrows(IllegalArgumentException.class, () -> service.forScore(supplied));
        ScoreResult persisted = score("2026-09", "60.00", "0.80", computedAt);
        when(reader.findByUserIdAndMonth(userId, "2026-09")).thenReturn(Optional.of(persisted));
        assertSame(persisted, service.forScore(supplied).score());
    }

    @Test
    void missingProfileFailsBeforeReadingAnyScores() {
        when(profileRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.latest(userId));
        verifyNoInteractions(reader);
    }

    @Test
    void invalidRequestsFailBeforeDatabaseAccess() {
        assertThrows(IllegalArgumentException.class, () -> service.latest(null));
        assertThrows(IllegalArgumentException.class, () -> service.forMonth(userId, "2026-13"));
        assertThrows(IllegalArgumentException.class, () -> service.forScore(null));
        verifyNoInteractions(reader, profileRepository);
    }

    private BusinessProfile profile(String language) {
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(userId);
        profile.setLanguagePreference(language);
        when(profileRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(profile));
        return profile;
    }

    private ScoreResult score(String month, String value, String completeness, LocalDateTime timestamp) {
        Map<String, BigDecimal> components = new LinkedHashMap<>();
        ScoreResult.COMPONENT_KEYS.forEach(key -> components.put(key, new BigDecimal(value)));
        components.put("repayment", null);
        return new ScoreResult(userId, month, new BigDecimal(value), "Stable", components,
                "cashflow", new BigDecimal(completeness), timestamp);
    }
}
