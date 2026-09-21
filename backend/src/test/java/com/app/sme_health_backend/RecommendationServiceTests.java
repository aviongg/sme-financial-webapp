package com.app.sme_health_backend;

import com.app.i18n.TranslationService;
import com.app.sme_health_backend.recommendation.entity.Recommendation;
import com.app.sme_health_backend.recommendation.repository.RecommendationRepository;
import com.app.sme_health_backend.recommendation.service.RecommendationService;
import com.app.sme_health_backend.score.dto.ScoreResult;
import com.app.sme_health_backend.shared.advice.AdviceContext;
import com.app.sme_health_backend.shared.advice.AdviceContextService;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTests {

    @Mock
    private RecommendationRepository recommendationRepository;

    @Mock
    private AdviceContextService adviceContextService;

    private RecommendationService recommendationService;
    private UUID userId;
    private static final LocalDateTime COMPUTED_AT = LocalDateTime.of(2026, 9, 15, 12, 30);

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService(
                recommendationRepository, adviceContextService,
                new TranslationService(new ObjectMapper()));
        userId = UUID.randomUUID();
    }

    @Test
    void shouldGenerateComponentBandAndDataQualityAdviceFromContract() {
        ScoreResult score = scoreResult("Needs Attention", "profitability", "0.65");
        List<Recommendation> recommendations = recommendationService.generateRecommendations(score);

        assertEquals(3, recommendations.size());
        assertEquals("profitability", recommendations.get(0).getCategory());
        assertEquals("high", recommendations.get(0).getPriority());
        assertTrue(recommendations.get(0).getText().contains("48.00"));
        assertTrue(recommendations.get(0).getText().contains("pricing"));
        assertEquals("overall_health", recommendations.get(1).getCategory());
        assertEquals("high", recommendations.get(1).getPriority());
        assertEquals("data_quality", recommendations.get(2).getCategory());
        assertEquals("high", recommendations.get(2).getPriority());
        recommendations.forEach(recommendation -> {
            assertEquals(userId, recommendation.getUserId());
            assertEquals("2026-09", recommendation.getMonth());
            assertEquals("en", recommendation.getLanguage());
            assertEquals(COMPUTED_AT, recommendation.getSourceComputedAt());
            assertEquals(0, recommendation.getCreatedAt().getNano() % 1_000);
            assertFalse(recommendation.getText().contains("{"));
        });
        verifyNoInteractions(adviceContextService, recommendationRepository);
    }

    @ParameterizedTest
    @CsvSource({"Strong,low", "Stable,medium", "Needs Attention,high", "At Risk,high"})
    void shouldUseEveryCanonicalBandWithoutRecomputingScore(String band, String priority) {
        // The composite stays 95 for every band: the caller owns band calculation.
        List<Recommendation> recommendations = recommendationService.generateRecommendations(
                scoreResult(band, "cashflow", "0.90"));
        assertEquals(priority, recommendations.get(1).getPriority());
        assertTrue(recommendations.get(1).getText().contains(band));
        assertEquals("medium", recommendations.get(0).getPriority());
        assertEquals("low", recommendations.get(2).getPriority());
    }

    @ParameterizedTest
    @CsvSource({"cashflow,weekly", "profitability,pricing", "repayment,due dates",
            "trend,monthly results", "compliance,up to date"})
    void shouldGiveSpecificActionForEveryCanonicalComponent(String component, String action) {
        Recommendation recommendation = recommendationService.generateRecommendations(
                scoreResult("Stable", component, "1.0")).get(0);
        assertEquals(component, recommendation.getCategory());
        assertTrue(recommendation.getText().contains(action));
    }

    @ParameterizedTest
    @CsvSource({"59.99,high", "60.00,medium", "79.99,medium", "80.00,low"})
    void shouldUseComponentPriorityBoundaries(String value, String priority) {
        Map<String, BigDecimal> components = components();
        components.put("cashflow", new BigDecimal(value));
        ScoreResult score = scoreWithComponents("Stable", "cashflow", components);
        assertEquals(priority, recommendationService.generateRecommendations(score).get(0).getPriority());
    }

    @Test
    void shouldHandleUnavailableComponentWithoutInventingScore() {
        Map<String, BigDecimal> components = components();
        components.put("trend", null);
        components.put("compliance", null);
        ScoreResult score = scoreWithComponents("At Risk", "trend", components);
        Recommendation recommendation = recommendationService.generateRecommendations(score).get(0);
        assertEquals("high", recommendation.getPriority());
        assertTrue(recommendation.getText().contains("recent monthly results"));
        assertFalse(recommendation.getText().contains("component score"));
        assertFalse(recommendation.getText().contains("null"));
    }

    @Test
    void shouldTreatEightyPercentCompletenessAsComplete() {
        List<Recommendation> recommendations = recommendationService.generateRecommendations(
                scoreResult("Stable", "cashflow", "0.80"));
        assertEquals("low", recommendations.get(2).getPriority());
    }

    @Test
    void shouldGenerateUrduAndFallbackToEnglishForUnsupportedLanguage() {
        ScoreResult score = scoreResult("Stable", "cashflow", "0.90");
        List<Recommendation> urdu = recommendationService.generateRecommendations(score, " UR ");
        assertTrue(urdu.get(0).getText().contains("کیش فلو"));
        assertEquals("ur", urdu.get(0).getLanguage());
        assertFalse(urdu.get(1).getText().startsWith("Your financial"));
        assertEquals(recommendationService.generateRecommendations(score, "en").get(0).getText(),
                recommendationService.generateRecommendations(score, "fr").get(0).getText());
    }

    @Test
    void shouldGenerateLatestStoredScoreAndRetainRowsOnRepeatedReads() {
        AdviceContext context = context("en", "a".repeat(64));
        AtomicReference<List<Recommendation>> persisted = new AtomicReference<>(List.of());
        when(adviceContextService.latest(userId)).thenReturn(Optional.of(context));
        when(recommendationRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-09"))
                .thenAnswer(invocation -> persisted.get());
        when(recommendationRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Recommendation> rows = invocation.getArgument(0);
            persisted.set(rows);
            return rows;
        });

        List<Recommendation> first = recommendationService.getRecommendations(userId);
        List<Recommendation> second = recommendationService.getRecommendations(userId);

        assertEquals(3, first.size());
        for (int i = 0; i < first.size(); i++) {
            assertSame(first.get(i), second.get(i));
            assertEquals("a".repeat(64), first.get(i).getSourceVersion());
            assertEquals(COMPUTED_AT, first.get(i).getSourceComputedAt());
        }
        InOrder writes = inOrder(recommendationRepository);
        writes.verify(recommendationRepository).findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-09");
        writes.verify(recommendationRepository).deleteByUserIdAndMonth(userId, "2026-09");
        writes.verify(recommendationRepository).flush();
        writes.verify(recommendationRepository).saveAll(anyList());
        verify(recommendationRepository, times(1)).saveAll(anyList());
    }

    @Test
    void shouldReturnExistingCategoriesInDeterministicOrder() {
        AdviceContext context = context("en", "a".repeat(64));
        List<Recommendation> cached = cached(context);
        when(adviceContextService.latest(userId)).thenReturn(Optional.of(context));
        when(recommendationRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-09"))
                .thenReturn(List.of(cached.get(2), cached.get(0), cached.get(1)));

        assertEquals(cached, recommendationService.getRecommendations(userId));
        verify(recommendationRepository, never()).saveAll(anyList());
        verify(recommendationRepository, never()).deleteByUserIdAndMonth(userId, "2026-09");
    }

    @ParameterizedTest
    @ValueSource(strings = {"source", "language", "computedAt", "text", "priority", "partial", "duplicate", "legacy"})
    void shouldRefreshStalePartialAndInvalidCachedAdvice(String change) {
        AdviceContext context = context("en", "b".repeat(64));
        List<Recommendation> stored = new ArrayList<>(cached(context));
        switch (change) {
            case "source" -> stored.get(0).setSourceVersion("a".repeat(64));
            case "language" -> stored.get(0).setLanguage("ur");
            case "computedAt" -> stored.get(0).setSourceComputedAt(COMPUTED_AT.minusDays(1));
            case "text" -> stored.get(0).setText("Outdated advice");
            case "priority" -> stored.get(0).setPriority("low");
            case "partial" -> stored.remove(0);
            case "duplicate" -> stored.set(0, stored.get(1));
            case "legacy" -> stored.forEach(row -> row.setSourceVersion(null));
            default -> fail("Unknown change");
        }
        when(adviceContextService.latest(userId)).thenReturn(Optional.of(context));
        when(recommendationRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-09"))
                .thenReturn(stored);
        when(recommendationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Recommendation> result = recommendationService.getRecommendations(userId);

        assertEquals(3, result.size());
        assertEquals(3, result.stream().map(Recommendation::getCategory).distinct().count());
        assertEquals("b".repeat(64), result.get(0).getSourceVersion());
        verify(recommendationRepository).deleteByUserIdAndMonth(userId, "2026-09");
        verify(recommendationRepository).flush();
        verify(recommendationRepository).saveAll(anyList());
    }

    @Test
    void shouldGenerateExactRequestedMonth() {
        AdviceContext context = context("en", "a".repeat(64));
        when(adviceContextService.forMonth(userId, "2026-09")).thenReturn(Optional.of(context));
        when(recommendationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Recommendation> result = recommendationService.getRecommendations(userId, "2026-09");

        assertEquals("2026-09", result.get(0).getMonth());
        verify(adviceContextService, never()).latest(userId);
    }

    @Test
    void shouldReturnEmptyWithoutScoreAndNeverServeOldAdviceAsCurrent() {
        when(adviceContextService.latest(userId)).thenReturn(Optional.empty());
        assertEquals(List.of(), recommendationService.getRecommendations(userId));
        verifyNoInteractions(recommendationRepository);
    }

    @Test
    void shouldReturnEmptyForRequestedMonthWithoutScore() {
        when(adviceContextService.forMonth(userId, "2026-08")).thenReturn(Optional.empty());
        assertEquals(List.of(), recommendationService.getRecommendations(userId, "2026-08"));
        verifyNoInteractions(recommendationRepository);
    }

    @Test
    void shouldGenerateAndPersistUrduUsingPersistedProfileContext() {
        AdviceContext context = context("ur", "a".repeat(64));
        when(adviceContextService.forScore(context.score())).thenReturn(context);
        when(recommendationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Recommendation> result = recommendationService.generateAndSaveRecommendations(context.score());

        assertTrue(result.get(0).getText().contains("کیش فلو"));
        assertEquals("ur", result.get(0).getLanguage());
        verify(adviceContextService).forScore(context.score());
    }

    @Test
    void shouldRejectStaleOrArbitrarySnapshotBeforeWriting() {
        ScoreResult supplied = scoreResult("Stable", "cashflow", "0.90");
        when(adviceContextService.forScore(supplied))
                .thenThrow(new IllegalArgumentException("Score snapshot is stale"));
        assertThrows(IllegalArgumentException.class,
                () -> recommendationService.generateAndSaveRecommendations(supplied));
        verifyNoInteractions(recommendationRepository);
    }

    @Test
    void shouldRejectUnknownBusinessProfileBeforeWriting() {
        when(adviceContextService.latest(userId))
                .thenThrow(new ResourceNotFoundException("Business profile not found for this user"));
        assertThrows(ResourceNotFoundException.class,
                () -> recommendationService.getRecommendations(userId));
        verifyNoInteractions(recommendationRepository);
    }

    @Test
    void shouldRejectNullUserAndScore() {
        assertThrows(IllegalArgumentException.class,
                () -> recommendationService.getRecommendations(null));
        assertThrows(IllegalArgumentException.class,
                () -> recommendationService.generateRecommendations(null));
        verifyNoInteractions(adviceContextService, recommendationRepository);
    }

    @Test
    void shouldRejectNonCanonicalComponentNames() {
        Map<String, BigDecimal> components = components();
        components.put("liquidity", components.remove("cashflow"));
        ScoreResult score = scoreWithComponents("Stable", "liquidity", components);
        assertThrows(IllegalArgumentException.class,
                () -> recommendationService.generateRecommendations(score));
    }

    private AdviceContext context(String language, String sourceVersion) {
        return new AdviceContext(scoreResult("Stable", "cashflow", "0.90"), null, language, sourceVersion);
    }

    private List<Recommendation> cached(AdviceContext context) {
        List<Recommendation> rows = recommendationService.generateRecommendations(context.score(), context.language());
        rows.forEach(row -> row.setSourceVersion(context.sourceVersion()));
        return rows;
    }

    private ScoreResult scoreResult(String band, String weakest, String completeness) {
        return new ScoreResult(userId, "2026-09", new BigDecimal("95.00"), band,
                components(), weakest, new BigDecimal(completeness), COMPUTED_AT);
    }

    private ScoreResult scoreWithComponents(String band, String weakest, Map<String, BigDecimal> components) {
        return new ScoreResult(userId, "2026-09", new BigDecimal("95.00"), band,
                components, weakest, new BigDecimal("0.90"), COMPUTED_AT);
    }

    private Map<String, BigDecimal> components() {
        return new LinkedHashMap<>(Map.of(
                "cashflow", new BigDecimal("70.00"),
                "profitability", new BigDecimal("48.00"),
                "repayment", new BigDecimal("74.00"),
                "trend", new BigDecimal("66.00"),
                "compliance", new BigDecimal("80.00")));
    }
}
