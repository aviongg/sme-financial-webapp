package com.app.sme_health_backend.shared.advice;

import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.scoring.dto.ComponentScoresDto;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import com.app.sme_health_backend.scoring.repository.ScoreResultRepository;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdviceContextServiceTests {

    @Mock
    private ScoreResultRepository scoreResultRepository;

    @Mock
    private BusinessProfileRepository businessProfileRepository;

    private AdviceContextService adviceContextService;
    private UUID userId;
    private BusinessProfile profile;

    @BeforeEach
    void setUp() {
        adviceContextService = new AdviceContextService(scoreResultRepository, businessProfileRepository);
        userId = UUID.randomUUID();

        profile = new BusinessProfile();
        profile.setUserId(userId);
        profile.setLanguagePreference("en");
    }

    @Test
    void shouldReturnAdviceContextForLatestScore() {
        ScoreResult score = createScore(userId, "2026-03", new BigDecimal("75.00"));
        ScoreResult previous = createScore(userId, "2026-02", new BigDecimal("70.00"));

        when(businessProfileRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(profile));
        when(scoreResultRepository.findLatestLocked(userId)).thenReturn(Optional.of(score));
        when(scoreResultRepository.findByUserIdAndMonthLocked(userId, "2026-02")).thenReturn(Optional.of(previous));

        Optional<AdviceContext> contextOpt = adviceContextService.latest(userId);

        assertTrue(contextOpt.isPresent());
        AdviceContext context = contextOpt.get();
        assertEquals(score, context.score());
        assertEquals(previous, context.previousScore());
        assertEquals("en", context.language());
        assertNotNull(context.sourceVersion());
        assertFalse(context.sourceVersion().isBlank());
    }

    @Test
    void shouldReturnAdviceContextForSpecificMonthWithoutPreviousMonthIfMissing() {
        ScoreResult score = createScore(userId, "2026-03", new BigDecimal("75.00"));

        when(businessProfileRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(profile));
        when(scoreResultRepository.findByUserIdAndMonthLocked(userId, "2026-03")).thenReturn(Optional.of(score));
        when(scoreResultRepository.findByUserIdAndMonthLocked(userId, "2026-02")).thenReturn(Optional.empty());

        Optional<AdviceContext> contextOpt = adviceContextService.forMonth(userId, "2026-03");

        assertTrue(contextOpt.isPresent());
        AdviceContext context = contextOpt.get();
        assertEquals(score, context.score());
        assertNull(context.previousScore());
        assertEquals("en", context.language());
    }

    @Test
    void shouldThrowIfProfileNotFound() {
        when(businessProfileRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> adviceContextService.latest(userId));
    }

    @Test
    void shouldThrowIfLanguagePreferenceInvalid() {
        profile.setLanguagePreference("invalid");
        ScoreResult score = createScore(userId, "2026-03", new BigDecimal("75.00"));

        when(businessProfileRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(profile));
        when(scoreResultRepository.findLatestLocked(userId)).thenReturn(Optional.of(score));

        assertThrows(IllegalStateException.class, () -> adviceContextService.latest(userId));
    }

    private ScoreResult createScore(UUID userId, String month, BigDecimal composite) {
        ScoreResult result = new ScoreResult();
        result.setId(UUID.randomUUID());
        result.setUserId(userId);
        result.setMonth(month);
        result.setCompositeScore(composite);
        result.setBand("Stable");
        result.setWeakestComponent("cashflow");
        result.setDataCompleteness(new BigDecimal("1.00"));
        result.setComputedAt(LocalDateTime.now());
        result.setComponentScores(new ComponentScoresDto(
                new BigDecimal("70.00"),
                new BigDecimal("80.00"),
                new BigDecimal("75.00"),
                new BigDecimal("72.00"),
                new BigDecimal("85.00")
        ));
        return result;
    }
}
