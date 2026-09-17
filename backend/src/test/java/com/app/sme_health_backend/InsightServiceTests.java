package com.app.sme_health_backend;

import com.app.sme_health_backend.insight.entity.Insight;
import com.app.sme_health_backend.insight.repository.InsightRepository;
import com.app.sme_health_backend.insight.service.InsightService;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
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
class InsightServiceTests {

    @Mock
    private InsightRepository insightRepository;

    @Mock
    private BusinessProfileRepository businessProfileRepository;

    @Mock
    private ScoringService scoringService;

    private InsightService insightService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        insightService = new InsightService(
                insightRepository,
                businessProfileRepository,
                scoringService
        );
        userId = UUID.randomUUID();
    }

    @Test
    void shouldGenerateInsightsFromScoreResult() {
        ScoreResult scoreResult = scoreResult(
                new BigDecimal("48.00"),
                new BigDecimal("0.65"),
                "profitability"
        );

        List<Insight> insights = insightService.generateInsights(scoreResult);

        assertEquals(3, insights.size());
        assertEquals("profitability", insights.get(0).getCategory());
        assertEquals("high", insights.get(0).getPriority());
        assertEquals("high", insights.get(1).getPriority());
        assertEquals("data_quality", insights.get(2).getCategory());
        assertEquals("high", insights.get(2).getPriority());
        assertEquals(userId, insights.get(0).getUserId());
        assertEquals("2026-09", insights.get(0).getMonth());
        assertFalse(insights.get(0).getText().isBlank());
    }

    @Test
    void shouldReturnStoredInsightsWithoutGeneratingDuplicates() {
        Insight storedInsight = insight(userId);

        when(businessProfileRepository.existsById(userId))
                .thenReturn(true);
        when(insightRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(storedInsight));

        List<Insight> result = insightService.getInsights(userId);

        assertEquals(List.of(storedInsight), result);
        verify(insightRepository).findByUserIdOrderByCreatedAtDesc(userId);
        verify(insightRepository, never()).saveAll(anyList());
    }

    @Test
    void shouldGenerateAndPersistMockInsightsWhenNoneExist() {
        when(businessProfileRepository.existsById(userId))
                .thenReturn(true);
        when(insightRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of());
        when(insightRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<Insight> result = insightService.getInsights(userId);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(userId, result.get(0).getUserId());
        assertEquals("liquidity", result.get(0).getCategory());
        assertEquals("high", result.get(0).getPriority());

        verify(insightRepository).findByUserIdOrderByCreatedAtDesc(userId);
        verify(insightRepository).saveAll(anyList());
    }

    @Test
    void shouldRejectInsightsForUnknownBusinessProfile() {
        when(businessProfileRepository.existsById(userId))
                .thenReturn(false);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> insightService.getInsights(userId)
        );

        assertEquals(
                "Business profile not found for this user",
                exception.getMessage()
        );

        verify(insightRepository, never())
                .findByUserIdOrderByCreatedAtDesc(userId);
        verify(insightRepository, never()).saveAll(anyList());
    }

    @Test
    void shouldRejectNullUserId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> insightService.getInsights(null)
        );

        assertEquals("User ID is required", exception.getMessage());
    }

    @Test
    void shouldRejectNullScoreResult() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> insightService.generateInsights(null)
        );

        assertEquals("Score result is required", exception.getMessage());
    }

    @Test
    void shouldGenerateAndPersistInsightsFromRealScoreResultWhenScoreExists() {
        ScoreResult realScore = scoreResult(
                new BigDecimal("76.00"),
                new BigDecimal("1.00"),
                "repayment"
        );
        realScore.setMonth("2026-08");

        when(businessProfileRepository.existsById(userId)).thenReturn(true);
        when(scoringService.getLatestScore(userId)).thenReturn(Optional.of(realScore));
        when(insightRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-08"))
                .thenReturn(List.of());
        when(insightRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of());
        when(insightRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<Insight> result = insightService.getInsights(userId);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("repayment", result.get(0).getCategory());
        assertEquals("2026-08", result.get(0).getMonth());
        assertTrue(result.get(0).getText().contains("repayment"));
        verify(insightRepository).saveAll(anyList());
    }

    @Test
    void shouldPrioritizeRealScoreResultOverOldMockPersistedInsights() {
        Insight oldMockInsight = insight(userId); // category = "liquidity"
        ScoreResult realScore = scoreResult(
                new BigDecimal("76.00"),
                new BigDecimal("1.00"),
                "repayment"
        );
        realScore.setMonth("2026-08");

        when(businessProfileRepository.existsById(userId)).thenReturn(true);
        when(scoringService.getLatestScore(userId)).thenReturn(Optional.of(realScore));
        when(insightRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-08"))
                .thenReturn(List.of());
        when(insightRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(oldMockInsight));
        when(insightRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<Insight> result = insightService.getInsights(userId);

        assertNotNull(result);
        assertEquals("repayment", result.get(0).getCategory());
        verify(insightRepository).deleteAll(List.of(oldMockInsight));
        verify(insightRepository).saveAll(anyList());
    }

    @Test
    void shouldReturnStoredRealInsightsWithoutGeneratingDuplicatesOnRepeatedReads() {
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(5);
        ScoreResult realScore = scoreResult(
                new BigDecimal("76.00"),
                new BigDecimal("1.00"),
                "repayment"
        );
        realScore.setMonth("2026-08");
        realScore.setComputedAt(t1);

        Insight i1 = new Insight();
        i1.setUserId(userId);
        i1.setMonth("2026-08");
        i1.setCategory("repayment");
        i1.setText("Focus first on repayment.");
        i1.setPriority("high");
        i1.setCreatedAt(t1.plusSeconds(10));

        Insight i2 = new Insight();
        i2.setUserId(userId);
        i2.setMonth("2026-08");
        i2.setCategory("overall_health");
        i2.setText("Overall health is solid.");
        i2.setPriority("medium");
        i2.setCreatedAt(t1.plusSeconds(10));

        Insight i3 = new Insight();
        i3.setUserId(userId);
        i3.setMonth("2026-08");
        i3.setCategory("data_quality");
        i3.setText("Data quality is good.");
        i3.setPriority("low");
        i3.setCreatedAt(t1.plusSeconds(10));

        List<Insight> storedList = List.of(i1, i2, i3);

        when(businessProfileRepository.existsById(userId)).thenReturn(true);
        when(scoringService.getLatestScore(userId)).thenReturn(Optional.of(realScore));
        when(insightRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-08"))
                .thenReturn(storedList);

        List<Insight> result = insightService.getInsights(userId);

        assertEquals(storedList, result);
        verify(insightRepository, never()).saveAll(anyList());
        verify(insightRepository, never()).deleteAll(anyList());
    }

    @Test
    void shouldRegenerateInsightsWhenPartialInsightsExist() {
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(5);
        ScoreResult realScore = scoreResult(
                new BigDecimal("76.00"),
                new BigDecimal("1.00"),
                "repayment"
        );
        realScore.setMonth("2026-08");
        realScore.setComputedAt(t1);

        Insight partialInsight = new Insight();
        partialInsight.setUserId(userId);
        partialInsight.setMonth("2026-08");
        partialInsight.setCategory("repayment");
        partialInsight.setCreatedAt(t1.plusSeconds(10));

        when(businessProfileRepository.existsById(userId)).thenReturn(true);
        when(scoringService.getLatestScore(userId)).thenReturn(Optional.of(realScore));
        when(insightRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-08"))
                .thenReturn(List.of(partialInsight));
        when(insightRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(partialInsight));
        when(insightRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<Insight> result = insightService.getInsights(userId);

        assertEquals(3, result.size());
        verify(insightRepository).deleteAll(List.of(partialInsight));
        verify(insightRepository).saveAll(anyList());
    }

    @Test
    void shouldPreserveLegitimateHistoricalInsightsFromOlderMonths() {
        Insight olderLegitimateInsight = new Insight();
        olderLegitimateInsight.setUserId(userId);
        olderLegitimateInsight.setMonth("2026-07");
        olderLegitimateInsight.setCategory("cashflow");
        olderLegitimateInsight.setCreatedAt(LocalDateTime.now().minusDays(30));

        ScoreResult realScore = scoreResult(
                new BigDecimal("76.00"),
                new BigDecimal("1.00"),
                "repayment"
        );
        realScore.setMonth("2026-08");

        when(businessProfileRepository.existsById(userId)).thenReturn(true);
        when(scoringService.getLatestScore(userId)).thenReturn(Optional.of(realScore));
        when(insightRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-08"))
                .thenReturn(List.of());
        when(insightRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(olderLegitimateInsight));
        when(insightRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<Insight> result = insightService.getInsights(userId);

        assertNotNull(result);
        assertEquals(3, result.size());
        verify(insightRepository, never()).deleteAll(anyList());
        verify(insightRepository).saveAll(anyList());
    }

    @Test
    void shouldRegenerateInsightsWhenScoreResultHasBeenRecomputed() {
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(10);
        LocalDateTime t2 = LocalDateTime.now(); // recomputed after stored insight

        Insight staleInsight = new Insight();
        staleInsight.setUserId(userId);
        staleInsight.setMonth("2026-08");
        staleInsight.setCategory("repayment");
        staleInsight.setText("Focus first on repayment.");
        staleInsight.setPriority("high");
        staleInsight.setCreatedAt(t1);

        ScoreResult rescored = scoreResult(
                new BigDecimal("60.00"),
                new BigDecimal("1.00"),
                "profitability"
        );
        rescored.setMonth("2026-08");
        rescored.setComputedAt(t2);

        when(businessProfileRepository.existsById(userId)).thenReturn(true);
        when(scoringService.getLatestScore(userId)).thenReturn(Optional.of(rescored));
        when(insightRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-08"))
                .thenReturn(List.of(staleInsight));
        when(insightRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(staleInsight));
        when(insightRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<Insight> result = insightService.getInsights(userId);

        assertNotNull(result);
        assertEquals("profitability", result.get(0).getCategory());
        verify(insightRepository).deleteAll(List.of(staleInsight));
        verify(insightRepository).saveAll(anyList());
    }

    @Test
    void shouldRespectCanonicalComponentNamesWhenGeneratingFromRealScoreResult() {
        List<String> canonicalComponents = List.of(
                "cashflow", "profitability", "repayment", "trend", "compliance"
        );

        for (String component : canonicalComponents) {
            ScoreResult realScore = scoreResult(
                    new BigDecimal("65.00"),
                    new BigDecimal("0.85"),
                    component
            );
            List<Insight> insights = insightService.generateInsights(realScore);
            assertEquals(component, insights.get(0).getCategory());
            assertTrue(insights.get(0).getText().contains(component));
        }
    }

    private ScoreResult scoreResult(
            BigDecimal compositeScore,
            BigDecimal dataCompleteness,
            String weakestComponent
    ) {
        ScoreResult result = new ScoreResult();
        result.setUserId(userId);
        result.setMonth("2026-09");
        result.setCompositeScore(compositeScore);
        result.setBand("needs_attention");
        result.setComponentScores(ComponentScoresDto.fromMap(
                Map.of(weakestComponent, new BigDecimal("48.00"))
        ));
        result.setWeakestComponent(weakestComponent);
        result.setDataCompleteness(dataCompleteness);
        result.setComputedAt(LocalDateTime.now());
        return result;
    }

    private Insight insight(UUID userId) {
        Insight insight = new Insight();

        insight.setUserId(userId);
        insight.setMonth("2026-09");
        insight.setText("Monitor cash availability.");
        insight.setCategory("liquidity");
        insight.setPriority("high");
        insight.setCreatedAt(LocalDateTime.now());

        return insight;
    }
}
