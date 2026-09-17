package com.app.sme_health_backend;

import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.recommendation.entity.Recommendation;
import com.app.sme_health_backend.recommendation.repository.RecommendationRepository;
import com.app.sme_health_backend.recommendation.service.RecommendationService;
import com.app.sme_health_backend.scoring.dto.ComponentScoresDto;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import com.app.sme_health_backend.scoring.service.ScoringService;
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
import java.util.Optional;
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

    @Mock
    private ScoringService scoringService;

    private RecommendationService recommendationService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService(
                recommendationRepository,
                businessProfileRepository,
                scoringService
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

    @Test
    void shouldGenerateAndPersistRecommendationsFromRealScoreResultWhenScoreExists() {
        ScoreResult realScore = scoreResult(
                new BigDecimal("76.00"),
                "Stable",
                new BigDecimal("1.00"),
                "repayment"
        );
        realScore.setMonth("2026-08");

        when(businessProfileRepository.existsById(userId)).thenReturn(true);
        when(scoringService.getLatestScore(userId)).thenReturn(Optional.of(realScore));
        when(recommendationRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-08"))
                .thenReturn(List.of());
        when(recommendationRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of());
        when(recommendationRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<Recommendation> result = recommendationService.getRecommendations(userId);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("repayment", result.get(0).getCategory());
        assertEquals("2026-08", result.get(0).getMonth());
        assertTrue(result.get(0).getText().contains("repayment"));
        verify(recommendationRepository).saveAll(anyList());
    }

    @Test
    void shouldPrioritizeRealScoreResultOverOldMockPersistedRecommendations() {
        Recommendation oldMock = recommendation(userId);
        oldMock.setCategory("liquidity");
        oldMock.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        ScoreResult realScore = scoreResult(
                new BigDecimal("76.00"),
                "Stable",
                new BigDecimal("1.00"),
                "repayment"
        );
        realScore.setMonth("2026-08");

        when(businessProfileRepository.existsById(userId)).thenReturn(true);
        when(scoringService.getLatestScore(userId)).thenReturn(Optional.of(realScore));
        when(recommendationRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-08"))
                .thenReturn(List.of());
        when(recommendationRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(oldMock));
        when(recommendationRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<Recommendation> result = recommendationService.getRecommendations(userId);

        assertNotNull(result);
        assertEquals("repayment", result.get(0).getCategory());
        verify(recommendationRepository).deleteAll(List.of(oldMock));
        verify(recommendationRepository).saveAll(anyList());
    }

    @Test
    void shouldReturnStoredRealRecommendationsWithoutGeneratingDuplicatesOnRepeatedReads() {
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(5);
        ScoreResult realScore = scoreResult(
                new BigDecimal("76.00"),
                "Stable",
                new BigDecimal("1.00"),
                "repayment"
        );
        realScore.setMonth("2026-08");
        realScore.setComputedAt(t1);

        Recommendation r1 = new Recommendation();
        r1.setUserId(userId);
        r1.setMonth("2026-08");
        r1.setCategory("repayment");
        r1.setText("Track upcoming repayments.");
        r1.setPriority("high");
        r1.setCreatedAt(t1.plusSeconds(10));

        Recommendation r2 = new Recommendation();
        r2.setUserId(userId);
        r2.setMonth("2026-08");
        r2.setCategory("overall_health");
        r2.setText("Overall health is Stable.");
        r2.setPriority("medium");
        r2.setCreatedAt(t1.plusSeconds(10));

        Recommendation r3 = new Recommendation();
        r3.setUserId(userId);
        r3.setMonth("2026-08");
        r3.setCategory("data_quality");
        r3.setText("Keep records complete.");
        r3.setPriority("low");
        r3.setCreatedAt(t1.plusSeconds(10));

        List<Recommendation> storedList = List.of(r1, r2, r3);

        when(businessProfileRepository.existsById(userId)).thenReturn(true);
        when(scoringService.getLatestScore(userId)).thenReturn(Optional.of(realScore));
        when(recommendationRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-08"))
                .thenReturn(storedList);

        List<Recommendation> result = recommendationService.getRecommendations(userId);

        assertEquals(storedList, result);
        verify(recommendationRepository, never()).saveAll(anyList());
        verify(recommendationRepository, never()).deleteAll(anyList());
    }

    @Test
    void shouldRegenerateRecommendationsWhenPartialRecommendationsExist() {
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(5);
        ScoreResult realScore = scoreResult(
                new BigDecimal("76.00"),
                "Stable",
                new BigDecimal("1.00"),
                "repayment"
        );
        realScore.setMonth("2026-08");
        realScore.setComputedAt(t1);

        Recommendation partialRec = new Recommendation();
        partialRec.setUserId(userId);
        partialRec.setMonth("2026-08");
        partialRec.setCategory("repayment");
        partialRec.setCreatedAt(t1.plusSeconds(10));

        when(businessProfileRepository.existsById(userId)).thenReturn(true);
        when(scoringService.getLatestScore(userId)).thenReturn(Optional.of(realScore));
        when(recommendationRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-08"))
                .thenReturn(List.of(partialRec));
        when(recommendationRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(partialRec));
        when(recommendationRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<Recommendation> result = recommendationService.getRecommendations(userId);

        assertEquals(3, result.size());
        verify(recommendationRepository).deleteAll(List.of(partialRec));
        verify(recommendationRepository).saveAll(anyList());
    }

    @Test
    void shouldPreserveLegitimateHistoricalRecommendationsFromOlderMonths() {
        Recommendation olderLegitimateRec = new Recommendation();
        olderLegitimateRec.setUserId(userId);
        olderLegitimateRec.setMonth("2026-07");
        olderLegitimateRec.setCategory("cashflow");
        olderLegitimateRec.setCreatedAt(LocalDateTime.now().minusDays(30));

        ScoreResult realScore = scoreResult(
                new BigDecimal("76.00"),
                "Stable",
                new BigDecimal("1.00"),
                "repayment"
        );
        realScore.setMonth("2026-08");

        when(businessProfileRepository.existsById(userId)).thenReturn(true);
        when(scoringService.getLatestScore(userId)).thenReturn(Optional.of(realScore));
        when(recommendationRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-08"))
                .thenReturn(List.of());
        when(recommendationRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(olderLegitimateRec));
        when(recommendationRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<Recommendation> result = recommendationService.getRecommendations(userId);

        assertNotNull(result);
        assertEquals(3, result.size());
        verify(recommendationRepository, never()).deleteAll(anyList());
        verify(recommendationRepository).saveAll(anyList());
    }

    @Test
    void shouldRegenerateRecommendationsWhenScoreResultHasBeenRecomputed() {
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(10);
        LocalDateTime t2 = LocalDateTime.now();

        Recommendation staleRec = new Recommendation();
        staleRec.setUserId(userId);
        staleRec.setMonth("2026-08");
        staleRec.setCategory("repayment");
        staleRec.setText("Track upcoming repayments.");
        staleRec.setPriority("high");
        staleRec.setCreatedAt(t1);

        ScoreResult rescored = scoreResult(
                new BigDecimal("60.00"),
                "Stable",
                new BigDecimal("1.00"),
                "profitability"
        );
        rescored.setMonth("2026-08");
        rescored.setComputedAt(t2);

        when(businessProfileRepository.existsById(userId)).thenReturn(true);
        when(scoringService.getLatestScore(userId)).thenReturn(Optional.of(rescored));
        when(recommendationRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-08"))
                .thenReturn(List.of(staleRec));
        when(recommendationRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(staleRec));
        when(recommendationRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<Recommendation> result = recommendationService.getRecommendations(userId);

        assertNotNull(result);
        assertEquals("profitability", result.get(0).getCategory());
        verify(recommendationRepository).deleteAll(List.of(staleRec));
        verify(recommendationRepository).saveAll(anyList());
    }

    @Test
    void shouldRespectCanonicalComponentNamesWhenGeneratingFromRealScoreResult() {
        List<String> canonicalComponents = List.of(
                "cashflow", "profitability", "repayment", "trend", "compliance"
        );

        for (String component : canonicalComponents) {
            ScoreResult realScore = scoreResult(
                    new BigDecimal("65.00"),
                    "Stable",
                    new BigDecimal("0.85"),
                    component
            );
            List<Recommendation> recommendations =
                    recommendationService.generateRecommendations(realScore);
            assertEquals(component, recommendations.get(0).getCategory());
            assertTrue(recommendations.get(0).getText().contains(component));
        }
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
