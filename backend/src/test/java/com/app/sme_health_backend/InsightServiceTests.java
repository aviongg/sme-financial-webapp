package com.app.sme_health_backend;

import com.app.sme_health_backend.insight.entity.Insight;
import com.app.sme_health_backend.insight.repository.InsightRepository;
import com.app.sme_health_backend.insight.service.InsightService;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
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

    private InsightService insightService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        insightService = new InsightService(
                insightRepository,
                businessProfileRepository
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
