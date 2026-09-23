package com.app.sme_health_backend;

import com.app.sme_health_backend.i18n.TranslationService;
import com.app.sme_health_backend.recommendation.entity.Recommendation;
import com.app.sme_health_backend.recommendation.repository.RecommendationRepository;
import com.app.sme_health_backend.recommendation.service.RecommendationService;
import com.app.sme_health_backend.scoring.dto.ComponentScoresDto;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import com.app.sme_health_backend.shared.advice.AdviceContext;
import com.app.sme_health_backend.shared.advice.AdviceContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
        assertEquals(List.of("profitability", "overall_health", "data_quality"),
                recommendations.stream().map(Recommendation::getCategory).toList());
        assertEquals(List.of("high", "high", "high"),
                recommendations.stream().map(Recommendation::getPriority).toList());
        assertTrue(recommendations.stream().allMatch(r -> userId.equals(r.getUserId())
                && "2026-09".equals(r.getMonth()) && "en".equals(r.getLanguage())
                && COMPUTED_AT.equals(r.getSourceComputedAt()) && !r.getText().isBlank()));
        verifyNoInteractions(recommendationRepository, adviceContextService);
    }

    @ParameterizedTest
    @CsvSource({"Strong,low", "Stable,medium", "Needs Attention,high", "At Risk,high"})
    void shouldUseBandForOverallHealthPriority(String band, String priority) {
        ScoreResult score = scoreResult(band, "cashflow", "0.90");
        Recommendation overall = recommendationService.generateRecommendations(score).get(1);
        assertEquals("overall_health", overall.getCategory());
        assertEquals(priority, overall.getPriority());
    }

    @Test
    void shouldTranslateRecommendationsToUrdu() {
        ScoreResult score = scoreResult("Stable", "cashflow", "0.90");
        List<Recommendation> recommendations = recommendationService.generateRecommendations(score, "ur");

        assertEquals(3, recommendations.size());
        assertTrue(recommendations.get(0).getText().contains("کیش فلو"));
        assertTrue(recommendations.stream().allMatch(r -> "ur".equals(r.getLanguage())));
    }

    @Test
    void shouldReturnEmptyListWhenScoreIsAbsent() {
        when(adviceContextService.latest(userId)).thenReturn(Optional.empty());

        List<Recommendation> recommendations = recommendationService.getRecommendations(userId);

        assertTrue(recommendations.isEmpty());
        verify(adviceContextService).latest(userId);
        verifyNoInteractions(recommendationRepository);
    }

    @Test
    void shouldRefreshAndPersistRecommendationsWhenExistingAreMissingOrStale() {
        ScoreResult score = scoreResult("Stable", "cashflow", "0.90");
        AdviceContext context = new AdviceContext(score, null, "en", "fp-456");

        when(adviceContextService.latest(userId)).thenReturn(Optional.of(context));
        when(recommendationRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-09"))
                .thenReturn(List.of());
        when(recommendationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<Recommendation> recommendations = recommendationService.getRecommendations(userId);

        assertEquals(3, recommendations.size());
        verify(recommendationRepository).deleteByUserIdAndMonth(userId, "2026-09");
        verify(recommendationRepository).flush();
        verify(recommendationRepository).saveAll(anyList());
    }

    @Test
    void shouldReturnExistingRecommendationsWhenFingerprintAndContentMatch() {
        ScoreResult score = scoreResult("Stable", "cashflow", "0.90");
        AdviceContext context = new AdviceContext(score, null, "en", "fp-456");

        List<Recommendation> generated = recommendationService.generateRecommendations(score, "en");
        generated.forEach(r -> r.setSourceVersion("fp-456"));

        when(adviceContextService.latest(userId)).thenReturn(Optional.of(context));
        when(recommendationRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-09"))
                .thenReturn(generated);

        List<Recommendation> recommendations = recommendationService.getRecommendations(userId);

        assertEquals(3, recommendations.size());
        verify(recommendationRepository, never()).deleteByUserIdAndMonth(any(), any());
        verify(recommendationRepository, never()).saveAll(anyList());
    }

    private ScoreResult scoreResult(String band, String weakest, String completeness) {
        ScoreResult result = new ScoreResult();
        result.setId(UUID.randomUUID());
        result.setUserId(userId);
        result.setMonth("2026-09");
        result.setCompositeScore(new BigDecimal("65.00"));
        result.setBand(band);
        result.setDataCompleteness(new BigDecimal(completeness));
        result.setWeakestComponent(weakest);
        result.setComputedAt(COMPUTED_AT);
        result.setComponentScores(new ComponentScoresDto(
                new BigDecimal("55.00"),
                new BigDecimal("40.00"),
                new BigDecimal("70.00"),
                new BigDecimal("60.00"),
                new BigDecimal("80.00")
        ));
        return result;
    }
}
