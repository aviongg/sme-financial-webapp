package com.app.sme_health_backend;

import com.app.i18n.TranslationService;
import com.app.sme_health_backend.insight.entity.Insight;
import com.app.sme_health_backend.insight.repository.InsightRepository;
import com.app.sme_health_backend.insight.service.InsightService;
import com.app.sme_health_backend.score.dto.ScoreResult;
import com.app.sme_health_backend.shared.advice.AdviceContext;
import com.app.sme_health_backend.shared.advice.AdviceContextService;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsightServiceTests {

    @Mock
    private InsightRepository insightRepository;
    @Mock
    private AdviceContextService adviceContextService;

    private InsightService insightService;
    private UUID userId;
    private static final LocalDateTime COMPUTED_AT = LocalDateTime.of(2026, 9, 18, 12, 30);

    @BeforeEach
    void setUp() {
        insightService = new InsightService(insightRepository, adviceContextService,
                new TranslationService(new ObjectMapper()));
        userId = UUID.randomUUID();
    }

    @Test
    void shouldGenerateThreeInsightsFromCanonicalScoreIncludingInactiveComponents() {
        ScoreResult score = score("2026-09", "48.00", "Needs Attention", "0.65", "profitability");

        List<Insight> insights = insightService.generateInsights(score);

        assertEquals(3, insights.size());
        assertEquals(List.of("profitability", "overall_health", "data_quality"),
                insights.stream().map(Insight::getCategory).toList());
        assertEquals(List.of("high", "high", "high"),
                insights.stream().map(Insight::getPriority).toList());
        assertTrue(insights.stream().allMatch(i -> userId.equals(i.getUserId())
                && "2026-09".equals(i.getMonth()) && "en".equals(i.getLanguage())
                && COMPUTED_AT.equals(i.getSourceComputedAt()) && !i.getText().isBlank()));
        verifyNoInteractions(insightRepository, adviceContextService);
    }

    @ParameterizedTest
    @CsvSource({"Strong,low", "Stable,medium", "Needs Attention,high", "At Risk,high"})
    void shouldUseSuppliedBandForOverallPriority(String band, String priority) {
        ScoreResult score = score("2026-09", "75.00", band, "0.90", "cashflow");
        assertEquals(priority, insightService.generateInsights(score).get(1).getPriority());
    }

    @ParameterizedTest
    @CsvSource({"70.00,improved by 2 points,low", "74.00,declined by 2 points,high",
            "72.00,unchanged,low"})
    void shouldExplainExactPreviousMonthDelta(String previousValue, String text, String priority) {
        ScoreResult current = score("2026-09", "72.00", "Stable", "0.90", "cashflow");
        ScoreResult previous = score("2026-08", previousValue, "Stable", "0.90", "cashflow");

        List<Insight> insights = insightService.generateInsights(current, "en", previous);

        assertEquals(4, insights.size());
        Insight change = insights.get(3);
        assertEquals("monthly_change", change.getCategory());
        assertEquals(priority, change.getPriority());
        assertTrue(change.getText().contains(text));
        assertTrue(change.getText().contains("2026-08"));
    }

    @Test
    void shouldHandleYearBoundaryAndTranslateTheMonthlyDelta() {
        ScoreResult current = score("2026-01", "72.00", "Stable", "0.90", "cashflow");
        ScoreResult previous = score("2025-12", "70.00", "Stable", "0.90", "cashflow");

        List<Insight> insights = insightService.generateInsights(current, "ur", previous);

        assertEquals(4, insights.size());
        assertTrue(insights.get(0).getText().contains("کیش فلو"));
        assertTrue(insights.get(3).getText().contains("بہتر"));
        assertTrue(insights.get(3).getText().contains("2025-12"));
        assertTrue(insights.stream().allMatch(i -> "ur".equals(i.getLanguage())));
    }

    @ParameterizedTest
    @CsvSource({"70.00,بہتر", "74.00,کم", "72.00,برقرار"})
    void shouldTranslateEveryDeltaDirectionIntoUrdu(String previousValue, String expectedText) {
        ScoreResult current = score("2026-09", "72.00", "Stable", "0.90", "cashflow");
        ScoreResult previous = score("2026-08", previousValue, "Stable", "0.90", "cashflow");

        Insight delta = insightService.generateInsights(current, "ur", previous).get(3);

        assertEquals("ur", delta.getLanguage());
        assertTrue(delta.getText().contains(expectedText));
        assertFalse(delta.getText().contains("{"));
    }

    @Test
    void shouldNotTreatAnOlderMonthAsThePreviousMonth() {
        ScoreResult current = score("2026-09", "72.00", "Stable", "0.90", "cashflow");
        ScoreResult older = score("2026-07", "70.00", "Stable", "0.90", "cashflow");
        assertEquals(3, insightService.generateInsights(current, "en", older).size());
    }

    @Test
    void shouldRejectPreviousScoresFromAnotherUser() {
        ScoreResult current = score("2026-09", "72.00", "Stable", "0.90", "cashflow");
        userId = UUID.randomUUID();
        ScoreResult previous = score("2026-08", "70.00", "Stable", "0.90", "cashflow");
        assertThrows(IllegalArgumentException.class,
                () -> insightService.generateInsights(current, "en", previous));
    }

    @Test
    void shouldGenerateMissingInsightsFromPersistedLatestScore() {
        AdviceContext context = context("en", "a", null);
        when(adviceContextService.latest(userId)).thenReturn(Optional.of(context));
        when(insightRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-09"))
                .thenReturn(List.of());
        saveGeneratedRows();

        List<Insight> insights = insightService.getInsights(userId);

        assertEquals(3, insights.size());
        assertTrue(insights.stream().allMatch(i -> context.sourceVersion().equals(i.getSourceVersion())));
        InOrder order = inOrder(insightRepository);
        order.verify(insightRepository).findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-09");
        order.verify(insightRepository).deleteByUserIdAndMonth(userId, "2026-09");
        order.verify(insightRepository).flush();
        order.verify(insightRepository).saveAll(anyList());
    }

    @Test
    void shouldPreserveStoredIdsAndCreationTimesWithoutWritesOnRepeatedReads() {
        AdviceContext context = context("en", "a", null);
        List<Insight> cached = cached(context);
        List<UUID> ids = cached.stream().map(Insight::getId).toList();
        List<LocalDateTime> times = cached.stream().map(Insight::getCreatedAt).toList();
        when(adviceContextService.latest(userId)).thenReturn(Optional.of(context));
        // Persisted retrieval order is allowed to differ from the canonical response order.
        when(insightRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-09"))
                .thenReturn(List.of(cached.get(2), cached.get(0), cached.get(1)));

        List<Insight> first = insightService.getInsights(userId);
        List<Insight> second = insightService.getInsights(userId);

        assertEquals(ids, first.stream().map(Insight::getId).toList());
        assertEquals(ids, second.stream().map(Insight::getId).toList());
        assertEquals(times, second.stream().map(Insight::getCreatedAt).toList());
        verify(insightRepository, never()).deleteByUserIdAndMonth(any(), any());
        verify(insightRepository, never()).flush();
        verify(insightRepository, never()).saveAll(anyList());
    }

    @Test
    void shouldRefreshLegacyRowsEvenWhenTheirTextStillMatches() {
        AdviceContext context = context("en", "a", null);
        List<Insight> cached = cached(context);
        cached.forEach(i -> { i.setSourceVersion(null); i.setSourceComputedAt(null); i.setLanguage(null); });
        when(adviceContextService.latest(userId)).thenReturn(Optional.of(context));
        when(insightRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-09"))
                .thenReturn(cached);
        saveGeneratedRows();

        List<Insight> result = insightService.getInsights(userId);

        assertEquals(context.sourceVersion(), result.get(0).getSourceVersion());
        verify(insightRepository).saveAll(anyList());
    }

    @Test
    void shouldRepairIncompleteOrDuplicateCategorySets() {
        AdviceContext context = context("en", "a", null);
        List<Insight> expected = cached(context);
        List<Insight> duplicate = List.of(expected.get(0), expected.get(0), expected.get(2));
        when(adviceContextService.latest(userId)).thenReturn(Optional.of(context));
        when(insightRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-09"))
                .thenReturn(duplicate).thenReturn(List.of(expected.get(0)));
        saveGeneratedRows();

        assertEquals(3, insightService.getInsights(userId).size());
        assertEquals(3, insightService.getInsights(userId).size());
        verify(insightRepository, times(2)).saveAll(anyList());
    }

    @Test
    void shouldRefreshWhenLanguageOrPersistedSnapshotChanges() {
        AdviceContext original = context("en", "a", null);
        AdviceContext translated = context("ur", "b", null);
        when(adviceContextService.latest(userId)).thenReturn(Optional.of(translated));
        when(insightRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-09"))
                .thenReturn(cached(original));
        saveGeneratedRows();

        List<Insight> result = insightService.getInsights(userId);

        assertEquals("ur", result.get(0).getLanguage());
        assertEquals(translated.sourceVersion(), result.get(0).getSourceVersion());
        assertTrue(result.get(0).getText().contains("کیش فلو"));
        verify(insightRepository).saveAll(anyList());
    }

    @Test
    void shouldRefreshDeltaAfterPreviousMonthCorrection() {
        ScoreResult previous = score("2026-08", "70.00", "Stable", "0.90", "cashflow");
        ScoreResult correctedPrevious = score("2026-08", "75.00", "Stable", "0.90", "cashflow");
        AdviceContext original = context("en", "a", previous);
        AdviceContext corrected = context("en", "b", correctedPrevious);
        when(adviceContextService.latest(userId)).thenReturn(Optional.of(corrected));
        when(insightRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-09"))
                .thenReturn(cached(original));
        saveGeneratedRows();

        List<Insight> result = insightService.getInsights(userId);

        assertEquals(4, result.size());
        assertTrue(result.get(3).getText().contains("declined by 3 points"));
        assertEquals("high", result.get(3).getPriority());
        verify(insightRepository).saveAll(anyList());
    }

    @Test
    void shouldReturnEmptyWithoutConsultingLegacyAdviceWhenNoPersistedScoreExists() {
        when(adviceContextService.latest(userId)).thenReturn(Optional.empty());

        assertEquals(List.of(), insightService.getInsights(userId));

        verifyNoInteractions(insightRepository);
    }

    @Test
    void shouldUseExactRequestedMonthAndNeverFallbackToLatest() {
        when(adviceContextService.forMonth(userId, "2026-07")).thenReturn(Optional.empty());

        assertEquals(List.of(), insightService.getInsights(userId, "2026-07"));

        verify(adviceContextService, never()).latest(any());
        verifyNoInteractions(insightRepository);
    }

    @Test
    void shouldServeCachedInsightsForAnExplicitMonthWithoutReadingLatest() {
        ScoreResult historical = score("2026-07", "72.00", "Stable", "0.90", "cashflow");
        AdviceContext context = new AdviceContext(historical, null, "en", "a".repeat(64));
        List<Insight> cached = cached(context);
        when(adviceContextService.forMonth(userId, "2026-07")).thenReturn(Optional.of(context));
        when(insightRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-07"))
                .thenReturn(cached);

        assertEquals(cached, insightService.getInsights(userId, "2026-07"));

        verify(adviceContextService, never()).latest(any());
        verify(insightRepository, never()).saveAll(anyList());
    }

    @Test
    void shouldValidateProvidedSnapshotAgainstPersistedSourceBeforeSaving() {
        AdviceContext context = context("ur", "a", null);
        when(adviceContextService.forScore(context.score())).thenReturn(context);
        when(insightRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-09"))
                .thenReturn(List.of());
        saveGeneratedRows();

        List<Insight> result = insightService.generateAndSaveInsights(context.score());

        assertEquals("ur", result.get(0).getLanguage());
        verify(adviceContextService).forScore(context.score());
        verify(insightRepository).saveAll(anyList());
    }

    @Test
    void shouldPropagateMissingProfileAndStaleSourceFailuresWithoutWriting() {
        when(adviceContextService.latest(userId)).thenThrow(
                new ResourceNotFoundException("Business profile not found for this user"));
        assertThrows(ResourceNotFoundException.class, () -> insightService.getInsights(userId));
        AdviceContext context = context("en", "a", null);
        when(adviceContextService.forScore(context.score())).thenThrow(
                new IllegalArgumentException("Score snapshot no longer matches persisted source"));
        assertThrows(IllegalArgumentException.class,
                () -> insightService.generateAndSaveInsights(context.score()));
        verifyNoInteractions(insightRepository);
    }

    @Test
    void shouldRejectNullAndMalformedInputs() {
        assertEquals("User ID is required", assertThrows(IllegalArgumentException.class,
                () -> insightService.getInsights(null)).getMessage());
        assertEquals("Score result is required", assertThrows(IllegalArgumentException.class,
                () -> insightService.generateInsights(null)).getMessage());
        ScoreResult malformed = score("2026-13", "72", "Stable", "0.90", "cashflow");
        assertThrows(IllegalArgumentException.class, () -> insightService.generateInsights(malformed));
        verifyNoInteractions(insightRepository, adviceContextService);
    }

    private void saveGeneratedRows() {
        when(insightRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private AdviceContext context(String language, String versionCharacter, ScoreResult previous) {
        return new AdviceContext(score("2026-09", "72.00", "Stable", "0.90", "cashflow"),
                previous, language, versionCharacter.repeat(64));
    }

    private List<Insight> cached(AdviceContext context) {
        List<Insight> insights = new ArrayList<>(insightService.generateInsights(
                context.score(), context.language(), context.previousScore()));
        insights.forEach(i -> {
            i.setSourceVersion(context.sourceVersion());
            i.setCreatedAt(COMPUTED_AT.minusDays(1));
            ReflectionTestUtils.setField(i, "id", UUID.randomUUID());
        });
        return insights;
    }

    private ScoreResult score(String month, String composite, String band, String completeness, String weakest) {
        Map<String, BigDecimal> components = new HashMap<>();
        components.put("cashflow", new BigDecimal("80.00"));
        components.put("profitability", new BigDecimal("70.00"));
        components.put("repayment", null);
        components.put("trend", null);
        components.put("compliance", new BigDecimal("75.00"));
        components.put(weakest, new BigDecimal("48.00"));
        return new ScoreResult(userId, month, new BigDecimal(composite), band, components,
                weakest, new BigDecimal(completeness), COMPUTED_AT);
    }
}
