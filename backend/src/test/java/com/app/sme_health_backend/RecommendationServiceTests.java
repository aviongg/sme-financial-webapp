package com.app.sme_health_backend;

import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.recommendation.entity.Recommendation;
import com.app.sme_health_backend.recommendation.repository.RecommendationRepository;
import com.app.sme_health_backend.recommendation.service.RecommendationService;
import com.app.sme_health_backend.scoring.dto.ComponentScoresDto;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTests {

    @Mock
    private RecommendationRepository recommendationRepository;

    @Mock
    private BusinessProfileRepository businessProfileRepository;

    private RecommendationService recommendationService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService(
                recommendationRepository,
                businessProfileRepository
        );
        userId = UUID.randomUUID();
    }

    @Test
    void shouldGenerateRecommendationsFromScoreResult() {
        ScoreResult scoreResult = scoreResult(
                new BigDecimal("48.00"),
                "Needs Attention",
                new BigDecimal("0.65"),
                "profitability"
        );

        List<Recommendation> recommendations =
                recommendationService.generateRecommendations(scoreResult);

        assertEquals(3, recommendations.size());
        assertEquals("profitability", recommendations.get(0).getCategory());
        assertEquals("high", recommendations.get(0).getPriority());
        assertTrue(recommendations.get(0).getText().contains("48.00"));
        assertEquals("overall_health", recommendations.get(1).getCategory());
        assertEquals("high", recommendations.get(1).getPriority());
        assertEquals("data_quality", recommendations.get(2).getCategory());
        assertEquals("high", recommendations.get(2).getPriority());
        assertEquals(userId, recommendations.get(0).getUserId());
        assertEquals("2026-09", recommendations.get(0).getMonth());
        assertFalse(recommendations.get(0).getText().isBlank());
    }

    @Test
    void shouldUseBandPriorityWithoutRecalculatingCompositeScore() {
        ScoreResult scoreResult = scoreResult(
                new BigDecimal("95.00"),
                "At Risk",
                new BigDecimal("0.90"),
                "cashflow"
        );

        List<Recommendation> recommendations =
                recommendationService.generateRecommendations(scoreResult);

        assertEquals("medium", recommendations.get(0).getPriority());
        assertEquals("high", recommendations.get(1).getPriority());
        assertEquals("low", recommendations.get(2).getPriority());
        assertTrue(recommendations.get(1).getText().contains("At Risk"));
    }

    @Test
    void shouldReturnStoredRecommendationsWithoutGeneratingDuplicates() {
        Recommendation storedRecommendation = recommendation(userId);

        when(businessProfileRepository.existsById(userId))
                .thenReturn(true);
        when(recommendationRepository
                .findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(storedRecommendation));

        List<Recommendation> result =
                recommendationService.getRecommendations(userId);

        assertEquals(List.of(storedRecommendation), result);
        verify(recommendationRepository)
                .findByUserIdOrderByCreatedAtDesc(userId);
        verify(recommendationRepository, never()).saveAll(anyList());
    }

    @Test
    void shouldGenerateAndPersistMockRecommendationsWhenNoneExist() {
        when(businessProfileRepository.existsById(userId))
                .thenReturn(true);
        when(recommendationRepository
                .findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of());
        when(recommendationRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<Recommendation> result =
                recommendationService.getRecommendations(userId);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(userId, result.get(0).getUserId());
        assertEquals("cashflow", result.get(0).getCategory());
        assertEquals("high", result.get(0).getPriority());

        verify(recommendationRepository)
                .findByUserIdOrderByCreatedAtDesc(userId);
        verify(recommendationRepository).saveAll(anyList());
    }

    @Test
    void shouldRejectRecommendationsForUnknownBusinessProfile() {
        when(businessProfileRepository.existsById(userId))
                .thenReturn(false);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> recommendationService.getRecommendations(userId)
        );

        assertEquals(
                "Business profile not found for this user",
                exception.getMessage()
        );

        verify(recommendationRepository, never())
                .findByUserIdOrderByCreatedAtDesc(userId);
        verify(recommendationRepository, never()).saveAll(anyList());
    }

    @Test
    void shouldRejectNullUserId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> recommendationService.getRecommendations(null)
        );

        assertEquals("User ID is required", exception.getMessage());
    }

    @Test
    void shouldRejectNullScoreResult() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> recommendationService.generateRecommendations(null)
        );

        assertEquals("Score result is required", exception.getMessage());
    }

    private ScoreResult scoreResult(
            BigDecimal compositeScore,
            String band,
            BigDecimal dataCompleteness,
            String weakestComponent
    ) {
        ScoreResult result = new ScoreResult();
        result.setUserId(userId);
        result.setMonth("2026-09");
        result.setCompositeScore(compositeScore);
        result.setBand(band);
        result.setComponentScores(ComponentScoresDto.fromMap(Map.of(
                "cashflow", new BigDecimal("70.00"),
                "profitability", new BigDecimal("48.00"),
                "repayment", new BigDecimal("74.00")
        )));
        result.setWeakestComponent(weakestComponent);
        result.setDataCompleteness(dataCompleteness);
        result.setComputedAt(LocalDateTime.now());
        return result;
    }

    private Recommendation recommendation(UUID userId) {
        Recommendation recommendation = new Recommendation();

        recommendation.setUserId(userId);
        recommendation.setMonth("2026-09");
        recommendation.setText("Review monthly cash commitments.");
        recommendation.setCategory("cashflow");
        recommendation.setPriority("high");
        recommendation.setCreatedAt(LocalDateTime.now());

        return recommendation;
    }
}
